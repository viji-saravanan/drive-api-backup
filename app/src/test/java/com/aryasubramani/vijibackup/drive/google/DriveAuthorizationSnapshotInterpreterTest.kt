package com.aryasubramani.vijibackup.drive.google

import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.drive.domain.DRIVE_AUTHORIZATION_SCOPE
import com.aryasubramani.vijibackup.drive.domain.DriveConnectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAuthorizationSnapshotInterpreterTest {
    private val account = requireNotNull(
        GoogleAccount.create(
            subject = "approved-subject",
            email = "approved@example.test",
            displayName = "Approved User",
        ),
    )

    @Test
    fun snapshotDescriptionNeverIncludesAccessToken() {
        val snapshot = snapshot()

        assertEquals(
            "DriveAuthorizationSnapshot(accessToken=REDACTED, grantedScopes=1, accountEmail=present)",
            snapshot.toString(),
        )
        assertFalse(snapshot.toString().contains("access-token-value"))
    }

    @Test
    fun completeBoundResultReturnsAnEphemeralRedactedToken() {
        val evaluation = interpretDriveAuthorizationSnapshot(
            expectedAccount = account,
            snapshot = snapshot(),
        )

        assertTrue(evaluation is DriveAuthorizationEvaluation.Authorized)
        val token = (evaluation as DriveAuthorizationEvaluation.Authorized).accessToken
        assertEquals("access-token-value", token.value)
        assertFalse(token.toString().contains("access-token-value"))
    }

    @Test
    fun absentReturnedIdentityIsAcceptedBecauseTheRequestWasAccountBound() {
        assertTrue(
            interpretDriveAuthorizationSnapshot(
                expectedAccount = account,
                snapshot = snapshot(accountEmail = null),
            ) is DriveAuthorizationEvaluation.Authorized,
        )
    }

    @Test
    fun returnedIdentityIsComparedCaseInsensitively() {
        assertTrue(
            interpretDriveAuthorizationSnapshot(
                expectedAccount = account,
                snapshot = snapshot(accountEmail = "APPROVED@EXAMPLE.TEST"),
            ) is DriveAuthorizationEvaluation.Authorized,
        )
    }

    @Test
    fun differentReturnedIdentityFailsClosed() {
        assertEquals(
            DriveAuthorizationEvaluation.Failed(DriveConnectionResult.AccountMismatch),
            interpretDriveAuthorizationSnapshot(
                expectedAccount = account,
                snapshot = snapshot(accountEmail = "other@example.test"),
            ),
        )
    }

    @Test
    fun missingDriveGrantRequiresAuthorization() {
        assertEquals(
            DriveAuthorizationEvaluation.Failed(DriveConnectionResult.NeedsAuthorization),
            interpretDriveAuthorizationSnapshot(
                expectedAccount = account,
                snapshot = snapshot(grantedScopes = setOf("openid")),
            ),
        )
    }

    @Test
    fun missingBlankOrWhitespaceTokenIsInvalid() {
        listOf(null, "", "   ", "token with spaces", "token\nvalue").forEach { token ->
            assertEquals(
                DriveAuthorizationEvaluation.Failed(DriveConnectionResult.InvalidResponse),
                interpretDriveAuthorizationSnapshot(
                    expectedAccount = account,
                    snapshot = snapshot(accessToken = token),
                ),
            )
        }
    }

    private fun snapshot(
        accessToken: String? = "access-token-value",
        grantedScopes: Set<String> = setOf(DRIVE_AUTHORIZATION_SCOPE),
        accountEmail: String? = account.email,
    ) = DriveAuthorizationSnapshot(
        accessToken = accessToken,
        grantedScopes = grantedScopes,
        accountEmail = accountEmail,
    )
}
