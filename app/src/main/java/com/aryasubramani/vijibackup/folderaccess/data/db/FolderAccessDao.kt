package com.aryasubramani.vijibackup.folderaccess.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FolderAccessDao {
    @Query(
        """
        SELECT * FROM local_folder_mappings
        ORDER BY source_display_name COLLATE NOCASE ASC, id ASC
        """,
    )
    abstract fun observeMappings(): Flow<List<LocalFolderMappingEntity>>

    @Query("SELECT * FROM local_folder_mappings")
    abstract suspend fun allMappings(): List<LocalFolderMappingEntity>

    @Query("SELECT * FROM local_folder_mappings WHERE id = :mappingId LIMIT 1")
    abstract suspend fun mappingById(mappingId: String): LocalFolderMappingEntity?

    @Query("SELECT * FROM local_folder_mappings WHERE tree_uri = :treeUri LIMIT 1")
    abstract suspend fun mappingByTreeUri(treeUri: String): LocalFolderMappingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMapping(mapping: LocalFolderMappingEntity)

    @Query("UPDATE local_folder_mappings SET enabled = :enabled WHERE id = :mappingId")
    abstract suspend fun updateMappingEnabled(mappingId: String, enabled: Boolean): Int

    @Query(
        "UPDATE local_folder_mappings SET source_display_name = :displayName WHERE id = :mappingId",
    )
    abstract suspend fun updateMappingDisplayName(mappingId: String, displayName: String): Int

    @Query("DELETE FROM local_folder_mappings WHERE id = :mappingId")
    abstract suspend fun deleteMapping(mappingId: String): Int

    @Query("SELECT * FROM pending_folder_operations")
    protected abstract suspend fun allPendingOperations(): List<PendingFolderOperationEntity>

    @Transaction
    open suspend fun pendingOperation(): PendingFolderOperationEntity? {
        val pending = allPendingOperations()
        check(pending.size <= 1) { "Multiple pending folder operations found" }
        return pending.firstOrNull().also { operation ->
            check(operation == null || operation.slot == PendingFolderOperationEntity.SINGLETON_SLOT) {
                "Invalid pending folder operation slot"
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPendingOperation(
        operation: PendingFolderOperationEntity,
    ): Long

    @Transaction
    open suspend fun tryBeginPendingOperation(operation: PendingFolderOperationEntity): Boolean {
        if (allPendingOperations().isNotEmpty()) return false
        return insertPendingOperation(operation) != INSERT_IGNORED
    }

    @Query(
        """
        UPDATE pending_folder_operations
        SET selected_tree_uri = :selectedTreeUri, state = 'SELECTION_RECEIVED'
        WHERE slot = 1
          AND request_token = :requestToken
          AND state = 'REQUESTED'
        """,
    )
    abstract suspend fun markSelectionReceived(
        requestToken: String,
        selectedTreeUri: String,
    ): Int

    @Query(
        """
        UPDATE pending_folder_operations
        SET state = 'ABANDONING'
        WHERE slot = 1
          AND request_token = :requestToken
          AND state IN ('REQUESTED', 'SELECTION_RECEIVED')
        """,
    )
    abstract suspend fun markPendingAbandoning(requestToken: String): Int

    @Query(
        """
        DELETE FROM pending_folder_operations
        WHERE slot = 1
          AND request_token = :requestToken
          AND state = 'REQUESTED'
        """,
    )
    abstract suspend fun cancelRequestedOperation(requestToken: String): Int

    @Query(
        """
        DELETE FROM pending_folder_operations
        WHERE slot = 1
          AND request_token = :requestToken
          AND state = 'ABANDONING'
        """,
    )
    abstract suspend fun deleteAbandoningOperation(requestToken: String): Int

    @Query(
        """
        DELETE FROM pending_folder_operations
        WHERE slot = 1
          AND request_token = :requestToken
          AND state = 'SELECTION_RECEIVED'
        """,
    )
    protected abstract suspend fun deleteSelectionReceivedForCommit(
        requestToken: String,
    ): Int

    @Transaction
    open suspend fun commitAddedMapping(
        mapping: LocalFolderMappingEntity,
        requestToken: String,
    ): Boolean {
        val pending = pendingOperation()
        if (
            pending?.requestToken != requestToken ||
            pending.state != PendingFolderOperationState.SelectionReceived ||
            pending.operation != PendingFolderOperationType.Add ||
            pending.selectedTreeUri != mapping.treeUri
        ) {
            return false
        }

        insertMapping(mapping)
        check(deleteSelectionReceivedForCommit(requestToken) == 1) {
            "Pending folder operation changed during add transaction"
        }
        return true
    }

    @Query(
        """
        UPDATE local_folder_mappings
        SET tree_uri = :replacementTreeUri, source_display_name = :replacementDisplayName
        WHERE id = :mappingId
        """,
    )
    protected abstract suspend fun updateMappingTreeUri(
        mappingId: String,
        replacementTreeUri: String,
        replacementDisplayName: String?,
    ): Int

    @Transaction
    open suspend fun commitRepairedMapping(
        mappingId: String,
        replacementTreeUri: String,
        replacementDisplayName: String?,
        requestToken: String,
    ): Boolean {
        val pending = pendingOperation()
        if (
            pending?.requestToken != requestToken ||
            pending.state != PendingFolderOperationState.SelectionReceived ||
            pending.operation != PendingFolderOperationType.Repair ||
            pending.targetMappingId != mappingId ||
            pending.selectedTreeUri != replacementTreeUri
        ) {
            return false
        }

        if (
            updateMappingTreeUri(
                mappingId = mappingId,
                replacementTreeUri = replacementTreeUri,
                replacementDisplayName = replacementDisplayName,
            ) != 1
        ) {
            return false
        }
        check(deleteSelectionReceivedForCommit(requestToken) == 1) {
            "Pending folder operation changed during repair transaction"
        }
        return true
    }

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}
