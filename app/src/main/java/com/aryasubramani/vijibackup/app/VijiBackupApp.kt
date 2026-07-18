package com.aryasubramani.vijibackup.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryasubramani.vijibackup.R
import com.aryasubramani.vijibackup.auth.presentation.AuthGateContent
import com.aryasubramani.vijibackup.auth.presentation.AuthStatusContent
import com.aryasubramani.vijibackup.auth.presentation.AuthTestTags
import com.aryasubramani.vijibackup.auth.presentation.AuthUiState
import com.aryasubramani.vijibackup.downloadsaccess.presentation.DownloadsAccessContent
import com.aryasubramani.vijibackup.downloadsaccess.presentation.DownloadsAccessUiState
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessContent
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessUiState
import com.aryasubramani.vijibackup.drive.presentation.DriveConnectionContent
import com.aryasubramani.vijibackup.drive.presentation.DriveConnectionUiState
import com.aryasubramani.vijibackup.ui.theme.VijiBackupTheme

@Composable
internal fun VijiBackupApp(
    uiState: AuthUiState,
    folderAccessUiState: FolderAccessUiState = FolderAccessUiState(),
    downloadsAccessUiState: DownloadsAccessUiState = DownloadsAccessUiState(),
    driveConnectionUiState: DriveConnectionUiState = DriveConnectionUiState(),
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onChangeAccount: () -> Unit = {},
    onAddFolder: () -> Unit = {},
    onRepairFolder: (String) -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onSetFolderEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onScanFolder: (String) -> Unit = {},
    onCancelScan: (String) -> Unit = {},
    onRequestDownloadsAccess: () -> Unit = {},
    onReviewDownloadsPermission: () -> Unit = {},
    onSetDownloadsEnabled: (Boolean) -> Unit = {},
    onRemoveDownloads: () -> Unit = {},
    onRefreshDownloads: () -> Unit = {},
    onScanDownloads: () -> Unit = {},
    onCancelDownloadsScan: () -> Unit = {},
    onConnectDrive: () -> Unit = {},
    onRefreshDrive: () -> Unit = {},
) {
    VijiBackupTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .testTag(AuthTestTags.Screen),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))

                    if (uiState is AuthUiState.Approved) {
                        ApprovedContent(
                            uiState = uiState,
                            folderAccessUiState = folderAccessUiState,
                            downloadsAccessUiState = downloadsAccessUiState,
                            driveConnectionUiState = driveConnectionUiState,
                            onSignOut = onSignOut,
                            onChangeAccount = onChangeAccount,
                            onAddFolder = onAddFolder,
                            onRepairFolder = onRepairFolder,
                            onRemoveFolder = onRemoveFolder,
                            onSetFolderEnabled = onSetFolderEnabled,
                            onScanFolder = onScanFolder,
                            onCancelScan = onCancelScan,
                            onRequestDownloadsAccess = onRequestDownloadsAccess,
                            onReviewDownloadsPermission = onReviewDownloadsPermission,
                            onSetDownloadsEnabled = onSetDownloadsEnabled,
                            onRemoveDownloads = onRemoveDownloads,
                            onRefreshDownloads = onRefreshDownloads,
                            onScanDownloads = onScanDownloads,
                            onCancelDownloadsScan = onCancelDownloadsScan,
                            onConnectDrive = onConnectDrive,
                            onRefreshDrive = onRefreshDrive,
                        )
                    } else {
                        AuthGateContent(
                            uiState = uiState,
                            onSignIn = onSignIn,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovedContent(
    uiState: AuthUiState.Approved,
    folderAccessUiState: FolderAccessUiState,
    downloadsAccessUiState: DownloadsAccessUiState,
    driveConnectionUiState: DriveConnectionUiState,
    onSignOut: () -> Unit,
    onChangeAccount: () -> Unit,
    onAddFolder: () -> Unit,
    onRepairFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onSetFolderEnabled: (String, Boolean) -> Unit,
    onScanFolder: (String) -> Unit,
    onCancelScan: (String) -> Unit,
    onRequestDownloadsAccess: () -> Unit,
    onReviewDownloadsPermission: () -> Unit,
    onSetDownloadsEnabled: (Boolean) -> Unit,
    onRemoveDownloads: () -> Unit,
    onRefreshDownloads: () -> Unit,
    onScanDownloads: () -> Unit,
    onCancelDownloadsScan: () -> Unit,
    onConnectDrive: () -> Unit,
    onRefreshDrive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VijiBackupTestTags.ProtectedContent),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuthStatusContent(
            title = stringResource(R.string.auth_approved_title),
            message = stringResource(
                R.string.auth_approved_message,
                uiState.account.email,
            ),
            actionLabel = stringResource(R.string.auth_change_account_action),
            actionTag = AuthTestTags.ChangeAccountButton,
            onAction = onChangeAccount,
            outlinedAction = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(AuthTestTags.SignOutButton),
        ) {
            Text(stringResource(R.string.auth_sign_out_action))
        }
        Spacer(Modifier.height(32.dp))
        DriveConnectionContent(
            uiState = driveConnectionUiState,
            onConnect = onConnectDrive,
            onRefresh = onRefreshDrive,
        )
        Spacer(Modifier.height(32.dp))
        DownloadsAccessContent(
            uiState = downloadsAccessUiState,
            onRequestAccess = onRequestDownloadsAccess,
            onReviewPermission = onReviewDownloadsPermission,
            onSetEnabled = onSetDownloadsEnabled,
            onRemove = onRemoveDownloads,
            onUseSafPicker = onAddFolder,
            onRefresh = onRefreshDownloads,
            onScan = onScanDownloads,
            onCancelScan = onCancelDownloadsScan,
        )
        Spacer(Modifier.height(32.dp))
        FolderAccessContent(
            uiState = folderAccessUiState,
            onAddFolder = onAddFolder,
            onRepairFolder = onRepairFolder,
            onRemoveFolder = onRemoveFolder,
            onSetFolderEnabled = onSetFolderEnabled,
            onScanFolder = onScanFolder,
            onCancelScan = onCancelScan,
        )
    }
}

internal object VijiBackupTestTags {
    const val ProtectedContent = "protected_content"
}
