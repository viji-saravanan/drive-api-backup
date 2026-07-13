package com.aryasubramani.vijibackup.app

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
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMappingRepository
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerCompletion
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
            ActivityScenario.launch(MainActivity::class.java).use {
                composeRule.waitForIdle()

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
    fun mainActivityDispatchesOnceRelocksAfterBackgroundAndSurvivesRecreation() {
        val application = ApplicationProvider.getApplicationContext<VijiBackupApplication>()
        val account = approvedAccount()
        val sessionStore = InMemoryAuthSessionStore()
        val signInModes = mutableListOf<GoogleSignInMode>()
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
            override val folderMappingRepository = EmptyFolderMappingRepository()
            override val isGoogleSignInConfigured = true
        }
        application.testAppContainer = fakeContainer

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(AuthTestTags.SignInButton).performClick()
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertIsDisplayed()
                assertEquals(listOf(GoogleSignInMode.Explicit), signInModes)

                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertIsDisplayed()
                assertEquals(
                    listOf(
                        GoogleSignInMode.Explicit,
                        GoogleSignInMode.AuthorizedAccounts,
                    ),
                    signInModes,
                )

                scenario.recreate()
                composeRule.waitForIdle()

                composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent)
                    .assertIsDisplayed()
                assertEquals(2, signInModes.size)
            }
        } finally {
            application.testAppContainer = null
        }
    }
}

private class EmptyFolderMappingRepository : FolderMappingRepository {
    override fun observeMappings(): Flow<List<FolderMapping>> = flowOf(emptyList())

    override suspend fun beginAdd(): BeginFolderPickerResult =
        BeginFolderPickerResult.StorageFailure

    override suspend fun beginRepair(mappingId: String): BeginFolderPickerResult =
        BeginFolderPickerResult.StorageFailure

    override suspend fun completePicker(
        requestToken: String,
        selection: FolderPickerSelection,
    ): FolderPickerCompletion = FolderPickerCompletion.StorageFailure
}

private class InMemoryAuthSessionStore : AuthSessionStore {
    private var account: GoogleAccount? = null

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
