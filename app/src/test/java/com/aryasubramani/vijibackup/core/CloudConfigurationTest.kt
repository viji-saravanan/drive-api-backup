package com.aryasubramani.vijibackup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudConfigurationTest {
    @Test
    fun configuredAllowedAccountsAreNormalizedAndUnique() {
        assertEquals(
            CloudConfiguration.allowedGoogleAccounts.map(String::lowercase).toSet(),
            CloudConfiguration.allowedGoogleAccounts,
        )
    }

    fun driveScopeRemainsLeastPrivilege() {
        assertEquals("https://www.googleapis.com/auth/drive.file", CloudConfiguration.driveFileScope)
    }

    @Test
    fun googleSignInConfigurationUsesASeparateWebClientId() {
        assertEquals(
            CloudConfiguration.googleSignInWebClientId.isNotBlank(),
            CloudConfiguration.isGoogleSignInConfigured,
        )
        assertFalse(
            CloudConfiguration.googleSignInWebClientId.isNotEmpty() &&
                CloudConfiguration.googleSignInWebClientId == CloudConfiguration.androidOAuthClientId,
        )
    }
}
