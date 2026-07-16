package com.aryasubramani.vijibackup

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class AppContextInstrumentedTest {
    @Test
    fun appContextUsesVijiBackupIdentity() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedPackage = when (BuildConfig.FLAVOR) {
            "internal" -> "com.aryasubramani.vijibackup.internal"
            "public" -> "com.aryasubramani.vijibackup"
            else -> error("Unexpected distribution flavor")
        }

        assertEquals(expectedPackage, appContext.packageName)
        assertEquals(expectedPackage, BuildConfig.APPLICATION_ID)
        assertEquals("Viji Backup", appContext.getString(R.string.app_name))
        assertEquals(BuildConfig.FLAVOR, appContext.getString(R.string.app_channel))
    }

    @Test
    fun appDataAutoBackupIsDisabledUntilSecretRulesAreDesigned() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertFalse((appContext.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0)
    }

    @Test
    fun folderAccessDatabaseIsExcludedFromEveryBackupTransport() {
        assertEquals(
            listOf("full-backup-content"),
            databaseExclusionParents(R.xml.backup_rules),
        )
        assertEquals(
            listOf("cloud-backup", "device-transfer"),
            databaseExclusionParents(R.xml.data_extraction_rules).sorted(),
        )
    }

    @Test
    fun signOutCleanupIntentIsExcludedFromEveryBackupTransport() {
        val path = "datastore/folder_sign_out_cleanup.preferences_pb"
        assertEquals(
            listOf("full-backup-content"),
            exclusionParents(R.xml.backup_rules, "file", path),
        )
        assertEquals(
            listOf("cloud-backup", "device-transfer"),
            exclusionParents(R.xml.data_extraction_rules, "file", path).sorted(),
        )
    }

    private fun databaseExclusionParents(resourceId: Int): List<String> {
        return exclusionParents(resourceId, "database", ".")
    }

    private fun exclusionParents(
        resourceId: Int,
        domain: String,
        path: String,
    ): List<String> {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        return appContext.resources.getXml(resourceId).use { parser ->
            val elementStack = ArrayDeque<String>()
            val parents = mutableListOf<String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (
                            parser.name == "exclude" &&
                            parser.getAttributeValue(null, "domain") == domain &&
                            parser.getAttributeValue(null, "path") == path
                        ) {
                            parents += elementStack.last()
                        }
                        elementStack.addLast(parser.name)
                    }
                    XmlPullParser.END_TAG -> elementStack.removeLast()
                }
                event = parser.next()
            }
            parents
        }
    }
}
