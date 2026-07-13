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
        mappingObservation?.cancel()
        mappingObservation = null
    }

    fun addFolder() {
        beginPicker { repository.beginAdd() }
    }

    fun repairFolder(mappingId: String) {
        beginPicker { repository.beginRepair(mappingId) }
    }

    fun removeFolder(mappingId: String) = Unit

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
        if (!isActive || beginOperation?.isActive == true) return
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
