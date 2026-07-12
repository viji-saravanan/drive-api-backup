package com.aryasubramani.vijibackup.auth.config

import com.aryasubramani.vijibackup.BuildConfig
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount

internal object GoogleSignInBuildConfiguration {
    val value: GoogleSignInConfiguration = createGoogleSignInConfiguration(
        webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        allowedAccountValues = BuildConfig.ALLOWED_GOOGLE_ACCOUNTS
            .split(',')
            .filter(String::isNotEmpty),
    )
}

internal sealed interface GoogleSignInConfiguration {
    data class Ready(
        val webClientId: String,
        val allowedAccounts: Set<String>,
    ) : GoogleSignInConfiguration

    data object Invalid : GoogleSignInConfiguration
}

internal fun createGoogleSignInConfiguration(
    webClientId: String,
    allowedAccountValues: List<String>,
): GoogleSignInConfiguration {
    val normalizedWebClientId = webClientId.trim()
    if (normalizedWebClientId.isEmpty() || allowedAccountValues.isEmpty()) {
        return GoogleSignInConfiguration.Invalid
    }

    val normalizedAccounts = allowedAccountValues.map { value ->
        GoogleAccount.normalizeEmail(value) ?: return GoogleSignInConfiguration.Invalid
    }
    if (normalizedAccounts.size != normalizedAccounts.toSet().size) {
        return GoogleSignInConfiguration.Invalid
    }

    return GoogleSignInConfiguration.Ready(
        webClientId = normalizedWebClientId,
        allowedAccounts = normalizedAccounts.toSet(),
    )
}
