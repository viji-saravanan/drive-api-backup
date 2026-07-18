package com.aryasubramani.vijibackup.drive.config

import com.aryasubramani.vijibackup.BuildConfig

internal object DriveBuildConfiguration {
    val value: DriveConfiguration = createDriveConfiguration(
        folderId = BuildConfig.DRIVE_UPLOAD_FOLDER_ID,
    )
}

internal sealed interface DriveConfiguration {
    data class Ready(val folderId: String) : DriveConfiguration {
        override fun toString(): String = "DriveConfiguration.Ready(folderId=REDACTED)"
    }

    data object Invalid : DriveConfiguration
}

internal fun createDriveConfiguration(folderId: String): DriveConfiguration {
    val normalizedFolderId = folderId.trim()
    if (!normalizedFolderId.matches(DRIVE_FILE_ID_PATTERN)) {
        return DriveConfiguration.Invalid
    }
    return DriveConfiguration.Ready(folderId = normalizedFolderId)
}

private val DRIVE_FILE_ID_PATTERN = Regex("^[A-Za-z0-9_-]+$")
