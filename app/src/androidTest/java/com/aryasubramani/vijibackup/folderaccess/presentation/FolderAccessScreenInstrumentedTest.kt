package com.aryasubramani.vijibackup.folderaccess.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryasubramani.vijibackup.R
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping
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
        composeRule.onNodeWithText("mapping-a").assertDoesNotExist()
        composeRule.onNodeWithText("mapping-b").assertDoesNotExist()
        composeRule.onAllNodesWithTag(FolderAccessTestTags.MappingRow).assertCountEquals(2)

        composeRule.onNodeWithTag(FolderAccessTestTags.repairButton("mapping-b"))
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("mapping-b"), repaired)
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
            FolderAccessNotice.SelectionExpired to R.string.folder_access_notice_expired,
            FolderAccessNotice.InvalidSelection to R.string.folder_access_notice_invalid,
            FolderAccessNotice.ReadPermissionMissing to R.string.folder_access_notice_read_missing,
            FolderAccessNotice.DuplicateFolder to R.string.folder_access_notice_duplicate,
            FolderAccessNotice.GrantFailure to R.string.folder_access_notice_grant_failure,
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
    ) {
        setContent {
            VijiBackupTheme {
                FolderAccessContent(
                    uiState = state,
                    onAddFolder = onAddFolder,
                    onRepairFolder = onRepairFolder,
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
