package com.aryasubramani.vijibackup.downloadsaccess.presentation

import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryasubramani.vijibackup.downloadsaccess.data.DownloadsSourceConfiguration
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessHealth
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessSnapshot
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsScanProgress
import com.aryasubramani.vijibackup.ui.theme.VijiBackupTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsAccessScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unconfiguredDownloadsOffersThePermissionFlow() {
        var requested = false
        composeRule.setDownloadsContent(
            state = state(DownloadsAccessHealth.NotConfigured, configured = false),
            onRequestAccess = { requested = true },
        )

        composeRule.onNodeWithTag(DownloadsAccessTestTags.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadsAccessTestTags.Status).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadsAccessTestTags.PrimaryAction)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
            .performClick()

        assertTrue(requested)
    }

    @Test
    fun readyDownloadsExposesIndependentPauseManageAndConfirmedRemoveActions() {
        var enabled = true
        var permissionReviewRequested = false
        var removed = false
        composeRule.setDownloadsContent(
            state = state(DownloadsAccessHealth.Ready),
            onSetEnabled = { enabled = it },
            onReviewPermission = { permissionReviewRequested = true },
            onRemove = { removed = true },
        )

        composeRule.onNodeWithTag(DownloadsAccessTestTags.EnabledSwitch)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(DownloadsAccessTestTags.ManagePermission)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(DownloadsAccessTestTags.Remove)
            .assertIsDisplayed()
            .performClick()

        assertFalse(enabled)
        assertTrue(permissionReviewRequested)
        assertFalse(removed)
        composeRule.onNodeWithTag(DownloadsAccessTestTags.RemoveDialog).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadsAccessTestTags.ConfirmRemove).performClick()
        assertTrue(removed)
    }

    @Test
    fun legacyPlatformDelegatesDownloadsSelectionToTheExistingSafPicker() {
        var pickerRequested = false
        composeRule.setDownloadsContent(
            state = state(DownloadsAccessHealth.UseSafPicker, configured = false),
            onUseSafPicker = { pickerRequested = true },
        )

        composeRule.onNodeWithTag(DownloadsAccessTestTags.PrimaryAction)
            .assertIsDisplayed()
            .performClick()

        assertTrue(pickerRequested)
    }

    @Test
    fun readyDownloadsOffersScan() {
        var scanRequested = false
        composeRule.setDownloadsContent(
            state = state(DownloadsAccessHealth.Ready),
            onScan = { scanRequested = true },
        )

        composeRule.onNodeWithTag(DownloadsAccessTestTags.ScanStatus).assertIsDisplayed()
        composeRule.onNodeWithTag(DownloadsAccessTestTags.ScanAction)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertTrue(scanRequested)
    }

    @Test
    fun runningScanShowsAggregateProgressAndIsolatedCancellation() {
        val knownBytes = 1_348_273_556L
        var cancellationRequested = false
        composeRule.setDownloadsContent(
            state = state(
                health = DownloadsAccessHealth.Ready,
                scanState = DownloadsScanUiState.Running(
                    DownloadsScanProgress(
                        foldersVisited = 2,
                        filesDiscovered = 3,
                        knownBytes = knownBytes,
                        unreadableEntries = 1,
                    ),
                ),
            ),
            onCancelScan = { cancellationRequested = true },
        )
        val readableSize = Formatter.formatShortFileSize(composeRule.activity, knownBytes)
        composeRule.onNodeWithText(readableSize, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(knownBytes.toString(), substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(DownloadsAccessTestTags.CancelScan)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertTrue(cancellationRequested)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.setDownloadsContent(
        state: DownloadsAccessUiState,
        onRequestAccess: () -> Unit = {},
        onReviewPermission: () -> Unit = {},
        onSetEnabled: (Boolean) -> Unit = {},
        onRemove: () -> Unit = {},
        onUseSafPicker: () -> Unit = {},
        onScan: () -> Unit = {},
        onCancelScan: () -> Unit = {},
    ) {
        setContent {
            VijiBackupTheme {
                DownloadsAccessContent(
                    uiState = state,
                    onRequestAccess = onRequestAccess,
                    onReviewPermission = onReviewPermission,
                    onSetEnabled = onSetEnabled,
                    onRemove = onRemove,
                    onUseSafPicker = onUseSafPicker,
                    onScan = onScan,
                    onCancelScan = onCancelScan,
                )
            }
        }
        waitForIdle()
    }
}

private fun state(
    health: DownloadsAccessHealth,
    configured: Boolean = true,
    scanState: DownloadsScanUiState = DownloadsScanUiState.Idle,
) = DownloadsAccessUiState(
    snapshot = DownloadsAccessSnapshot(
        configuration = DownloadsSourceConfiguration(
            configured = configured,
            enabled = health != DownloadsAccessHealth.Disabled,
        ),
        health = health,
    ),
    isLoading = false,
    scanState = scanState,
)
