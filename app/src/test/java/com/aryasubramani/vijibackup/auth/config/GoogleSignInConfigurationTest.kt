package com.aryasubramani.vijibackup.auth.config

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInConfigurationTest {
    @Test
    fun blankWebClientIdIsInvalid() {
        assertEquals(
            GoogleSignInConfiguration.Invalid,
            createGoogleSignInConfiguration("   ", listOf("user@example.test")),
        )
    }

    @Test
    fun emptyAllowlistIsInvalid() {
        assertEquals(
            GoogleSignInConfiguration.Invalid,
            createGoogleSignInConfiguration("web-client", emptyList()),
        )
    }

    @Test
    fun malformedAddressIsInvalid() {
        assertEquals(
            GoogleSignInConfiguration.Invalid,
            createGoogleSignInConfiguration("web-client", listOf("not-an-email")),
        )
    }

    @Test
    fun duplicateNormalizedAddressIsInvalid() {
        assertEquals(
            GoogleSignInConfiguration.Invalid,
            createGoogleSignInConfiguration(
                "web-client",
                listOf("user@example.test", " USER@example.test "),
            ),
        )
    }

    @Test
    fun validConfigurationNormalizesItsInputs() {
        assertEquals(
            GoogleSignInConfiguration.Ready(
                webClientId = "web-client",
                allowedAccounts = setOf("user@example.test"),
            ),
            createGoogleSignInConfiguration(
                webClientId = "  web-client  ",
                allowedAccountValues = listOf(" USER@example.test "),
            ),
        )
    }

    @Test
    fun distinctValidAddressesRemainDistinct() {
        assertEquals(
            GoogleSignInConfiguration.Ready(
                webClientId = "web-client",
                allowedAccounts = setOf("first@example.test", "second@example.test"),
            ),
            createGoogleSignInConfiguration(
                webClientId = "web-client",
                allowedAccountValues = listOf("first@example.test", "second@example.test"),
            ),
        )
    }
}
