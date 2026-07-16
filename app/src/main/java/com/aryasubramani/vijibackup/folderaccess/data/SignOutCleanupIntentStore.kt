package com.aryasubramani.vijibackup.folderaccess.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

interface SignOutCleanupIntentStore {
    suspend fun readRequestToken(): String?

    suspend fun writeRequestToken(requestToken: String)

    suspend fun clearRequestToken(requestToken: String): Boolean
}

class DataStoreSignOutCleanupIntentStore(
    private val dataStore: DataStore<Preferences>,
) : SignOutCleanupIntentStore {
    override suspend fun readRequestToken(): String? =
        dataStore.data.first()[Keys.requestToken]

    override suspend fun writeRequestToken(requestToken: String) {
        require(requestToken.isNotBlank())
        dataStore.edit { preferences -> preferences[Keys.requestToken] = requestToken }
    }

    override suspend fun clearRequestToken(requestToken: String): Boolean {
        var cleared = false
        dataStore.edit { preferences ->
            val storedToken = preferences[Keys.requestToken]
            if (storedToken == null || storedToken == requestToken) {
                preferences.remove(Keys.requestToken)
                cleared = true
            }
        }
        return cleared
    }

    private object Keys {
        val requestToken = stringPreferencesKey("request_token")
    }
}

internal fun signOutCleanupIntentCorruptionHandler() =
    ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

internal val Context.signOutCleanupIntentDataStore by preferencesDataStore(
    name = "folder_sign_out_cleanup",
    corruptionHandler = signOutCleanupIntentCorruptionHandler(),
)
