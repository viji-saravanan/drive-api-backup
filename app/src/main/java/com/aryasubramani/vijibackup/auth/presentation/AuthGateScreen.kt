package com.aryasubramani.vijibackup.auth.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryasubramani.vijibackup.R

@Composable
internal fun AuthGateContent(
    uiState: AuthUiState,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
) {
    when (uiState) {
        AuthUiState.Initializing -> AuthStatusContent(
            title = stringResource(R.string.auth_initializing_title),
            message = stringResource(R.string.auth_initializing_message),
            showProgress = true,
        )

        AuthUiState.ConfigurationRequired -> AuthStatusContent(
            title = stringResource(R.string.auth_configuration_title),
            message = stringResource(R.string.auth_configuration_message),
        )

        is AuthUiState.SignedOut -> AuthStatusContent(
            title = stringResource(R.string.auth_signed_out_title),
            message = stringResource(R.string.auth_signed_out_message),
            warning = uiState.warning?.warningText(),
            actionLabel = stringResource(R.string.auth_sign_in_action),
            actionTag = AuthTestTags.SignInButton,
            onAction = onSignIn,
        )

        is AuthUiState.ReauthenticationRequired -> AuthStatusContent(
            title = stringResource(R.string.auth_reauthentication_title),
            message = stringResource(
                R.string.auth_reauthentication_message,
                uiState.account.email,
            ),
            showProgress = uiState.automaticAttemptPending,
            actionLabel = if (uiState.automaticAttemptPending) {
                null
            } else {
                stringResource(R.string.auth_choose_account_action)
            },
            actionTag = AuthTestTags.SignInButton,
            onAction = onSignIn,
        )

        is AuthUiState.SigningIn -> AuthStatusContent(
            title = stringResource(R.string.auth_signing_in_title),
            message = stringResource(R.string.auth_signing_in_message),
            showProgress = true,
        )

        is AuthUiState.Approved -> Unit

        is AuthUiState.Blocked -> AuthStatusContent(
            title = stringResource(R.string.auth_blocked_title),
            message = stringResource(
                R.string.auth_blocked_message,
                uiState.account.email,
            ),
            warning = uiState.warning?.warningText(),
            actionLabel = stringResource(R.string.auth_choose_account_action),
            actionTag = AuthTestTags.SignInButton,
            onAction = onSignIn,
        )

        is AuthUiState.Error -> AuthStatusContent(
            title = stringResource(R.string.auth_error_title),
            message = stringResource(uiState.reason.messageResource()),
            actionLabel = stringResource(R.string.auth_retry_action),
            actionTag = AuthTestTags.RetryButton,
            onAction = onRetry,
        )

        AuthUiState.SigningOut -> AuthStatusContent(
            title = stringResource(R.string.auth_signing_out_title),
            message = stringResource(R.string.auth_signing_out_message),
            showProgress = true,
        )
    }
}

@Composable
internal fun AuthStatusContent(
    title: String,
    message: String,
    warning: String? = null,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    actionTag: String = "",
    onAction: () -> Unit = {},
    outlinedAction: Boolean = false,
) {
    Text(
        text = title,
        modifier = Modifier
            .testTag(AuthTestTags.StatusTitle)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    if (warning != null) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = warning,
            modifier = Modifier.testTag(AuthTestTags.Warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
        )
    }

    if (showProgress) {
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(
            modifier = Modifier.testTag(AuthTestTags.Progress),
        )
    }

    if (actionLabel != null) {
        Spacer(Modifier.height(24.dp))
        val actionModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(actionTag)
        if (outlinedAction) {
            OutlinedButton(
                onClick = onAction,
                modifier = actionModifier,
            ) {
                Text(actionLabel)
            }
        } else {
            Button(
                onClick = onAction,
                modifier = actionModifier,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AuthWarning.warningText(): String = stringResource(
    when (this) {
        AuthWarning.ProviderStateNotCleared -> R.string.auth_warning_provider_state
        AuthWarning.SignOutCleanupIncomplete -> R.string.auth_warning_sign_out_cleanup
        AuthWarning.BlockedCleanupIncomplete -> R.string.auth_warning_blocked_cleanup
    },
)

@StringRes
private fun AuthError.messageResource(): Int = when (this) {
    AuthError.Persistence -> R.string.auth_error_persistence
    AuthError.ProviderUnavailable -> R.string.auth_error_provider_unavailable
    AuthError.Interrupted -> R.string.auth_error_interrupted
    AuthError.InvalidCredential -> R.string.auth_error_invalid_credential
    AuthError.Unknown -> R.string.auth_error_unknown
}

internal object AuthTestTags {
    const val Screen = "auth_screen"
    const val Progress = "auth_progress"
    const val Warning = "auth_warning"
    const val StatusTitle = "auth_status_title"
    const val SignInButton = "auth_sign_in"
    const val RetryButton = "auth_retry"
    const val SignOutButton = "auth_sign_out"
}
