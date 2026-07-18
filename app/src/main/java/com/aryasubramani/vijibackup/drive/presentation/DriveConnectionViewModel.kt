package com.aryasubramani.vijibackup.drive.presentation

import androidx.lifecycle.ViewModel
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.drive.domain.DriveConnectionResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

internal data class DriveConnectionUiState(
    val health: DriveConnectionHealth = DriveConnectionHealth.Inactive,
    val isBusy: Boolean = false,
    val isAwaitingAuthorization: Boolean = false,
    val notice: DriveConnectionNotice? = null,
)

internal enum class DriveConnectionHealth {
    Inactive,
    Checking,
    ConfigurationRequired,
    NeedsAuthorization,
    AccountMismatch,
    DestinationMissingOrInaccessible,
    DestinationNotFolder,
    DestinationTrashed,
    DestinationReadOnly,
    DestinationQuotaExceeded,
    TemporaryFailure,
    ProviderUnavailable,
    InvalidResponse,
    Ready,
}

internal enum class DriveConnectionNotice {
    AuthorizationCancelled,
    AuthorizationUnavailable,
}

internal enum class DriveConnectionRequestMode {
    SilentProbe,
    Interactive,
}

internal data class DriveConnectionRequest(
    val id: Long,
    val account: GoogleAccount,
    val mode: DriveConnectionRequestMode,
)

internal class DriveConnectionViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(DriveConnectionUiState())
    private val requestChannel = Channel<DriveConnectionRequest>(Channel.BUFFERED)
    private var activeAccount: GoogleAccount? = null
    private var currentRequest: DriveConnectionRequest? = null
    private var nextRequestId = 0L

    val uiState: StateFlow<DriveConnectionUiState> = mutableUiState.asStateFlow()
    val requests: Flow<DriveConnectionRequest> = requestChannel.receiveAsFlow()

    fun activate(account: GoogleAccount) {
        if (activeAccount == account) return

        activeAccount = account
        currentRequest = null
        beginRequest(account, DriveConnectionRequestMode.SilentProbe)
    }

    fun deactivate() {
        activeAccount = null
        currentRequest = null
        mutableUiState.value = DriveConnectionUiState()
    }

    fun connect() {
        val account = activeAccount ?: return
        if (currentRequest != null) return
        if (
            mutableUiState.value.health != DriveConnectionHealth.NeedsAuthorization &&
            mutableUiState.value.health != DriveConnectionHealth.ProviderUnavailable
        ) {
            return
        }

        beginRequest(account, DriveConnectionRequestMode.Interactive)
    }

    fun refresh() {
        val account = activeAccount ?: return
        if (currentRequest != null) return
        beginRequest(account, DriveConnectionRequestMode.SilentProbe)
    }

    fun onResult(requestId: Long, result: DriveConnectionResult) {
        if (!isCurrent(requestId)) return

        currentRequest = null
        mutableUiState.value = DriveConnectionUiState(health = result.toHealth())
    }

    fun onAuthorizationResolutionLaunched(requestId: Long): Boolean {
        val request = currentRequest ?: return false
        if (
            request.id != requestId ||
            request.mode != DriveConnectionRequestMode.Interactive
        ) {
            return false
        }

        mutableUiState.value = mutableUiState.value.copy(
            isBusy = true,
            isAwaitingAuthorization = true,
            notice = null,
        )
        return true
    }

    fun onAuthorizationCancelled(requestId: Long) {
        val request = currentRequest ?: return
        if (
            request.id != requestId ||
            request.mode != DriveConnectionRequestMode.Interactive ||
            !mutableUiState.value.isAwaitingAuthorization
        ) {
            return
        }

        currentRequest = null
        mutableUiState.value = DriveConnectionUiState(
            health = DriveConnectionHealth.NeedsAuthorization,
            notice = DriveConnectionNotice.AuthorizationCancelled,
        )
    }

    fun onAuthorizationLaunchFailed(requestId: Long) {
        val request = currentRequest ?: return
        if (
            request.id != requestId ||
            request.mode != DriveConnectionRequestMode.Interactive
        ) {
            return
        }

        currentRequest = null
        mutableUiState.value = DriveConnectionUiState(
            health = DriveConnectionHealth.ProviderUnavailable,
            notice = DriveConnectionNotice.AuthorizationUnavailable,
        )
    }

    fun pendingAuthorizationRequestId(): Long? {
        return pendingAuthorizationRequest()?.id
    }

    fun pendingAuthorizationRequest(): DriveConnectionRequest? {
        if (!mutableUiState.value.isAwaitingAuthorization) return null
        return currentRequest
            ?.takeIf { request -> request.mode == DriveConnectionRequestMode.Interactive }
    }

    fun claimAuthorizationResult(requestId: Long): DriveConnectionRequest? {
        val request = pendingAuthorizationRequest()
            ?.takeIf { pending -> pending.id == requestId }
            ?: return null
        mutableUiState.value = mutableUiState.value.copy(
            isAwaitingAuthorization = false,
        )
        return request
    }

    private fun beginRequest(
        account: GoogleAccount,
        mode: DriveConnectionRequestMode,
    ) {
        val request = DriveConnectionRequest(
            id = ++nextRequestId,
            account = account,
            mode = mode,
        )
        currentRequest = request
        mutableUiState.value = DriveConnectionUiState(
            health = DriveConnectionHealth.Checking,
            isBusy = true,
        )
        requestChannel.trySend(request)
    }

    private fun isCurrent(requestId: Long): Boolean =
        activeAccount != null && currentRequest?.id == requestId
}

private fun DriveConnectionResult.toHealth(): DriveConnectionHealth = when (this) {
    DriveConnectionResult.ConfigurationRequired -> DriveConnectionHealth.ConfigurationRequired
    DriveConnectionResult.NeedsAuthorization -> DriveConnectionHealth.NeedsAuthorization
    DriveConnectionResult.AccountMismatch -> DriveConnectionHealth.AccountMismatch
    DriveConnectionResult.DestinationMissingOrInaccessible ->
        DriveConnectionHealth.DestinationMissingOrInaccessible
    DriveConnectionResult.DestinationNotFolder -> DriveConnectionHealth.DestinationNotFolder
    DriveConnectionResult.DestinationTrashed -> DriveConnectionHealth.DestinationTrashed
    DriveConnectionResult.DestinationReadOnly -> DriveConnectionHealth.DestinationReadOnly
    DriveConnectionResult.DestinationQuotaExceeded ->
        DriveConnectionHealth.DestinationQuotaExceeded
    DriveConnectionResult.TemporaryFailure -> DriveConnectionHealth.TemporaryFailure
    DriveConnectionResult.ProviderUnavailable -> DriveConnectionHealth.ProviderUnavailable
    DriveConnectionResult.InvalidResponse -> DriveConnectionHealth.InvalidResponse
    DriveConnectionResult.Ready -> DriveConnectionHealth.Ready
}
