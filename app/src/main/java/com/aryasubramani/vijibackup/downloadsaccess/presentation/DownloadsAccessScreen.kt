package com.aryasubramani.vijibackup.downloadsaccess.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryasubramani.vijibackup.R
import com.aryasubramani.vijibackup.core.formatReadableFileSize
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessHealth

@Composable
internal fun DownloadsAccessContent(
    uiState: DownloadsAccessUiState,
    onRequestAccess: () -> Unit,
    onReviewPermission: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onUseSafPicker: () -> Unit,
    onRefresh: () -> Unit = {},
    onScan: () -> Unit = {},
    onCancelScan: () -> Unit = {},
) {
    val context = LocalContext.current
    var showRemoveConfirmation by rememberSaveable { mutableStateOf(false) }
    val snapshot = uiState.snapshot
    val health = snapshot?.health
    val baseActionsEnabled =
        !uiState.isLoading && !uiState.isBusy && !uiState.isAwaitingSettings
    val scanInProgress =
        uiState.scanState is DownloadsScanUiState.Running ||
            uiState.scanState is DownloadsScanUiState.Cancelling
    val actionsEnabled = baseActionsEnabled && !scanInProgress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DownloadsAccessTestTags.Screen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.downloads_access_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        uiState.notice?.let { notice ->
            Text(
                text = stringResource(notice.messageResource()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DownloadsAccessTestTags.Notice)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(DownloadsAccessTestTags.Progress),
                )
            }
        } else if (health != null) {
            Text(
                text = stringResource(health.messageResource()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DownloadsAccessTestTags.Status)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge,
                color = if (health == DownloadsAccessHealth.Ready) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (snapshot.configuration.configured) {
                val description = stringResource(R.string.downloads_access_enabled_description)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.downloads_access_include))
                    Switch(
                        checked = snapshot.configuration.enabled,
                        onCheckedChange = onSetEnabled,
                        enabled = actionsEnabled,
                        modifier = Modifier
                            .testTag(DownloadsAccessTestTags.EnabledSwitch)
                            .semantics { contentDescription = description },
                    )
                }
            }

            if (snapshot.configuration.configured) {
                Text(
                    text = stringResource(uiState.scanState.messageResource()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DownloadsAccessTestTags.ScanStatus)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                )
                uiState.scanState.progressOrNull()?.let { progress ->
                    Text(
                        text = stringResource(
                            R.string.downloads_access_scan_progress,
                            progress.foldersVisited,
                            progress.filesDiscovered,
                            formatReadableFileSize(context, progress.knownBytes),
                            progress.filesWithUnknownSize,
                            progress.unreadableEntries,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (uiState.scanState) {
                    is DownloadsScanUiState.Running,
                    is DownloadsScanUiState.Cancelling,
                    -> OutlinedButton(
                        onClick = onCancelScan,
                        enabled = baseActionsEnabled &&
                            uiState.scanState is DownloadsScanUiState.Running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(DownloadsAccessTestTags.CancelScan),
                    ) {
                        Text(stringResource(R.string.downloads_access_cancel_scan))
                    }
                    else -> if (health == DownloadsAccessHealth.Ready) {
                        OutlinedButton(
                            onClick = onScan,
                            enabled = actionsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(DownloadsAccessTestTags.ScanAction),
                        ) {
                            Text(stringResource(R.string.downloads_access_scan))
                        }
                    }
                }
            }

            health.primaryAction()?.let { action ->
                Button(
                    onClick = when (action) {
                        DownloadsPrimaryAction.RequestAccess -> onRequestAccess
                        DownloadsPrimaryAction.Refresh -> onRefresh
                        DownloadsPrimaryAction.UseSafPicker -> onUseSafPicker
                    },
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(DownloadsAccessTestTags.PrimaryAction),
                ) {
                    Text(stringResource(action.labelResource()))
                }
            }

            if (health != DownloadsAccessHealth.UseSafPicker && health != DownloadsAccessHealth.NotConfigured) {
                OutlinedButton(
                    onClick = onReviewPermission,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(DownloadsAccessTestTags.ManagePermission),
                ) {
                    Text(stringResource(R.string.downloads_access_manage_permission))
                }
            }

            if (snapshot.configuration.configured) {
                OutlinedButton(
                    onClick = { showRemoveConfirmation = true },
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(DownloadsAccessTestTags.Remove),
                ) {
                    Text(stringResource(R.string.downloads_access_remove))
                }
            }
        }
    }

    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            modifier = Modifier.testTag(DownloadsAccessTestTags.RemoveDialog),
            title = { Text(stringResource(R.string.downloads_access_remove_dialog_title)) },
            text = { Text(stringResource(R.string.downloads_access_remove_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmation = false
                        onRemove()
                    },
                    enabled = actionsEnabled,
                    modifier = Modifier.testTag(DownloadsAccessTestTags.ConfirmRemove),
                ) {
                    Text(stringResource(R.string.downloads_access_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) {
                    Text(stringResource(R.string.downloads_access_remove_cancel))
                }
            },
        )
    }
}

