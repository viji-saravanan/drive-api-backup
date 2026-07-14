package com.aryasubramani.vijibackup.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessContent
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessUiState
import com.aryasubramani.vijibackup.ui.theme.VijiBackupTheme

@Composable
fun VijiBackupApp(
    uiState: AuthUiState,
    folderAccessUiState: FolderAccessUiState = FolderAccessUiState(),
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onAddFolder: () -> Unit = {},
    onRepairFolder: (String) -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
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
                            onSignOut = onSignOut,
                            onAddFolder = onAddFolder,
                            onRepairFolder = onRepairFolder,
                            onRemoveFolder = onRemoveFolder,
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
    onSignOut: () -> Unit,
    onAddFolder: () -> Unit,
    onRepairFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
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
            actionLabel = stringResource(R.string.auth_sign_out_action),
            actionTag = AuthTestTags.SignOutButton,
            onAction = onSignOut,
            outlinedAction = true,
        )
        Spacer(Modifier.height(32.dp))
        FolderAccessContent(
            uiState = folderAccessUiState,
            onAddFolder = onAddFolder,
            onRepairFolder = onRepairFolder,
            onRemoveFolder = onRemoveFolder,
        )
    }
}

internal object VijiBackupTestTags {
    const val ProtectedContent = "protected_content"
}
