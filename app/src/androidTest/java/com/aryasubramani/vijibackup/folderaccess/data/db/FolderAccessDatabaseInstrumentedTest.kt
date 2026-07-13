package com.aryasubramani.vijibackup.folderaccess.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderAccessDatabaseInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: VijiBackupDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun mappingCrudPersistsAndRejectsDuplicateTreeUris() = runTest {
        val first = mapping(
            id = "mapping-a",
            treeUri = "content://provider.test/tree/a",
            displayName = "Folder A",
        )
        database.folderAccessDao().insertMapping(first)

        assertEquals(listOf(first), database.folderAccessDao().observeMappings().first())
        assertEquals(1, database.folderAccessDao().updateMappingEnabled(first.id, false))
        assertFalse(database.folderAccessDao().mappingById(first.id)?.enabled ?: true)

        val duplicateFailure = runCatching {
            database.folderAccessDao().insertMapping(
                mapping(
                    id = "mapping-b",
                    treeUri = first.treeUri,
                    displayName = "Duplicate",
                ),
            )
        }.exceptionOrNull()
        assertTrue(duplicateFailure is SQLiteConstraintException)

        database.close()
        database = openDatabase()
        assertEquals(first.id, database.folderAccessDao().mappingById(first.id)?.id)
        assertFalse(database.folderAccessDao().mappingById(first.id)?.enabled ?: true)

        assertEquals(1, database.folderAccessDao().deleteMapping(first.id))
        assertNull(database.folderAccessDao().mappingById(first.id))
    }

    @Test
    fun pendingPickerSlotAdmitsOnlyOneOperationUntilMatchingClear() = runTest {
        val first = pendingOperation(
            requestToken = "request-a",
            operation = PendingFolderOperationType.Add,
        )
        val second = pendingOperation(
            requestToken = "request-b",
            operation = PendingFolderOperationType.Repair,
            targetMappingId = "mapping-a",
        )

        assertTrue(database.folderAccessDao().tryBeginPendingOperation(first))
        assertFalse(database.folderAccessDao().tryBeginPendingOperation(second))
        assertEquals(first, database.folderAccessDao().pendingOperation())
        assertEquals(0, database.folderAccessDao().clearPendingOperation("wrong-token"))
        assertEquals(first, database.folderAccessDao().pendingOperation())
        assertEquals(1, database.folderAccessDao().clearPendingOperation(first.requestToken))
        assertTrue(database.folderAccessDao().tryBeginPendingOperation(second))
        assertEquals(second, database.folderAccessDao().pendingOperation())
    }

    private fun openDatabase(): VijiBackupDatabase =
        Room.databaseBuilder(
            context,
            VijiBackupDatabase::class.java,
            TEST_DATABASE_NAME,
        ).build()

    private fun mapping(
        id: String,
        treeUri: String,
        displayName: String,
    ) = LocalFolderMappingEntity(
        id = id,
        treeUri = treeUri,
        sourceDisplayName = displayName,
        enabled = true,
    )

    private fun pendingOperation(
        requestToken: String,
        operation: PendingFolderOperationType,
        targetMappingId: String? = null,
    ) = PendingFolderOperationEntity(
        requestToken = requestToken,
        operation = operation,
        targetMappingId = targetMappingId,
        selectedTreeUri = null,
        state = PendingFolderOperationState.Requested,
        createdAtEpochMs = 1_000L,
    )

    private companion object {
        const val TEST_DATABASE_NAME = "folder_access_room_test.db"
    }
}
