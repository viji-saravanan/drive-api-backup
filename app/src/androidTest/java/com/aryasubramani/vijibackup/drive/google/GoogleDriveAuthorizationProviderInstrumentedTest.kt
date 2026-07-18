package com.aryasubramani.vijibackup.drive.google

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.drive.domain.DRIVE_AUTHORIZATION_SCOPE
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleDriveAuthorizationProviderInstrumentedTest {
    private val account = requireNotNull(
        GoogleAccount.create(
            subject = "approved-subject",
            email = "approved@example.test",
            displayName = "Approved User",
        ),
    )

    @Test
    fun requestIsBoundToTheApprovedAccountAndOnlyTheDriveScope() {
        val request = buildDriveAuthorizationRequest(account)

        assertEquals(account.email, request.account?.name)
        assertEquals("com.google", request.account?.type)
        assertEquals(listOf(Scope(DRIVE_AUTHORIZATION_SCOPE)), request.requestedScopes)
        assertEquals(AuthorizationRequest.Prompt.NOT_SET, request.prompt)
        assertFalse(request.isOfflineAccessRequested)
        assertFalse(request.isForceCodeForRefreshToken)
        assertFalse(request.optOutIncludingGrantedScopes)
        assertNull(request.serverClientId)
    }

    @Test
    fun directGoogleResultMapsOnlyFieldsNeededByTheInterpreter() {
        val result = AuthorizationResult(
            null,
            "ephemeral-access-token",
            null,
            listOf(DRIVE_AUTHORIZATION_SCOPE),
            null,
            null,
            Bundle.EMPTY,
        )

        val snapshot = result.toDriveAuthorizationSnapshot()

        assertEquals("ephemeral-access-token", snapshot.accessToken)
        assertEquals(setOf(DRIVE_AUTHORIZATION_SCOPE), snapshot.grantedScopes)
        assertNull(snapshot.accountEmail)
        assertFalse(snapshot.toString().contains("ephemeral-access-token"))
        assertTrue(snapshot.toString().contains("REDACTED"))
    }
}
