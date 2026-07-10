package com.aryasubramani.vijibackup.core

import com.aryasubramani.vijibackup.BuildConfig
import java.util.Locale

object CloudConfiguration {
    const val driveFileScope = "https://www.googleapis.com/auth/drive.file"

    val driveUploadFolderId = BuildConfig.DRIVE_UPLOAD_FOLDER_ID.trim()
    val androidOAuthClientId = BuildConfig.ANDROID_OAUTH_CLIENT_ID.trim()

    val googleSignInWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
    val isGoogleSignInConfigured: Boolean
        get() = googleSignInWebClientId.isNotEmpty()

    val allowedGoogleAccounts = BuildConfig.ALLOWED_GOOGLE_ACCOUNTS
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { it.lowercase(Locale.ROOT) }
        .toSet()

    fun isAllowedGoogleAccount(email: String): Boolean =
        email.trim().lowercase(Locale.ROOT) in allowedGoogleAccounts
}
