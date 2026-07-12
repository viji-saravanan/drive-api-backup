package com.aryasubramani.vijibackup.auth.google

import android.content.Context

fun interface GoogleSignInClient {
    suspend fun signIn(
        activityContext: Context,
        mode: GoogleSignInMode,
    ): GoogleSignInResult
}
