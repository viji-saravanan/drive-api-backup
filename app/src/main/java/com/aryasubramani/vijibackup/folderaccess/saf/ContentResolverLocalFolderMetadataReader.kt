package com.aryasubramani.vijibackup.folderaccess.saf

import android.content.ContentResolver
import com.aryasubramani.vijibackup.folderaccess.domain.LocalFolderMetadataReader

internal class ContentResolverLocalFolderMetadataReader(
    private val contentResolver: ContentResolver,
) : LocalFolderMetadataReader {
    override suspend fun displayName(treeUri: String): String? = null
}
