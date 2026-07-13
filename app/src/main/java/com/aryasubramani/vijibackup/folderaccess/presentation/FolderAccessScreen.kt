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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
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
                enabled = !uiState.isLoading,
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
                    FolderMappingRow(
                        mapping = mapping,
                        fallbackIndex = index + 1,
                        onRepair = { onRepairFolder(mapping.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderMappingRow(
    mapping: FolderMapping,
    fallbackIndex: Int,
    onRepair: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = 12.dp)
            .testTag(FolderAccessTestTags.MappingRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = mapping.displayName
                    ?: stringResource(R.string.folder_access_fallback_name, fallbackIndex),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    if (mapping.enabled) {
                        R.string.folder_access_included
                    } else {
                        R.string.folder_access_paused
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = onRepair,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(FolderAccessTestTags.repairButton(mapping.id)),
        ) {
            Text(stringResource(R.string.folder_access_repair))
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

    fun repairButton(mappingId: String) = "folder_access_repair_$mappingId"
}
