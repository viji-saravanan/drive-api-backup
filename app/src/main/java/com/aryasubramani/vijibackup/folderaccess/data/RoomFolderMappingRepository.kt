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
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderScanResult
import com.aryasubramani.vijibackup.folderaccess.domain.FolderAccessHealth
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMappingRepository
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerCompletion
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerLaunch
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import com.aryasubramani.vijibackup.folderaccess.domain.LocalFolderAccessValidator
import com.aryasubramani.vijibackup.folderaccess.domain.LocalFolderMetadataReader
import com.aryasubramani.vijibackup.folderaccess.domain.LocalFolderScanner
import com.aryasubramani.vijibackup.folderaccess.domain.PendingFolderCleanupResult
import com.aryasubramani.vijibackup.folderaccess.domain.RemoveFolderResult
import com.aryasubramani.vijibackup.folderaccess.domain.SetFolderEnabledResult
import com.aryasubramani.vijibackup.folderaccess.domain.ValidateFolderAccessResult
import com.aryasubramani.vijibackup.folderaccess.saf.AcquireReadGrantResult
import com.aryasubramani.vijibackup.folderaccess.saf.GrantReleaseResult
import com.aryasubramani.vijibackup.folderaccess.saf.LocalFolderGrantManager
import com.aryasubramani.vijibackup.folderaccess.saf.PersistedFolderGrant
import com.aryasubramani.vijibackup.folderaccess.saf.WriteGrantRemovalResult
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
    private val signOutCleanupIntentStore: SignOutCleanupIntentStore,
    private val grantManager: LocalFolderGrantManager,
    private val metadataReader: LocalFolderMetadataReader,
    private val accessValidator: LocalFolderAccessValidator,
    private val scanner: LocalFolderScanner,
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

    override suspend fun remove(mappingId: String): RemoveFolderResult = storageResult(
        failure = RemoveFolderResult.StorageFailure,
    ) {
        serialized {
            if (dao.pendingOperation() != null) {
                return@serialized RemoveFolderResult.Busy
            }
            val mapping = dao.mappingById(mappingId)
                ?: return@serialized RemoveFolderResult.MappingNotFound
            if (grantManager.releaseGrant(mapping.treeUri) != GrantReleaseResult.Released) {
                return@serialized RemoveFolderResult.GrantFailure
            }
            if (dao.deleteMapping(mapping.id) == 1) {
                RemoveFolderResult.Removed
            } else {
                RemoveFolderResult.StorageFailure
            }
        }
    }

    override suspend fun validate(mappingId: String): ValidateFolderAccessResult = storageResult(
        failure = ValidateFolderAccessResult.StorageFailure,
    ) {
        withContext(ioDispatcher) {
            ensureInitialized()
            val mapping = dao.mappingById(mappingId)
                ?: return@withContext ValidateFolderAccessResult.MappingNotFound
            when (val health = accessValidator.validate(mapping.treeUri)) {
                FolderAccessHealth.Checking -> ValidateFolderAccessResult.StorageFailure
                else -> ValidateFolderAccessResult.Found(health)
            }
        }
    }

    override suspend fun beginScan(mappingId: String): BeginFolderScanResult = storageResult(
        failure = BeginFolderScanResult.StorageFailure,
    ) {
        withContext(ioDispatcher) {
            ensureInitialized()
            val mapping = dao.mappingById(mappingId)
                ?: return@withContext BeginFolderScanResult.MappingNotFound
            when (val health = accessValidator.validate(mapping.treeUri)) {
                FolderAccessHealth.Ready -> BeginFolderScanResult.Ready(
                    scanner.scan(mapping.treeUri),
                )
                FolderAccessHealth.Checking -> BeginFolderScanResult.StorageFailure
                else -> BeginFolderScanResult.AccessUnavailable(health)
            }
        }
    }

    override suspend fun setEnabled(
        mappingId: String,
        enabled: Boolean,
    ): SetFolderEnabledResult = storageResult(
        failure = SetFolderEnabledResult.StorageFailure,
    ) {
        serialized {
            val mapping = dao.mappingById(mappingId)
                ?: return@serialized SetFolderEnabledResult.MappingNotFound
            if (mapping.enabled == enabled) {
                return@serialized SetFolderEnabledResult.Updated
            }
            if (dao.updateMappingEnabled(mappingId, enabled) == 1) {
                SetFolderEnabledResult.Updated
            } else {
                SetFolderEnabledResult.MappingNotFound
            }
        }
    }

    override suspend fun prepareForSignOut(): PendingFolderCleanupResult = withContext(ioDispatcher) {
        operationMutex.withLock {
            try {
                preparePendingCleanupForSignOut().also { result ->
                    if (result == PendingFolderCleanupResult.RetryRequired) {
                        initialized.set(false)
                    }
                }
            } catch (cancelled: CancellationException) {
                initialized.set(false)
                throw cancelled
            } catch (_: Exception) {
                initialized.set(false)
                PendingFolderCleanupResult.RetryRequired
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

    private suspend fun preparePendingCleanupForSignOut(): PendingFolderCleanupResult {
        val pending = dao.pendingOperation() ?: return PendingFolderCleanupResult.Complete
        val intentPersisted = try {
            signOutCleanupIntentStore.writeRequestToken(pending.requestToken)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (pending.state != PendingFolderOperationState.Abandoning &&
            dao.markPendingAbandoning(pending.requestToken) != 1
        ) {
            return PendingFolderCleanupResult.RetryRequired
        }

        val selectedTreeUri = pending.selectedTreeUri
        if (selectedTreeUri != null &&
            dao.mappingByTreeUri(selectedTreeUri) == null &&
            grantManager.persistedGrants().any { it.treeUri == selectedTreeUri }
        ) {
            if (grantManager.releaseGrant(selectedTreeUri) != GrantReleaseResult.Released) {
                return PendingFolderCleanupResult.RetryRequired
            }
        }

        if (dao.deleteAbandoningOperation(pending.requestToken) != 1) {
            return PendingFolderCleanupResult.RetryRequired
        }
        if (intentPersisted &&
            !signOutCleanupIntentStore.clearRequestToken(pending.requestToken)
        ) {
            return PendingFolderCleanupResult.RetryRequired
        }
        return PendingFolderCleanupResult.Complete
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
        val displayName = resolveDisplayName(treeUri)
        val committed = dao.commitAddedMapping(
            mapping = LocalFolderMappingEntity(
                id = mappingId,
                treeUri = treeUri,
                sourceDisplayName = displayName,
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
        val replacementDisplayName = resolveDisplayName(replacementTreeUri)
            ?: currentMapping.sourceDisplayName.takeIf {
                currentMapping.treeUri == replacementTreeUri
            }
        if (currentMapping.treeUri == replacementTreeUri) {
            return if (
                dao.commitRepairedMapping(
                    mappingId = currentMapping.id,
                    replacementTreeUri = replacementTreeUri,
                    replacementDisplayName = replacementDisplayName,
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
            replacementDisplayName = replacementDisplayName,
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
        withContext(ioDispatcher) {
            initializationMutex.withLock {
                if (initialized.get()) return@withLock
                operationMutex.withLock {
                    reconcileLocked()
                    initialized.set(true)
                }
            }
        }
    }

    private suspend fun reconcileLocked() {
        val signOutRequestToken = signOutCleanupIntentStore.readRequestToken()
        var pending = dao.pendingOperation()
        dao.allMappings()
        val persistedGrants = normalizePersistedGrants(grantManager.persistedGrants())
        val grantsByUri = persistedGrants.associateBy(PersistedFolderGrant::treeUri)
        val releasedUris = mutableSetOf<String>()

        if (signOutRequestToken != null) {
            if (pending?.requestToken == signOutRequestToken) {
                cleanupPending(
                    pending = pending,
                    grantsByUri = grantsByUri,
                    releasedUris = releasedUris,
                )
                pending = null
            }
            check(signOutCleanupIntentStore.clearRequestToken(signOutRequestToken)) {
                "Sign-out cleanup intent changed during reconciliation"
            }
        }

        if (pending != null) {
            when {
                pending.isExpired(clock()) -> cleanupPending(
                    pending = pending,
                    grantsByUri = grantsByUri,
                    releasedUris = releasedUris,
                )
                pending.state == PendingFolderOperationState.Requested -> Unit
                pending.state == PendingFolderOperationState.SelectionReceived -> {
                    val selectedTreeUri = requireNotNull(pending.selectedTreeUri)
                    if (grantsByUri[selectedTreeUri]?.hasReadAccess == true) {
                        resumeSelection(pending, selectedTreeUri, grantsByUri, releasedUris)
                    } else {
                        cleanupPending(pending, grantsByUri, releasedUris)
                    }
                }
                pending.state == PendingFolderOperationState.Abandoning -> cleanupPending(
                    pending = pending,
                    grantsByUri = grantsByUri,
                    releasedUris = releasedUris,
                )
            }
        }

        val mappedUris = dao.allMappings().mapTo(mutableSetOf()) { it.treeUri }
        val livePendingUri = dao.pendingOperation()
            ?.takeUnless { it.state == PendingFolderOperationState.Abandoning }
            ?.selectedTreeUri

        persistedGrants.forEach { grant ->
            if (
                grant.treeUri !in mappedUris &&
                grant.treeUri != livePendingUri &&
                grant.treeUri !in releasedUris
            ) {
                releaseOrFail(grant.treeUri)
                releasedUris += grant.treeUri
            }
        }

        refreshMappedDisplayNames(grantsByUri)
    }

    private suspend fun normalizePersistedGrants(
        persistedGrants: List<PersistedFolderGrant>,
    ): List<PersistedFolderGrant> = buildList {
        persistedGrants.forEach { grant ->
            if (!grant.hasWriteAccess) {
                add(grant)
                return@forEach
            }

            when (grantManager.removePersistedWriteAccess(grant.treeUri)) {
                WriteGrantRemovalResult.ReadOnlyConfirmed -> add(
                    grant.copy(
                        hasReadAccess = true,
                        hasWriteAccess = false,
                    ),
                )
                WriteGrantRemovalResult.ReadAccessMissing -> Unit
                WriteGrantRemovalResult.Failed -> error(
                    "Persisted write grant cleanup is incomplete",
                )
            }
        }
    }

    private suspend fun resumeSelection(
        pending: PendingFolderOperationEntity,
        selectedTreeUri: String,
        grantsByUri: Map<String, PersistedFolderGrant>,
        releasedUris: MutableSet<String>,
    ) {
        val committed = when (pending.operation) {
            PendingFolderOperationType.Add -> runCatching {
                val displayName = resolveDisplayName(selectedTreeUri)
                dao.commitAddedMapping(
                    mapping = LocalFolderMappingEntity(
                        id = mappingIdFactory(),
                        treeUri = selectedTreeUri,
                        sourceDisplayName = displayName,
                        enabled = true,
                    ),
                    requestToken = pending.requestToken,
                )
            }.getOrElse { error ->
                cleanupPendingIfPresent(pending, grantsByUri, releasedUris)
                throw error
            }
            PendingFolderOperationType.Repair -> {
                val targetMappingId = requireNotNull(pending.targetMappingId)
                val currentMapping = dao.mappingById(targetMappingId)
                val replacementDisplayName = resolveDisplayName(selectedTreeUri)
                    ?: currentMapping?.sourceDisplayName.takeIf {
                        currentMapping?.treeUri == selectedTreeUri
                    }
                dao.commitRepairedMapping(
                    mappingId = targetMappingId,
                    replacementTreeUri = selectedTreeUri,
                    replacementDisplayName = replacementDisplayName,
                    requestToken = pending.requestToken,
                )
            }
        }

        if (!committed) {
            cleanupPendingIfPresent(pending, grantsByUri, releasedUris)
        }
    }

    private suspend fun cleanupPendingIfPresent(
        expected: PendingFolderOperationEntity,
        grantsByUri: Map<String, PersistedFolderGrant>,
        releasedUris: MutableSet<String>,
    ) {
        val current = dao.pendingOperation()
        if (current?.requestToken == expected.requestToken) {
            cleanupPending(current, grantsByUri, releasedUris)
        }
    }

    private suspend fun cleanupPending(
        pending: PendingFolderOperationEntity,
        grantsByUri: Map<String, PersistedFolderGrant>,
        releasedUris: MutableSet<String>,
    ) {
        if (pending.state != PendingFolderOperationState.Abandoning) {
            check(dao.markPendingAbandoning(pending.requestToken) == 1) {
                "Pending folder operation changed during reconciliation"
            }
        }

        val selectedTreeUri = pending.selectedTreeUri
        val isDurablyReferenced = selectedTreeUri?.let { dao.mappingByTreeUri(it) } != null
        if (
            selectedTreeUri != null &&
            !isDurablyReferenced &&
            grantsByUri.containsKey(selectedTreeUri)
        ) {
            releaseOrFail(selectedTreeUri)
            releasedUris += selectedTreeUri
        }

        check(dao.deleteAbandoningOperation(pending.requestToken) == 1) {
            "Pending folder cleanup changed during reconciliation"
        }
    }

    private suspend fun releaseOrFail(treeUri: String) {
        check(grantManager.releaseGrant(treeUri) == GrantReleaseResult.Released) {
            "Folder grant cleanup is incomplete"
        }
    }

    private suspend fun refreshMappedDisplayNames(
        grantsByUri: Map<String, PersistedFolderGrant>,
    ) {
        dao.allMappings().forEach { mapping ->
            if (grantsByUri[mapping.treeUri]?.hasReadAccess != true) return@forEach
            val displayName = resolveDisplayName(mapping.treeUri) ?: return@forEach
            if (displayName == mapping.sourceDisplayName) return@forEach
            check(dao.updateMappingDisplayName(mapping.id, displayName) == 1) {
                "Folder mapping changed during metadata refresh"
            }
        }
    }

    private suspend fun resolveDisplayName(treeUri: String): String? = try {
        metadataReader.displayName(treeUri)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
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

    private fun PendingFolderOperationEntity.isExpired(nowEpochMs: Long): Boolean =
        nowEpochMs > createdAtEpochMs && nowEpochMs - createdAtEpochMs > PENDING_TIMEOUT_MS

    private companion object {
        const val PENDING_TIMEOUT_MS = 24L * 60L * 60L * 1_000L
    }
}
