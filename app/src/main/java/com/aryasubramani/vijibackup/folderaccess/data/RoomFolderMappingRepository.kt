package com.aryasubramani.vijibackup.folderaccess.data

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.aryasubramani.vijibackup.folderaccess.data.db.FolderAccessDao
import com.aryasubramani.vijibackup.folderaccess.data.db.LocalFolderMappingEntity
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationEntity
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationState
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationType
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderPickerResult
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMappingRepository
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerCompletion
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerLaunch
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import com.aryasubramani.vijibackup.folderaccess.saf.AcquireReadGrantResult
import com.aryasubramani.vijibackup.folderaccess.saf.GrantReleaseResult
import com.aryasubramani.vijibackup.folderaccess.saf.LocalFolderGrantManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomFolderMappingRepository(
    private val dao: FolderAccessDao,
    private val grantManager: LocalFolderGrantManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val requestTokenFactory: () -> String = { UUID.randomUUID().toString() },
    private val mappingIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) : FolderMappingRepository {
    private val initializationMutex = Mutex()
    private val operationMutex = Mutex()
    private val initialized = AtomicBoolean(false)

    override fun observeMappings(): Flow<List<FolderMapping>> = flow {
        ensureInitialized()
        emitAll(
            dao.observeMappings().map { mappings ->
                mappings.map { it.toDomain() }
            },
        )
    }

    override suspend fun beginAdd(): BeginFolderPickerResult = storageResult(
        failure = BeginFolderPickerResult.StorageFailure,
    ) {
        serialized {
            beginOperation(
                operation = PendingFolderOperationType.Add,
                targetMappingId = null,
                initialTreeUri = null,
            )
        }
    }

    override suspend fun beginRepair(mappingId: String): BeginFolderPickerResult = storageResult(
        failure = BeginFolderPickerResult.StorageFailure,
    ) {
        serialized {
            val mapping = dao.mappingById(mappingId)
                ?: return@serialized BeginFolderPickerResult.MappingNotFound
            beginOperation(
                operation = PendingFolderOperationType.Repair,
                targetMappingId = mapping.id,
                initialTreeUri = mapping.treeUri,
            )
        }
    }

    override suspend fun completePicker(
        requestToken: String,
        selection: FolderPickerSelection,
    ): FolderPickerCompletion = storageResult(
        failure = FolderPickerCompletion.StorageFailure,
    ) {
        serialized {
            val pending = dao.pendingOperation()
            if (pending?.requestToken != requestToken) {
                return@serialized FolderPickerCompletion.Stale
            }

            when (selection) {
                FolderPickerSelection.Cancelled -> {
                    if (dao.cancelRequestedOperation(requestToken) == 1) {
                        FolderPickerCompletion.Cancelled
                    } else {
                        FolderPickerCompletion.Stale
                    }
                }
                is FolderPickerSelection.Selected -> completeSelection(pending, selection)
            }
        }
    }

    private suspend fun beginOperation(
        operation: PendingFolderOperationType,
        targetMappingId: String?,
        initialTreeUri: String?,
    ): BeginFolderPickerResult {
        val requestToken = requestTokenFactory()
        val pending = PendingFolderOperationEntity(
            requestToken = requestToken,
            operation = operation,
            targetMappingId = targetMappingId,
            selectedTreeUri = null,
            state = PendingFolderOperationState.Requested,
            createdAtEpochMs = clock(),
        )
        return if (dao.tryBeginPendingOperation(pending)) {
            BeginFolderPickerResult.Started(
                FolderPickerLaunch(
                    requestToken = requestToken,
                    initialTreeUri = initialTreeUri,
                ),
            )
        } else {
            BeginFolderPickerResult.Busy
        }
    }

    private suspend fun completeSelection(
        pending: PendingFolderOperationEntity,
        selection: FolderPickerSelection.Selected,
    ): FolderPickerCompletion {
        if (!selection.treeUri.isValidTreeUri()) {
            dao.cancelRequestedOperation(pending.requestToken)
            return FolderPickerCompletion.InvalidSelection
        }

        val existing = dao.mappingByTreeUri(selection.treeUri)
        val repairTarget = pending.targetMappingId?.let { dao.mappingById(it) }
        if (
            pending.operation == PendingFolderOperationType.Add && existing != null ||
            pending.operation == PendingFolderOperationType.Repair &&
            existing != null && existing.id != pending.targetMappingId
        ) {
            dao.cancelRequestedOperation(pending.requestToken)
            return FolderPickerCompletion.Duplicate
        }
        if (pending.operation == PendingFolderOperationType.Repair && repairTarget == null) {
            dao.cancelRequestedOperation(pending.requestToken)
            return FolderPickerCompletion.Stale
        }

        if (
            dao.markSelectionReceived(
                requestToken = pending.requestToken,
                selectedTreeUri = selection.treeUri,
            ) != 1
        ) {
            return FolderPickerCompletion.Stale
        }

        if (!selection.grantedFlags.hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION) ||
            !selection.grantedFlags.hasFlag(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        ) {
            return if (abandonWithoutGrant(pending.requestToken)) {
                FolderPickerCompletion.ReadPermissionMissing
            } else {
                initialized.set(false)
                FolderPickerCompletion.CleanupIncomplete
            }
        }

        when (
            grantManager.acquireReadGrant(
                treeUri = selection.treeUri,
                grantedFlags = selection.grantedFlags,
            )
        ) {
            AcquireReadGrantResult.Acquired -> Unit
            AcquireReadGrantResult.RejectedClean -> {
                return if (abandonWithoutGrant(pending.requestToken)) {
                    FolderPickerCompletion.GrantFailure
                } else {
                    initialized.set(false)
                    FolderPickerCompletion.CleanupIncomplete
                }
            }
            AcquireReadGrantResult.CleanupRequired -> {
                return if (abandonAndRelease(pending.requestToken, selection.treeUri)) {
                    FolderPickerCompletion.GrantFailure
                } else {
                    FolderPickerCompletion.CleanupIncomplete
                }
            }
        }

        return try {
            when (pending.operation) {
                PendingFolderOperationType.Add -> commitAdd(pending, selection.treeUri)
                PendingFolderOperationType.Repair ->
                    commitRepair(pending, requireNotNull(repairTarget), selection.treeUri)
            }
        } catch (cancelled: CancellationException) {
            initialized.set(false)
            throw cancelled
        } catch (_: Exception) {
            if (abandonAndRelease(pending.requestToken, selection.treeUri)) {
                FolderPickerCompletion.StorageFailure
            } else {
                FolderPickerCompletion.CleanupIncomplete
            }
        }
    }

    private suspend fun commitAdd(
        pending: PendingFolderOperationEntity,
        treeUri: String,
    ): FolderPickerCompletion {
        val mappingId = mappingIdFactory()
        val committed = dao.commitAddedMapping(
            mapping = LocalFolderMappingEntity(
                id = mappingId,
                treeUri = treeUri,
                sourceDisplayName = null,
                enabled = true,
            ),
            requestToken = pending.requestToken,
        )
        if (!committed) {
            return if (abandonAndRelease(pending.requestToken, treeUri)) {
                FolderPickerCompletion.StorageFailure
            } else {
                FolderPickerCompletion.CleanupIncomplete
            }
        }
        return FolderPickerCompletion.Added(mappingId)
    }

    private suspend fun commitRepair(
        pending: PendingFolderOperationEntity,
        currentMapping: LocalFolderMappingEntity,
        replacementTreeUri: String,
    ): FolderPickerCompletion {
        if (currentMapping.treeUri == replacementTreeUri) {
            return if (
                dao.commitRepairedMapping(
                    mappingId = currentMapping.id,
                    replacementTreeUri = replacementTreeUri,
                    requestToken = pending.requestToken,
                )
            ) {
                FolderPickerCompletion.Repaired(currentMapping.id)
            } else {
                FolderPickerCompletion.StorageFailure
            }
        }

        val committed = dao.commitRepairedMapping(
            mappingId = currentMapping.id,
            replacementTreeUri = replacementTreeUri,
            requestToken = pending.requestToken,
        )
        if (!committed) {
            return if (abandonAndRelease(pending.requestToken, replacementTreeUri)) {
                FolderPickerCompletion.StorageFailure
            } else {
                FolderPickerCompletion.CleanupIncomplete
            }
        }

        return if (grantManager.releaseGrant(currentMapping.treeUri) == GrantReleaseResult.Released) {
            FolderPickerCompletion.Repaired(currentMapping.id)
        } else {
            initialized.set(false)
            FolderPickerCompletion.CleanupIncomplete
        }
    }

    private suspend fun abandonAndRelease(requestToken: String, treeUri: String): Boolean {
        dao.markPendingAbandoning(requestToken)
        if (dao.mappingByTreeUri(treeUri) != null) {
            return dao.deleteAbandoningOperation(requestToken) == 1
        }
        if (grantManager.releaseGrant(treeUri) != GrantReleaseResult.Released) {
            initialized.set(false)
            return false
        }
        return dao.deleteAbandoningOperation(requestToken) == 1
    }

    private suspend fun abandonWithoutGrant(requestToken: String): Boolean =
        dao.markPendingAbandoning(requestToken) == 1 &&
            dao.deleteAbandoningOperation(requestToken) == 1

    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(ioDispatcher) {
        ensureInitialized()
        operationMutex.withLock { block() }
    }

    private suspend fun ensureInitialized() {
        if (initialized.get()) return
        initializationMutex.withLock {
            if (initialized.get()) return
            operationMutex.withLock {
                dao.pendingOperation()
                initialized.set(true)
            }
        }
    }

    private suspend fun <T> storageResult(failure: T, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        failure
    }

    private fun LocalFolderMappingEntity.toDomain() = FolderMapping(
        id = id,
        displayName = sourceDisplayName,
        enabled = enabled,
    )

    private fun String.isValidTreeUri(): Boolean {
        val uri = runCatching(Uri::parse).getOrNull() ?: return false
        return uri.scheme == "content" && DocumentsContract.isTreeUri(uri)
    }

    private fun Int.hasFlag(flag: Int): Boolean = this and flag == flag
}
