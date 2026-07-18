package com.aryasubramani.vijibackup.drive.config

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveConfigurationTest {
    @Test
    fun readyConfigurationDescriptionRedactsFolderId() {
        val configuration = DriveConfiguration.Ready("private-folder-id")

        assertEquals(
            "DriveConfiguration.Ready(folderId=REDACTED)",
            configuration.toString(),
        )
    }

    @Test
    fun blankFolderIdIsInvalid() {
        assertEquals(
            DriveConfiguration.Invalid,
            createDriveConfiguration("   "),
        )
    }

    @Test
    fun pathAndUrlLikeValuesAreInvalid() {
        listOf(
            "folder/child",
            "https://drive.google.com/folder",
            "folder?id=value",
            "folder id",
        ).forEach { value ->
            assertEquals(DriveConfiguration.Invalid, createDriveConfiguration(value))
        }
    }

    @Test
    fun punctuationOutsideOpaqueDriveAlphabetIsInvalid() {
        assertEquals(
            DriveConfiguration.Invalid,
            createDriveConfiguration("folder.value"),
        )
    }

    @Test
    fun opaqueFolderIdIsTrimmedAndAccepted() {
        assertEquals(
            DriveConfiguration.Ready(folderId = "folder-ID_123"),
            createDriveConfiguration("  folder-ID_123  "),
        )
    }

    @Test
    fun veryLongButValidOpaqueIdIsAcceptedWithoutReformatting() {
        val folderId = "a".repeat(512)

        assertEquals(
            DriveConfiguration.Ready(folderId),
            createDriveConfiguration(folderId),
        )
    }
}
