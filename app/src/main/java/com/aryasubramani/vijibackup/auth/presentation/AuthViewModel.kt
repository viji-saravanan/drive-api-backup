package com.aryasubramani.vijibackup.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aryasubramani.vijibackup.auth.data.AuthSessionManager
import com.aryasubramani.vijibackup.auth.data.AuthorizeAccountResult
import com.aryasubramani.vijibackup.auth.data.LoadAuthSessionResult
import com.aryasubramani.vijibackup.auth.data.SignOutResult
import com.aryasubramani.vijibackup.auth.google.GoogleSignInMode
import com.aryasubramani.vijibackup.auth.google.GoogleSignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val sessionManager: AuthSessionManager,
    private val isGoogleSignInConfigured: Boolean,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<AuthUiState>(AuthUiState.Initializing)
    private var nextOperationId = 0L
    private var activeSignIn: ActiveSignIn? = null
    private var retryAction: RetryAction? = null

    val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

    init {
        initialize()
    }

    fun startAutomaticReauthentication() {
        val currentState = mutableUiState.value as? AuthUiState.ReauthenticationRequired ?: return
        if (!currentState.automaticAttemptPending) return

        beginSignIn(
            mode = GoogleSignInMode.AuthorizedAccounts,
            fallback = currentState.copy(automaticAttemptPending = false),
        )
    }

    fun signIn() {
        val currentState = mutableUiState.value
        val fallback = when (currentState) {
            is AuthUiState.SignedOut -> currentState
            is AuthUiState.Blocked -> currentState
            is AuthUiState.ReauthenticationRequired ->
                currentState.copy(automaticAttemptPending = false)
            else -> return
        }

        beginSignIn(mode = GoogleSignInMode.Explicit, fallback = fallback)
    }

    fun consumeSignInRequest(requestId: Long): GoogleSignInMode? {
        val operation = activeSignIn ?: return null
        val state = mutableUiState.value as? AuthUiState.SigningIn ?: return null
        if (operation.id != requestId || state.request?.id != requestId) return null

        mutableUiState.value = state.copy(request = null)
        return operation.mode
    }

    fun onSignInResult(requestId: Long, result: GoogleSignInResult) {
        val operation = activeSignIn?.takeIf { it.id == requestId } ?: return

        when (result) {
            is GoogleSignInResult.Success -> authorize(operation, result)
            GoogleSignInResult.Cancelled -> restoreAfterSignIn(operation)
            GoogleSignInResult.NoCredential -> {
                activeSignIn = null
                beginSignOut()
            }
            GoogleSignInResult.ConfigurationRequired -> {
                activeSignIn = null
                retryAction = null
                mutableUiState.value = AuthUiState.ConfigurationRequired
            }
            GoogleSignInResult.ProviderUnavailable -> showSignInError(
                operation = operation,
                reason = AuthError.ProviderUnavailable,
            )
            GoogleSignInResult.Interrupted -> showSignInError(
                operation = operation,
                reason = AuthError.Interrupted,
            )
            GoogleSignInResult.InvalidCredential -> showSignInError(
                operation = operation,
                reason = AuthError.InvalidCredential,
            )
            GoogleSignInResult.UnknownFailure -> showSignInError(
                operation = operation,
                reason = AuthError.Unknown,
            )
        }
    }

    fun onSignInInterrupted(requestId: Long) {
        val operation = activeSignIn?.takeIf { it.id == requestId } ?: return
        restoreAfterSignIn(operation)
    }

    fun retry() {
        val action = retryAction ?: return
        if (mutableUiState.value !is AuthUiState.Error) return

        retryAction = null
        when (action) {
            RetryAction.Initialize -> initialize()
            is RetryAction.SignIn -> beginSignIn(action.mode, action.fallback)
            RetryAction.SignOut -> signOutFromError()
        }
    }

    fun signOut() {
        if (mutableUiState.value !is AuthUiState.Approved) return
        beginSignOut()
    }

    fun onAppBackgrounded() {
        // Approval is scoped to this ViewModel/process. A cold process still reloads as locked.
    }

    fun onAppForegrounded() {
        // Reserved for a future user-configurable inactivity timeout.
    }

    private fun initialize() {
        if (!isGoogleSignInConfigured) {
            mutableUiState.value = AuthUiState.ConfigurationRequired
            return
        }

        mutableUiState.value = AuthUiState.Initializing
        viewModelScope.launch {
            mutableUiState.value = when (val result = sessionManager.loadCachedSession()) {
                is LoadAuthSessionResult.SignedOut -> AuthUiState.SignedOut(
                    warning = if (result.providerStateCleared) {
                        null
                    } else {
                        AuthWarning.ProviderStateNotCleared
                    },
                )
                is LoadAuthSessionResult.ReauthenticationRequired ->
                    AuthUiState.ReauthenticationRequired(
                        account = result.account,
                        automaticAttemptPending = true,
                    )
                LoadAuthSessionResult.PersistenceFailure -> {
                    retryAction = RetryAction.Initialize
                    AuthUiState.Error(reason = AuthError.Persistence)
                }
            }
        }
    }

    private fun beginSignIn(mode: GoogleSignInMode, fallback: AuthUiState) {
        if (activeSignIn != null || mutableUiState.value is AuthUiState.SigningOut) return

        val operation = ActiveSignIn(
            id = ++nextOperationId,
            mode = mode,
            fallback = fallback,
        )
        activeSignIn = operation
        retryAction = null
        mutableUiState.value = AuthUiState.SigningIn(
            request = AuthSignInRequest(id = operation.id, mode = mode),
        )
    }

    private fun authorize(operation: ActiveSignIn, result: GoogleSignInResult.Success) {
        viewModelScope.launch {
            if (activeSignIn?.id != operation.id) return@launch
            activeSignIn = null
            mutableUiState.value = when (val authorization = sessionManager.authorize(result.account)) {
                is AuthorizeAccountResult.Approved -> {
                    retryAction = null
                    AuthUiState.Approved(authorization.account)
                }
                is AuthorizeAccountResult.Blocked -> {
                    retryAction = null
                    AuthUiState.Blocked(
                        account = authorization.account,
                        warning = if (
                            authorization.localStateCleared && authorization.providerStateCleared
                        ) {
                            null
                        } else {
                            AuthWarning.BlockedCleanupIncomplete
                        },
                    )
                }
                AuthorizeAccountResult.PersistenceFailure -> {
                    retryAction = RetryAction.SignIn(operation.mode, operation.fallback)
                    AuthUiState.Error(reason = AuthError.Persistence)
                }
            }
        }
    }

    private fun restoreAfterSignIn(operation: ActiveSignIn) {
        activeSignIn = null
        retryAction = null
        mutableUiState.value = operation.fallback
    }

    private fun showSignInError(operation: ActiveSignIn, reason: AuthError) {
        activeSignIn = null
        retryAction = RetryAction.SignIn(operation.mode, operation.fallback)
        mutableUiState.value = AuthUiState.Error(reason = reason)
    }

    private fun beginSignOut() {
        retryAction = null
        mutableUiState.value = AuthUiState.SigningOut
        viewModelScope.launch {
            mutableUiState.value = when (val result = sessionManager.signOut()) {
                is SignOutResult.SignedOut -> {
                    retryAction = null
                    AuthUiState.SignedOut(
                        warning = if (result.providerStateCleared) {
                            null
                        } else {
                            AuthWarning.ProviderStateNotCleared
                        },
                    )
                }
                SignOutResult.PersistenceFailure -> {
                    retryAction = RetryAction.SignOut
                    AuthUiState.Error(reason = AuthError.Persistence)
                }
            }
        }
    }

    private fun signOutFromError() {
        if (mutableUiState.value !is AuthUiState.Error) return
        beginSignOut()
    }

    class Factory(
        private val sessionManager: AuthSessionManager,
        private val isGoogleSignInConfigured: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return AuthViewModel(
                sessionManager = sessionManager,
                isGoogleSignInConfigured = isGoogleSignInConfigured,
            ) as T
        }
    }

    private data class ActiveSignIn(
        val id: Long,
        val mode: GoogleSignInMode,
        val fallback: AuthUiState,
    )

    private sealed interface RetryAction {
        data object Initialize : RetryAction
        data class SignIn(
            val mode: GoogleSignInMode,
            val fallback: AuthUiState,
        ) : RetryAction
        data object SignOut : RetryAction
    }
}
