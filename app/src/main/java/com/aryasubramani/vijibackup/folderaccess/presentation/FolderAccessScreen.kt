package com.aryasubramani.vijibackup.folderaccess.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryasubramani.vijibackup.R
import com.aryasubramani.vijibackup.folderaccess.domain.FolderMapping

@Composable
internal fun FolderAccessContent(
    uiState: FolderAccessUiState,
    onAddFolder: () -> Unit,
    onRepairFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit = {},
) {
    var pendingRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
    val actionsEnabled = !uiState.isLoading && uiState.removingMappingId == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FolderAccessTestTags.Screen),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.folder_access_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onAddFolder,
                enabled = actionsEnabled,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(FolderAccessTestTags.AddButton),
            ) {
                Text(stringResource(R.string.folder_access_add))
            }
        }

        uiState.notice?.let { notice ->
            Text(
                text = stringResource(notice.messageResource()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(FolderAccessTestTags.Notice)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(FolderAccessTestTags.Progress),
                )
            }
            uiState.mappings.isEmpty() -> Text(
                text = stringResource(R.string.folder_access_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(modifier = Modifier.fillMaxWidth()) {
                uiState.mappings.forEachIndexed { index, mapping ->
                    if (index > 0) HorizontalDivider()
                    val displayName = mapping.displayName
                        ?: stringResource(R.string.folder_access_fallback_name, index + 1)
                    FolderMappingRow(
                        mapping = mapping,
                        displayName = displayName,
                        isRemoving = uiState.removingMappingId == mapping.id,
                        actionsEnabled = actionsEnabled,
                        onRepair = { onRepairFolder(mapping.id) },
                        onRemove = { pendingRemovalId = mapping.id },
                    )
                }
            }
        }
    }

    val pendingRemovalIndex = uiState.mappings.indexOfFirst { mapping ->
        mapping.id == pendingRemovalId
    }
    if (pendingRemovalIndex >= 0) {
        val pendingRemoval = uiState.mappings[pendingRemovalIndex]
        val displayName = pendingRemoval.displayName
            ?: stringResource(
                R.string.folder_access_fallback_name,
                pendingRemovalIndex + 1,
            )
        AlertDialog(
            onDismissRequest = { pendingRemovalId = null },
            modifier = Modifier.testTag(FolderAccessTestTags.RemoveDialog),
            title = {
                Text(
                    stringResource(
                        R.string.folder_access_remove_dialog_title,
                        displayName,
                    ),
                )
            },
            text = {
                Text(stringResource(R.string.folder_access_remove_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemovalId = null
                        onRemoveFolder(pendingRemoval.id)
                    },
                    enabled = actionsEnabled,
                    modifier = Modifier.testTag(FolderAccessTestTags.ConfirmRemove),
                ) {
                    Text(stringResource(R.string.folder_access_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemovalId = null },
                    modifier = Modifier.testTag(FolderAccessTestTags.CancelRemove),
                ) {
                    Text(stringResource(R.string.folder_access_remove_cancel))
                }
            },
        )
    }
}

@Composable
private fun FolderMappingRow(
    mapping: FolderMapping,
    displayName: String,
    isRemoving: Boolean,
    actionsEnabled: Boolean,
    onRepair: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .padding(vertical = 12.dp)
            .testTag(FolderAccessTestTags.MappingRow),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isRemoving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = if (isRemoving) {
                    stringResource(R.string.folder_access_removing)
                } else {
                    stringResource(
                        if (mapping.enabled) {
                            R.string.folder_access_included
                        } else {
                            R.string.folder_access_paused
                        },
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.End,
            ),
        ) {
            OutlinedButton(
                onClick = onRepair,
                enabled = actionsEnabled,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(FolderAccessTestTags.repairButton(mapping.id)),
            ) {
                Text(stringResource(R.string.folder_access_repair))
            }
            OutlinedButton(
                onClick = onRemove,
                enabled = actionsEnabled,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(FolderAccessTestTags.removeButton(mapping.id)),
            ) {
                Text(stringResource(R.string.folder_access_remove))
            }
        }
    }
}

@StringRes
private fun FolderAccessNotice.messageResource(): Int = when (this) {
    FolderAccessNotice.PickerBusy -> R.string.folder_access_notice_busy
    FolderAccessNotice.MappingMissing -> R.string.folder_access_notice_mapping_missing
    FolderAccessNotice.FolderAdded -> R.string.folder_access_notice_added
    FolderAccessNotice.FolderRepaired -> R.string.folder_access_notice_repaired
    FolderAccessNotice.FolderRemoved -> R.string.folder_access_notice_removed
    FolderAccessNotice.SelectionExpired -> R.string.folder_access_notice_expired
    FolderAccessNotice.InvalidSelection -> R.string.folder_access_notice_invalid
    FolderAccessNotice.ReadPermissionMissing -> R.string.folder_access_notice_read_missing
    FolderAccessNotice.DuplicateFolder -> R.string.folder_access_notice_duplicate
    FolderAccessNotice.GrantFailure -> R.string.folder_access_notice_grant_failure
    FolderAccessNotice.RemovalGrantFailure ->
        R.string.folder_access_notice_removal_grant_failure
    FolderAccessNotice.StorageFailure -> R.string.folder_access_notice_storage_failure
    FolderAccessNotice.CleanupIncomplete -> R.string.folder_access_notice_cleanup
}

internal object FolderAccessTestTags {
    const val Screen = "folder_access_screen"
    const val Progress = "folder_access_progress"
    const val Notice = "folder_access_notice"
    const val AddButton = "folder_access_add"
    const val MappingRow = "folder_access_mapping"
    const val RemoveDialog = "folder_access_remove_dialog"
    const val ConfirmRemove = "folder_access_remove_confirm"
    const val CancelRemove = "folder_access_remove_cancel"

    fun repairButton(mappingId: String) = "folder_access_repair_$mappingId"
    fun removeButton(mappingId: String) = "folder_access_remove_$mappingId"
}
