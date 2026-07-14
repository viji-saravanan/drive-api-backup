package com.aryasubramani.vijibackup.folderaccess.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_folder_operations")
data class PendingFolderOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "slot")
    val slot: Int = SINGLETON_SLOT,
    @ColumnInfo(name = "request_token")
    val requestToken: String,
    @ColumnInfo(name = "operation")
    val operation: PendingFolderOperationType,
    @ColumnInfo(name = "target_mapping_id")
    val targetMappingId: String?,
    @ColumnInfo(name = "selected_tree_uri")
    val selectedTreeUri: String?,
    @ColumnInfo(name = "state")
    val state: PendingFolderOperationState,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
) {
    init {
        require(slot == SINGLETON_SLOT)
        require(requestToken.isNotBlank())
        require(createdAtEpochMs >= 0L)
        require(
            when (operation) {
                PendingFolderOperationType.Add -> targetMappingId == null
                PendingFolderOperationType.Repair -> !targetMappingId.isNullOrBlank()
            },
        )
        require(
            when (state) {
                PendingFolderOperationState.Requested -> selectedTreeUri == null
                PendingFolderOperationState.SelectionReceived -> !selectedTreeUri.isNullOrBlank()
                PendingFolderOperationState.Abandoning ->
                    selectedTreeUri == null || selectedTreeUri.isNotBlank()
            },
        )
    }

    companion object {
        const val SINGLETON_SLOT = 1
    }
}

enum class PendingFolderOperationType {
    Add,
    Repair,
}

enum class PendingFolderOperationState {
    Requested,
    SelectionReceived,
    Abandoning,
}
