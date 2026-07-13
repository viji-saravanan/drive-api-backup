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

    @Query("SELECT * FROM local_folder_mappings WHERE id = :mappingId LIMIT 1")
    abstract suspend fun mappingById(mappingId: String): LocalFolderMappingEntity?

    @Query("SELECT * FROM local_folder_mappings WHERE tree_uri = :treeUri LIMIT 1")
    abstract suspend fun mappingByTreeUri(treeUri: String): LocalFolderMappingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMapping(mapping: LocalFolderMappingEntity)

    @Query("UPDATE local_folder_mappings SET enabled = :enabled WHERE id = :mappingId")
    abstract suspend fun updateMappingEnabled(mappingId: String, enabled: Boolean): Int

    @Query("DELETE FROM local_folder_mappings WHERE id = :mappingId")
    abstract suspend fun deleteMapping(mappingId: String): Int

    @Query("SELECT * FROM pending_folder_operations WHERE slot = 1 LIMIT 1")
    abstract suspend fun pendingOperation(): PendingFolderOperationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPendingOperation(
        operation: PendingFolderOperationEntity,
    ): Long

    @Transaction
    open suspend fun tryBeginPendingOperation(operation: PendingFolderOperationEntity): Boolean =
        insertPendingOperation(operation) != INSERT_IGNORED

    @Query(
        """
        DELETE FROM pending_folder_operations
        WHERE slot = 1 AND request_token = :requestToken
        """,
    )
    abstract suspend fun clearPendingOperation(requestToken: String): Int

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}
