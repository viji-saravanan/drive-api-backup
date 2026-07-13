package com.aryasubramani.vijibackup.folderaccess.data.db

import androidx.room.TypeConverter

class FolderAccessTypeConverters {
    @TypeConverter
    fun encodeOperation(value: PendingFolderOperationType): String = when (value) {
        PendingFolderOperationType.Add -> "ADD"
        PendingFolderOperationType.Repair -> "REPAIR"
    }

    @TypeConverter
    fun decodeOperation(value: String): PendingFolderOperationType = when (value) {
        "ADD" -> PendingFolderOperationType.Add
        "REPAIR" -> PendingFolderOperationType.Repair
        else -> throw IllegalArgumentException("Unknown pending folder operation type")
    }

    @TypeConverter
    fun encodeState(value: PendingFolderOperationState): String = when (value) {
        PendingFolderOperationState.Requested -> "REQUESTED"
        PendingFolderOperationState.SelectionReceived -> "SELECTION_RECEIVED"
        PendingFolderOperationState.Abandoning -> "ABANDONING"
    }

    @TypeConverter
    fun decodeState(value: String): PendingFolderOperationState = when (value) {
        "REQUESTED" -> PendingFolderOperationState.Requested
        "SELECTION_RECEIVED" -> PendingFolderOperationState.SelectionReceived
        "ABANDONING" -> PendingFolderOperationState.Abandoning
        else -> throw IllegalArgumentException("Unknown pending folder operation state")
    }
}
