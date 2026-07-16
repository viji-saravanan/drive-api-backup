package com.aryasubramani.vijibackup.auth.data

import com.aryasubramani.vijibackup.auth.domain.AccountAccess
import com.aryasubramani.vijibackup.auth.domain.AccountAccessPolicy
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import kotlinx.coroutines.CancellationException

class AuthSessionManager(
    private val accessPolicy: AccountAccessPolicy,
    private val sessionStore: AuthSessionStore,
    private val credentialStateClearer: CredentialStateClearer,
) {
    suspend fun loadCachedSession(): LoadAuthSessionResult {
        val cleanupPending = captureFailure { sessionStore.isProviderCleanupPending() }
            .getOrElse { return LoadAuthSessionResult.PersistenceFailure }
        val providerStateCleared = if (cleanupPending) {
            clearProviderStateAndCompleteMarker()
        } else {
            true
        }
        val account = captureFailure { sessionStore.read() }
            .getOrElse { return LoadAuthSessionResult.PersistenceFailure }

        if (account == null) {
            return LoadAuthSessionResult.SignedOut(providerStateCleared)
        }

        return when (val access = accessPolicy.evaluate(account)) {
            is AccountAccess.Approved -> LoadAuthSessionResult.Approved(access.account)
            is AccountAccess.Blocked -> clearBlockedAccount(access.account).let { cleanup ->
                LoadAuthSessionResult.Blocked(
                    account = cleanup.account,
                    localStateCleared = cleanup.localStateCleared,
                    providerStateCleared = cleanup.providerStateCleared,
                )
            }
        }
    }

    suspend fun authorize(account: GoogleAccount): AuthorizeAccountResult =
        when (val access = accessPolicy.evaluate(account)) {
            is AccountAccess.Approved -> {
                captureFailure { sessionStore.save(access.account) }
                    .fold(
                        onSuccess = { AuthorizeAccountResult.Approved(access.account) },
                        onFailure = { AuthorizeAccountResult.PersistenceFailure },
                    )
            }

            is AccountAccess.Blocked -> {
                val cleanup = clearBlockedAccount(access.account)
                AuthorizeAccountResult.Blocked(
                    account = cleanup.account,
                    localStateCleared = cleanup.localStateCleared,
                    providerStateCleared = cleanup.providerStateCleared,
                )
            }
        }

    suspend fun signOut(): SignOutResult {
        if (captureFailure { sessionStore.beginProviderCleanup() }.isFailure) {
            return SignOutResult.PersistenceFailure
        }

        return SignOutResult.SignedOut(
            providerStateCleared = clearProviderStateAndCompleteMarker(),
        )
    }

    private suspend fun clearProviderStateAndCompleteMarker(): Boolean {
        if (captureFailure { credentialStateClearer.clear() }.isFailure) return false
        return captureFailure { sessionStore.completeProviderCleanup() }.isSuccess
    }

    private suspend fun clearBlockedAccount(account: GoogleAccount): BlockedAccountCleanup {
        val localStateCleared = captureFailure {
            sessionStore.beginProviderCleanup()
        }.isSuccess
        val providerStateCleared = if (localStateCleared) {
            clearProviderStateAndCompleteMarker()
        } else {
            captureFailure { credentialStateClearer.clear() }.isSuccess
        }
        return BlockedAccountCleanup(
            account = account,
            localStateCleared = localStateCleared,
            providerStateCleared = providerStateCleared,
        )
    }
}

private data class BlockedAccountCleanup(
    val account: GoogleAccount,
    val localStateCleared: Boolean,
    val providerStateCleared: Boolean,
)

private suspend fun <T> captureFailure(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }

sealed interface LoadAuthSessionResult {
    data class SignedOut(val providerStateCleared: Boolean) : LoadAuthSessionResult

    data class Approved(val account: GoogleAccount) : LoadAuthSessionResult

    data class Blocked(
        val account: GoogleAccount,
        val localStateCleared: Boolean,
        val providerStateCleared: Boolean,
    ) : LoadAuthSessionResult

    data object PersistenceFailure : LoadAuthSessionResult
}

sealed interface AuthorizeAccountResult {
    data class Approved(val account: GoogleAccount) : AuthorizeAccountResult

    data class Blocked(
        val account: GoogleAccount,
        val localStateCleared: Boolean,
        val providerStateCleared: Boolean,
    ) : AuthorizeAccountResult

    data object PersistenceFailure : AuthorizeAccountResult
}

sealed interface SignOutResult {
    data class SignedOut(val providerStateCleared: Boolean) : SignOutResult

    data object PersistenceFailure : SignOutResult
}
