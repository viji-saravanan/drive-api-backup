package com.aryasubramani.vijibackup.folderaccess.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSignOutCleanupIntentStoreInstrumentedTest {
    @Test
    fun exactRequestTokenSurvivesStoreRecreationAndClearsConditionally() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "sign-out-intent-${UUID.randomUUID()}.preferences_pb")
        val first = createSignOutStoreFixture(file)

        first.store.writeRequestToken("retiring-token")
        first.close()

        val second = createSignOutStoreFixture(file)
        try {
            assertEquals("retiring-token", second.store.readRequestToken())
            assertFalse(second.store.clearRequestToken("newer-token"))
            assertEquals("retiring-token", second.store.readRequestToken())
            assertTrue(second.store.clearRequestToken("retiring-token"))
            assertNull(second.store.readRequestToken())
        } finally {
            second.close()
            file.delete()
        }
    }
}

private class SignOutStoreFixture(
    private val scope: CoroutineScope,
    dataStore: DataStore<Preferences>,
) {
    val store = DataStoreSignOutCleanupIntentStore(dataStore)

    suspend fun close() {
        scope.coroutineContext[Job]?.cancelAndJoin()
    }
}

private fun createSignOutStoreFixture(file: File): SignOutStoreFixture {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = signOutCleanupIntentCorruptionHandler(),
        scope = scope,
    ) { file }
    return SignOutStoreFixture(scope, dataStore)
}
