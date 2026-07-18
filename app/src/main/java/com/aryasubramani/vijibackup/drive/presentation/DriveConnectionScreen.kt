package com.aryasubramani.vijibackup.drive.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

@Composable
internal fun DriveConnectionContent(
    uiState: DriveConnectionUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DriveConnectionTestTags.Screen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.drive_connection_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        uiState.notice?.let { notice ->
            Text(
                text = stringResource(notice.messageResource()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DriveConnectionTestTags.Notice)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            text = stringResource(uiState.statusResource()),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DriveConnectionTestTags.Status)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.health == DriveConnectionHealth.Ready) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (uiState.isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(DriveConnectionTestTags.Progress),
                )
            }
        }
        uiState.health.primaryAction()?.let { action ->
            Button(
                onClick = when (action) {
                    DriveConnectionAction.Connect -> onConnect
                    DriveConnectionAction.Refresh -> onRefresh
                },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(DriveConnectionTestTags.PrimaryAction),
            ) {
                Text(stringResource(action.labelResource()))
            }
        }
    }
}

internal object DriveConnectionTestTags {
    const val Screen = "drive_connection_screen"
    const val Status = "drive_connection_status"
    const val Notice = "drive_connection_notice"
    const val Progress = "drive_connection_progress"
    const val PrimaryAction = "drive_connection_primary_action"
}

private enum class DriveConnectionAction {
    Connect,
    Refresh,
}

private fun DriveConnectionHealth.primaryAction(): DriveConnectionAction? = when (this) {
    DriveConnectionHealth.NeedsAuthorization,
    DriveConnectionHealth.ProviderUnavailable,
    -> DriveConnectionAction.Connect
    DriveConnectionHealth.AccountMismatch,
    DriveConnectionHealth.DestinationMissingOrInaccessible,
    DriveConnectionHealth.DestinationNotFolder,
    DriveConnectionHealth.DestinationTrashed,
    DriveConnectionHealth.DestinationReadOnly,
    DriveConnectionHealth.DestinationQuotaExceeded,
    DriveConnectionHealth.TemporaryFailure,
    DriveConnectionHealth.InvalidResponse,
    DriveConnectionHealth.Ready,
    -> DriveConnectionAction.Refresh
    DriveConnectionHealth.Inactive,
    DriveConnectionHealth.Checking,
    DriveConnectionHealth.ConfigurationRequired,
    -> null
}

@StringRes
private fun DriveConnectionAction.labelResource(): Int = when (this) {
    DriveConnectionAction.Connect -> R.string.drive_connection_connect
    DriveConnectionAction.Refresh -> R.string.drive_connection_refresh
}

@StringRes
private fun DriveConnectionUiState.statusResource(): Int =
    if (isAwaitingAuthorization) {
        R.string.drive_connection_awaiting_authorization
    } else {
        health.messageResource()
    }

@StringRes
private fun DriveConnectionHealth.messageResource(): Int = when (this) {
    DriveConnectionHealth.Inactive -> R.string.drive_connection_inactive
    DriveConnectionHealth.Checking -> R.string.drive_connection_checking
    DriveConnectionHealth.ConfigurationRequired -> R.string.drive_connection_configuration_required
    DriveConnectionHealth.NeedsAuthorization -> R.string.drive_connection_needs_authorization
    DriveConnectionHealth.AccountMismatch -> R.string.drive_connection_account_mismatch
    DriveConnectionHealth.DestinationMissingOrInaccessible ->
        R.string.drive_connection_destination_inaccessible
    DriveConnectionHealth.DestinationNotFolder -> R.string.drive_connection_destination_not_folder
    DriveConnectionHealth.DestinationTrashed -> R.string.drive_connection_destination_trashed
    DriveConnectionHealth.DestinationReadOnly -> R.string.drive_connection_destination_read_only
    DriveConnectionHealth.DestinationQuotaExceeded ->
        R.string.drive_connection_destination_quota
    DriveConnectionHealth.TemporaryFailure -> R.string.drive_connection_temporary_failure
    DriveConnectionHealth.ProviderUnavailable -> R.string.drive_connection_provider_unavailable
    DriveConnectionHealth.InvalidResponse -> R.string.drive_connection_invalid_response
    DriveConnectionHealth.Ready -> R.string.drive_connection_ready
}

@StringRes
private fun DriveConnectionNotice.messageResource(): Int = when (this) {
    DriveConnectionNotice.AuthorizationCancelled -> R.string.drive_connection_notice_cancelled
    DriveConnectionNotice.AuthorizationUnavailable -> R.string.drive_connection_notice_unavailable
}
