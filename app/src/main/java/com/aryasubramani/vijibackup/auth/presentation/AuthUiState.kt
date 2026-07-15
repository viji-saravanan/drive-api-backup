package com.aryasubramani.vijibackup.auth.presentation

import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.auth.google.GoogleSignInMode

sealed interface AuthUiState {
    data object Initializing : AuthUiState

    data object ConfigurationRequired : AuthUiState

    data class SignedOut(
        val warning: AuthWarning? = null,
    ) : AuthUiState

    data class ReauthenticationRequired(
        val account: GoogleAccount,
        val automaticAttemptPending: Boolean,
    ) : AuthUiState

    data class SigningIn(
        val request: AuthSignInRequest?,
    ) : AuthUiState

    data class Approved(val account: GoogleAccount) : AuthUiState

    data class Blocked(
        val account: GoogleAccount,
        val warning: AuthWarning? = null,
    ) : AuthUiState

    data class Error(val reason: AuthError) : AuthUiState

    data object SigningOut : AuthUiState
}

data class AuthSignInRequest(
    val id: Long,
    val mode: GoogleSignInMode,
)

enum class AuthError {
    Persistence,
    ProviderUnavailable,
    Interrupted,
    InvalidCredential,
    Unknown,
}

enum class AuthWarning {
    ProviderStateNotCleared,
    SignOutCleanupIncomplete,
    BlockedCleanupIncomplete,
}
