package com.aryasubramani.vijibackup.auth.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DataStoreAuthSessionStoreInstrumentedTest {
    @Test
    fun approvedMetadataRoundTripsWithoutTokenMaterialAndClears() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "auth-session-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = DataStoreAuthSessionStore(dataStore)
        val account = requireNotNull(
            GoogleAccount.create(
                subject = "test-approved-google-subject",
                email = "primary.user@example.test",
                displayName = "Primary User",
            ),
        )

        try {
            assertNull(store.read())

            store.save(account)

            assertEquals(account, store.read())
            val persistedPreferences = dataStore.data.first().asMap()
            assertEquals(3, persistedPreferences.size)
            assertTrue(
                persistedPreferences.keys.none { key ->
                    key.name.contains("token", ignoreCase = true)
                },
            )

            val accountWithoutDisplayName = requireNotNull(
                GoogleAccount.create(
                    subject = account.subject,
                    email = account.email,
                    displayName = null,
                ),
            )
            store.save(accountWithoutDisplayName)

            assertEquals(accountWithoutDisplayName, store.read())
            assertEquals(2, dataStore.data.first().asMap().size)

            store.clear()

            assertNull(store.read())
            assertTrue(dataStore.data.first().asMap().isEmpty())

            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("google_account_email")] = "primary.user@example.test"
            }

            assertTrue(runCatching { store.read() }.isFailure)
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
