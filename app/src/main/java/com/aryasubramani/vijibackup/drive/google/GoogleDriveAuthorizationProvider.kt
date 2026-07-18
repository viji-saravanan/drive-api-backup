package com.aryasubramani.vijibackup.drive.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.drive.domain.DRIVE_AUTHORIZATION_SCOPE
import com.aryasubramani.vijibackup.drive.domain.DriveConnectionResult
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class GoogleDriveAuthorizationProvider(
    private val authorizationClient: AuthorizationClient,
) : DriveAuthorizationProvider {
    constructor(context: Context) : this(
        authorizationClient = Identity.getAuthorizationClient(context.applicationContext),
    )

    override suspend fun begin(account: GoogleAccount): DriveAuthorizationStart {
        val result = try {
            authorizationClient
                .authorize(buildDriveAuthorizationRequest(account))
                .awaitResult()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: ApiException) {
            return DriveAuthorizationStart.Failed(exception.toStartFailure())
        } catch (_: Exception) {
            return DriveAuthorizationStart.Failed(DriveConnectionResult.ProviderUnavailable)
        }

        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: return DriveAuthorizationStart.Failed(DriveConnectionResult.InvalidResponse)
            return DriveAuthorizationStart.ResolutionRequired(pendingIntent)
        }
        return when (
            val evaluation = interpretDriveAuthorizationSnapshot(
                expectedAccount = account,
                snapshot = result.toDriveAuthorizationSnapshot(),
            )
        ) {
            is DriveAuthorizationEvaluation.Authorized ->
                DriveAuthorizationStart.Authorized(evaluation.accessToken)
            is DriveAuthorizationEvaluation.Failed ->
                DriveAuthorizationStart.Failed(evaluation.result)
        }
    }

    override fun complete(
        expectedAccount: GoogleAccount,
        data: Intent?,
    ): DriveAuthorizationEvaluation {
        if (data == null) {
            return DriveAuthorizationEvaluation.Failed(DriveConnectionResult.InvalidResponse)
        }
        val result = try {
            authorizationClient.getAuthorizationResultFromIntent(data)
        } catch (exception: ApiException) {
            return DriveAuthorizationEvaluation.Failed(exception.toCompletionFailure())
        } catch (_: Exception) {
            return DriveAuthorizationEvaluation.Failed(DriveConnectionResult.InvalidResponse)
        }
        if (result.hasResolution()) {
            return DriveAuthorizationEvaluation.Failed(DriveConnectionResult.InvalidResponse)
        }
        return interpretDriveAuthorizationSnapshot(
            expectedAccount = expectedAccount,
            snapshot = result.toDriveAuthorizationSnapshot(),
        )
    }
}

internal fun buildDriveAuthorizationRequest(account: GoogleAccount): AuthorizationRequest =
    AuthorizationRequest.builder()
        .setAccount(Account(account.email, GOOGLE_ACCOUNT_TYPE))
        .setRequestedScopes(listOf(Scope(DRIVE_AUTHORIZATION_SCOPE)))
        .build()

internal fun AuthorizationResult.toDriveAuthorizationSnapshot() = DriveAuthorizationSnapshot(
    accessToken = accessToken,
    grantedScopes = grantedScopes.orEmpty().toSet(),
    accountEmail = toGoogleSignInAccount()?.email,
)

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { completedTask ->
            if (!continuation.isActive) return@addOnCompleteListener
            when {
                completedTask.isCanceled -> continuation.cancel()
                completedTask.isSuccessful -> continuation.resume(completedTask.result)
                else -> continuation.resumeWithException(
                    completedTask.exception
                        ?: IllegalStateException("Google authorization task failed"),
                )
            }
        }
    }

private fun ApiException.toStartFailure(): DriveConnectionResult = when (statusCode) {
    CommonStatusCodes.SIGN_IN_REQUIRED -> DriveConnectionResult.NeedsAuthorization
    CommonStatusCodes.NETWORK_ERROR,
    CommonStatusCodes.TIMEOUT,
    CommonStatusCodes.INTERRUPTED,
    -> DriveConnectionResult.TemporaryFailure
    else -> DriveConnectionResult.ProviderUnavailable
}

private fun ApiException.toCompletionFailure(): DriveConnectionResult = when (statusCode) {
    CommonStatusCodes.CANCELED,
    CommonStatusCodes.SIGN_IN_REQUIRED,
    -> DriveConnectionResult.NeedsAuthorization
    CommonStatusCodes.NETWORK_ERROR,
    CommonStatusCodes.TIMEOUT,
    CommonStatusCodes.INTERRUPTED,
    -> DriveConnectionResult.TemporaryFailure
    else -> DriveConnectionResult.InvalidResponse
}

private const val GOOGLE_ACCOUNT_TYPE = "com.google"
