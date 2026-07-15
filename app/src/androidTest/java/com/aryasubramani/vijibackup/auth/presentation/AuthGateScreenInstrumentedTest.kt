package com.aryasubramani.vijibackup.auth.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryasubramani.vijibackup.R
import com.aryasubramani.vijibackup.app.VijiBackupApp
import com.aryasubramani.vijibackup.app.VijiBackupTestTags
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.auth.google.GoogleSignInMode
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessTestTags
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthGateScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun signedOutOffersSignInWithoutRenderingProtectedContent() {
        var signInRequested = false
        composeRule.setContent {
            VijiBackupApp(
                uiState = AuthUiState.SignedOut(),
                onSignIn = { signInRequested = true },
                onRetry = {},
                onSignOut = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuthTestTags.SignInButton)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextEquals(appString(R.string.auth_sign_in_action))
            .performClick()
        composeRule.onNodeWithText(appString(R.string.auth_signed_out_title)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(VijiBackupTestTags.ProtectedContent).assertCountEquals(0)

        assertTrue(signInRequested)
    }

    @Test
    fun approvedAccountIsTheOnlyStateThatRendersProtectedContent() {
        var signOutRequested = false
        var addFolderRequested = false
        composeRule.setContent {
            VijiBackupApp(
                uiState = AuthUiState.Approved(approvedAccount()),
                folderAccessUiState = FolderAccessUiState(isLoading = false),
                onSignIn = {},
                onRetry = {},
                onSignOut = { signOutRequested = true },
                onAddFolder = { addFolderRequested = true },
                onRepairFolder = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VijiBackupTestTags.ProtectedContent).assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.auth_approved_title)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AuthTestTags.SignInButton).assertCountEquals(0)
        composeRule.onNodeWithTag(FolderAccessTestTags.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(FolderAccessTestTags.AddButton)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(AuthTestTags.SignOutButton)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextEquals(appString(R.string.auth_sign_out_action))
            .performClick()

        assertTrue(signOutRequested)
        assertTrue(addFolderRequested)
    }

    @Test
    fun everyNonApprovedStateKeepsProtectedContentAbsent() {
        val state = mutableStateOf<AuthUiState>(AuthUiState.Initializing)
        composeRule.setContent {
            VijiBackupApp(
                uiState = state.value,
                onSignIn = {},
                onRetry = {},
                onSignOut = {},
            )
        }
        composeRule.waitForIdle()

        val nonApprovedStates = listOf(
            AuthUiState.Initializing,
            AuthUiState.ConfigurationRequired,
            AuthUiState.SignedOut(),
            AuthUiState.ReauthenticationRequired(
                account = approvedAccount(),
                automaticAttemptPending = true,
            ),
            AuthUiState.ReauthenticationRequired(
                account = approvedAccount(),
                automaticAttemptPending = false,
            ),
            AuthUiState.SigningIn(
                request = AuthSignInRequest(1L, GoogleSignInMode.Explicit),
            ),
            AuthUiState.SigningIn(request = null),
            AuthUiState.Blocked(blockedAccount()),
            AuthUiState.Error(AuthError.Persistence),
            AuthUiState.SigningOut,
        )

        nonApprovedStates.forEach { nextState ->
            composeRule.runOnIdle { state.value = nextState }
            composeRule.onAllNodesWithTag(VijiBackupTestTags.ProtectedContent)
                .assertCountEquals(0)
            composeRule.onAllNodesWithTag(FolderAccessTestTags.Screen)
                .assertCountEquals(0)
        }
    }

    @Test
    fun everyGateStateAndErrorShowsItsSpecificUserFacingText() {
        val account = approvedAccount()
        val state = mutableStateOf<AuthUiState>(AuthUiState.Initializing)
        composeRule.setContent {
            VijiBackupApp(
                uiState = state.value,
                onSignIn = {},
                onRetry = {},
                onSignOut = {},
            )
        }

        val stateExpectations = listOf(
            StateTextExpectation(
                AuthUiState.Initializing,
                appString(R.string.auth_initializing_title),
                appString(R.string.auth_initializing_message),
            ),
            StateTextExpectation(
                AuthUiState.ConfigurationRequired,
                appString(R.string.auth_configuration_title),
                appString(R.string.auth_configuration_message),
            ),
            StateTextExpectation(
                AuthUiState.SignedOut(AuthWarning.ProviderStateNotCleared),
                appString(R.string.auth_signed_out_title),
                appString(R.string.auth_signed_out_message),
                appString(R.string.auth_warning_provider_state),
            ),
            StateTextExpectation(
                AuthUiState.SignedOut(AuthWarning.SignOutCleanupIncomplete),
                appString(R.string.auth_signed_out_title),
                appString(R.string.auth_signed_out_message),
                appString(R.string.auth_warning_sign_out_cleanup),
            ),
            StateTextExpectation(
                AuthUiState.ReauthenticationRequired(account, automaticAttemptPending = false),
                appString(R.string.auth_reauthentication_title),
                appString(R.string.auth_reauthentication_message, account.email),
            ),
            StateTextExpectation(
                AuthUiState.SigningIn(request = null),
                appString(R.string.auth_signing_in_title),
                appString(R.string.auth_signing_in_message),
            ),
            StateTextExpectation(
                AuthUiState.Blocked(blockedAccount()),
                appString(R.string.auth_blocked_title),
                appString(R.string.auth_blocked_message, blockedAccount().email),
            ),
            StateTextExpectation(
                AuthUiState.SigningOut,
                appString(R.string.auth_signing_out_title),
                appString(R.string.auth_signing_out_message),
            ),
        )
        stateExpectations.forEach { expectation ->
            composeRule.runOnIdle { state.value = expectation.state }
            composeRule.onNodeWithText(expectation.title).assertIsDisplayed()
            composeRule.onNodeWithText(expectation.message).assertIsDisplayed()
            expectation.warning?.let { warning ->
                composeRule.onNodeWithText(warning).assertIsDisplayed()
            }
        }

        val errorMessages = listOf(
            AuthError.Persistence to R.string.auth_error_persistence,
            AuthError.ProviderUnavailable to R.string.auth_error_provider_unavailable,
            AuthError.Interrupted to R.string.auth_error_interrupted,
            AuthError.InvalidCredential to R.string.auth_error_invalid_credential,
            AuthError.Unknown to R.string.auth_error_unknown,
        )
        errorMessages.forEach { (error, messageResource) ->
            composeRule.runOnIdle { state.value = AuthUiState.Error(error) }
            composeRule.onNodeWithText(appString(R.string.auth_error_title)).assertIsDisplayed()
            composeRule.onNodeWithText(appString(messageResource)).assertIsDisplayed()
            composeRule.onNodeWithTag(AuthTestTags.RetryButton)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun blockedAccountOffersAnotherAccountAndShowsCleanupWarning() {
        var signInRequested = false
        composeRule.setContent {
            VijiBackupApp(
                uiState = AuthUiState.Blocked(
                    account = blockedAccount(),
                    warning = AuthWarning.BlockedCleanupIncomplete,
                ),
                onSignIn = { signInRequested = true },
                onRetry = {},
                onSignOut = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuthTestTags.Warning)
            .assertIsDisplayed()
            .assertTextEquals(appString(R.string.auth_warning_blocked_cleanup))
        composeRule.onNodeWithTag(AuthTestTags.SignInButton)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue(signInRequested)
    }

    @Test
    fun signedOutCleanupWarningUsesGenericNonSensitiveMessage() {
        composeRule.setContent {
            VijiBackupApp(
                uiState = AuthUiState.SignedOut(AuthWarning.SignOutCleanupIncomplete),
                onSignIn = {},
                onRetry = {},
                onSignOut = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuthTestTags.Warning)
            .assertIsDisplayed()
            .assertTextEquals(appString(R.string.auth_warning_sign_out_cleanup))
    }

    @Test
    fun retryableFailureExposesOnlyTheRetryAction() {
        var retryRequested = false
        composeRule.setContent {
            VijiBackupApp(
                uiState = AuthUiState.Error(AuthError.ProviderUnavailable),
                onSignIn = {},
                onRetry = { retryRequested = true },
                onSignOut = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(AuthTestTags.SignInButton).assertCountEquals(0)
        composeRule.onNodeWithTag(AuthTestTags.RetryButton)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextEquals(appString(R.string.auth_retry_action))
            .performClick()

        assertTrue(retryRequested)
    }

    @Test
    fun busyStatesShowProgressWithoutInteractiveAuthActions() {
        val state = mutableStateOf<AuthUiState>(AuthUiState.Initializing)
        composeRule.setContent {
            VijiBackupApp(
                uiState = state.value,
                onSignIn = {},
                onRetry = {},
                onSignOut = {},
            )
        }
        composeRule.waitForIdle()

        val busyStates = listOf(
            AuthUiState.Initializing,
            AuthUiState.ReauthenticationRequired(
                account = approvedAccount(),
                automaticAttemptPending = true,
            ),
            AuthUiState.SigningIn(request = null),
            AuthUiState.SigningOut,
        )
        busyStates.forEach { nextState ->
            composeRule.runOnIdle { state.value = nextState }
            composeRule.onNodeWithTag(AuthTestTags.Progress).assertIsDisplayed()
                .assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
            composeRule.onAllNodesWithTag(AuthTestTags.SignInButton).assertCountEquals(0)
            composeRule.onAllNodesWithTag(AuthTestTags.RetryButton).assertCountEquals(0)
            composeRule.onAllNodesWithTag(AuthTestTags.SignOutButton).assertCountEquals(0)
        }
    }

    @Test
    fun completedAutomaticAttemptOffersManualAccountChooser() {
        var signInRequested = false
        composeRule.setContent {
            VijiBackupApp(
                uiState = AuthUiState.ReauthenticationRequired(
                    account = approvedAccount(),
                    automaticAttemptPending = false,
                ),
                onSignIn = { signInRequested = true },
                onRetry = {},
                onSignOut = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(AuthTestTags.Progress).assertCountEquals(0)
        composeRule.onNodeWithTag(AuthTestTags.SignInButton)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextEquals(appString(R.string.auth_choose_account_action))
            .performClick()

        assertTrue(signInRequested)
    }

    @Test
    fun statusIsAnnouncedAndPrimaryActionRemainsVisibleAtLargeFontScale() {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f),
            ) {
                VijiBackupApp(
                    uiState = AuthUiState.SignedOut(),
                    onSignIn = {},
                    onRetry = {},
                    onSignOut = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuthTestTags.StatusTitle)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithTag(AuthTestTags.SignInButton)
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}

private data class StateTextExpectation(
    val state: AuthUiState,
    val title: String,
    val message: String,
    val warning: String? = null,
)

private fun appString(resourceId: Int, vararg formatArgs: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(
        resourceId,
        *formatArgs,
    )

private fun approvedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "test-subject",
        email = "approved.user@example.test",
        displayName = "Approved User",
    ),
)

private fun blockedAccount() = requireNotNull(
    GoogleAccount.create(
        subject = "blocked-subject",
        email = "blocked.user@example.test",
        displayName = "Blocked User",
    ),
)
