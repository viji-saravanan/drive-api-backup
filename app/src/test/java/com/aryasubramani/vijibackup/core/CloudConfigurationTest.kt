package com.aryasubramani.vijibackup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudConfigurationTest {
    @Test
    fun configuredAllowedAccountsAreNormalizedAndUnique() {
        assertEquals(
            CloudConfiguration.allowedGoogleAccounts.map(String::lowercase).toSet(),
            CloudConfiguration.allowedGoogleAccounts,
        )
    }

    @Test
    fun emptyAndUnknownAccountsAreDenied() {
        assertFalse(CloudConfiguration.isAllowedGoogleAccount(""))
        assertFalse(CloudConfiguration.isAllowedGoogleAccount("unknown@example.test"))
    }

    @Test
    fun configuredAccountChecksNormalizeInput() {
        val configuredAccount = CloudConfiguration.allowedGoogleAccounts.firstOrNull() ?: return

        assertTrue(CloudConfiguration.isAllowedGoogleAccount("  ${configuredAccount.uppercase()}  "))
    }

    @Test
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
