package com.aryasubramani.vijibackup.drive.google

import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.drive.domain.DRIVE_AUTHORIZATION_SCOPE
import com.aryasubramani.vijibackup.drive.domain.DriveConnectionResult

internal class DriveAuthorizationSnapshot(
    val accessToken: String?,
    val grantedScopes: Set<String>,
    val accountEmail: String?,
) {
    override fun toString(): String =
        "DriveAuthorizationSnapshot(" +
            "accessToken=REDACTED, " +
            "grantedScopes=${grantedScopes.size}, " +
            "accountEmail=${if (accountEmail.isNullOrBlank()) "absent" else "present"}" +
            ")"
}

internal sealed interface DriveAuthorizationEvaluation {
    data class Authorized(val accessToken: DriveAccessToken) : DriveAuthorizationEvaluation

    data class Failed(val result: DriveConnectionResult) : DriveAuthorizationEvaluation
}

internal class DriveAccessToken private constructor(internal val value: String) {
    override fun toString(): String = "DriveAccessToken(REDACTED)"

    internal companion object {
        fun create(value: String?): DriveAccessToken? {
            if (value.isNullOrBlank() || value.any(Char::isWhitespace)) return null
            return DriveAccessToken(value)
        }
    }
}

internal fun interpretDriveAuthorizationSnapshot(
    expectedAccount: GoogleAccount,
    snapshot: DriveAuthorizationSnapshot,
): DriveAuthorizationEvaluation {
    val returnedEmail = snapshot.accountEmail?.takeIf(String::isNotBlank)
    if (returnedEmail != null) {
        val normalizedEmail = GoogleAccount.normalizeEmail(returnedEmail)
            ?: return DriveAuthorizationEvaluation.Failed(
                DriveConnectionResult.InvalidResponse,
            )
        if (normalizedEmail != expectedAccount.email) {
            return DriveAuthorizationEvaluation.Failed(DriveConnectionResult.AccountMismatch)
        }
    }

    if (DRIVE_AUTHORIZATION_SCOPE !in snapshot.grantedScopes) {
        return DriveAuthorizationEvaluation.Failed(DriveConnectionResult.NeedsAuthorization)
    }

    val token = DriveAccessToken.create(snapshot.accessToken)
        ?: return DriveAuthorizationEvaluation.Failed(DriveConnectionResult.InvalidResponse)
    return DriveAuthorizationEvaluation.Authorized(token)
}
