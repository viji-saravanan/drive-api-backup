package com.aryasubramani.vijibackup.folderaccess.data

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryasubramani.vijibackup.folderaccess.data.db.LocalFolderMappingEntity
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationEntity
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationState
import com.aryasubramani.vijibackup.folderaccess.data.db.PendingFolderOperationType
import com.aryasubramani.vijibackup.folderaccess.data.db.VijiBackupDatabase
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderPickerResult
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerCompletion
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import com.aryasubramani.vijibackup.folderaccess.domain.LocalFolderMetadataReader
import com.aryasubramani.vijibackup.folderaccess.domain.RemoveFolderResult
import com.aryasubramani.vijibackup.folderaccess.saf.AcquireReadGrantResult
import com.aryasubramani.vijibackup.folderaccess.saf.GrantReleaseResult
import com.aryasubramani.vijibackup.folderaccess.saf.LocalFolderGrantManager
import com.aryasubramani.vijibackup.folderaccess.saf.PersistedFolderGrant
import com.aryasubramani.vijibackup.folderaccess.saf.WriteGrantRemovalResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomFolderMappingRepositoryInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: VijiBackupDatabase
    private lateinit var grants: RecordingGrantManager
    private lateinit var metadata: RecordingFolderMetadataReader
    private lateinit var repository: RoomFolderMappingRepository
    private val requestTokens = ArrayDeque(listOf("request-a", "request-b", "request-c"))
    private val mappingIds = ArrayDeque(listOf("mapping-a", "mapping-b", "mapping-c"))

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
        database = Room.databaseBuilder(
            context,
            VijiBackupDatabase::class.java,
            TEST_DATABASE_NAME,
        ).build()
        grants = RecordingGrantManager()
        metadata = RecordingFolderMetadataReader()
        repository = RoomFolderMappingRepository(
            dao = database.folderAccessDao(),
            grantManager = grants,
            metadataReader = metadata,
            ioDispatcher = Dispatchers.IO,
            requestTokenFactory = { requestTokens.removeFirst() },
            mappingIdFactory = { mappingIds.removeFirst() },
            clock = { NOW_EPOCH_MS },
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun beginAndCancelAreDurableExclusiveAndHaveNoMappingSideEffect() = runTest {
        val started = repository.beginAdd() as BeginFolderPickerResult.Started

        assertEquals("request-a", started.request.requestToken)
        assertEquals(BeginFolderPickerResult.Busy, repository.beginAdd())
        assertEquals(
            FolderPickerCompletion.Cancelled,
            repository.completePicker(
                requestToken = started.request.requestToken,
                selection = FolderPickerSelection.Cancelled,
            ),
        )
        assertNull(database.folderAccessDao().pendingOperation())
        assertTrue(database.folderAccessDao().observeMappings().first().isEmpty())
        assertTrue(grants.acquireCalls.isEmpty())
        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
    }

    @Test
    fun staleSelectionIsIgnoredBeforeGrantAcquisition() = runTest {
        repository.beginAdd()

        assertEquals(
            FolderPickerCompletion.Stale,
            repository.completePicker(
                requestToken = "stale-token",
                selection = selected("content://provider.test/tree/stale"),
            ),
        )
        assertTrue(grants.acquireCalls.isEmpty())
        assertEquals("request-a", database.folderAccessDao().pendingOperation()?.requestToken)
    }

    @Test
    fun selectedTreeRequiresValidUriReadAndPersistableFlags() = runTest {
        val started = repository.beginAdd() as BeginFolderPickerResult.Started

        assertEquals(
            FolderPickerCompletion.InvalidSelection,
            repository.completePicker(
                started.request.requestToken,
                selected("https://provider.test/tree/not-content"),
            ),
        )
        assertTrue(grants.acquireCalls.isEmpty())
        assertNull(database.folderAccessDao().pendingOperation())

        val second = repository.beginAdd() as BeginFolderPickerResult.Started
        assertEquals(
            FolderPickerCompletion.ReadPermissionMissing,
            repository.completePicker(
                second.request.requestToken,
                FolderPickerSelection.Selected(
                    treeUri = "content://provider.test/tree/no-read",
                    grantedFlags = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                ),
            ),
        )
        assertTrue(grants.acquireCalls.isEmpty())
        assertNull(database.folderAccessDao().pendingOperation())
    }

    @Test
    fun successfulSelectionAcquiresReadAndCommitsOneMapping() = runTest {
        val started = repository.beginAdd() as BeginFolderPickerResult.Started
        val treeUri = "content://provider.test/tree/selected"
        metadata.displayNames[treeUri] = "Selected folder"

        assertEquals(
            FolderPickerCompletion.Added(mappingId = "mapping-a"),
            repository.completePicker(started.request.requestToken, selected(treeUri)),
        )

        assertEquals(listOf(treeUri), grants.acquireCalls.map { it.treeUri })
        assertNull(database.folderAccessDao().pendingOperation())
        val mapping = database.folderAccessDao().mappingById("mapping-a")
        requireNotNull(mapping)
        assertEquals(treeUri, mapping.treeUri)
        assertEquals("Selected folder", mapping.sourceDisplayName)
        assertTrue(mapping.enabled)
    }

    @Test
    fun removeRevokesOnlyTheSelectedFolderGrantAndDeletesOnlyItsMapping() = runTest {
        val removedTreeUri = "content://provider.test/tree/remove"
        val retainedTreeUri = "content://provider.test/tree/retain"
        database.folderAccessDao().insertMapping(mapping("remove-mapping", removedTreeUri))
        database.folderAccessDao().insertMapping(mapping("retain-mapping", retainedTreeUri))
        grants.persisted = listOf(readGrant(removedTreeUri), readGrant(retainedTreeUri))

        assertEquals(RemoveFolderResult.Removed, repository.remove("remove-mapping"))

        assertNull(database.folderAccessDao().mappingById("remove-mapping"))
        assertEquals(
            retainedTreeUri,
            database.folderAccessDao().mappingById("retain-mapping")?.treeUri,
        )
        assertEquals(listOf(removedTreeUri), grants.releaseCalls)
    }

    @Test
    fun removeKeepsMappingWhenGrantRevocationCannotBeVerified() = runTest {
        val treeUri = "content://provider.test/tree/revocation-failure"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", treeUri))
        grants.persisted = listOf(readGrant(treeUri))
        grants.releaseResult = GrantReleaseResult.Failed

        assertEquals(RemoveFolderResult.GrantFailure, repository.remove("existing-mapping"))

        assertEquals(
            treeUri,
            database.folderAccessDao().mappingById("existing-mapping")?.treeUri,
        )
        assertEquals(listOf(treeUri), grants.releaseCalls)
    }

    @Test
    fun removeWaitsForPendingFolderSelectionWithoutChangingMappingOrGrant() = runTest {
        val treeUri = "content://provider.test/tree/pending-removal"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", treeUri))
        grants.persisted = listOf(readGrant(treeUri))
        insertPending(operation = PendingFolderOperationType.Add)

        assertEquals(RemoveFolderResult.Busy, repository.remove("existing-mapping"))

        assertEquals(
            treeUri,
            database.folderAccessDao().mappingById("existing-mapping")?.treeUri,
        )
        assertEquals("pending-token", database.folderAccessDao().pendingOperation()?.requestToken)
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun removeReportsMissingMappingWithoutTouchingAnyGrant() = runTest {
        assertEquals(RemoveFolderResult.MappingNotFound, repository.remove("missing-mapping"))

        assertTrue(grants.releaseCalls.isEmpty())
        assertTrue(database.folderAccessDao().allMappings().isEmpty())
    }

    @Test
    fun removePropagatesCancellationAndKeepsMappingRetryable() = runTest {
        val treeUri = "content://provider.test/tree/cancel-removal"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", treeUri))
        grants.persisted = listOf(readGrant(treeUri))
        grants.releaseFailure = CancellationException("test cancellation")
        var cancellation: CancellationException? = null

        try {
            repository.remove("existing-mapping")
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertTrue("Expected removal cancellation", cancellation != null)
        assertEquals(
            treeUri,
            database.folderAccessDao().mappingById("existing-mapping")?.treeUri,
        )
        assertEquals(listOf(treeUri), grants.releaseCalls)
    }

    @Test
    fun removeRetriesIdempotentGrantReleaseAfterDatabaseDeleteFailure() = runTest {
        val treeUri = "content://provider.test/tree/delete-failure"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", treeUri))
        grants.persisted = listOf(readGrant(treeUri))
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_mapping_delete
            BEFORE DELETE ON local_folder_mappings
            BEGIN
                SELECT RAISE(FAIL, 'test delete failure');
            END
            """.trimIndent(),
        )

        assertEquals(RemoveFolderResult.StorageFailure, repository.remove("existing-mapping"))
        assertEquals(
            treeUri,
            database.folderAccessDao().mappingById("existing-mapping")?.treeUri,
        )
        assertEquals(listOf(treeUri), grants.releaseCalls)

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_mapping_delete")

        assertEquals(RemoveFolderResult.Removed, repository.remove("existing-mapping"))
        assertNull(database.folderAccessDao().mappingById("existing-mapping"))
        assertEquals(listOf(treeUri, treeUri), grants.releaseCalls)
    }

    @Test
    fun initializationBackfillsExistingFolderNameWithoutReselection() = runTest {
        val treeUri = "content://provider.test/tree/existing"
        database.folderAccessDao().insertMapping(
            LocalFolderMappingEntity(
                id = "existing-mapping",
                treeUri = treeUri,
                sourceDisplayName = null,
                enabled = true,
            ),
        )
        grants.persisted = listOf(readGrant(treeUri))
        metadata.displayNames[treeUri] = "Existing folder"

        val mapping = repository.observeMappings().first().single()

        assertEquals("Existing folder", mapping.displayName)
        assertEquals(
            "Existing folder",
            database.folderAccessDao().mappingById("existing-mapping")?.sourceDisplayName,
        )
    }

    @Test
    fun metadataFailureDoesNotInterruptSuccessfulSelection() = runTest {
        val started = repository.beginAdd() as BeginFolderPickerResult.Started
        val treeUri = "content://provider.test/tree/metadata-failure"
        metadata.failure = IllegalStateException("test provider failure")

        assertEquals(
            FolderPickerCompletion.Added(mappingId = "mapping-a"),
            repository.completePicker(started.request.requestToken, selected(treeUri)),
        )

        assertNull(database.folderAccessDao().mappingById("mapping-a")?.sourceDisplayName)
        assertNull(database.folderAccessDao().pendingOperation())
    }

    @Test
    fun duplicateSelectionIsRejectedBeforeASecondGrantCall() = runTest {
        val first = repository.beginAdd() as BeginFolderPickerResult.Started
        val treeUri = "content://provider.test/tree/selected"
        assertTrue(repository.completePicker(first.request.requestToken, selected(treeUri)) is
            FolderPickerCompletion.Added)
        val second = repository.beginAdd() as BeginFolderPickerResult.Started

        assertEquals(
            FolderPickerCompletion.Duplicate,
            repository.completePicker(second.request.requestToken, selected(treeUri)),
        )
        assertEquals(1, grants.acquireCalls.size)
        assertNull(database.folderAccessDao().pendingOperation())
        assertEquals(1, database.folderAccessDao().observeMappings().first().size)
    }

    @Test
    fun cleanGrantRejectionLeavesNoStateAndDoesNotReleaseAnUnownedGrant() = runTest {
        grants.acquireResult = AcquireReadGrantResult.RejectedClean
        val started = repository.beginAdd() as BeginFolderPickerResult.Started
        val treeUri = "content://provider.test/tree/unavailable"

        assertEquals(
            FolderPickerCompletion.GrantFailure,
            repository.completePicker(
                started.request.requestToken,
                selected(treeUri),
            ),
        )
        assertTrue(database.folderAccessDao().observeMappings().first().isEmpty())
        assertNull(database.folderAccessDao().pendingOperation())
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun uncertainGrantFailureRetainsTombstoneWhenCleanupCannotBeVerified() = runTest {
        grants.acquireResult = AcquireReadGrantResult.CleanupRequired
        grants.releaseResult = GrantReleaseResult.Failed
        val started = repository.beginAdd() as BeginFolderPickerResult.Started
        val treeUri = "content://provider.test/tree/uncertain"

        assertEquals(
            FolderPickerCompletion.CleanupIncomplete,
            repository.completePicker(
                started.request.requestToken,
                selected(treeUri),
            ),
        )
        assertTrue(database.folderAccessDao().observeMappings().first().isEmpty())
        val pending = requireNotNull(database.folderAccessDao().pendingOperation())
        assertEquals(PendingFolderOperationState.Abandoning, pending.state)
        assertEquals(treeUri, pending.selectedTreeUri)
        assertEquals(listOf(treeUri), grants.releaseCalls)
    }

    @Test
    fun requestedOperationAtExpiryBoundaryRemainsPending() = runTest {
        insertPending(
            operation = PendingFolderOperationType.Add,
            createdAtEpochMs = NOW_EPOCH_MS - PENDING_TIMEOUT_MS,
        )

        assertEquals(BeginFolderPickerResult.Busy, repository.beginAdd())
        assertEquals("pending-token", database.folderAccessDao().pendingOperation()?.requestToken)
        assertEquals(1, grants.persistedGrantReads)
    }

    @Test
    fun requestedOperationOlderThanExpiryIsRemovedBeforeNewWork() = runTest {
        insertPending(
            operation = PendingFolderOperationType.Add,
            createdAtEpochMs = NOW_EPOCH_MS - PENDING_TIMEOUT_MS - 1L,
        )

        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
        assertEquals("request-a", database.folderAccessDao().pendingOperation()?.requestToken)
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun stagedSelectionWithoutPersistedGrantIsAbandonedBeforeNewWork() = runTest {
        val treeUri = "content://provider.test/tree/not-persisted"
        insertPending(
            operation = PendingFolderOperationType.Add,
            selectedTreeUri = treeUri,
        )

        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
        assertEquals("request-a", database.folderAccessDao().pendingOperation()?.requestToken)
        assertTrue(database.folderAccessDao().observeMappings().first().isEmpty())
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun stagedAddWithPersistedReadGrantIsCommittedDuringInitialization() = runTest {
        val treeUri = "content://provider.test/tree/recover-add"
        insertPending(
            operation = PendingFolderOperationType.Add,
            selectedTreeUri = treeUri,
        )
        grants.persisted = listOf(readGrant(treeUri))

        val mappings = repository.observeMappings().first()

        assertEquals(listOf("mapping-a"), mappings.map { it.id })
        assertEquals(treeUri, database.folderAccessDao().mappingById("mapping-a")?.treeUri)
        assertNull(database.folderAccessDao().pendingOperation())
        assertTrue(grants.acquireCalls.isEmpty())
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun stagedRepairWithPersistedReadGrantCommitsThenReleasesOldOrphan() = runTest {
        val oldTreeUri = "content://provider.test/tree/repair-old"
        val replacementTreeUri = "content://provider.test/tree/repair-new"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", oldTreeUri))
        insertPending(
            operation = PendingFolderOperationType.Repair,
            targetMappingId = "existing-mapping",
            selectedTreeUri = replacementTreeUri,
        )
        grants.persisted = listOf(readGrant(oldTreeUri), readGrant(replacementTreeUri))

        repository.observeMappings().first()

        assertEquals(
            replacementTreeUri,
            database.folderAccessDao().mappingById("existing-mapping")?.treeUri,
        )
        assertNull(database.folderAccessDao().pendingOperation())
        assertEquals(listOf(oldTreeUri), grants.releaseCalls)
    }

    @Test
    fun stagedSameUriRepairNeverReleasesReferencedGrant() = runTest {
        val treeUri = "content://provider.test/tree/repair-same"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", treeUri))
        insertPending(
            operation = PendingFolderOperationType.Repair,
            targetMappingId = "existing-mapping",
            selectedTreeUri = treeUri,
        )
        grants.persisted = listOf(readGrant(treeUri))

        repository.observeMappings().first()

        assertEquals(treeUri, database.folderAccessDao().mappingById("existing-mapping")?.treeUri)
        assertNull(database.folderAccessDao().pendingOperation())
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun expiredStagedSelectionIsReleasedInsteadOfResumed() = runTest {
        val treeUri = "content://provider.test/tree/expired-selection"
        insertPending(
            operation = PendingFolderOperationType.Add,
            selectedTreeUri = treeUri,
            createdAtEpochMs = NOW_EPOCH_MS - PENDING_TIMEOUT_MS - 1L,
        )
        grants.persisted = listOf(readGrant(treeUri))

        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)

        assertTrue(database.folderAccessDao().observeMappings().first().isEmpty())
        assertEquals(listOf(treeUri), grants.releaseCalls)
    }

    @Test
    fun abandoningCleanupFailureBlocksInitializationAndRetries() = runTest {
        val treeUri = "content://provider.test/tree/abandoning"
        insertPending(
            operation = PendingFolderOperationType.Add,
            selectedTreeUri = treeUri,
        )
        assertEquals(1, database.folderAccessDao().markPendingAbandoning("pending-token"))
        grants.persisted = listOf(readGrant(treeUri))
        grants.releaseResult = GrantReleaseResult.Failed

        assertEquals(BeginFolderPickerResult.StorageFailure, repository.beginAdd())
        assertEquals(
            PendingFolderOperationState.Abandoning,
            database.folderAccessDao().pendingOperation()?.state,
        )

        grants.releaseResult = GrantReleaseResult.Released
        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
        assertEquals(listOf(treeUri, treeUri), grants.releaseCalls)
    }

    @Test
    fun orphanCleanupFailureKeepsBarrierClosedUntilRetrySucceeds() = runTest {
        val treeUri = "content://provider.test/tree/orphan"
        grants.persisted = listOf(readGrant(treeUri))
        grants.releaseResult = GrantReleaseResult.Failed

        assertEquals(BeginFolderPickerResult.StorageFailure, repository.beginAdd())

        grants.releaseResult = GrantReleaseResult.Released
        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
        assertEquals(listOf(treeUri, treeUri), grants.releaseCalls)
        assertEquals(2, grants.persistedGrantReads)
    }

    @Test
    fun mappedGrantIsRetainedWhileOnlyUnreferencedGrantIsReleased() = runTest {
        val mappedTreeUri = "content://provider.test/tree/mapped"
        val orphanTreeUri = "content://provider.test/tree/orphan"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", mappedTreeUri))
        grants.persisted = listOf(readGrant(mappedTreeUri), readGrant(orphanTreeUri))

        repository.observeMappings().first()

        assertEquals(listOf(orphanTreeUri), grants.releaseCalls)
        assertEquals(mappedTreeUri, database.folderAccessDao().mappingById("existing-mapping")?.treeUri)
    }

    @Test
    fun persistedWriteAccessIsRemovedBeforeRecoveredSelectionCommits() = runTest {
        val treeUri = "content://provider.test/tree/legacy-write"
        insertPending(
            operation = PendingFolderOperationType.Add,
            selectedTreeUri = treeUri,
        )
        grants.persisted = listOf(
            PersistedFolderGrant(
                treeUri = treeUri,
                hasReadAccess = true,
                hasWriteAccess = true,
            ),
        )
        repository = RoomFolderMappingRepository(
            dao = database.folderAccessDao(),
            grantManager = grants,
            metadataReader = metadata,
            ioDispatcher = Dispatchers.IO,
            requestTokenFactory = { requestTokens.removeFirst() },
            mappingIdFactory = {
                grants.events += "commit"
                mappingIds.removeFirst()
            },
            clock = { NOW_EPOCH_MS },
        )

        repository.observeMappings().first()

        assertEquals(listOf(treeUri), grants.writeRemovalCalls)
        assertTrue(grants.events.indexOf("remove-write") < grants.events.indexOf("commit"))
        assertEquals(treeUri, database.folderAccessDao().mappingById("mapping-a")?.treeUri)
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun writeAccessCleanupFailureKeepsInitializationRetryable() = runTest {
        val treeUri = "content://provider.test/tree/write-cleanup-failure"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", treeUri))
        grants.persisted = listOf(
            PersistedFolderGrant(treeUri, hasReadAccess = true, hasWriteAccess = true),
        )
        grants.writeRemovalResult = WriteGrantRemovalResult.Failed

        assertEquals(BeginFolderPickerResult.StorageFailure, repository.beginAdd())

        grants.writeRemovalResult = WriteGrantRemovalResult.ReadOnlyConfirmed
        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
        assertEquals(listOf(treeUri, treeUri), grants.writeRemovalCalls)
    }

    @Test
    fun missingReadAfterWriteRemovalPreservesMappingWithoutOpeningGrant() = runTest {
        val treeUri = "content://provider.test/tree/read-lost"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", treeUri))
        grants.persisted = listOf(
            PersistedFolderGrant(treeUri, hasReadAccess = true, hasWriteAccess = true),
        )
        grants.writeRemovalResult = WriteGrantRemovalResult.ReadAccessMissing

        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)

        assertEquals(listOf(treeUri), grants.writeRemovalCalls)
        assertEquals(treeUri, database.folderAccessDao().mappingById("existing-mapping")?.treeUri)
        assertTrue(grants.releaseCalls.isEmpty())
    }

    @Test
    fun cancelledInitializationCanRetryWithoutRecreatingRepository() = runTest {
        grants.persistedFailure = CancellationException("test cancellation")

        assertInitializationCancelled { repository.beginAdd() }

        grants.persistedFailure = null
        assertTrue(repository.beginAdd() is BeginFolderPickerResult.Started)
        assertEquals(2, grants.persistedGrantReads)
    }

    @Test
    fun cancellationAfterRepairCommitRetriesOldGrantCleanup() = runTest {
        val oldTreeUri = "content://provider.test/tree/cancel-old"
        val replacementTreeUri = "content://provider.test/tree/cancel-new"
        database.folderAccessDao().insertMapping(mapping("existing-mapping", oldTreeUri))
        insertPending(
            operation = PendingFolderOperationType.Repair,
            targetMappingId = "existing-mapping",
            selectedTreeUri = replacementTreeUri,
        )
        grants.persisted = listOf(readGrant(oldTreeUri), readGrant(replacementTreeUri))
        grants.releaseFailure = CancellationException("test cancellation")

        assertInitializationCancelled { repository.observeMappings().first() }
        assertEquals(
            replacementTreeUri,
            database.folderAccessDao().mappingById("existing-mapping")?.treeUri,
        )
        assertNull(database.folderAccessDao().pendingOperation())

        grants.releaseFailure = null
        repository.observeMappings().first()
        assertEquals(listOf(oldTreeUri, oldTreeUri), grants.releaseCalls)
    }

    private suspend fun assertInitializationCancelled(block: suspend () -> Any?) {
        var cancellation: CancellationException? = null
        try {
            block()
        } catch (error: CancellationException) {
            cancellation = error
        }
        assertTrue("Expected initialization cancellation", cancellation != null)
    }

    private suspend fun insertPending(
        operation: PendingFolderOperationType,
        targetMappingId: String? = null,
        selectedTreeUri: String? = null,
        createdAtEpochMs: Long = NOW_EPOCH_MS,
    ) {
        val dao = database.folderAccessDao()
        assertTrue(
            dao.tryBeginPendingOperation(
                PendingFolderOperationEntity(
                    requestToken = "pending-token",
                    operation = operation,
                    targetMappingId = targetMappingId,
                    selectedTreeUri = null,
                    state = PendingFolderOperationState.Requested,
                    createdAtEpochMs = createdAtEpochMs,
                ),
            ),
        )
        if (selectedTreeUri != null) {
            assertEquals(1, dao.markSelectionReceived("pending-token", selectedTreeUri))
        }
    }

    private fun mapping(id: String, treeUri: String) = LocalFolderMappingEntity(
        id = id,
        treeUri = treeUri,
        sourceDisplayName = "Test folder",
        enabled = true,
    )

    private fun readGrant(treeUri: String) = PersistedFolderGrant(
        treeUri = treeUri,
        hasReadAccess = true,
        hasWriteAccess = false,
    )

    private fun selected(treeUri: String) = FolderPickerSelection.Selected(
        treeUri = treeUri,
        grantedFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
    )

    private companion object {
        const val TEST_DATABASE_NAME = "folder_mapping_repository_test.db"
        const val PENDING_TIMEOUT_MS = 24L * 60L * 60L * 1_000L
        const val NOW_EPOCH_MS = PENDING_TIMEOUT_MS * 2L
    }
}

private class RecordingGrantManager : LocalFolderGrantManager {
    val acquireCalls = mutableListOf<FolderPickerSelection.Selected>()
    val releaseCalls = mutableListOf<String>()
    val writeRemovalCalls = mutableListOf<String>()
    val events = mutableListOf<String>()
    var acquireResult: AcquireReadGrantResult = AcquireReadGrantResult.Acquired
    var releaseResult: GrantReleaseResult = GrantReleaseResult.Released
    var writeRemovalResult: WriteGrantRemovalResult = WriteGrantRemovalResult.ReadOnlyConfirmed
    var persisted: List<PersistedFolderGrant> = emptyList()
    var persistedGrantReads = 0
    var persistedFailure: Throwable? = null
    var releaseFailure: Throwable? = null

    override suspend fun persistedGrants(): List<PersistedFolderGrant> {
        persistedGrantReads += 1
        persistedFailure?.let { throw it }
        return persisted
    }

    override suspend fun acquireReadGrant(
        treeUri: String,
        grantedFlags: Int,
    ): AcquireReadGrantResult {
        acquireCalls += FolderPickerSelection.Selected(treeUri, grantedFlags)
        return acquireResult
    }

    override suspend fun removePersistedWriteAccess(
        treeUri: String,
    ): WriteGrantRemovalResult {
        writeRemovalCalls += treeUri
        events += "remove-write"
        return writeRemovalResult
    }

    override suspend fun releaseGrant(treeUri: String): GrantReleaseResult {
        releaseCalls += treeUri
        releaseFailure?.let { throw it }
        return releaseResult
    }
}

private class RecordingFolderMetadataReader : LocalFolderMetadataReader {
    val displayNames = mutableMapOf<String, String?>()
    val reads = mutableListOf<String>()
    var failure: Throwable? = null

    override suspend fun displayName(treeUri: String): String? {
        reads += treeUri
        failure?.let { throw it }
        return displayNames[treeUri]
    }
}
