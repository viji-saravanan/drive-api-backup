package com.aryasubramani.vijibackup.folderaccess.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LocalFolderMappingEntity::class,
        PendingFolderOperationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(FolderAccessTypeConverters::class)
abstract class VijiBackupDatabase : RoomDatabase() {
    abstract fun folderAccessDao(): FolderAccessDao

    companion object {
        const val DATABASE_NAME = "viji_backup.db"
    }
}
