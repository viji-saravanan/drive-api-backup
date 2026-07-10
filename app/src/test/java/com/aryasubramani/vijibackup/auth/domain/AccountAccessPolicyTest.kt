package com.aryasubramani.vijibackup.auth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AccountAccessPolicyTest {
    @Test
    fun everyConfiguredTestAccountIsApproved() {
        val policy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS)

        TEST_ALLOWED_GOOGLE_ACCOUNTS.forEachIndexed { index, email ->
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
        assertNull(GoogleAccount.create(subject = "", email = APPROVED_EMAIL, displayName = null))
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
            AccountAccessPolicy(setOf(APPROVED_EMAIL, "not-an-email-address"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountAccessPolicy(setOf(APPROVED_EMAIL, "  PRIMARY.USER@EXAMPLE.TEST "))
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
                    email = "  PRIMARY.USER@EXAMPLE.TEST  ",
                    displayName = "  Primary User  ",
                ),
            )

            assertEquals("stable-google-subject", account.subject)
            assertEquals(APPROVED_EMAIL, account.email)
            assertEquals("Primary User", account.displayName)
            assertTrue(
                AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS).evaluate(account) is
                    AccountAccess.Approved,
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun aliasesAndLookalikeAddressesAreBlocked() {
        val policy = AccountAccessPolicy(TEST_ALLOWED_GOOGLE_ACCOUNTS)
        val unapprovedAddresses = listOf(
            "primary.user+backup@example.test",
            "primary.user@example.test.attacker.invalid",
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

private const val APPROVED_EMAIL = "primary.user@example.test"

private val TEST_ALLOWED_GOOGLE_ACCOUNTS = setOf(
    APPROVED_EMAIL,
    "alternate.user@example.test",
    "owner.primary@example.test",
    "owner.alternate@example.test",
)
