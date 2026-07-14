package com.aryasubramani.vijibackup.app

import android.os.Bundle

internal class FolderPickerRequestState private constructor(
    currentToken: String?,
) {
    var currentToken: String? = currentToken
        private set

    fun stageForLaunch(requestToken: String): Boolean {
        if (currentToken != null || requestToken.isBlank()) return false
        currentToken = requestToken
        return true
    }

    fun clearIfMatching(requestToken: String): Boolean {
        if (currentToken != requestToken) return false
        currentToken = null
        return true
    }

    fun saveTo(outState: Bundle) {
        currentToken?.let { token ->
            outState.putString(SAVED_TOKEN_KEY, token)
        } ?: outState.remove(SAVED_TOKEN_KEY)
    }

    companion object {
        internal const val SAVED_TOKEN_KEY = "folder_picker_request_token"

        fun restore(savedInstanceState: Bundle?): FolderPickerRequestState {
            val restoredToken = try {
                savedInstanceState?.getString(SAVED_TOKEN_KEY)
            } catch (_: Exception) {
                null
            }?.takeIf { token -> token.isNotBlank() }
            return FolderPickerRequestState(restoredToken)
        }
    }
}
