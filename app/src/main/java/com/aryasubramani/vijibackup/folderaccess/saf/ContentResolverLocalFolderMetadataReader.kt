package com.aryasubramani.vijibackup.folderaccess.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.aryasubramani.vijibackup.folderaccess.domain.LocalFolderMetadataReader

internal class ContentResolverLocalFolderMetadataReader(
    private val contentResolver: ContentResolver,
) : LocalFolderMetadataReader {
    override suspend fun displayName(treeUri: String): String? {
        val documentUri = treeUri.toRootDocumentUri() ?: return null
        return try {
            contentResolver.query(
                documentUri,
                DISPLAY_NAME_PROJECTION,
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) {
                    null
                } else {
                    cursor.getString(0)?.toSafeDisplayName()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun String.toRootDocumentUri(): Uri? = try {
        val uri = Uri.parse(this)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || !DocumentsContract.isTreeUri(uri)) {
            return null
        }
        DocumentsContract.buildDocumentUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri),
        )
    } catch (_: Exception) {
        null
    }

    private fun String.toSafeDisplayName(): String? {
        val normalized = buildString(length) {
            this@toSafeDisplayName.forEach { character ->
                if (
                    character.isISOControl() ||
                    Character.getType(character) == Character.FORMAT.toInt()
                ) {
                    append(' ')
                } else {
                    append(character)
                }
            }
        }
            .trim()
            .replace(WHITESPACE, " ")
            .take(MAX_DISPLAY_NAME_LENGTH)
            .trimEnd()
            .dropLastWhile(Character::isHighSurrogate)
        return normalized.takeIf(String::isNotEmpty)
    }

    private companion object {
        val DISPLAY_NAME_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val WHITESPACE = Regex("\\s+")
        const val MAX_DISPLAY_NAME_LENGTH = 256
    }
}
