package com.aryasubramani.vijibackup.folderaccess.presentation

import com.aryasubramani.vijibackup.auth.presentation.MainDispatcherRule
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderPickerResult
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMappingRepository
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerCompletion
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerLaunch
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import com.aryasubramani.vijibackup.folderaccess.domain.RemoveFolderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FolderAccessViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observedMappingsBecomeReadyWithoutExposingStorageIdentifiers() = runTest {
        val repository = FakeFolderMappingRepository().apply {
            mappings.value = listOf(
                FolderMapping(
                    id = "mapping-a",
                    displayName = null,
                    enabled = true,
                ),
            )
        }
        val viewModel = FolderAccessViewModel(repository)

        assertEquals(0, repository.observeCalls)
        viewModel.activate()
        runCurrent()

        assertEquals(
            FolderAccessUiState(
                mappings = repository.mappings.value,
                isLoading = false,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun mappingObservationFailureBecomesNonSensitiveStorageNotice() = runTest {
        val repository = FakeFolderMappingRepository().apply {
            observationFailure = IllegalStateException("sensitive provider detail")
        }
        val viewModel = FolderAccessViewModel(repository)

        viewModel.activate()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(FolderAccessNotice.StorageFailure, viewModel.uiState.value.notice)
    }

    @Test
    fun addEmitsExactOpaqueLaunchOnceWhileBeginIsRunning() = runTest {
        val launch = FolderPickerLaunch(
            requestToken = "opaque-request-token",
            initialTreeUri = null,
        )
        val beginGate = CompletableDeferred<Unit>()
        val repository = FakeFolderMappingRepository().apply {
            beginAddResult = BeginFolderPickerResult.Started(launch)
            this.beginGate = beginGate
        }
        val viewModel = FolderAccessViewModel(repository)
        viewModel.activate()
        runCurrent()

        viewModel.addFolder()
        viewModel.addFolder()
        runCurrent()

        assertEquals(1, repository.beginAddCalls)
        beginGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(launch, viewModel.pickerLaunches.first())
    }

    @Test
    fun beginOutcomesHaveDistinctNoticesAndRepairForwardsMappingId() = runTest {
        val repository = FakeFolderMappingRepository()
        val viewModel = FolderAccessViewModel(repository)
        viewModel.activate()
        runCurrent()

        val addCases = listOf(
            BeginFolderPickerResult.Busy to FolderAccessNotice.PickerBusy,
            BeginFolderPickerResult.MappingNotFound to FolderAccessNotice.MappingMissing,
            BeginFolderPickerResult.StorageFailure to FolderAccessNotice.StorageFailure,
        )
        addCases.forEach { (result, expectedNotice) ->
            repository.beginAddResult = result
            viewModel.addFolder()
            advanceUntilIdle()
            assertEquals(expectedNotice, viewModel.uiState.value.notice)
        }

        repository.beginRepairResult = BeginFolderPickerResult.MappingNotFound
        viewModel.repairFolder("mapping-a")
        advanceUntilIdle()

        assertEquals(listOf("mapping-a"), repository.repairCalls)
        assertEquals(FolderAccessNotice.MappingMissing, viewModel.uiState.value.notice)
    }

    @Test
    fun removeForwardsOneMappingAndExposesProgressUntilSuccessfulCompletion() = runTest {
        val removeGate = CompletableDeferred<Unit>()
        val repository = FakeFolderMappingRepository().apply {
            removeResult = RemoveFolderResult.Removed
            this.removeGate = removeGate
        }
        val viewModel = FolderAccessViewModel(repository)
        viewModel.activate()
        runCurrent()

        viewModel.removeFolder("mapping-a")
        viewModel.removeFolder("mapping-b")
        viewModel.addFolder()
        viewModel.repairFolder("mapping-c")
        runCurrent()

        assertEquals(listOf("mapping-a"), repository.removeCalls)
        assertEquals(0, repository.beginAddCalls)
        assertTrue(repository.repairCalls.isEmpty())
        assertEquals("mapping-a", viewModel.uiState.value.removingMappingId)

        removeGate.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.removingMappingId)
        assertEquals(FolderAccessNotice.FolderRemoved, viewModel.uiState.value.notice)
    }

    @Test
    fun everyRemoveOutcomeHasAnExplicitNoticeAndClearsProgress() = runTest {
        val repository = FakeFolderMappingRepository()
        val viewModel = FolderAccessViewModel(repository)
        viewModel.activate()
        runCurrent()
        val cases = listOf(
            RemoveFolderResult.MappingNotFound to FolderAccessNotice.MappingMissing,
            RemoveFolderResult.Busy to FolderAccessNotice.PickerBusy,
            RemoveFolderResult.GrantFailure to FolderAccessNotice.RemovalGrantFailure,
            RemoveFolderResult.StorageFailure to FolderAccessNotice.StorageFailure,
        )

        cases.forEachIndexed { index, (result, expectedNotice) ->
            repository.removeResult = result

            viewModel.removeFolder("mapping-$index")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.removingMappingId)
            assertEquals(expectedNotice, viewModel.uiState.value.notice)
        }
        assertEquals(cases.indices.map { "mapping-$it" }, repository.removeCalls)
    }

    @Test
    fun removeExceptionBecomesNonSensitiveStorageNotice() = runTest {
        val repository = FakeFolderMappingRepository().apply {
            removeFailure = IllegalStateException("sensitive provider detail")
        }
        val viewModel = FolderAccessViewModel(repository)
        viewModel.activate()
        runCurrent()

        viewModel.removeFolder("mapping-a")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.removingMappingId)
        assertEquals(FolderAccessNotice.StorageFailure, viewModel.uiState.value.notice)
    }

    @Test
    fun deactivationCancelsRemovalClearsProgressAndBlocksFurtherRemoval() = runTest {
        val removeGate = CompletableDeferred<Unit>()
        val repository = FakeFolderMappingRepository().apply {
            removeResult = RemoveFolderResult.Removed
            this.removeGate = removeGate
        }
        val viewModel = FolderAccessViewModel(repository)
        viewModel.activate()
        runCurrent()

        viewModel.removeFolder("mapping-a")
        runCurrent()
        assertEquals("mapping-a", viewModel.uiState.value.removingMappingId)

        viewModel.deactivate()
        advanceUntilIdle()
        viewModel.removeFolder("mapping-b")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.removingMappingId)
        assertNull(viewModel.uiState.value.notice)
        assertEquals(listOf("mapping-a"), repository.removeCalls)
    }

    @Test
    fun lateCancelledRemovalCannotClearANewerRemoval() = runTest {
        val firstEntered = CompletableDeferred<Unit>()
        val finishCancelledFirst = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        val repository = FakeFolderMappingRepository().apply {
            removeHandler = { mappingId ->
                when (mappingId) {
                    "mapping-a" -> {
                        firstEntered.complete(Unit)
                        withContext(NonCancellable) {
                            finishCancelledFirst.await()
                        }
                        throw CancellationException("cancelled first removal")
                    }
                    "mapping-b" -> {
                        finishSecond.await()
                        RemoveFolderResult.Removed
                    }
                    else -> RemoveFolderResult.Removed
                }
            }
        }
        val viewModel = FolderAccessViewModel(repository)
        viewModel.activate()
        runCurrent()

        viewModel.removeFolder("mapping-a")
        runCurrent()
        assertTrue(firstEntered.isCompleted)

        viewModel.deactivate()
        viewModel.activate()
        viewModel.removeFolder("mapping-b")
        runCurrent()
        assertEquals("mapping-b", viewModel.uiState.value.removingMappingId)

        finishCancelledFirst.complete(Unit)
        runCurrent()

        assertEquals("mapping-b", viewModel.uiState.value.removingMappingId)
        viewModel.removeFolder("mapping-c")
        runCurrent()
        assertEquals(listOf("mapping-a", "mapping-b"), repository.removeCalls)

        finishSecond.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.removingMappingId)
        assertEquals(FolderAccessNotice.FolderRemoved, viewModel.uiState.value.notice)
    }

    @Test
    fun everyPickerCompletionHasAnExplicitNoticeAndForwardsExactSelection() = runTest {
        val repository = FakeFolderMappingRepository()
        val viewModel = FolderAccessViewModel(repository)
        runCurrent()
        val selection = FolderPickerSelection.Selected(
            treeUri = "content://provider.test/tree/selected",
            grantedFlags = 65,
        )
        val cases = listOf(
            FolderPickerCompletion.Added("mapping-a") to FolderAccessNotice.FolderAdded,
            FolderPickerCompletion.Repaired("mapping-a") to FolderAccessNotice.FolderRepaired,
            FolderPickerCompletion.Cancelled to null,
            FolderPickerCompletion.Stale to FolderAccessNotice.SelectionExpired,
            FolderPickerCompletion.InvalidSelection to FolderAccessNotice.InvalidSelection,
            FolderPickerCompletion.ReadPermissionMissing to FolderAccessNotice.ReadPermissionMissing,
            FolderPickerCompletion.Duplicate to FolderAccessNotice.DuplicateFolder,
            FolderPickerCompletion.GrantFailure to FolderAccessNotice.GrantFailure,
            FolderPickerCompletion.StorageFailure to FolderAccessNotice.StorageFailure,
            FolderPickerCompletion.CleanupIncomplete to FolderAccessNotice.CleanupIncomplete,
        )

        cases.forEach { (completion, expectedNotice) ->
            repository.completionResult = completion

            assertEquals(
                completion,
                viewModel.completePicker("opaque-request-token", selection),
            )
            assertEquals(expectedNotice, viewModel.uiState.value.notice)
        }

        assertTrue(repository.completionCalls.all { it.first == "opaque-request-token" })
        assertTrue(repository.completionCalls.all { it.second == selection })
    }

    @Test
    fun cancellationPropagatesWithoutBeingConvertedToAUserNotice() = runTest {
        val repository = FakeFolderMappingRepository().apply {
            completionFailure = CancellationException("test cancellation")
        }
        val viewModel = FolderAccessViewModel(repository)
        runCurrent()
        var cancellation: CancellationException? = null

        try {
            viewModel.completePicker("opaque-request-token", FolderPickerSelection.Cancelled)
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertTrue(cancellation != null)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun inactiveViewModelDoesNotObserveOrBeginNewFolderWork() = runTest {
        val repository = FakeFolderMappingRepository().apply {
            beginAddResult = BeginFolderPickerResult.Started(
                FolderPickerLaunch("request-a", null),
            )
            beginRepairResult = BeginFolderPickerResult.Started(
                FolderPickerLaunch("request-b", null),
            )
        }
        val viewModel = FolderAccessViewModel(repository)

        viewModel.addFolder()
        viewModel.repairFolder("mapping-a")
        viewModel.removeFolder("mapping-a")
        advanceUntilIdle()

        assertEquals(0, repository.observeCalls)
        assertEquals(0, repository.beginAddCalls)
        assertTrue(repository.repairCalls.isEmpty())
        assertTrue(repository.removeCalls.isEmpty())
    }

    @Test
    fun activationIsIdempotentAndDeactivationBlocksFurtherActions() = runTest {
        val repository = FakeFolderMappingRepository()
        val viewModel = FolderAccessViewModel(repository)

        viewModel.activate()
        viewModel.activate()
        runCurrent()
        assertEquals(1, repository.observeCalls)

        viewModel.deactivate()
        repository.beginAddResult = BeginFolderPickerResult.StorageFailure
        viewModel.addFolder()
        viewModel.removeFolder("mapping-a")
        advanceUntilIdle()

        assertEquals(0, repository.beginAddCalls)
        assertTrue(repository.removeCalls.isEmpty())
    }
}

private class FakeFolderMappingRepository : FolderMappingRepository {
    val mappings = MutableStateFlow<List<FolderMapping>>(emptyList())
    var observationFailure: Throwable? = null
    var beginAddResult: BeginFolderPickerResult = BeginFolderPickerResult.StorageFailure
    var beginRepairResult: BeginFolderPickerResult = BeginFolderPickerResult.StorageFailure
    var completionResult: FolderPickerCompletion = FolderPickerCompletion.StorageFailure
    var completionFailure: Throwable? = null
    var removeResult: RemoveFolderResult = RemoveFolderResult.StorageFailure
    var removeFailure: Throwable? = null
    var removeHandler: (suspend (String) -> RemoveFolderResult)? = null
    var beginGate: CompletableDeferred<Unit>? = null
    var removeGate: CompletableDeferred<Unit>? = null
    var observeCalls = 0
    var beginAddCalls = 0
    val repairCalls = mutableListOf<String>()
    val completionCalls = mutableListOf<Pair<String, FolderPickerSelection>>()
    val removeCalls = mutableListOf<String>()

    override fun observeMappings(): Flow<List<FolderMapping>> {
        observeCalls += 1
        return observationFailure?.let { error ->
            flow { throw error }
        } ?: mappings
    }

    override suspend fun beginAdd(): BeginFolderPickerResult {
        beginAddCalls += 1
        beginGate?.await()
        return beginAddResult
    }

    override suspend fun beginRepair(mappingId: String): BeginFolderPickerResult {
        repairCalls += mappingId
        return beginRepairResult
    }

    override suspend fun completePicker(
        requestToken: String,
        selection: FolderPickerSelection,
    ): FolderPickerCompletion {
        completionCalls += requestToken to selection
        completionFailure?.let { throw it }
        return completionResult
    }

    override suspend fun remove(mappingId: String): RemoveFolderResult {
        removeCalls += mappingId
        removeHandler?.let { handler -> return handler(mappingId) }
        removeGate?.await()
        removeFailure?.let { throw it }
        return removeResult
    }
}
