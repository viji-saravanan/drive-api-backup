package com.aryasubramani.vijibackup.drive.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryasubramani.vijibackup.app.VijiBackupApp
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.auth.presentation.AuthUiState
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveConnectionScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun approvedContentShowsGoogleDriveBackupStatus() {
        composeRule.setContent {
            VijiBackupApp(
                uiState = AuthUiState.Approved(approvedAccount()),
                onSignIn = {},
                onRetry = {},
                onSignOut = {},
            )
        }

        composeRule.onNodeWithText("Google Drive backup").assertIsDisplayed()
    }

    @Test
    fun authorizationRequiredExplainsTheNextStepAndConnectsOnDemand() {
        var connectCalls = 0
        composeRule.setContent {
            DriveConnectionContent(
                uiState = DriveConnectionUiState(
                    health = DriveConnectionHealth.NeedsAuthorization,
                ),
                onConnect = { connectCalls += 1 },
                onRefresh = {},
            )
        }

        composeRule.onNodeWithText(
            "Allow Viji Backup to access Google Drive before backing up files.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Connect Google Drive").performClick()

        assertEquals(1, connectCalls)
    }

    @Test
    fun everyDriveHealthStateUsesPlainLanguage() {
        val state = mutableStateOf(DriveConnectionUiState())
        composeRule.setContent {
            DriveConnectionContent(
                uiState = state.value,
                onConnect = {},
                onRefresh = {},
            )
        }

        val expectedMessages = listOf(
            DriveConnectionHealth.Inactive to "Waiting for account confirmation.",
            DriveConnectionHealth.Checking to "Checking Google Drive access.",
            DriveConnectionHealth.ConfigurationRequired to
                "This build does not have a backup folder configured.",
            DriveConnectionHealth.NeedsAuthorization to
                "Allow Viji Backup to access Google Drive before backing up files.",
            DriveConnectionHealth.AccountMismatch to
                "Google Drive returned a different account. Change accounts and try again.",
            DriveConnectionHealth.DestinationMissingOrInaccessible to
                "The backup folder is missing or this account cannot open it. Check sharing access, then refresh.",
            DriveConnectionHealth.DestinationNotFolder to
                "The configured Drive destination is not a folder.",
            DriveConnectionHealth.DestinationTrashed to
                "The backup folder is in Drive trash. Restore it, then refresh.",
            DriveConnectionHealth.DestinationReadOnly to
                "This account can view the backup folder but cannot upload. Give it Editor access, then refresh.",
            DriveConnectionHealth.DestinationQuotaExceeded to
                "The backup Drive has no usable storage space. Free space, then refresh.",
            DriveConnectionHealth.TemporaryFailure to
                "Google Drive is temporarily unavailable. Check the connection and try again.",
            DriveConnectionHealth.ProviderUnavailable to
                "Google Play services could not open Drive authorization. Try again.",
            DriveConnectionHealth.InvalidResponse to
                "Google Drive returned an unexpected response. Try again.",
            DriveConnectionHealth.Ready to
                "Connected. This account can upload to the backup folder.",
        )

        expectedMessages.forEach { (health, message) ->
            composeRule.runOnIdle {
                state.value = DriveConnectionUiState(health = health)
            }
            composeRule.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun readyStateRefreshesAndBusyStateCannotStartAnotherAction() {
        var refreshCalls = 0
        val state = mutableStateOf(
            DriveConnectionUiState(health = DriveConnectionHealth.Ready),
        )
        composeRule.setContent {
            DriveConnectionContent(
                uiState = state.value,
                onConnect = {},
                onRefresh = { refreshCalls += 1 },
            )
        }

        composeRule.onNodeWithText("Refresh Drive status").performClick()
        assertEquals(1, refreshCalls)

        composeRule.runOnIdle {
            state.value = DriveConnectionUiState(
                health = DriveConnectionHealth.Checking,
                isBusy = true,
            )
        }

        composeRule.onNodeWithTag("drive_connection_progress").assertIsDisplayed()
        composeRule.onAllNodesWithTag(DriveConnectionTestTags.PrimaryAction)
            .assertCountEquals(0)
    }

    @Test
    fun awaitingConsentDisablesConnectAndExplainsCancellation() {
        val state = mutableStateOf(
            DriveConnectionUiState(
                health = DriveConnectionHealth.NeedsAuthorization,
                isBusy = true,
                isAwaitingAuthorization = true,
            ),
        )
        composeRule.setContent {
            DriveConnectionContent(
                uiState = state.value,
                onConnect = {},
                onRefresh = {},
            )
        }

        composeRule.onNodeWithTag(DriveConnectionTestTags.PrimaryAction)
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            state.value = DriveConnectionUiState(
                health = DriveConnectionHealth.NeedsAuthorization,
                notice = DriveConnectionNotice.AuthorizationCancelled,
            )
        }
        composeRule.onNodeWithText(
            "Google Drive authorization was not completed.",
        ).assertIsDisplayed()
    }
}

private fun approvedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "approved-subject",
        email = "approved.user@example.test",
        displayName = "Approved User",
    ),
)