private enum class DownloadsPrimaryAction {
    RequestAccess,
    Refresh,
    UseSafPicker,
}

private fun DownloadsAccessHealth.primaryAction(): DownloadsPrimaryAction? = when (this) {
    DownloadsAccessHealth.NotConfigured,
    DownloadsAccessHealth.PermissionGrantedButUnused,
    DownloadsAccessHealth.NeedsPermission,
    -> DownloadsPrimaryAction.RequestAccess
    DownloadsAccessHealth.StorageUnavailable -> DownloadsPrimaryAction.Refresh
    DownloadsAccessHealth.UseSafPicker -> DownloadsPrimaryAction.UseSafPicker
    DownloadsAccessHealth.Disabled,
    DownloadsAccessHealth.Ready,
    -> null
}

@StringRes
private fun DownloadsPrimaryAction.labelResource(): Int = when (this) {
    DownloadsPrimaryAction.RequestAccess -> R.string.downloads_access_add_or_repair
    DownloadsPrimaryAction.Refresh -> R.string.downloads_access_refresh
    DownloadsPrimaryAction.UseSafPicker -> R.string.downloads_access_choose_folder
}

@StringRes
private fun DownloadsAccessHealth.messageResource(): Int = when (this) {
    DownloadsAccessHealth.NotConfigured -> R.string.downloads_access_not_configured
    DownloadsAccessHealth.PermissionGrantedButUnused -> R.string.downloads_access_permission_unused
    DownloadsAccessHealth.NeedsPermission -> R.string.downloads_access_needs_permission
    DownloadsAccessHealth.StorageUnavailable -> R.string.downloads_access_storage_unavailable
    DownloadsAccessHealth.Disabled -> R.string.downloads_access_disabled
    DownloadsAccessHealth.Ready -> R.string.downloads_access_ready
    DownloadsAccessHealth.UseSafPicker -> R.string.downloads_access_use_picker
}

@StringRes
private fun DownloadsAccessNotice.messageResource(): Int = when (this) {
    DownloadsAccessNotice.PermissionNotGranted -> R.string.downloads_access_notice_permission_denied
    DownloadsAccessNotice.SettingsUnavailable -> R.string.downloads_access_notice_settings_unavailable
    DownloadsAccessNotice.SourceRemoved -> R.string.downloads_access_notice_removed
    DownloadsAccessNotice.StorageFailure -> R.string.downloads_access_notice_storage_failure
}

@StringRes
private fun DownloadsScanUiState.messageResource(): Int = when (this) {
    DownloadsScanUiState.Idle -> R.string.downloads_access_scan_idle
    is DownloadsScanUiState.Running -> R.string.downloads_access_scan_running
    is DownloadsScanUiState.Cancelling -> R.string.downloads_access_scan_cancelling
    is DownloadsScanUiState.Complete -> R.string.downloads_access_scan_complete
    is DownloadsScanUiState.Partial -> R.string.downloads_access_scan_partial
    is DownloadsScanUiState.Failed -> R.string.downloads_access_scan_failed
    is DownloadsScanUiState.Cancelled -> R.string.downloads_access_scan_cancelled
}

private fun DownloadsScanUiState.progressOrNull() = when (this) {
    DownloadsScanUiState.Idle -> null
    is DownloadsScanUiState.Running -> progress
    is DownloadsScanUiState.Cancelling -> progress
    is DownloadsScanUiState.Complete -> summary.progress
    is DownloadsScanUiState.Partial -> summary.progress
    is DownloadsScanUiState.Failed -> summary?.progress
    is DownloadsScanUiState.Cancelled -> progress
}

internal object DownloadsAccessTestTags {
    const val Screen = "downloads_access_screen"
    const val Status = "downloads_access_status"
    const val Notice = "downloads_access_notice"
    const val Progress = "downloads_access_progress"
    const val PrimaryAction = "downloads_access_primary_action"
    const val EnabledSwitch = "downloads_access_enabled"
    const val ManagePermission = "downloads_access_manage_permission"
    const val Remove = "downloads_access_remove"
    const val RemoveDialog = "downloads_access_remove_dialog"
    const val ConfirmRemove = "downloads_access_remove_confirm"
    const val ScanStatus = "downloads_access_scan_status"
    const val ScanAction = "downloads_access_scan"
    const val CancelScan = "downloads_access_cancel_scan"
}
