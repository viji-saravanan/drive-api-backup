package com.aryasubramani.vijibackup.folderaccess.data

import com.aryasubramani.vijibackup.folderaccess.data.db.FolderAccessDao
import com.aryasubramani.vijibackup.folderaccess.data.db.LocalFolderMappingEntity
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationEntity
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationState
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationType
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderPickerResult
import com.aryasubramani.vijibackup.folderaccess.domain.FolderAccessHealth
import com.aryasubramani.vijibackup.folderaccess.domain.PendingFolderCleanupResult
import com.aryasubramani.vijibackup.folderaccess.saf.AcquireReadGrantResult
import com.aryasubramani.vijibackup.folderaccess.saf.GrantReleaseResult
import com.aryasubramani.vijibackup.folderaccess.saf.LocalFolderGrantManager
import com.aryasubramani.vijibackup.folderaccess.saf.PersistedFolderGrant
import com.aryasubramani.vijibackup.folderaccess.saf.WriteGrantRemovalResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomFolderMappingRepositorySignOutRecoveryTest {
    @Test
    fun markReturningZeroIsRetriedAfterRepositoryRecreationAndAdmitsNewWork() = runTest {
        val intentStore = InMemorySignOutCleanupIntentStore()
        val dao = InMemoryFolderAccessDao(
            pending = pending("retiring-token"),
            markResults = ArrayDeque(listOf(0, 1)),
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstRepository = repository(dao, intentStore, "unused-token", dispatcher)
        assertEquals(
            PendingFolderCleanupResult.RetryRequired,
            firstRepository.prepareForSignOut(),
        )

        val recreatedRepository = repository(dao, intentStore, "new-token", dispatcher)
        assertTrue(recreatedRepository.beginAdd() is BeginFolderPickerResult.Started)
    }

    @Test
    fun markThrowingIsRetriedAfterRepositoryRecreationAndAdmitsNewWork() = runTest {
        val intentStore = InMemorySignOutCleanupIntentStore()
        val dao = InMemoryFolderAccessDao(pending("retiring-token"))
        dao.markFailure = IllegalStateException("storage failure")
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertEquals(
            PendingFolderCleanupResult.RetryRequired,
            repository(dao, intentStore, "unused-token", dispatcher).prepareForSignOut(),
        )

        dao.markFailure = null
        assertTrue(
            repository(dao, intentStore, "new-token", dispatcher).beginAdd() is
                BeginFolderPickerResult.Started,
        )
    }

    @Test
    fun ordinaryRequestedOperationSurvivesRepositoryRecreationWithoutIntent() = runTest {
        val dao = InMemoryFolderAccessDao(pending("live-token"))
        val intentStore = InMemorySignOutCleanupIntentStore()

        assertEquals(
            BeginFolderPickerResult.Busy,
            repository(
                dao,
                intentStore,
                "new-token",
                StandardTestDispatcher(testScheduler),
            ).beginAdd(),
        )
        assertEquals("live-token", dao.pendingForTest?.requestToken)
        assertEquals(0, dao.markCalls)
    }

    @Test
    fun staleIntentDoesNotAbandonNewerRequestedOperation() = runTest {
        val dao = InMemoryFolderAccessDao(pending("newer-token"))
        val intentStore = InMemorySignOutCleanupIntentStore("stale-token")

        assertEquals(
            BeginFolderPickerResult.Busy,
            repository(
                dao,
                intentStore,
                "another-token",
                StandardTestDispatcher(testScheduler),
            ).beginAdd(),
        )
        assertEquals("newer-token", dao.pendingForTest?.requestToken)
        assertEquals(0, dao.markCalls)
        assertEquals(null, intentStore.requestTokenForTest)
    }

    @Test
    fun durableStoreReadFailureKeepsInitializationRetryable() = runTest {
        val dao = InMemoryFolderAccessDao(null)
        val intentStore = InMemorySignOutCleanupIntentStore().apply {
            readFailure = IllegalStateException("read failure")
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = repository(dao, intentStore, "new-token", dispatcher)

        assertEquals(BeginFolderPickerResult.StorageFailure, repository.beginAdd())

        intentStore.readFailure = null
        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
    }

    @Test
    fun durableStoreWriteFailureStillAttemptsAndCompletesRoomCleanup() = runTest {
        val dao = InMemoryFolderAccessDao(pending("retiring-token"))
        val intentStore = InMemorySignOutCleanupIntentStore().apply {
            writeFailure = IllegalStateException("write failure")
        }
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertEquals(
            PendingFolderCleanupResult.Complete,
            repository(dao, intentStore, "unused-token", dispatcher).prepareForSignOut(),
        )
        assertEquals(null, dao.pendingForTest)
    }

    @Test
    fun durableStoreClearFailureIsRetriedAfterRepositoryRecreation() = runTest {
        val dao = InMemoryFolderAccessDao(pending("retiring-token"))
        val intentStore = InMemorySignOutCleanupIntentStore().apply {
            clearFailure = IllegalStateException("clear failure")
        }
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertEquals(
            PendingFolderCleanupResult.RetryRequired,
            repository(dao, intentStore, "unused-token", dispatcher).prepareForSignOut(),
        )
        assertEquals(null, dao.pendingForTest)

        intentStore.clearFailure = null
        assertTrue(
            repository(dao, intentStore, "new-token", dispatcher).beginAdd() is
                BeginFolderPickerResult.Started,
        )
    }

    private fun repository(
        dao: FolderAccessDao,
        intentStore: SignOutCleanupIntentStore,
        nextToken: String,
        dispatcher: TestDispatcher,
    ) = RoomFolderMappingRepository(
        dao = dao,
        signOutCleanupIntentStore = intentStore,
        grantManager = EmptyGrantManager,
        metadataReader = { null },
        accessValidator = { FolderAccessHealth.TemporarilyUnavailable },
        scanner = { flowOf() },
        ioDispatcher = dispatcher,
        requestTokenFactory = { nextToken },
        mappingIdFactory = { "mapping-id" },
        clock = { 1L },
    )

    private fun pending(token: String) = PendingFolderOperationEntity(
        requestToken = token,
        operation = PendingFolderOperationType.Add,
        targetMappingId = null,
        selectedTreeUri = null,
        state = PendingFolderOperationState.Requested,
        createdAtEpochMs = 0L,
    )
}

private class InMemorySignOutCleanupIntentStore(
    private var requestToken: String? = null,
) : SignOutCleanupIntentStore {
    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null
    var clearFailure: Throwable? = null
    val requestTokenForTest: String?
        get() = requestToken

    override suspend fun readRequestToken(): String? {
        readFailure?.let { throw it }
        return requestToken
    }

    override suspend fun writeRequestToken(requestToken: String) {
        writeFailure?.let { throw it }
        this.requestToken = requestToken
    }

    override suspend fun clearRequestToken(requestToken: String): Boolean {
        clearFailure?.let { throw it }
        if (this.requestToken != null && this.requestToken != requestToken) return false
        this.requestToken = null
        return true
    }
}

private object EmptyGrantManager : LocalFolderGrantManager {
    override suspend fun persistedGrants(): List<PersistedFolderGrant> = emptyList()
    override suspend fun acquireReadGrant(treeUri: String, grantedFlags: Int) =
        AcquireReadGrantResult.Acquired
    override suspend fun removePersistedWriteAccess(treeUri: String) =
        WriteGrantRemovalResult.ReadOnlyConfirmed
    override suspend fun releaseGrant(treeUri: String) = GrantReleaseResult.Released
}

private class InMemoryFolderAccessDao(
    pending: PendingFolderOperationEntity?,
    private val markResults: ArrayDeque<Int> = ArrayDeque(),
) : FolderAccessDao() {
    private val mappings = linkedMapOf<String, LocalFolderMappingEntity>()
    private val observedMappings = MutableStateFlow<List<LocalFolderMappingEntity>>(emptyList())
    private var pending = pending
    var markFailure: Throwable? = null
    var markCalls = 0
        private set
    val pendingForTest: PendingFolderOperationEntity?
        get() = pending

    override fun observeMappings(): Flow<List<LocalFolderMappingEntity>> = observedMappings
    override suspend fun allMappings(): List<LocalFolderMappingEntity> = mappings.values.toList()
    override suspend fun mappingById(mappingId: String) = mappings[mappingId]
    override suspend fun mappingByTreeUri(treeUri: String) =
        mappings.values.firstOrNull { it.treeUri == treeUri }

    override suspend fun insertMapping(mapping: LocalFolderMappingEntity) {
        mappings[mapping.id] = mapping
        observedMappings.value = mappings.values.toList()
    }

    override suspend fun updateMappingEnabled(mappingId: String, enabled: Boolean): Int = 0
    override suspend fun updateMappingDisplayName(mappingId: String, displayName: String): Int = 0
    override suspend fun deleteMapping(mappingId: String): Int = 0
    override suspend fun allPendingOperations(): List<PendingFolderOperationEntity> =
        listOfNotNull(pending)

    override suspend fun insertPendingOperation(operation: PendingFolderOperationEntity): Long {
        if (pending != null) return -1L
        pending = operation
        return 1L
    }

    override suspend fun markSelectionReceived(requestToken: String, selectedTreeUri: String): Int = 0

    override suspend fun markPendingAbandoning(requestToken: String): Int {
        markCalls += 1
        markFailure?.let { throw it }
        val result = markResults.removeFirstOrNull() ?: 1
        if (result == 1 && pending?.requestToken == requestToken) {
            pending = pending?.copy(state = PendingFolderOperationState.Abandoning)
        }
        return result
    }

    override suspend fun cancelRequestedOperation(requestToken: String): Int = 0

    override suspend fun deleteAbandoningOperation(requestToken: String): Int {
        if (
            pending?.requestToken != requestToken ||
            pending?.state != PendingFolderOperationState.Abandoning
        ) {
            return 0
        }
        pending = null
        return 1
    }

    override suspend fun deleteSelectionReceivedForCommit(requestToken: String): Int = 0
    override suspend fun updateMappingTreeUri(
        mappingId: String,
        replacementTreeUri: String,
        replacementDisplayName: String?,
    ): Int = 0
}
