package com.aryasubramani.vijibackup.folderaccess.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        assertEquals(0, database.folderAccessDao().cancelRequestedOperation("wrong-token"))
        assertEquals(first, database.folderAccessDao().pendingOperation())
        assertEquals(1, database.folderAccessDao().cancelRequestedOperation(first.requestToken))
        assertTrue(database.folderAccessDao().tryBeginPendingOperation(second))
        assertEquals(second, database.folderAccessDao().pendingOperation())
    }

    @Test
    fun selectionTransitionRequiresTheCurrentRequestedTokenExactlyOnce() = runTest {
        val operation = pendingOperation(
            requestToken = "request-a",
            operation = PendingFolderOperationType.Add,
        )
        val selectedTreeUri = "content://provider.test/tree/selected"
        assertTrue(database.folderAccessDao().tryBeginPendingOperation(operation))

        assertEquals(
            0,
            database.folderAccessDao().markSelectionReceived(
                requestToken = "stale-token",
                selectedTreeUri = selectedTreeUri,
            ),
        )
        assertEquals(operation, database.folderAccessDao().pendingOperation())

        assertEquals(
            1,
            database.folderAccessDao().markSelectionReceived(
                requestToken = operation.requestToken,
                selectedTreeUri = selectedTreeUri,
            ),
        )
        assertEquals(
            operation.copy(
                selectedTreeUri = selectedTreeUri,
                state = PendingFolderOperationState.SelectionReceived,
            ),
            database.folderAccessDao().pendingOperation(),
        )
        assertEquals(
            0,
            database.folderAccessDao().markSelectionReceived(
                requestToken = operation.requestToken,
                selectedTreeUri = "content://provider.test/tree/replayed",
            ),
        )
    }

    @Test
    fun concurrentBeginsAdmitExactlyOnePendingOperation() = runTest {
        val start = CompletableDeferred<Unit>()
        val results = coroutineScope {
            val first = async(Dispatchers.IO) {
                start.await()
                database.folderAccessDao().tryBeginPendingOperation(
                    pendingOperation("request-a", PendingFolderOperationType.Add),
                )
            }
            val second = async(Dispatchers.IO) {
                start.await()
                database.folderAccessDao().tryBeginPendingOperation(
                    pendingOperation("request-b", PendingFolderOperationType.Add),
                )
            }
            start.complete(Unit)
            listOf(first.await(), second.await())
        }

        assertEquals(1, results.count { it })
        assertEquals(1, results.count { !it })
        assertTrue(
            database.folderAccessDao().pendingOperation()?.requestToken in
                setOf("request-a", "request-b"),
        )
    }

    @Test
    fun everyPendingStateSurvivesDatabaseRecreation() = runTest {
        val requested = pendingOperation("request-a", PendingFolderOperationType.Add)
        assertTrue(database.folderAccessDao().tryBeginPendingOperation(requested))
        reopenDatabase()
        assertEquals(requested, database.folderAccessDao().pendingOperation())

        val treeUri = "content://provider.test/tree/selected"
        assertEquals(
            1,
            database.folderAccessDao().markSelectionReceived(requested.requestToken, treeUri),
        )
        reopenDatabase()
        assertEquals(
            requested.copy(
                selectedTreeUri = treeUri,
                state = PendingFolderOperationState.SelectionReceived,
            ),
            database.folderAccessDao().pendingOperation(),
        )

        assertEquals(
            1,
            database.folderAccessDao().markPendingAbandoning(requested.requestToken),
        )
        reopenDatabase()
        assertEquals(
            PendingFolderOperationState.Abandoning,
            database.folderAccessDao().pendingOperation()?.state,
        )
    }

    @Test
    fun malformedSingletonSlotFailsClosedAndCannotAdmitAnotherOperation() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO pending_folder_operations(
                slot, request_token, operation, target_mapping_id,
                selected_tree_uri, state, created_at_epoch_ms
            ) VALUES(2, 'corrupt-token', 'ADD', NULL, NULL, 'REQUESTED', 1000)
            """.trimIndent(),
        )

        assertTrue(
            runCatching { database.folderAccessDao().pendingOperation() }.isFailure,
        )
        assertTrue(
            runCatching {
                database.folderAccessDao().tryBeginPendingOperation(
                    pendingOperation("request-a", PendingFolderOperationType.Add),
                )
            }.isFailure,
        )
    }

    @Test
    fun unknownPersistedEnumFailsClosed() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO pending_folder_operations(
                slot, request_token, operation, target_mapping_id,
                selected_tree_uri, state, created_at_epoch_ms
            ) VALUES(1, 'corrupt-token', 'UNKNOWN', NULL, NULL, 'REQUESTED', 1000)
            """.trimIndent(),
        )

        assertTrue(
            runCatching { database.folderAccessDao().pendingOperation() }.isFailure,
        )
    }

    private fun openDatabase(): VijiBackupDatabase =
        Room.databaseBuilder(
            context,
            VijiBackupDatabase::class.java,
            TEST_DATABASE_NAME,
        ).build()

    private fun reopenDatabase() {
        database.close()
        database = openDatabase()
    }

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
