package com.aryasubramani.vijibackup.folderaccess

import android.content.Context
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryasubramani.vijibackup.folderaccess.data.db.VijiBackupDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveFolderAccessStateProbeInstrumentedTest {
    @Test
    fun liveStateMatchesExplicitRedactedExpectations() {
        val arguments = InstrumentationRegistry.getArguments()
        if (!arguments.getString(ARG_ENABLED).toBoolean()) return

        val expectedMappings = arguments.requiredInt(ARG_EXPECTED_MAPPINGS)
        val expectedNamedMappings = arguments.optionalInt(ARG_EXPECTED_NAMED_MAPPINGS)
        val expectedTreeGrants = arguments.requiredInt(ARG_EXPECTED_TREE_GRANTS)
        val expectedWriteGrants = arguments.requiredInt(ARG_EXPECTED_WRITE_GRANTS)
        val expectedPendingState = arguments.requiredString(ARG_EXPECTED_PENDING_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(
            context,
            VijiBackupDatabase::class.java,
            VijiBackupDatabase.DATABASE_NAME,
        ).build()

        try {
            runBlocking {
                val mappings = database.folderAccessDao().allMappings()
                assertEquals(expectedMappings, mappings.size)
                expectedNamedMappings?.let { expected ->
                    assertEquals(
                        "named mappings",
                        expected,
                        mappings.count { mapping ->
                            !mapping.sourceDisplayName.isNullOrBlank()
                        },
                    )
                }
                assertEquals(
                    expectedPendingState,
                    database.folderAccessDao().pendingOperation()?.state?.name ?: NO_PENDING,
                )
            }
            val treeGrants = context.contentResolver.persistedUriPermissions
                .filter { permission -> DocumentsContract.isTreeUri(permission.uri) }
            assertEquals(expectedTreeGrants, treeGrants.size)
            assertEquals(expectedWriteGrants, treeGrants.count { it.isWritePermission })
        } finally {
            database.close()
        }
    }

    private fun android.os.Bundle.requiredInt(key: String): Int =
        requiredString(key).toInt()

    private fun android.os.Bundle.requiredString(key: String): String =
        requireNotNull(getString(key)).also { value ->
            require(value.isNotBlank())
        }

    private fun android.os.Bundle.optionalInt(key: String): Int? =
        getString(key)?.toInt()

    private companion object {
        const val ARG_ENABLED = "live_probe_enabled"
        const val ARG_EXPECTED_MAPPINGS = "expected_mappings"
        const val ARG_EXPECTED_NAMED_MAPPINGS = "expected_named_mappings"
        const val ARG_EXPECTED_TREE_GRANTS = "expected_tree_grants"
        const val ARG_EXPECTED_WRITE_GRANTS = "expected_write_grants"
        const val ARG_EXPECTED_PENDING_STATE = "expected_pending_state"
        const val NO_PENDING = "NONE"
    }
}
