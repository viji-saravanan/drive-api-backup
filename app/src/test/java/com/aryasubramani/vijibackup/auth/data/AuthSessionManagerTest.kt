package com.aryasubramani.vijibackup.auth.data

import com.aryasubramani.vijibackup.auth.domain.AccountAccessPolicy
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionManagerTest {
    @Test
    fun approvedAccountMetadataIsPersisted() = runTest {
        val store = RecordingAuthSessionStore()
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )
        val account = approvedAccount()

        val result = manager.authorize(account)

        assertTrue(result is AuthorizeAccountResult.Approved)
        assertEquals(account, store.account)
        assertEquals(0, credentialStateClearer.clearCount)
    }

    @Test
    fun blockedAccountClearsStaleLocalAndProviderState() = runTest {
        val store = RecordingAuthSessionStore().apply { account = approvedAccount() }
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )
        val blockedAccount = requireNotNull(
            GoogleAccount.create(
                subject = "unapproved-google-subject",
                email = "blocked.user@example.test",
                displayName = "Blocked account",
            ),
        )

        val result = manager.authorize(blockedAccount)

        assertEquals(
            AuthorizeAccountResult.Blocked(
                account = blockedAccount,
                localStateCleared = true,
                providerStateCleared = true,
            ),
            result,
        )
        assertEquals(null, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun approvedAccountFailsClosedWhenPersistenceFails() = runTest {
        val store = RecordingAuthSessionStore().apply {
            saveFailure = IllegalStateException("test storage failure")
        }
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        val result = manager.authorize(approvedAccount())

        assertEquals(AuthorizeAccountResult.PersistenceFailure, result)
        assertEquals(null, store.account)
        assertEquals(0, credentialStateClearer.clearCount)
    }

    @Test
    fun cachedAccountRequiresReauthenticationInsteadOfImmediateApproval() = runTest {
        val cachedAccount = approvedAccount()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = RecordingAuthSessionStore().apply { account = cachedAccount },
            credentialStateClearer = RecordingCredentialStateClearer(),
        )

        val result = manager.loadCachedSession()

        assertEquals(LoadAuthSessionResult.ReauthenticationRequired(cachedAccount), result)
    }

    @Test
    fun signOutClearsLocalAndCredentialProviderState() = runTest {
        val store = RecordingAuthSessionStore().apply { account = approvedAccount() }
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        val result = manager.signOut()

        assertEquals(SignOutResult.SignedOut(providerStateCleared = true), result)
        assertEquals(null, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun signOutRemainsLocalWhenProviderStateCannotBeCleared() = runTest {
        val store = RecordingAuthSessionStore().apply { account = approvedAccount() }
        val credentialStateClearer = RecordingCredentialStateClearer().apply {
            clearFailure = IllegalStateException("test provider failure")
        }
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        val result = manager.signOut()

        assertEquals(SignOutResult.SignedOut(providerStateCleared = false), result)
        assertEquals(null, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun signOutDoesNotClaimSuccessWhenLocalStateCannotBeCleared() = runTest {
        val cachedAccount = approvedAccount()
        val store = RecordingAuthSessionStore().apply {
            account = cachedAccount
            clearFailure = IllegalStateException("test storage failure")
        }
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        val result = manager.signOut()

        assertEquals(SignOutResult.PersistenceFailure, result)
        assertEquals(cachedAccount, store.account)
        assertEquals(0, credentialStateClearer.clearCount)
    }

    @Test
    fun absentOrUnreadableCacheNeverCreatesAnApprovedSession() = runTest {
        val emptyManager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = RecordingAuthSessionStore(),
            credentialStateClearer = RecordingCredentialStateClearer(),
        )
        val failingManager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = RecordingAuthSessionStore().apply {
                readFailure = IllegalStateException("test storage failure")
            },
            credentialStateClearer = RecordingCredentialStateClearer(),
        )

        assertEquals(LoadAuthSessionResult.SignedOut, emptyManager.loadCachedSession())
        assertEquals(LoadAuthSessionResult.PersistenceFailure, failingManager.loadCachedSession())
    }

    @Test
    fun blockedAccountRemainsBlockedWhenCleanupFails() = runTest {
        val store = RecordingAuthSessionStore().apply {
            account = approvedAccount()
            clearFailure = IllegalStateException("test storage failure")
        }
        val credentialStateClearer = RecordingCredentialStateClearer().apply {
            clearFailure = IllegalStateException("test provider failure")
        }
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )
        val blockedAccount = requireNotNull(
            GoogleAccount.create(
                subject = "unapproved-google-subject",
                email = "blocked.user@example.test",
                displayName = null,
            ),
        )

        val result = manager.authorize(blockedAccount)

        assertEquals(
            AuthorizeAccountResult.Blocked(
                account = blockedAccount,
                localStateCleared = false,
                providerStateCleared = false,
            ),
            result,
        )
        assertEquals(approvedAccount(), store.account)
    }

    @Test
    fun coroutineCancellationIsNeverConvertedToAnAuthFailure() {
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = RecordingAuthSessionStore().apply {
                saveFailure = CancellationException("test cancellation")
            },
            credentialStateClearer = RecordingCredentialStateClearer(),
        )

        assertThrows(CancellationException::class.java) {
            runTest { manager.authorize(approvedAccount()) }
        }
    }
}

private fun approvedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "test-approved-google-subject",
        email = TEST_APPROVED_EMAIL,
        displayName = "Primary User",
    ),
)

private const val TEST_APPROVED_EMAIL = "primary.user@example.test"
private val TEST_ALLOWED_GOOGLE_ACCOUNTS = setOf(TEST_APPROVED_EMAIL)

private class RecordingAuthSessionStore : AuthSessionStore {
    var account: GoogleAccount? = null
    var readFailure: Throwable? = null
    var saveFailure: Throwable? = null
    var clearFailure: Throwable? = null

    override suspend fun read(): GoogleAccount? {
        readFailure?.let { throw it }
        return account
    }

    override suspend fun save(account: GoogleAccount) {
        saveFailure?.let { throw it }
        this.account = account
    }

    override suspend fun clear() {
        clearFailure?.let { throw it }
        account = null
    }
}

private class RecordingCredentialStateClearer : CredentialStateClearer {
    var clearCount = 0
    var clearFailure: Throwable? = null

    override suspend fun clear() {
        clearCount += 1
        clearFailure?.let { throw it }
    }
}
