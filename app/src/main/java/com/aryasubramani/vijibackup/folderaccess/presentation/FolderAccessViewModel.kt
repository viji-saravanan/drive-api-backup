package com.aryasubramani.vijibackup.folderaccess.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderPickerResult
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMappingRepository
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerCompletion
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerLaunch
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import com.aryasubramani.vijibackup.folderaccess.domain.RemoveFolderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderAccessUiState(
    val mappings: List<FolderMapping> = emptyList(),
    val isLoading: Boolean = true,
    val removingMappingId: String? = null,
    val notice: FolderAccessNotice? = null,
)

enum class FolderAccessNotice {
    PickerBusy,
    MappingMissing,
    FolderAdded,
    FolderRepaired,
    FolderRemoved,
    SelectionExpired,
    InvalidSelection,
    ReadPermissionMissing,
    DuplicateFolder,
    GrantFailure,
    RemovalGrantFailure,
    StorageFailure,
    CleanupIncomplete,
}

class FolderAccessViewModel(
    private val repository: FolderMappingRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(FolderAccessUiState())
    private val pickerLaunchChannel = Channel<FolderPickerLaunch>(Channel.BUFFERED)
    private var beginOperation: Job? = null
    private var removeOperation: Job? = null
    private var removeOperationGeneration = 0L
    private var mappingObservation: Job? = null
    private var isActive = false

    val uiState: StateFlow<FolderAccessUiState> = mutableUiState.asStateFlow()
    val pickerLaunches: Flow<FolderPickerLaunch> = pickerLaunchChannel.receiveAsFlow()

    fun activate() {
        isActive = true
        if (mappingObservation?.isActive == true) return
        mutableUiState.update { state -> state.copy(isLoading = true) }
        mappingObservation = observeMappings()
    }

    fun deactivate() {
        isActive = false
        beginOperation?.cancel()
        beginOperation = null
        removeOperationGeneration += 1
        removeOperation?.cancel()
        removeOperation = null
        mutableUiState.update { state -> state.copy(removingMappingId = null) }
        mappingObservation?.cancel()
        mappingObservation = null
    }

    fun addFolder() {
        beginPicker { repository.beginAdd() }
    }

    fun repairFolder(mappingId: String) {
        beginPicker { repository.beginRepair(mappingId) }
    }

    fun removeFolder(mappingId: String) {
        if (
            !isActive ||
            beginOperation?.isActive == true ||
            removeOperation?.isActive == true
        ) {
            return
        }
        val operationGeneration = ++removeOperationGeneration
        removeOperation = viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(
                    removingMappingId = mappingId,
                    notice = null,
                )
            }
            try {
                val result = try {
                    repository.remove(mappingId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    RemoveFolderResult.StorageFailure
                }
                if (operationGeneration == removeOperationGeneration) {
                    mutableUiState.update { state -> state.copy(notice = result.notice()) }
                }
            } finally {
                if (operationGeneration == removeOperationGeneration) {
                    mutableUiState.update { state -> state.copy(removingMappingId = null) }
                    removeOperation = null
                }
            }
        }
    }

    suspend fun completePicker(
        requestToken: String,
        selection: FolderPickerSelection,
    ): FolderPickerCompletion {
        val completion = try {
            repository.completePicker(requestToken, selection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FolderPickerCompletion.StorageFailure
        }
        mutableUiState.update { state ->
            state.copy(notice = completion.notice())
        }
        return completion
    }

    fun clearNotice() {
        mutableUiState.update { state -> state.copy(notice = null) }
    }

    private fun beginPicker(operation: suspend () -> BeginFolderPickerResult) {
        if (
            !isActive ||
            beginOperation?.isActive == true ||
            removeOperation?.isActive == true
        ) {
            return
        }
        beginOperation = viewModelScope.launch {
            val result = try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                BeginFolderPickerResult.StorageFailure
            }

            when (result) {
                is BeginFolderPickerResult.Started -> {
                    mutableUiState.update { state -> state.copy(notice = null) }
                    pickerLaunchChannel.send(result.request)
                }
                BeginFolderPickerResult.Busy -> showNotice(FolderAccessNotice.PickerBusy)
                BeginFolderPickerResult.MappingNotFound ->
                    showNotice(FolderAccessNotice.MappingMissing)
                BeginFolderPickerResult.StorageFailure ->
                    showNotice(FolderAccessNotice.StorageFailure)
            }
        }
    }

    private fun observeMappings(): Job = viewModelScope.launch {
        try {
            repository.observeMappings().collect { mappings ->
                mutableUiState.update { state ->
                    state.copy(
                        mappings = mappings,
                        isLoading = false,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableUiState.update { state ->
                state.copy(
                    isLoading = false,
                    notice = FolderAccessNotice.StorageFailure,
                )
            }
        }
    }

    private fun showNotice(notice: FolderAccessNotice) {
        mutableUiState.update { state -> state.copy(notice = notice) }
    }

    private fun FolderPickerCompletion.notice(): FolderAccessNotice? = when (this) {
        is FolderPickerCompletion.Added -> FolderAccessNotice.FolderAdded
        is FolderPickerCompletion.Repaired -> FolderAccessNotice.FolderRepaired
        FolderPickerCompletion.Cancelled -> null
        FolderPickerCompletion.Stale -> FolderAccessNotice.SelectionExpired
        FolderPickerCompletion.InvalidSelection -> FolderAccessNotice.InvalidSelection
        FolderPickerCompletion.ReadPermissionMissing -> FolderAccessNotice.ReadPermissionMissing
        FolderPickerCompletion.Duplicate -> FolderAccessNotice.DuplicateFolder
        FolderPickerCompletion.GrantFailure -> FolderAccessNotice.GrantFailure
        FolderPickerCompletion.StorageFailure -> FolderAccessNotice.StorageFailure
        FolderPickerCompletion.CleanupIncomplete -> FolderAccessNotice.CleanupIncomplete
    }

    private fun RemoveFolderResult.notice(): FolderAccessNotice = when (this) {
        RemoveFolderResult.Removed -> FolderAccessNotice.FolderRemoved
        RemoveFolderResult.MappingNotFound -> FolderAccessNotice.MappingMissing
        RemoveFolderResult.Busy -> FolderAccessNotice.PickerBusy
        RemoveFolderResult.GrantFailure -> FolderAccessNotice.RemovalGrantFailure
        RemoveFolderResult.StorageFailure -> FolderAccessNotice.StorageFailure
    }

    class Factory(
        private val repository: FolderMappingRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FolderAccessViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return FolderAccessViewModel(repository) as T
        }
    }
}
