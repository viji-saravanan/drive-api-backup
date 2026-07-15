package com.aryasubramani.vijibackup.auth.presentation

import com.aryasubramani.vijibackup.auth.data.AuthSessionManager
import com.aryasubramani.vijibackup.auth.data.AuthSessionStore
import com.aryasubramani.vijibackup.auth.data.CredentialStateClearer
import com.aryasubramani.vijibackup.auth.domain.AccountAccessPolicy
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.auth.google.GoogleSignInMode
import com.aryasubramani.vijibackup.auth.google.GoogleSignInResult
import com.aryasubramani.vijibackup.folderaccess.domain.PendingFolderCleanupResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun configuredAppLoadsEmptySessionAndBecomesSignedOut() = runTest {
        val viewModel = createViewModel()

        assertEquals(AuthUiState.Initializing, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun startupWarnsButRemainsSignedOutWhenPendingProviderCleanupStillFails() = runTest {
        val store = FakeAuthSessionStore().apply { providerCleanupPending = true }
        val credentialStateClearer = FakeCredentialStateClearer().apply {
            clearFailure = IllegalStateException("test provider failure")
        }
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
        )

        advanceUntilIdle()

        assertEquals(
            AuthUiState.SignedOut(AuthWarning.ProviderStateNotCleared),
            viewModel.uiState.value,
        )
        assertEquals(true, store.providerCleanupPending)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun missingConfigurationDoesNotLoadOrTrustCachedSession() = runTest {
        val store = FakeAuthSessionStore().apply { account = approvedAccount() }
        val viewModel = createViewModel(
            store = store,
            isGoogleSignInConfigured = false,
        )

        advanceUntilIdle()

        assertEquals(AuthUiState.ConfigurationRequired, viewModel.uiState.value)
        assertEquals(0, store.readCount)
    }

    @Test
    fun cachedSessionRequiresReauthenticationBeforeAuthorizedAccountAttempt() = runTest {
        val account = approvedAccount()
        val viewModel = createViewModel(
            store = FakeAuthSessionStore().apply { this.account = account },
        )

        advanceUntilIdle()

        assertEquals(
            AuthUiState.ReauthenticationRequired(
                account = account,
                automaticAttemptPending = true,
            ),
            viewModel.uiState.value,
        )

        viewModel.startAutomaticReauthentication()

        val signingIn = viewModel.uiState.value
        assertTrue(signingIn is AuthUiState.SigningIn)
        assertEquals(
            GoogleSignInMode.AuthorizedAccounts,
            (signingIn as AuthUiState.SigningIn).request?.mode,
        )
    }

    @Test
    fun duplicateExplicitSignInRequestsReuseTheSingleActiveOperation() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signIn()
        val firstRequest = (viewModel.uiState.value as AuthUiState.SigningIn).request

        viewModel.signIn()

        assertEquals(
            AuthSignInRequest(
                id = requireNotNull(firstRequest).id,
                mode = GoogleSignInMode.Explicit,
            ),
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
    }

    @Test
    fun approvedCredentialIsPersistedBeforeTheAppIsUnlocked() = runTest {
        val store = FakeAuthSessionStore()
        val account = approvedAccount()
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()

        viewModel.signIn()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        viewModel.onSignInResult(request.id, GoogleSignInResult.Success(account))
        advanceUntilIdle()

        assertEquals(AuthUiState.Approved(account), viewModel.uiState.value)
        assertEquals(account, store.account)
    }

    @Test
    fun backgroundingKeepsApprovedSessionValidForTheCurrentProcess() = runTest {
        val store = FakeAuthSessionStore()
        val account = approvedAccount()
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()

        viewModel.onAppBackgrounded()

        assertEquals(AuthUiState.Approved(account), viewModel.uiState.value)
        assertEquals(account, store.account)
    }

    @Test
    fun approvedStateIsNotEmittedWhileSessionPersistenceIsStillRunning() = runTest {
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStore().apply {
            this.saveStarted = saveStarted
            saveGate = allowSaveToFinish
        }
        val account = approvedAccount()
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()
        viewModel.signIn()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(request.id, GoogleSignInResult.Success(account))
        runCurrent()
        saveStarted.await()

        assertTrue(viewModel.uiState.value is AuthUiState.SigningIn)
        assertEquals(null, store.account)

        allowSaveToFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(AuthUiState.Approved(account), viewModel.uiState.value)
        assertEquals(account, store.account)
    }

    @Test
    fun credentialFinishingWhileBackgroundedApprovesTheCurrentProcess() = runTest {
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStore().apply {
            this.saveStarted = saveStarted
            saveGate = allowSaveToFinish
        }
        val account = approvedAccount()
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()
        viewModel.signIn()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        viewModel.onSignInResult(request.id, GoogleSignInResult.Success(account))
        runCurrent()
        saveStarted.await()

        viewModel.onAppBackgrounded()
        allowSaveToFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(AuthUiState.Approved(account), viewModel.uiState.value)
        assertEquals(account, store.account)
    }

    @Test
    fun blockedCredentialClearsLocalAndProviderStateWithoutUnlockingTheApp() = runTest {
        val store = FakeAuthSessionStore()
        val credentialStateClearer = FakeCredentialStateClearer()
        val account = blockedAccount()
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
        )
        advanceUntilIdle()

        viewModel.signIn()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        viewModel.onSignInResult(request.id, GoogleSignInResult.Success(account))
        advanceUntilIdle()

        assertEquals(AuthUiState.Blocked(account), viewModel.uiState.value)
        assertEquals(null, store.account)
        assertEquals(1, store.clearCount)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun blockedCredentialRemainsBlockedWhenCleanupIsIncomplete() = runTest {
        val store = FakeAuthSessionStore().apply {
            account = approvedAccount()
            clearFailure = IllegalStateException("test local cleanup failure")
        }
        val credentialStateClearer = FakeCredentialStateClearer().apply {
            clearFailure = IllegalStateException("test provider cleanup failure")
        }
        val account = blockedAccount()
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
        )
        advanceUntilIdle()
        viewModel.startAutomaticReauthentication()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(request.id, GoogleSignInResult.Success(account))
        advanceUntilIdle()

        assertEquals(
            AuthUiState.Blocked(account, AuthWarning.BlockedCleanupIncomplete),
            viewModel.uiState.value,
        )
    }

    @Test
    fun eachCredentialRequestCanBeConsumedOnlyOnce() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.signIn()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        assertEquals(GoogleSignInMode.Explicit, viewModel.consumeSignInRequest(request.id))
        assertEquals(null, viewModel.consumeSignInRequest(request.id))
        assertEquals(
            AuthUiState.SigningIn(request = null),
            viewModel.uiState.value,
        )
    }

    @Test
    fun staleCredentialCallbackCannotAuthorizeAReplacementRequest() = runTest {
        val store = FakeAuthSessionStore()
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()
        viewModel.signIn()
        val firstRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        viewModel.onSignInResult(firstRequest.id, GoogleSignInResult.Cancelled)
        viewModel.signIn()
        val replacementRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(
            firstRequest.id,
            GoogleSignInResult.Success(approvedAccount()),
        )
        advanceUntilIdle()

        assertEquals(
            AuthUiState.SigningIn(request = replacementRequest),
            viewModel.uiState.value,
        )
        assertEquals(null, store.account)
    }

    @Test
    fun staleLifecycleInterruptionCannotCancelAReplacementRequest() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.signIn()
        val firstRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        viewModel.onSignInResult(firstRequest.id, GoogleSignInResult.Cancelled)
        viewModel.signIn()
        val replacementRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInInterrupted(firstRequest.id)

        assertEquals(
            AuthUiState.SigningIn(request = replacementRequest),
            viewModel.uiState.value,
        )
    }

    @Test
    fun cancellingExplicitSignInReturnsToSignedOutWithoutPersistingAnything() = runTest {
        val store = FakeAuthSessionStore()
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()
        viewModel.signIn()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(request.id, GoogleSignInResult.Cancelled)

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(null, store.account)
    }

    @Test
    fun cancellingAutomaticReauthenticationDoesNotLoopAutomatically() = runTest {
        val account = approvedAccount()
        val viewModel = createViewModel(
            store = FakeAuthSessionStore().apply { this.account = account },
        )
        advanceUntilIdle()
        viewModel.startAutomaticReauthentication()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(request.id, GoogleSignInResult.Cancelled)
        viewModel.startAutomaticReauthentication()

        assertEquals(
            AuthUiState.ReauthenticationRequired(
                account = account,
                automaticAttemptPending = false,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun cancelledAutomaticReauthenticationCanBeRetriedWithAccountChooser() = runTest {
        val account = approvedAccount()
        val viewModel = createViewModel(
            store = FakeAuthSessionStore().apply { this.account = account },
        )
        advanceUntilIdle()
        viewModel.startAutomaticReauthentication()
        val automaticRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        viewModel.onSignInResult(automaticRequest.id, GoogleSignInResult.Cancelled)

        viewModel.signIn()

        val retryRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        assertEquals(GoogleSignInMode.Explicit, retryRequest.mode)
    }

    @Test
    fun missingAuthorizedCredentialClearsTheStaleCachedSession() = runTest {
        val store = FakeAuthSessionStore().apply { account = approvedAccount() }
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()
        viewModel.startAutomaticReauthentication()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(request.id, GoogleSignInResult.NoCredential)
        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(null, store.account)
        assertEquals(1, store.clearCount)
    }

    @Test
    fun openingTheGoogleAccountPromptDoesNotRelockOrReplaceTheActiveRequest() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.signIn()
        val activeRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onAppBackgrounded()

        assertEquals(
            AuthUiState.SigningIn(request = activeRequest),
            viewModel.uiState.value,
        )

        viewModel.onAppForegrounded()
        viewModel.onSignInResult(
            activeRequest.id,
            GoogleSignInResult.Success(approvedAccount()),
        )
        advanceUntilIdle()

        assertEquals(AuthUiState.Approved(approvedAccount()), viewModel.uiState.value)
    }

    @Test
    fun lifecycleInterruptionRestoresTheStateThatStartedSignIn() = runTest {
        val blocked = blockedAccount()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.signIn()
        val firstRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        viewModel.onSignInResult(firstRequest.id, GoogleSignInResult.Success(blocked))
        advanceUntilIdle()
        viewModel.signIn()
        val chooserRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInInterrupted(chooserRequest.id)

        assertEquals(AuthUiState.Blocked(blocked), viewModel.uiState.value)
    }

    @Test
    fun providerFailureCanRetryTheSameSignInModeWithANewRequest() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.signIn()
        val failedRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(
            failedRequest.id,
            GoogleSignInResult.ProviderUnavailable,
        )

        assertEquals(
            AuthUiState.Error(AuthError.ProviderUnavailable),
            viewModel.uiState.value,
        )

        viewModel.retry()

        val retryRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        assertEquals(GoogleSignInMode.Explicit, retryRequest.mode)
        assertTrue(retryRequest.id > failedRequest.id)
    }

    @Test
    fun credentialFailuresAreReportedWithoutUnlockingTheApp() = runTest {
        val cases = listOf(
            GoogleSignInResult.Interrupted to AuthError.Interrupted,
            GoogleSignInResult.InvalidCredential to AuthError.InvalidCredential,
            GoogleSignInResult.UnknownFailure to AuthError.Unknown,
        )

        cases.forEach { (result, expectedError) ->
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.signIn()
            val request = requireNotNull(
                (viewModel.uiState.value as AuthUiState.SigningIn).request,
            )

            viewModel.onSignInResult(request.id, result)

            assertEquals(
                AuthUiState.Error(expectedError),
                viewModel.uiState.value,
            )
        }
    }

    @Test
    fun configurationFailureCannotBeRetriedAsAProviderRequest() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.signIn()
        val request = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(request.id, GoogleSignInResult.ConfigurationRequired)
        viewModel.retry()

        assertEquals(AuthUiState.ConfigurationRequired, viewModel.uiState.value)
    }

    @Test
    fun unreadableSessionFailsClosedAndCanRetryInitialization() = runTest {
        val store = FakeAuthSessionStore().apply {
            readFailure = IllegalStateException("test read failure")
        }
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()

        assertEquals(
            AuthUiState.Error(AuthError.Persistence),
            viewModel.uiState.value,
        )

        store.readFailure = null
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(2, store.readCount)
    }

    @Test
    fun approvedCredentialCannotUnlockWhenSessionPersistenceFails() = runTest {
        val store = FakeAuthSessionStore().apply {
            saveFailure = IllegalStateException("test save failure")
        }
        val viewModel = createViewModel(store = store)
        advanceUntilIdle()
        viewModel.signIn()
        val failedRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )

        viewModel.onSignInResult(
            failedRequest.id,
            GoogleSignInResult.Success(approvedAccount()),
        )
        advanceUntilIdle()

        assertEquals(
            AuthUiState.Error(AuthError.Persistence),
            viewModel.uiState.value,
        )
        assertEquals(null, store.account)

        store.saveFailure = null
        viewModel.retry()

        val retryRequest = requireNotNull(
            (viewModel.uiState.value as AuthUiState.SigningIn).request,
        )
        assertEquals(GoogleSignInMode.Explicit, retryRequest.mode)
        assertTrue(retryRequest.id > failedRequest.id)
    }

    @Test
    fun signOutClearsLocalAndProviderStateBeforeReturningToSignedOut() = runTest {
        val store = FakeAuthSessionStore()
        val credentialStateClearer = FakeCredentialStateClearer()
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
        )
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(null, store.account)
        assertEquals(1, store.clearCount)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun signOutRunsPendingPickerCleanupBeforeAuthClear() = runTest {
        val events = mutableListOf<String>()
        val store = FakeAuthSessionStore().apply {
            onBeginProviderCleanup = { events += "auth-clear" }
        }
        val viewModel = createViewModel(
            store = store,
            preSignOut = {
                events += "prepare"
                PendingFolderCleanupResult.Complete
            },
        )
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(listOf("prepare", "auth-clear"), events)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun retryRequiredPendingPickerCleanupStillSignsOutAndShowsWarning() = runTest {
        val store = FakeAuthSessionStore()
        val viewModel = createViewModel(
            store = store,
            preSignOut = { PendingFolderCleanupResult.RetryRequired },
        )
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(
            AuthUiState.SignedOut(AuthWarning.SignOutCleanupIncomplete),
            viewModel.uiState.value,
        )
        assertEquals(null, store.account)
        assertEquals(1, store.clearCount)
    }

    @Test
    fun signOutCollapsesProviderAndFolderCleanupFailuresIntoOneWarning() = runTest {
        val store = FakeAuthSessionStore()
        val credentialStateClearer = FakeCredentialStateClearer().apply {
            clearFailure = IllegalStateException("provider cleanup detail")
        }
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
            preSignOut = { PendingFolderCleanupResult.RetryRequired },
        )
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(
            AuthUiState.SignedOut(AuthWarning.SignOutCleanupIncomplete),
            viewModel.uiState.value,
        )
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun duplicateSignOutWhileCleanupIsRunningDoesNotStartAnotherCleanup() = runTest {
        val clearStarted = CompletableDeferred<Unit>()
        val allowClearToFinish = CompletableDeferred<Unit>()
        val store = FakeAuthSessionStore()
        val credentialStateClearer = FakeCredentialStateClearer()
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
        )
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()
        store.clearStarted = clearStarted
        store.clearGate = allowClearToFinish

        viewModel.signOut()
        runCurrent()
        clearStarted.await()
        viewModel.signOut()

        assertEquals(AuthUiState.SigningOut, viewModel.uiState.value)
        assertEquals(1, store.clearCount)

        allowClearToFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(1, store.clearCount)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun providerOnlyCleanupFailureKeepsItsSpecificWarning() = runTest {
        val store = FakeAuthSessionStore()
        val credentialStateClearer = FakeCredentialStateClearer().apply {
            clearFailure = IllegalStateException("test provider cleanup failure")
        }
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
        )
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(
            AuthUiState.SignedOut(AuthWarning.ProviderStateNotCleared),
            viewModel.uiState.value,
        )
        assertEquals(null, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun localSignOutFailureKeepsTheAppLockedUntilCleanupRetrySucceeds() = runTest {
        val store = FakeAuthSessionStore()
        val credentialStateClearer = FakeCredentialStateClearer()
        val viewModel = createViewModel(
            store = store,
            credentialStateClearer = credentialStateClearer,
        )
        advanceUntilIdle()
        authorizeApproved(viewModel)
        advanceUntilIdle()
        store.clearFailure = IllegalStateException("test local cleanup failure")

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(
            AuthUiState.Error(AuthError.Persistence),
            viewModel.uiState.value,
        )
        assertEquals(approvedAccount(), store.account)
        assertEquals(0, credentialStateClearer.clearCount)

        store.clearFailure = null
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(null, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
    }
}

private fun createViewModel(
    store: FakeAuthSessionStore = FakeAuthSessionStore(),
    credentialStateClearer: FakeCredentialStateClearer = FakeCredentialStateClearer(),
    isGoogleSignInConfigured: Boolean = true,
    preSignOut: suspend () -> PendingFolderCleanupResult = {
        PendingFolderCleanupResult.Complete
    },
) = AuthViewModel(
    sessionManager = AuthSessionManager(
        accessPolicy = AccountAccessPolicy(setOf(APPROVED_EMAIL)),
        sessionStore = store,
        credentialStateClearer = credentialStateClearer,
    ),
    isGoogleSignInConfigured = isGoogleSignInConfigured,
    prepareForSignOut = preSignOut,
)

private class FakeAuthSessionStore : AuthSessionStore {
    var account: GoogleAccount? = null
    var providerCleanupPending = false
    var readCount = 0
    var clearCount = 0
    var readFailure: Exception? = null
    var saveFailure: Exception? = null
    var clearFailure: Exception? = null
    var saveStarted: CompletableDeferred<Unit>? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var clearStarted: CompletableDeferred<Unit>? = null
    var clearGate: CompletableDeferred<Unit>? = null
    var onBeginProviderCleanup: (() -> Unit)? = null

    override suspend fun read(): GoogleAccount? {
        readCount += 1
        readFailure?.let { throw it }
        return account
    }

    override suspend fun save(account: GoogleAccount) {
        saveStarted?.complete(Unit)
        saveGate?.await()
        saveFailure?.let { throw it }
        this.account = account
        providerCleanupPending = false
    }

    override suspend fun clear() {
        clearCount += 1
        clearStarted?.complete(Unit)
        clearGate?.await()
        clearFailure?.let { throw it }
        account = null
        providerCleanupPending = false
    }

    override suspend fun beginProviderCleanup() {
        clearCount += 1
        onBeginProviderCleanup?.invoke()
        clearStarted?.complete(Unit)
        clearGate?.await()
        clearFailure?.let { throw it }
        account = null
        providerCleanupPending = true
    }

    override suspend fun isProviderCleanupPending(): Boolean = providerCleanupPending

    override suspend fun completeProviderCleanup() {
        providerCleanupPending = false
    }
}

private fun approvedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "approved-subject",
        email = APPROVED_EMAIL,
        displayName = "Approved User",
    ),
)

private fun authorizeApproved(viewModel: AuthViewModel) {
    viewModel.signIn()
    val request = requireNotNull(
        (viewModel.uiState.value as AuthUiState.SigningIn).request,
    )
    viewModel.onSignInResult(
        request.id,
        GoogleSignInResult.Success(approvedAccount()),
    )
}

private fun blockedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "blocked-subject",
        email = "blocked.user@example.test",
        displayName = "Blocked User",
    ),
)

private class FakeCredentialStateClearer : CredentialStateClearer {
    var clearCount = 0
    var clearFailure: Exception? = null

    override suspend fun clear() {
        clearCount += 1
        clearFailure?.let { throw it }
    }
}

private const val APPROVED_EMAIL = "approved.user@example.test"
