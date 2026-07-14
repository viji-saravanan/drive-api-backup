package com.aryasubramani.vijibackup.folderaccess.domain

fun interface LocalFolderMetadataReader {
    suspend fun displayName(treeUri: String): String?
}
