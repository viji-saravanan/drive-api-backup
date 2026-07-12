package com.aryasubramani.vijibackup.auth.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

class CredentialManagerGoogleSignInClient internal constructor(
    private val credentialGateway: GoogleCredentialGateway,
    private val credentialParser: GoogleCredentialParser,
    webClientId: String,
) : GoogleSignInClient {
    constructor(
        credentialManager: CredentialManager,
        webClientId: String,
    ) : this(
        credentialGateway = GoogleCredentialGateway { activityContext, request ->
            credentialManager.getCredential(
                context = activityContext,
                request = request,
            )
        },
        credentialParser = GoogleCredentialResponseParser,
        webClientId = webClientId,
    )

    private val webClientId = webClientId.trim()

    override suspend fun signIn(
        activityContext: Context,
        mode: GoogleSignInMode,
    ): GoogleSignInResult {
        if (webClientId.isEmpty()) return GoogleSignInResult.ConfigurationRequired

        return try {
            val response = credentialGateway.getCredential(
                activityContext = activityContext,
                request = buildRequest(mode),
            )
            credentialParser.parse(response)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.NoCredential
        } catch (_: GetCredentialProviderConfigurationException) {
            GoogleSignInResult.ProviderUnavailable
        } catch (_: GetCredentialUnsupportedException) {
            GoogleSignInResult.ProviderUnavailable
        } catch (_: GetCredentialInterruptedException) {
            GoogleSignInResult.Interrupted
        } catch (_: GetCredentialException) {
            GoogleSignInResult.UnknownFailure
        } catch (_: Exception) {
            GoogleSignInResult.UnknownFailure
        }
    }

    private fun buildRequest(mode: GoogleSignInMode): GetCredentialRequest {
        val builder = GetCredentialRequest.Builder()
        when (mode) {
            GoogleSignInMode.AuthorizedAccounts -> builder.addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setAutoSelectEnabled(true)
                    .setServerClientId(webClientId)
                    .build(),
            )

            GoogleSignInMode.Explicit -> builder.addCredentialOption(
                GetSignInWithGoogleOption.Builder(webClientId).build(),
            )
        }
        return builder.build()
    }
}

internal fun interface GoogleCredentialGateway {
    suspend fun getCredential(
        activityContext: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse
}

internal fun interface GoogleCredentialParser {
    fun parse(response: GetCredentialResponse): GoogleSignInResult
}

internal object GoogleCredentialResponseParser : GoogleCredentialParser {
    override fun parse(response: GetCredentialResponse): GoogleSignInResult {
        val customCredential = response.credential as? CustomCredential
            ?: return GoogleSignInResult.InvalidCredential
        if (customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleSignInResult.InvalidCredential
        }

        return try {
            val googleCredential = GoogleIdTokenCredential.createFrom(customCredential.data)
            val account = GoogleAccount.create(
                subject = googleCredential.uniqueId,
                email = googleCredential.email.orEmpty(),
                displayName = googleCredential.displayName,
            )
            if (account == null) {
                GoogleSignInResult.InvalidCredential
            } else {
                GoogleSignInResult.Success(account)
            }
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInResult.InvalidCredential
        }
    }
}
