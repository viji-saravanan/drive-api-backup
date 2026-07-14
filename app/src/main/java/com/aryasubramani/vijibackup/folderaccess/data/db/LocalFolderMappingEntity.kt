package com.aryasubramani.vijibackup.folderaccess.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_folder_mappings",
    indices = [Index(value = ["tree_uri"], unique = true)],
)
data class LocalFolderMappingEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "tree_uri")
    val treeUri: String,
    @ColumnInfo(name = "source_display_name")
    val sourceDisplayName: String?,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
) {
    init {
        require(id.isNotBlank())
        require(treeUri.isNotBlank())
        require(sourceDisplayName == null || sourceDisplayName.isNotBlank())
    }
}
