package com.aryasubramani.vijibackup.auth.domain

import com.aryasubramani.vijibackup.core.CloudConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AccountAccessPolicyTest {
    @Test
    fun everyConfirmedGoogleAccountIsApproved() {
        val policy = AccountAccessPolicy(CloudConfiguration.allowedGoogleAccounts)

        CloudConfiguration.allowedGoogleAccounts.forEachIndexed { index, email ->
            val account = requireNotNull(
                GoogleAccount.create(
                    subject = "confirmed-account-$index",
                    email = email,
                    displayName = null,
                ),
            )

            assertTrue(policy.evaluate(account) is AccountAccess.Approved)
        }
    }

    @Test
    fun invalidCredentialClaimsCannotCreateAnAccount() {
        assertNull(GoogleAccount.create(subject = "", email = "primary.user@example.test", displayName = null))
        assertNull(GoogleAccount.create(subject = "google-subject", email = "", displayName = null))
        assertNull(
            GoogleAccount.create(
                subject = "google-subject",
                email = "not-an-email-address",
                displayName = null,
            ),
        )
    }

    @Test
    fun invalidAllowlistConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountAccessPolicy(setOf("primary.user@example.test", "not-an-email-address"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountAccessPolicy(setOf("primary.user@example.test", "  PRIMARY.USER@EXAMPLE.TEST "))
        }
    }

    @Test
    fun credentialNormalizationIsStableAcrossDeviceLocales() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            val account = requireNotNull(
                GoogleAccount.create(
                    subject = "  stable-google-subject  ",
                    email = "  ALTERNATE.USER@EXAMPLE.TEST  ",
                    displayName = "  Viji  ",
                ),
            )

            assertEquals("stable-google-subject", account.subject)
            assertEquals("alternate.user@example.test", account.email)
            assertEquals("Viji", account.displayName)
            assertTrue(
                AccountAccessPolicy(CloudConfiguration.allowedGoogleAccounts).evaluate(account) is
                    AccountAccess.Approved,
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun aliasesAndLookalikeAddressesAreBlocked() {
        val policy = AccountAccessPolicy(CloudConfiguration.allowedGoogleAccounts)
        val unapprovedAddresses = listOf(
            "primary.user+backup@example.test",
            "primary.user@example.test.example.org",
            "primary.user@exampl3.test",
            "primary.user@example.invalid",
        )

        unapprovedAddresses.forEachIndexed { index, email ->
            val account = requireNotNull(
                GoogleAccount.create(
                    subject = "unapproved-account-$index",
                    email = email,
                    displayName = null,
                ),
            )

            assertTrue(policy.evaluate(account) is AccountAccess.Blocked)
        }
    }
}
