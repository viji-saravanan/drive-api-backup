package com.aryasubramani.vijibackup.app

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import com.aryasubramani.vijibackup.R
import com.aryasubramani.vijibackup.auth.data.AuthSessionManager
import com.aryasubramani.vijibackup.auth.data.AuthSessionStore
import com.aryasubramani.vijibackup.auth.data.CredentialStateClearer
import com.aryasubramani.vijibackup.auth.data.DataStoreAuthSessionStore
import com.aryasubramani.vijibackup.auth.data.authSessionDataStore
import com.aryasubramani.vijibackup.auth.domain.AccountAccessPolicy
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.auth.google.GoogleSignInClient
import com.aryasubramani.vijibackup.auth.google.GoogleSignInMode
import com.aryasubramani.vijibackup.auth.google.GoogleSignInResult
import com.aryasubramani.vijibackup.auth.presentation.AuthTestTags
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderPickerResult
import com.aryasubramani.vijibackup.folderaccess.domain.BeginFolderScanResult
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMappingRepository
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerCompletion
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import com.aryasubramani.vijibackup.folderaccess.domain.PendingFolderCleanupResult
import com.aryasubramani.vijibackup.folderaccess.domain.RemoveFolderResult
import com.aryasubramani.vijibackup.folderaccess.domain.SetFolderEnabledResult
import com.aryasubramani.vijibackup.folderaccess.domain.ValidateFolderAccessResult
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessTestTags
import com.aryasubramani.vijibackup.folderaccess.saf.FolderPickerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCompositionInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun applicationProvidesOneStableCompositionRoot() {
        val application = ApplicationProvider.getApplicationContext<VijiBackupApplication>()

        assertSame(application.appContainer, application.appContainer)
        assertSame(
            application.appContainer.authSessionManager,
            application.appContainer.authSessionManager,
        )
        assertSame(
            application.appContainer.googleSignInClient,
            application.appContainer.googleSignInClient,
        )
        assertSame(
            application.appContainer.folderMappingRepository,
            application.appContainer.folderMappingRepository,
        )
    }

    @Test
    fun mainActivityUsesTheApplicationContainerAndKeepsProtectedContentLocked() {
        val application = ApplicationProvider.getApplicationContext<VijiBackupApplication>()
        val sessionStore = DataStoreAuthSessionStore(application.authSessionDataStore)
        val originalAccount = runBlocking { sessionStore.read() }
        runBlocking { sessionStore.clear() }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                composeRule.waitForIdle()

                scenario.onActivity { activity ->
                    assertTrue(activity.protectedWindowPolicyAppliedForTesting)
                }

                composeRule.onNodeWithTag(AuthTestTags.Screen).assertIsDisplayed()
                composeRule.onAllNodesWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertCountEquals(0)

                if (application.appContainer.isGoogleSignInConfigured) {
                    composeRule.onNodeWithTag(AuthTestTags.SignInButton).assertIsDisplayed()
                } else {
                    composeRule.onNodeWithText(
                        application.getString(R.string.auth_configuration_title),
                    ).assertIsDisplayed()
                }
            }
        } finally {
            runBlocking {
                if (originalAccount == null) {
                    sessionStore.clear()
                } else {
                    sessionStore.save(originalAccount)
                }
            }
        }
    }

    @Test
    fun mainActivityKeepsCurrentProcessApprovedAcrossBackgroundAndRecreation() {
        val application = ApplicationProvider.getApplicationContext<VijiBackupApplication>()
        val account = approvedAccount()
        val sessionStore = InMemoryAuthSessionStore()
        val signInModes = mutableListOf<GoogleSignInMode>()
        val folderRepository = EmptyFolderMappingRepository()
        val fakeContainer = object : AppContainer {
            override val authSessionManager = AuthSessionManager(
                accessPolicy = AccountAccessPolicy(setOf(account.email)),
                sessionStore = sessionStore,
                credentialStateClearer = CredentialStateClearer {},
            )
            override val googleSignInClient = GoogleSignInClient { _, mode ->
                signInModes += mode
                GoogleSignInResult.Success(account)
            }
            override val folderMappingRepository = folderRepository
            override val isGoogleSignInConfigured = true
        }
        application.testAppContainer = fakeContainer

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                composeRule.waitForIdle()

                assertEquals(0, folderRepository.observeCalls)
                composeRule.onNodeWithTag(AuthTestTags.SignInButton).performClick()
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertIsDisplayed()
                composeRule.onNodeWithTag(FolderAccessTestTags.Screen).assertIsDisplayed()
                assertEquals(listOf(GoogleSignInMode.Explicit), signInModes)
                assertEquals(1, folderRepository.observeCalls)

                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertIsDisplayed()
                assertEquals(listOf(GoogleSignInMode.Explicit), signInModes)
                assertEquals(1, folderRepository.observeCalls)

                scenario.recreate()
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertIsDisplayed()
                assertEquals(listOf(GoogleSignInMode.Explicit), signInModes)
                assertEquals(1, folderRepository.observeCalls)
            }
        } finally {
            application.testAppContainer = null
        }
    }

    @Test
    fun mainActivityRestoresCachedApprovedSessionWithoutCredentialRequest() {
        val application = ApplicationProvider.getApplicationContext<VijiBackupApplication>()
        val account = approvedAccount()
        val signInModes = mutableListOf<GoogleSignInMode>()
        val folderRepository = EmptyFolderMappingRepository()
        val fakeContainer = object : AppContainer {
            override val authSessionManager = AuthSessionManager(
                accessPolicy = AccountAccessPolicy(setOf(account.email)),
                sessionStore = InMemoryAuthSessionStore(account),
                credentialStateClearer = CredentialStateClearer {},
            )
            override val googleSignInClient = GoogleSignInClient { _, mode ->
                signInModes += mode
                error("Cached session must not request a Google credential")
            }
            override val folderMappingRepository = folderRepository
            override val isGoogleSignInConfigured = true
        }
        application.testAppContainer = fakeContainer

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertIsDisplayed()
                composeRule.onNodeWithTag(FolderAccessTestTags.Screen).assertIsDisplayed()
                assertTrue(signInModes.isEmpty())
                assertEquals(1, folderRepository.observeCalls)
            }
        } finally {
            application.testAppContainer = null
        }
    }

    @Test
    fun mainActivityCompletesActiveFolderPickerResultThroughRegistryAfterRecreation() {
        val application = ApplicationProvider.getApplicationContext<VijiBackupApplication>()
        val folderRepository = EmptyFolderMappingRepository()
        val registries = mutableListOf<ControllableActivityResultRegistry>()
        val requestToken = "registry-recreation-token"
        val treeUri = Uri.parse("content://provider.test/tree/recreated")
        val grantedFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        val selectedResult = FolderPickerResult.Selected(
            treeUri = treeUri,
            grantedFlags = grantedFlags,
        )
        val fakeContainer = object : AppContainer {
            override val authSessionManager = AuthSessionManager(
                accessPolicy = AccountAccessPolicy(emptySet()),
                sessionStore = InMemoryAuthSessionStore(),
                credentialStateClearer = CredentialStateClearer {},
            )
            override val googleSignInClient = GoogleSignInClient { _, _ ->
                error("Sign-in is not expected")
            }
            override val folderMappingRepository = folderRepository
            override val isGoogleSignInConfigured = false
        }
        application.testAppContainer = fakeContainer
        MainActivity.folderPickerActivityResultRegistryFactoryForTesting = {
            ControllableActivityResultRegistry().also(registries::add)
        }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                composeRule.waitForIdle()

                var originalRegistryKey: String? = null
                var originalRequestCode: Int? = null
                scenario.onActivity { activity ->
                    originalRegistryKey =
                        activity.launchFolderPickerThroughRegistryForTesting(requestToken)
                    originalRequestCode = registries.single().launchedRequestCodes.single()
                    assertEquals(
                        originalRegistryKey,
                        activity.currentFolderPickerRegistryKeyForTesting,
                    )
                    assertEquals(
                        requestToken,
                        activity.currentFolderPickerRequestTokenForTesting,
                    )
                }

                scenario.recreate()
                composeRule.waitForIdle()

                scenario.onActivity { activity ->
                    assertEquals(2, registries.size)
                    assertTrue(registries.last().launchedRequestCodes.isEmpty())
                    assertEquals(
                        originalRegistryKey,
                        activity.currentFolderPickerRegistryKeyForTesting,
                    )
                    assertEquals(
                        requestToken,
                        activity.currentFolderPickerRequestTokenForTesting,
                    )

                    val recreatedRegistry = registries.last()
                    assertTrue(
                        recreatedRegistry.dispatchResult(
                            checkNotNull(originalRequestCode),
                            selectedResult,
                        ),
                    )
                    assertTrue(
                        recreatedRegistry.dispatchResult(
                            checkNotNull(originalRequestCode),
                            FolderPickerResult.Cancelled,
                        ),
                    )
                }
                composeRule.waitForIdle()

                assertEquals(
                    listOf(
                        requestToken to FolderPickerSelection.Selected(
                            treeUri = treeUri.toString(),
                            grantedFlags = grantedFlags,
                        ),
                    ),
                    folderRepository.completionCalls,
                )
                scenario.onActivity { activity ->
                    assertNull(activity.currentFolderPickerRequestTokenForTesting)
                    assertNull(activity.currentFolderPickerRegistryKeyForTesting)
                }
            }
        } finally {
            MainActivity.folderPickerActivityResultRegistryFactoryForTesting = null
            application.testAppContainer = null
        }
    }

    @Test
    fun mainActivityDiscardsPickerCallbackAfterExplicitSignOut() {
        val application = ApplicationProvider.getApplicationContext<VijiBackupApplication>()
        val account = approvedAccount()
        val signInModes = mutableListOf<GoogleSignInMode>()
        val folderRepository = EmptyFolderMappingRepository()
        val fakeContainer = object : AppContainer {
            override val authSessionManager = AuthSessionManager(
                accessPolicy = AccountAccessPolicy(setOf(account.email)),
                sessionStore = InMemoryAuthSessionStore(),
                credentialStateClearer = CredentialStateClearer {},
            )
            override val googleSignInClient = GoogleSignInClient { _, mode ->
                signInModes += mode
                GoogleSignInResult.Success(account)
            }
            override val folderMappingRepository = folderRepository
            override val isGoogleSignInConfigured = true
        }
        application.testAppContainer = fakeContainer

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(AuthTestTags.SignInButton).performClick()
                composeRule.waitForIdle()

                var retiredRegistryKey: String? = null
                scenario.onActivity { activity ->
                    assertTrue(activity.stageFolderPickerRequestTokenForTesting("pending-token"))
                    retiredRegistryKey = activity.currentFolderPickerRegistryKeyForTesting
                }

                composeRule.onNodeWithTag(AuthTestTags.SignOutButton).performClick()
                composeRule.waitForIdle()

                scenario.recreate()
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(AuthTestTags.SignInButton).performClick()
                composeRule.waitForIdle()

                var replacementRegistryKey: String? = null
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.stageFolderPickerRequestTokenForTesting("replacement-token")
                    )
                    replacementRegistryKey = activity.currentFolderPickerRegistryKeyForTesting
                    assertTrue(replacementRegistryKey != retiredRegistryKey)
                }

                scenario.onActivity { activity ->
                    activity.deliverFolderPickerResultForTesting(
                        registryKey = checkNotNull(retiredRegistryKey),
                        result = FolderPickerResult.Selected(
                            treeUri = Uri.parse("content://provider.test/tree/late"),
                            grantedFlags =
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                        ),
                    )
                }
                composeRule.waitForIdle()

                assertEquals(
                    listOf(GoogleSignInMode.Explicit, GoogleSignInMode.Explicit),
                    signInModes,
                )
                assertEquals(1, folderRepository.prepareForSignOutCalls)
                assertTrue(folderRepository.completionCalls.isEmpty())
                scenario.onActivity { activity ->
                    assertEquals(
                        "replacement-token",
                        activity.currentFolderPickerRequestTokenForTesting,
                    )
                    assertEquals(
                        replacementRegistryKey,
                        activity.currentFolderPickerRegistryKeyForTesting,
                    )
                    activity.deliverFolderPickerResultForTesting(
                        registryKey = checkNotNull(replacementRegistryKey),
                        result = FolderPickerResult.Cancelled,
                    )
                }
                composeRule.waitForIdle()

                assertEquals(
                    listOf("replacement-token" to FolderPickerSelection.Cancelled),
                    folderRepository.completionCalls,
                )
                scenario.onActivity { activity ->
                    assertNull(activity.currentFolderPickerRequestTokenForTesting)
                    assertNull(activity.currentFolderPickerRegistryKeyForTesting)
                }
            }
        } finally {
            application.testAppContainer = null
        }
    }
}

