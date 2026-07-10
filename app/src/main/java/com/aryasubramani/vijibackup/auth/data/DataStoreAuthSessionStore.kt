package com.aryasubramani.vijibackup.auth.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import kotlinx.coroutines.flow.first

class DataStoreAuthSessionStore(
    private val dataStore: DataStore<Preferences>,
) : AuthSessionStore {
    override suspend fun read(): GoogleAccount? {
        val preferences = dataStore.data.first()
        val subject = preferences[Keys.subject]
        val email = preferences[Keys.email]
        val displayName = preferences[Keys.displayName]

        if (subject == null && email == null && displayName == null) return null

        return requireNotNull(
            GoogleAccount.create(
                subject = subject.orEmpty(),
                email = email.orEmpty(),
                displayName = displayName,
            ),
        ) {
            "Stored auth session is invalid"
        }
    }

    override suspend fun save(account: GoogleAccount) {
        dataStore.edit { preferences ->
            preferences[Keys.subject] = account.subject
            preferences[Keys.email] = account.email
            if (account.displayName == null) {
                preferences.remove(Keys.displayName)
            } else {
                preferences[Keys.displayName] = account.displayName
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private object Keys {
        val subject = stringPreferencesKey("google_account_subject")
        val email = stringPreferencesKey("google_account_email")
        val displayName = stringPreferencesKey("google_account_display_name")
    }
}

internal val Context.authSessionDataStore by preferencesDataStore(name = "auth_session")
