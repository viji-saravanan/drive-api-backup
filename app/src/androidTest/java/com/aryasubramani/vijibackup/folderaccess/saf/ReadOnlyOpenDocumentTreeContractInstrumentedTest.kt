package com.aryasubramani.vijibackup.folderaccess.saf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadOnlyOpenDocumentTreeContractInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val contract = ReadOnlyOpenDocumentTreeContract()

    @Test
    fun createIntentRequestsOnlyPersistentReadAccessFromLocalProviders() {
        val initialUri = Uri.parse("content://provider.test/tree/initial")

        val intent = contract.createIntent(
            context,
            ReadOnlyFolderPickerRequest(initialUri = initialUri),
        )

        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertTrue(intent.flags.hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(intent.flags.hasFlag(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        assertTrue(intent.flags.hasFlag(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
        assertFalse(intent.flags.hasFlag(Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        assertFalse(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        @Suppress("DEPRECATION")
        assertEquals(
            initialUri,
            intent.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI),
        )
    }

    @Test
    fun parseResultRetainsReturnedGrantFlagsForRepositoryValidation() {
        val treeUri = Uri.parse("content://provider.test/tree/selected")
        val returnedFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        val resultIntent = Intent().apply {
            data = treeUri
            addFlags(returnedFlags)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val result = contract.parseResult(Activity.RESULT_OK, resultIntent)

        assertEquals(
            FolderPickerResult.Selected(
                treeUri = treeUri,
                grantedFlags = returnedFlags,
            ),
            result,
        )
    }

    @Test
    fun parseResultDistinguishesCancellationFromMalformedSuccess() {
        assertEquals(
            FolderPickerResult.Cancelled,
            contract.parseResult(Activity.RESULT_CANCELED, null),
        )
        assertEquals(
            FolderPickerResult.InvalidResult,
            contract.parseResult(Activity.RESULT_OK, Intent()),
        )
    }

    private fun Int.hasFlag(flag: Int): Boolean = this and flag == flag
}