private class EmptyFolderMappingRepository : FolderMappingRepository {
    private val mappings = MutableStateFlow(emptyList<FolderMapping>())
    var observeCalls = 0
    var prepareForSignOutCalls = 0
    val completionCalls = mutableListOf<Pair<String, FolderPickerSelection>>()

    override fun observeMappings(): Flow<List<FolderMapping>> {
        observeCalls += 1
        return mappings
    }

    override suspend fun beginAdd(): BeginFolderPickerResult =
        BeginFolderPickerResult.StorageFailure

    override suspend fun beginRepair(mappingId: String): BeginFolderPickerResult =
        BeginFolderPickerResult.StorageFailure

    override suspend fun completePicker(
        requestToken: String,
        selection: FolderPickerSelection,
    ): FolderPickerCompletion {
        completionCalls += requestToken to selection
        return FolderPickerCompletion.StorageFailure
    }

    override suspend fun prepareForSignOut(): PendingFolderCleanupResult {
        prepareForSignOutCalls += 1
        return PendingFolderCleanupResult.Complete
    }

    override suspend fun validate(mappingId: String): ValidateFolderAccessResult =
        ValidateFolderAccessResult.MappingNotFound

    override suspend fun beginScan(mappingId: String): BeginFolderScanResult =
        BeginFolderScanResult.MappingNotFound

    override suspend fun setEnabled(
        mappingId: String,
        enabled: Boolean,
    ): SetFolderEnabledResult = SetFolderEnabledResult.MappingNotFound

    override suspend fun remove(mappingId: String): RemoveFolderResult =
        RemoveFolderResult.StorageFailure
}

private class ControllableActivityResultRegistry : ActivityResultRegistry() {
    val launchedRequestCodes = mutableListOf<Int>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launchedRequestCodes += requestCode
    }
}

private class InMemoryAuthSessionStore(
    private var account: GoogleAccount? = null,
) : AuthSessionStore {

    override suspend fun read(): GoogleAccount? = account

    override suspend fun save(account: GoogleAccount) {
        this.account = account
    }

    override suspend fun clear() {
        account = null
    }
}

private fun approvedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "approved-subject",
        email = "approved.user@example.test",
        displayName = "Approved User",
    ),
)
