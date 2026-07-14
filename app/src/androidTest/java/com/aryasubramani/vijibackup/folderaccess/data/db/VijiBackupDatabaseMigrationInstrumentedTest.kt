package com.aryasubramani.vijibackup.folderaccess.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VijiBackupDatabaseMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VijiBackupDatabase::class.java,
    )

    @Test
    fun exportedVersionOneSchemaCreatesBothFolderAccessTables() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 1).use { database ->
            val tables = database.query(
                """
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name NOT LIKE 'android_%'
                ORDER BY name
                """.trimIndent(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }

            assertEquals(
                listOf(
                    "local_folder_mappings",
                    "pending_folder_operations",
                    "room_master_table",
                ),
                tables,
            )
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "viji_backup_migration_test.db"
    }
}
