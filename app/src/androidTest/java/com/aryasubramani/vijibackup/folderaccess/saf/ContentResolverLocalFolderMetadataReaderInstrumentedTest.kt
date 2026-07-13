package com.aryasubramani.vijibackup.folderaccess.saf

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ContentResolverLocalFolderMetadataReaderInstrumentedTest {
    @Test
    fun treeRootDisplayNameIsQueriedAndNormalized() = runTest {
        val provider = RecordingMetadataProvider(displayName = "  Camera\nArchive  ")
        val reader = ContentResolverLocalFolderMetadataReader(ContentResolver.wrap(provider))

        assertEquals("Camera Archive", reader.displayName(TREE_URI))
        assertEquals("selected", provider.queriedDocumentId)
        assertEquals(
            listOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            provider.queriedProjection?.toList(),
        )
    }

    @Test
    fun unavailableOrInvalidMetadataReturnsNoDisplayName() = runTest {
        val emptyReader = ContentResolverLocalFolderMetadataReader(
            ContentResolver.wrap(RecordingMetadataProvider(displayName = null)),
        )
        val failingReader = ContentResolverLocalFolderMetadataReader(
            ContentResolver.wrap(
                RecordingMetadataProvider(
                    displayName = "unused",
                    queryFailure = SecurityException("test denial"),
                ),
            ),
        )

        assertNull(emptyReader.displayName(TREE_URI))
        assertNull(failingReader.displayName(TREE_URI))
        assertNull(failingReader.displayName("not-a-tree-uri"))
    }

    private companion object {
        const val TREE_URI = "content://provider.test/tree/selected"
    }
}

private class RecordingMetadataProvider(
    private val displayName: String?,
    private val queryFailure: RuntimeException? = null,
) : ContentProvider() {
    var queriedDocumentId: String? = null
        private set
    var queriedProjection: Array<out String>? = null
        private set

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        queryFailure?.let { throw it }
        queriedDocumentId = DocumentsContract.getDocumentId(uri)
        queriedProjection = projection
        val columns = requireNotNull(projection)
        return MatrixCursor(columns).apply {
            if (displayName != null) {
                addRow(columns.map { column ->
                    if (column == DocumentsContract.Document.COLUMN_DISPLAY_NAME) {
                        displayName
                    } else {
                        null
                    }
                })
            }
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
