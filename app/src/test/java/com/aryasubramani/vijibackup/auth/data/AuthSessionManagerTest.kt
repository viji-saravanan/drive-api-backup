package com.aryasubramani.vijibackup.auth.data

import com.aryasubramani.vijibackup.auth.domain.AccountAccessPolicy
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun cachedApprovedAccountRestoresLocalSessionWithoutProviderClear() = runTest {
        val cachedAccount = approvedAccount()
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = RecordingAuthSessionStore().apply { account = cachedAccount },
            credentialStateClearer = credentialStateClearer,
        )

        val result = manager.loadCachedSession()

        assertEquals(LoadAuthSessionResult.Approved(cachedAccount), result)
        assertEquals(0, credentialStateClearer.clearCount)
    }

    @Test
    fun cachedAccountRemovedFromAllowlistIsBlockedAndCleared() = runTest {
        val cachedAccount = approvedAccount()
        val store = RecordingAuthSessionStore().apply { account = cachedAccount }
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(emptySet()),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        val result = manager.loadCachedSession()

        assertEquals(
            LoadAuthSessionResult.Blocked(
                account = cachedAccount,
                localStateCleared = true,
                providerStateCleared = true,
            ),
            result,
        )
        assertEquals(null, store.account)
        assertEquals(false, store.providerCleanupPending)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun cachedBlockedAccountNeverApprovesWhenCleanupFails() = runTest {
        val cachedAccount = approvedAccount()
        val store = RecordingAuthSessionStore().apply {
            account = cachedAccount
            clearFailure = IllegalStateException("test storage failure")
        }
        val credentialStateClearer = RecordingCredentialStateClearer().apply {
            clearFailure = IllegalStateException("test provider failure")
        }
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(emptySet()),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        val result = manager.loadCachedSession()

        assertEquals(
            LoadAuthSessionResult.Blocked(
                account = cachedAccount,
                localStateCleared = false,
                providerStateCleared = false,
            ),
            result,
        )
        assertEquals(cachedAccount, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
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
        assertEquals(false, store.providerCleanupPending)
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
        assertEquals(true, store.providerCleanupPending)
        assertEquals(1, credentialStateClearer.clearCount)

        credentialStateClearer.clearFailure = null

        assertEquals(
            LoadAuthSessionResult.SignedOut(providerStateCleared = true),
            manager.loadCachedSession(),
        )
        assertEquals(false, store.providerCleanupPending)
        assertEquals(2, credentialStateClearer.clearCount)
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

        assertEquals(
            LoadAuthSessionResult.SignedOut(providerStateCleared = true),
            emptyManager.loadCachedSession(),
        )
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
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun blockedAccountReportsLocalCleanupFailureIndependently() = runTest {
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
        val blockedAccount = blockedAccount()

        val result = manager.authorize(blockedAccount)

        assertEquals(
            AuthorizeAccountResult.Blocked(
                account = blockedAccount,
                localStateCleared = false,
                providerStateCleared = true,
            ),
            result,
        )
        assertEquals(cachedAccount, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
    }

    @Test
    fun blockedAccountReportsProviderCleanupFailureIndependently() = runTest {
        val store = RecordingAuthSessionStore().apply { account = approvedAccount() }
        val credentialStateClearer = RecordingCredentialStateClearer().apply {
            clearFailure = IllegalStateException("test provider failure")
        }
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )
        val blockedAccount = blockedAccount()

        val result = manager.authorize(blockedAccount)

        assertEquals(
            AuthorizeAccountResult.Blocked(
                account = blockedAccount,
                localStateCleared = true,
                providerStateCleared = false,
            ),
            result,
        )
        assertEquals(null, store.account)
        assertEquals(1, credentialStateClearer.clearCount)
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

    @Test
    fun cancelledProviderCleanupIsRetriedFromTheDurableMarkerAfterRestart() = runTest {
        val store = RecordingAuthSessionStore().apply { account = approvedAccount() }
        val credentialStateClearer = RecordingCredentialStateClearer().apply {
            clearFailure = CancellationException("test provider cancellation")
        }
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        try {
            manager.signOut()
            fail("Expected provider cleanup cancellation")
        } catch (_: CancellationException) {
            // Expected: the durable marker must survive this cancellation.
        }
        assertEquals(null, store.account)
        assertEquals(true, store.providerCleanupPending)

        credentialStateClearer.clearFailure = null
        val restartResult = manager.loadCachedSession()

        assertEquals(
            LoadAuthSessionResult.SignedOut(providerStateCleared = true),
            restartResult,
        )
        assertEquals(false, store.providerCleanupPending)
        assertEquals(2, credentialStateClearer.clearCount)
    }

    @Test
    fun cancelledMarkerCompletionRetriesProviderCleanupAfterRestart() = runTest {
        val store = RecordingAuthSessionStore().apply {
            account = approvedAccount()
            completeCleanupFailure = CancellationException("test marker cancellation")
        }
        val credentialStateClearer = RecordingCredentialStateClearer()
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = credentialStateClearer,
        )

        try {
            manager.signOut()
            fail("Expected marker completion cancellation")
        } catch (_: CancellationException) {
            // Expected: the durable marker must survive this cancellation.
        }
        assertEquals(true, store.providerCleanupPending)

        store.completeCleanupFailure = null
        val restartResult = manager.loadCachedSession()

        assertEquals(
            LoadAuthSessionResult.SignedOut(providerStateCleared = true),
            restartResult,
        )
        assertEquals(false, store.providerCleanupPending)
        assertEquals(2, credentialStateClearer.clearCount)
    }

    @Test
    fun aNewApprovedSignInSupersedesAnOlderProviderCleanupMarker() = runTest {
        val store = RecordingAuthSessionStore().apply { providerCleanupPending = true }
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = store,
            credentialStateClearer = RecordingCredentialStateClearer(),
        )

        val result = manager.authorize(approvedAccount())

        assertEquals(AuthorizeAccountResult.Approved(approvedAccount()), result)
        assertEquals(false, store.providerCleanupPending)
        assertEquals(approvedAccount(), store.account)
    }

    @Test
    fun fatalErrorsAreNeverConvertedToAuthFailures() {
        val manager = AuthSessionManager(
            accessPolicy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS),
            sessionStore = RecordingAuthSessionStore().apply {
                saveFailure = AssertionError("test fatal error")
            },
            credentialStateClearer = RecordingCredentialStateClearer(),
        )

        assertThrows(AssertionError::class.java) {
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

private fun blockedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "test-blocked-google-subject",
        email = "blocked.user@example.test",
        displayName = "Blocked User",
    ),
)

private const val TEST_APPROVED_EMAIL = "primary.user@example.test"
private val TEST_ALLOWED_GOOGLE_ACCOUNTS = setOf(TEST_APPROVED_EMAIL)

private class RecordingAuthSessionStore : AuthSessionStore {
    var account: GoogleAccount? = null
    var providerCleanupPending = false
    var readFailure: Throwable? = null
    var saveFailure: Throwable? = null
    var clearFailure: Throwable? = null
    var completeCleanupFailure: Throwable? = null

    override suspend fun read(): GoogleAccount? {
        readFailure?.let { throw it }
        return account
    }

    override suspend fun save(account: GoogleAccount) {
        saveFailure?.let { throw it }
        this.account = account
        providerCleanupPending = false
    }

    override suspend fun clear() {
        clearFailure?.let { throw it }
        account = null
        providerCleanupPending = false
    }

    override suspend fun beginProviderCleanup() {
        clearFailure?.let { throw it }
        account = null
        providerCleanupPending = true
    }

    override suspend fun isProviderCleanupPending(): Boolean = providerCleanupPending

    override suspend fun completeProviderCleanup() {
        completeCleanupFailure?.let { throw it }
        providerCleanupPending = false
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
