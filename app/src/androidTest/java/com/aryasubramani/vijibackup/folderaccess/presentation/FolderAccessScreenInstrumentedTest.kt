package com.aryasubramani.vijibackup.folderaccess.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryasubramani.vijibackup.R
import com.aryasubramani.vijibackup.folderaccess.domain.FolderAccessHealth
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
import com.aryasubramani.vijibackup.folderaccess.domain.FolderScanIssue
import com.aryasubramani.vijibackup.folderaccess.domain.FolderScanProgress
import com.aryasubramani.vijibackup.folderaccess.domain.FolderScanSummary
import com.aryasubramani.vijibackup.ui.theme.VijiBackupTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderAccessScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingStateBlocksAddAndShowsProgress() {
        composeRule.setFolderContent(FolderAccessUiState())

        composeRule.onNodeWithTag(FolderAccessTestTags.Progress).assertIsDisplayed()
        composeRule.onNodeWithTag(FolderAccessTestTags.AddButton)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun emptyReadyStateOffersOneWorkingAddAction() {
        var addCalls = 0
        composeRule.setFolderContent(
            state = FolderAccessUiState(isLoading = false),
            onAddFolder = { addCalls += 1 },
        )

        composeRule.onNodeWithText(appString(R.string.folder_access_empty)).assertIsDisplayed()
        composeRule.onNodeWithTag(FolderAccessTestTags.AddButton)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextEquals(appString(R.string.folder_access_add))
            .performClick()

        assertEquals(1, addCalls)
    }

    @Test
    fun mappingsUseDisplayOrGenericLabelWithoutShowingInternalIds() {
        val repaired = mutableListOf<String>()
        val mappings = listOf(
            FolderMapping(id = "mapping-a", displayName = "Camera", enabled = true),
            FolderMapping(id = "mapping-b", displayName = null, enabled = false),
        )
        composeRule.setFolderContent(
            state = FolderAccessUiState(mappings = mappings, isLoading = false),
            onRepairFolder = repaired::add,
        )

        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.folder_access_fallback_name, 2))
            .assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.folder_access_included)).assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.folder_access_paused)).assertIsDisplayed()
        composeRule.onAllNodesWithText("mapping-a").assertCountEquals(0)
        composeRule.onAllNodesWithText("mapping-b").assertCountEquals(0)
        composeRule.onAllNodesWithTag(FolderAccessTestTags.MappingRow).assertCountEquals(2)

        composeRule.onNodeWithTag(FolderAccessTestTags.repairButton("mapping-b"))
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("mapping-b"), repaired)
    }

    @Test
    fun disabledReadyMappingCanToggleAndStartManualScan() {
        val mapping = FolderMapping("mapping-a", "Downloads test", enabled = false)
        val enabledChanges = mutableListOf<Pair<String, Boolean>>()
        val scans = mutableListOf<String>()
        composeRule.setFolderContent(
            state = FolderAccessUiState(
                mappings = listOf(mapping),
                isLoading = false,
                healthByMappingId = mapOf(mapping.id to FolderAccessHealth.Ready),
                scanStateByMappingId = mapOf(mapping.id to FolderScanUiState.NotStarted),
            ),
            onSetFolderEnabled = { mappingId, enabled ->
                enabledChanges += mappingId to enabled
            },
            onScanFolder = scans::add,
        )

        composeRule.onNodeWithTag(FolderAccessTestTags.enabledSwitch(mapping.id))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(FolderAccessTestTags.scanButton(mapping.id))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf(mapping.id to true), enabledChanges)
        assertEquals(listOf(mapping.id), scans)
    }

    @Test
    fun runningScanShowsLongProgressAndForwardsExactCancellation() {
        val mapping = FolderMapping("mapping-a", "Camera", enabled = true)
        val progress = FolderScanProgress(
            foldersVisited = Long.MAX_VALUE,
            filesDiscovered = Long.MAX_VALUE - 1,
            knownBytes = Long.MAX_VALUE - 2,
            filesWithUnknownSize = 3,
            unreadableEntries = 4,
        )
        val cancellations = mutableListOf<String>()
        composeRule.setFolderContent(
            state = FolderAccessUiState(
                mappings = listOf(mapping),
                isLoading = false,
                healthByMappingId = mapOf(mapping.id to FolderAccessHealth.Ready),
                scanStateByMappingId = mapOf(
                    mapping.id to FolderScanUiState.Running(progress),
                ),
            ),
            onCancelScan = cancellations::add,
        )

        composeRule.onNodeWithTag(FolderAccessTestTags.scanStatus(mapping.id))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            appString(
                R.string.folder_access_scan_progress,
                progress.foldersVisited,
                progress.filesDiscovered,
                progress.knownBytes,
                progress.filesWithUnknownSize,
                progress.unreadableEntries,
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(FolderAccessTestTags.cancelScanButton(mapping.id))
            .assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithTag(FolderAccessTestTags.scanButton(mapping.id))
            .assertCountEquals(0)
        assertEquals(listOf(mapping.id), cancellations)
    }

    @Test
    fun everyHealthAndTerminalScanStateHasTypedText() {
        val mapping = FolderMapping("mapping-a", "Camera", enabled = true)
        val state = mutableStateOf(
            FolderAccessUiState(mappings = listOf(mapping), isLoading = false),
        )
        composeRule.setContent {
            VijiBackupTheme {
                FolderAccessContent(
                    uiState = state.value,
                    onAddFolder = {},
                    onRepairFolder = {},
                )
            }
        }

        val healthResources = listOf(
            FolderAccessHealth.Checking to R.string.folder_access_health_checking,
            FolderAccessHealth.Ready to R.string.folder_access_health_ready,
            FolderAccessHealth.PermissionMissing to R.string.folder_access_health_permission_missing,
            FolderAccessHealth.TreeMissing to R.string.folder_access_health_tree_missing,
            FolderAccessHealth.ProviderAuthRequired to R.string.folder_access_health_auth_required,
            FolderAccessHealth.TemporarilyUnavailable to
                R.string.folder_access_health_temporarily_unavailable,
        )
        healthResources.forEach { (health, resource) ->
            composeRule.runOnIdle {
                state.value = state.value.copy(
                    healthByMappingId = mapOf(mapping.id to health),
                    scanStateByMappingId = mapOf(mapping.id to FolderScanUiState.NotStarted),
                )
            }
            composeRule.onNodeWithTag(FolderAccessTestTags.health(mapping.id))
                .assertTextEquals(appString(resource))
        }

        val summary = FolderScanSummary(
            progress = FolderScanProgress(filesDiscovered = 2, unreadableEntries = 1),
            elapsedTimeMillis = 10,
            issues = setOf(FolderScanIssue.MalformedEntry),
        )
        val terminalResources = listOf(
            FolderScanUiState.Complete(summary) to R.string.folder_access_scan_complete,
            FolderScanUiState.Partial(summary) to R.string.folder_access_scan_partial,
            FolderScanUiState.Failed(summary) to R.string.folder_access_scan_failed,
            FolderScanUiState.Failed() to R.string.folder_access_scan_failed,
            FolderScanUiState.Cancelled(summary.progress) to R.string.folder_access_scan_cancelled,
        )
        terminalResources.forEach { (scanState, resource) ->
            composeRule.runOnIdle {
                state.value = state.value.copy(
                    healthByMappingId = mapOf(mapping.id to FolderAccessHealth.Ready),
                    scanStateByMappingId = mapOf(mapping.id to scanState),
                )
            }
            composeRule.onNodeWithTag(FolderAccessTestTags.scanStatus(mapping.id))
                .assertTextEquals(appString(resource))
        }
    }

    @Test
    fun degradedMappingDisablesScanButKeepsRepairAndRemoveAvailable() {
        val mapping = FolderMapping("mapping-a", "Camera", enabled = true)
        composeRule.setFolderContent(
            state = FolderAccessUiState(
                mappings = listOf(mapping),
                isLoading = false,
                healthByMappingId = mapOf(
                    mapping.id to FolderAccessHealth.PermissionMissing,
                ),
                scanStateByMappingId = mapOf(mapping.id to FolderScanUiState.NotStarted),
            ),
        )

        composeRule.onNodeWithTag(FolderAccessTestTags.scanButton(mapping.id))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(FolderAccessTestTags.repairButton(mapping.id))
            .assertIsEnabled()
        composeRule.onNodeWithTag(FolderAccessTestTags.removeButton(mapping.id))
            .assertIsEnabled()
    }

    @Test
    fun removeRequiresNamedConfirmationBeforeForwardingExactMappingId() {
        val removed = mutableListOf<String>()
        val mapping = FolderMapping(
            id = "mapping-a",
            displayName = "Camera",
            enabled = true,
        )
        composeRule.setFolderContent(
            state = FolderAccessUiState(
                mappings = listOf(mapping),
                isLoading = false,
            ),
            onRemoveFolder = removed::add,
        )

        composeRule.onNodeWithTag(FolderAccessTestTags.removeButton(mapping.id))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(FolderAccessTestTags.RemoveDialog).assertIsDisplayed()
        composeRule.onNodeWithText(
            appString(R.string.folder_access_remove_dialog_title, "Camera"),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            appString(R.string.folder_access_remove_dialog_message),
        ).assertIsDisplayed()
        assertTrue(removed.isEmpty())

        composeRule.onNodeWithTag(FolderAccessTestTags.ConfirmRemove).performClick()

        assertEquals(listOf(mapping.id), removed)
    }

    @Test
    fun cancellingRemovalUsesFallbackNameAndHasNoSideEffect() {
        val removed = mutableListOf<String>()
        val mapping = FolderMapping(
            id = "mapping-a",
            displayName = null,
            enabled = true,
        )
        composeRule.setFolderContent(
            state = FolderAccessUiState(
                mappings = listOf(mapping),
                isLoading = false,
            ),
            onRemoveFolder = removed::add,
        )

        composeRule.onNodeWithTag(FolderAccessTestTags.removeButton(mapping.id)).performClick()
        composeRule.onNodeWithText(
            appString(
                R.string.folder_access_remove_dialog_title,
                appString(R.string.folder_access_fallback_name, 1),
            ),
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(FolderAccessTestTags.CancelRemove).performClick()

        composeRule.onAllNodesWithTag(FolderAccessTestTags.RemoveDialog).assertCountEquals(0)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun activeRemovalDisablesEveryCompetingFolderActionAndShowsProgress() {
        val mappings = listOf(
            FolderMapping(id = "mapping-a", displayName = "Camera", enabled = true),
            FolderMapping(id = "mapping-b", displayName = "Pictures", enabled = true),
        )
        composeRule.setFolderContent(
            state = FolderAccessUiState(
                mappings = mappings,
                isLoading = false,
                removingMappingId = "mapping-a",
            ),
        )

        composeRule.onNodeWithTag(FolderAccessTestTags.AddButton).assertIsNotEnabled()
        mappings.forEach { mapping ->
            composeRule.onNodeWithTag(FolderAccessTestTags.repairButton(mapping.id))
                .assertIsNotEnabled()
            composeRule.onNodeWithTag(FolderAccessTestTags.removeButton(mapping.id))
                .assertIsNotEnabled()
        }
        composeRule.onNodeWithText(appString(R.string.folder_access_removing))
            .assertIsDisplayed()
    }

    @Test
    fun everyOperationNoticeHasSpecificNonSensitiveText() {
        val state = mutableStateOf(
            FolderAccessUiState(isLoading = false, notice = FolderAccessNotice.PickerBusy),
        )
        composeRule.setContent {
            VijiBackupTheme {
                FolderAccessContent(
                    uiState = state.value,
                    onAddFolder = {},
                    onRepairFolder = {},
                )
            }
        }

        val expectations = listOf(
            FolderAccessNotice.PickerBusy to R.string.folder_access_notice_busy,
            FolderAccessNotice.MappingMissing to R.string.folder_access_notice_mapping_missing,
            FolderAccessNotice.FolderAdded to R.string.folder_access_notice_added,
            FolderAccessNotice.FolderRepaired to R.string.folder_access_notice_repaired,
            FolderAccessNotice.FolderRemoved to R.string.folder_access_notice_removed,
            FolderAccessNotice.SelectionExpired to R.string.folder_access_notice_expired,
            FolderAccessNotice.InvalidSelection to R.string.folder_access_notice_invalid,
            FolderAccessNotice.ReadPermissionMissing to R.string.folder_access_notice_read_missing,
            FolderAccessNotice.DuplicateFolder to R.string.folder_access_notice_duplicate,
            FolderAccessNotice.GrantFailure to R.string.folder_access_notice_grant_failure,
            FolderAccessNotice.RemovalGrantFailure to
                R.string.folder_access_notice_removal_grant_failure,
            FolderAccessNotice.StorageFailure to R.string.folder_access_notice_storage_failure,
            FolderAccessNotice.CleanupIncomplete to R.string.folder_access_notice_cleanup,
        )

        expectations.forEach { (notice, stringResource) ->
            composeRule.runOnIdle {
                state.value = state.value.copy(notice = notice)
            }
            composeRule.onNodeWithTag(FolderAccessTestTags.Notice)
                .assertIsDisplayed()
                .assertTextEquals(appString(stringResource))
        }
        assertTrue(expectations.map { it.second }.toSet().size == expectations.size)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.setFolderContent(
        state: FolderAccessUiState,
        onAddFolder: () -> Unit = {},
        onRepairFolder: (String) -> Unit = {},
        onRemoveFolder: (String) -> Unit = {},
        onSetFolderEnabled: (String, Boolean) -> Unit = { _, _ -> },
        onScanFolder: (String) -> Unit = {},
        onCancelScan: (String) -> Unit = {},
    ) {
        setContent {
            VijiBackupTheme {
                FolderAccessContent(
                    uiState = state,
                    onAddFolder = onAddFolder,
                    onRepairFolder = onRepairFolder,
                    onRemoveFolder = onRemoveFolder,
                    onSetFolderEnabled = onSetFolderEnabled,
                    onScanFolder = onScanFolder,
                    onCancelScan = onCancelScan,
                )
            }
        }
    }

    private fun appString(resource: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(
            resource,
            *formatArgs,
        )
}
