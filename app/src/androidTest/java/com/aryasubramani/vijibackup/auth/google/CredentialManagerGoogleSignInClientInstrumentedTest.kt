package com.aryasubramani.vijibackup.auth.google

import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialManagerGoogleSignInClientInstrumentedTest {
    @Test
    fun authorizedAccountsBuildsFilteredAutoSelectGoogleIdRequest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gateway = RecordingCredentialGateway()
        val client = CredentialManagerGoogleSignInClient(
            credentialGateway = gateway,
            credentialParser = GoogleCredentialParser { error("Parser must not be called") },
            webClientId = WEB_CLIENT_ID,
        )

        val result = client.signIn(
            activityContext = context,
            mode = GoogleSignInMode.AuthorizedAccounts,
        )

        assertEquals(GoogleSignInResult.NoCredential, result)
        val option = gateway.request.credentialOptions.single()
        assertTrue(option is GetGoogleIdOption)
        option as GetGoogleIdOption
        assertEquals(WEB_CLIENT_ID, option.serverClientId)
        assertTrue(option.filterByAuthorizedAccounts)
        assertTrue(option.autoSelectEnabled)
    }

    @Test
    fun explicitSignInBuildsAnAccountChooserRequest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gateway = RecordingCredentialGateway()
        val client = CredentialManagerGoogleSignInClient(
            credentialGateway = gateway,
            credentialParser = GoogleCredentialParser { error("Parser must not be called") },
            webClientId = WEB_CLIENT_ID,
        )

        val result = client.signIn(
            activityContext = context,
            mode = GoogleSignInMode.Explicit,
        )

        assertEquals(GoogleSignInResult.NoCredential, result)
        val option = gateway.request.credentialOptions.single()
        assertTrue(option is GetSignInWithGoogleOption)
        option as GetSignInWithGoogleOption
        assertEquals(WEB_CLIENT_ID, option.serverClientId)
    }

    @Test
    fun providerExceptionsMapToStableAuthOutcomes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cases = listOf(
            GetCredentialCancellationException() to GoogleSignInResult.Cancelled,
            NoCredentialException() to GoogleSignInResult.NoCredential,
            GetCredentialProviderConfigurationException() to
                GoogleSignInResult.ProviderUnavailable,
            GetCredentialUnsupportedException() to GoogleSignInResult.ProviderUnavailable,
            GetCredentialInterruptedException() to GoogleSignInResult.Interrupted,
            GetCredentialUnknownException() to GoogleSignInResult.UnknownFailure,
        )

        cases.forEach { (failure, expectedResult) ->
            val client = CredentialManagerGoogleSignInClient(
                credentialGateway = ThrowingCredentialGateway(failure),
                credentialParser = GoogleCredentialParser { error("Parser must not be called") },
                webClientId = WEB_CLIENT_ID,
            )

            assertEquals(
                expectedResult,
                client.signIn(context, GoogleSignInMode.Explicit),
            )
        }
    }

    @Test
    fun coroutineCancellationIsPropagatedInsteadOfBecomingAnAuthFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = CredentialManagerGoogleSignInClient(
            credentialGateway = ThrowingCredentialGateway(
                CancellationException("test cancellation"),
            ),
            credentialParser = GoogleCredentialParser { error("Parser must not be called") },
            webClientId = WEB_CLIENT_ID,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking {
                client.signIn(context, GoogleSignInMode.Explicit)
            }
        }
    }

    @Test
    fun googleIdCredentialIsParsedIntoNormalizedAccountMetadata() {
        val credential = GoogleIdTokenCredential.Builder()
            .setId("Approved.User@Example.Test")
            .setIdToken(
                testIdToken(
                    subject = "test-google-subject",
                    email = "Approved.User@Example.Test",
                ),
            )
            .setDisplayName("Approved User")
            .build()

        val result = GoogleCredentialResponseParser.parse(
            GetCredentialResponse(credential),
        )

        assertEquals(
            GoogleSignInResult.Success(
                requireNotNull(
                    GoogleAccount.create(
                        subject = "test-google-subject",
                        email = "approved.user@example.test",
                        displayName = "Approved User",
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun malformedOrUnexpectedCredentialsNeverProduceAnAccount() {
        val responses = listOf(
            GetCredentialResponse(PasswordCredential("test-id", "test-password")),
            GetCredentialResponse(CustomCredential("test.unsupported.type", Bundle())),
            GetCredentialResponse(
                CustomCredential(
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
                    Bundle(),
                ),
            ),
        )

        responses.forEach { response ->
            assertEquals(
                GoogleSignInResult.InvalidCredential,
                GoogleCredentialResponseParser.parse(response),
            )
        }
    }

    @Test
    fun tokenMissingEmailNeverProducesAnAccount() {
        val credential = GoogleIdTokenCredential.Builder()
            .setId("missing-email@example.test")
            .setIdToken(testIdToken(subject = "test-subject", email = ""))
            .build()

        assertEquals(
            GoogleSignInResult.InvalidCredential,
            GoogleCredentialResponseParser.parse(GetCredentialResponse(credential)),
        )
    }

    @Test
    fun unexpectedParserExceptionBecomesAnUnknownFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = CredentialManagerGoogleSignInClient(
            credentialGateway = StaticCredentialGateway(
                GetCredentialResponse(PasswordCredential("test-id", "test-password")),
            ),
            credentialParser = GoogleCredentialParser {
                throw IllegalStateException("test parser failure")
            },
            webClientId = WEB_CLIENT_ID,
        )

        assertEquals(
            GoogleSignInResult.UnknownFailure,
            client.signIn(context, GoogleSignInMode.Explicit),
        )
    }

    @Test
    fun fatalParserErrorsAreNeverConvertedToRecoverableAuthFailures() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = CredentialManagerGoogleSignInClient(
            credentialGateway = StaticCredentialGateway(
                GetCredentialResponse(PasswordCredential("test-id", "test-password")),
            ),
            credentialParser = GoogleCredentialParser {
                throw AssertionError("test fatal parser error")
            },
            webClientId = WEB_CLIENT_ID,
        )

        assertThrows(AssertionError::class.java) {
            runBlocking {
                client.signIn(context, GoogleSignInMode.Explicit)
            }
        }
    }

    @Test
    fun missingWebClientIdFailsBeforeCredentialUiIsOpened() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val client = CredentialManagerGoogleSignInClient(
            credentialManager = CredentialManager.create(context),
            webClientId = "",
        )

        val result = client.signIn(
            activityContext = context,
            mode = GoogleSignInMode.Explicit,
        )

        assertEquals(GoogleSignInResult.ConfigurationRequired, result)
    }

    private class RecordingCredentialGateway : GoogleCredentialGateway {
        lateinit var request: GetCredentialRequest

        override suspend fun getCredential(
            activityContext: Context,
            request: GetCredentialRequest,
        ): GetCredentialResponse {
            this.request = request
            throw NoCredentialException()
        }
    }

    private class ThrowingCredentialGateway(
        private val failure: Exception,
    ) : GoogleCredentialGateway {
        override suspend fun getCredential(
            activityContext: Context,
            request: GetCredentialRequest,
        ): GetCredentialResponse = throw failure
    }

    private class StaticCredentialGateway(
        private val response: GetCredentialResponse,
    ) : GoogleCredentialGateway {
        override suspend fun getCredential(
            activityContext: Context,
            request: GetCredentialRequest,
        ): GetCredentialResponse = response
    }

    private fun testIdToken(subject: String, email: String): String {
        val header = encodeTokenPart("{\"alg\":\"none\",\"typ\":\"JWT\"}")
        val payload = encodeTokenPart("{\"sub\":\"$subject\",\"email\":\"$email\"}")
        return "$header.$payload.test-signature"
    }

    private fun encodeTokenPart(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private companion object {
        const val WEB_CLIENT_ID = "123456789-test.apps.googleusercontent.com"
    }
}
