package com.aryasubramani.vijibackup.downloadsaccess.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessHealth
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessManager
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessResult
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessSnapshot
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

data class DownloadsAccessUiState(
    val snapshot: DownloadsAccessSnapshot? = null,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val isAwaitingSettings: Boolean = false,
    val notice: DownloadsAccessNotice? = null,
)

enum class DownloadsAccessNotice {
    PermissionNotGranted,
    SettingsUnavailable,
    SourceRemoved,
    StorageFailure,
}

enum class DownloadsSettingsPurpose {
    ConfigureOrRepair,
    ReviewPermission,
}

data class DownloadsSettingsLaunch(
    val id: Long,
    val purpose: DownloadsSettingsPurpose,
)

class DownloadsAccessViewModel(
    private val manager: DownloadsAccessManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadsAccessUiState())
    private val settingsLaunchChannel = Channel<DownloadsSettingsLaunch>(Channel.BUFFERED)
    private var operation: Job? = null
    private var operationGeneration = 0L
    private var nextSettingsRequestId = 0L
    private var currentSettingsRequest: DownloadsSettingsLaunch? = null
    private var isActive = false

    val uiState: StateFlow<DownloadsAccessUiState> = mutableUiState.asStateFlow()
    val settingsLaunches: Flow<DownloadsSettingsLaunch> = settingsLaunchChannel.receiveAsFlow()

    fun activate() {
        if (isActive) return
        isActive = true
        refresh()
    }

    fun deactivate() {
        isActive = false
        operationGeneration += 1
        operation?.cancel()
        operation = null
        currentSettingsRequest = null
        mutableUiState.value = DownloadsAccessUiState()
    }

    fun refresh() {
        if (currentSettingsRequest != null) return
        runOperation(isMutation = false, operation = manager::refresh)
    }

    fun requestAccess() {
        if (!isActive || mutableUiState.value.isBusy || currentSettingsRequest != null) return
        when (mutableUiState.value.snapshot?.health) {
            DownloadsAccessHealth.NotConfigured,
            DownloadsAccessHealth.NeedsPermission,
            -> requestSettings(DownloadsSettingsPurpose.ConfigureOrRepair)
            DownloadsAccessHealth.PermissionGrantedButUnused -> runOperation(
                isMutation = true,
                operation = manager::configureFromCurrentPermission,
            )
            else -> Unit
        }
    }

    fun reviewPermission() {
        if (!isActive || mutableUiState.value.isBusy || currentSettingsRequest != null) return
        if (mutableUiState.value.snapshot?.health == DownloadsAccessHealth.UseSafPicker) return
        requestSettings(DownloadsSettingsPurpose.ReviewPermission)
    }

    fun onSettingsResult() {
        val request = currentSettingsRequest ?: return
        currentSettingsRequest = null
        mutableUiState.update { state -> state.copy(isAwaitingSettings = false) }
        if (!isActive) return
        when (request.purpose) {
            DownloadsSettingsPurpose.ConfigureOrRepair -> runOperation(
                isMutation = true,
                resultNotice = { result ->
                    val health = (result as? DownloadsAccessResult.Success)?.snapshot?.health
                    if (
                        health == DownloadsAccessHealth.NotConfigured ||
                        health == DownloadsAccessHealth.NeedsPermission
                    ) {
                        DownloadsAccessNotice.PermissionNotGranted
                    } else {
                        null
                    }
                },
                operation = manager::configureFromCurrentPermission,
            )
            DownloadsSettingsPurpose.ReviewPermission -> refresh()
        }
    }

    fun onSettingsLaunchFailed(requestId: Long) {
        if (currentSettingsRequest?.id != requestId) return
        currentSettingsRequest = null
        mutableUiState.update { state ->
            state.copy(
                isAwaitingSettings = false,
                notice = if (isActive) DownloadsAccessNotice.SettingsUnavailable else null,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        runOperation(isMutation = true) { manager.setEnabled(enabled) }
    }

    fun remove() {
        runOperation(
            isMutation = true,
            resultNotice = { result ->
                if (result is DownloadsAccessResult.Success) {
                    DownloadsAccessNotice.SourceRemoved
                } else {
                    null
                }
            },
            operation = manager::remove,
        )
    }

    private fun requestSettings(purpose: DownloadsSettingsPurpose) {
        val request = DownloadsSettingsLaunch(
            id = ++nextSettingsRequestId,
            purpose = purpose,
        )
        currentSettingsRequest = request
        mutableUiState.update { state ->
            state.copy(isAwaitingSettings = true, notice = null)
        }
        if (settingsLaunchChannel.trySend(request).isFailure) {
            onSettingsLaunchFailed(request.id)
        }
    }

    private fun runOperation(
        isMutation: Boolean,
        resultNotice: (DownloadsAccessResult) -> DownloadsAccessNotice? = { null },
        operation: suspend () -> DownloadsAccessResult,
    ) {
        if (!isActive || this.operation?.isActive == true) return
        val generation = ++operationGeneration
        this.operation = viewModelScope.launch {
            mutableUiState.update { state ->
                state.copy(
                    isLoading = !isMutation && state.snapshot == null,
                    isBusy = isMutation,
                    notice = null,
                )
            }
            val result = try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DownloadsAccessResult.PersistenceFailure
            }
            if (!isActive || generation != operationGeneration) return@launch
            mutableUiState.value = when (result) {
                is DownloadsAccessResult.Success -> DownloadsAccessUiState(
                    snapshot = result.snapshot,
                    isLoading = false,
                    notice = resultNotice(result),
                )
                DownloadsAccessResult.PersistenceFailure -> mutableUiState.value.copy(
                    isLoading = false,
                    isBusy = false,
                    notice = DownloadsAccessNotice.StorageFailure,
                )
            }
        }
    }

    class Factory(
        private val manager: DownloadsAccessManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DownloadsAccessViewModel::class.java))
            return DownloadsAccessViewModel(manager) as T
        }
    }
}
