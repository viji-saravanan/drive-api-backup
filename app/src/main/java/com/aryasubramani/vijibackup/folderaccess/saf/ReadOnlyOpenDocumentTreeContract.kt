package com.aryasubramani.vijibackup.folderaccess.saf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

data class ReadOnlyFolderPickerRequest(
    val initialUri: Uri? = null,
)

sealed interface FolderPickerResult {
    data class Selected(
        val treeUri: Uri,
        val grantedFlags: Int,
    ) : FolderPickerResult

    data object Cancelled : FolderPickerResult
}

class ReadOnlyOpenDocumentTreeContract :
    ActivityResultContract<ReadOnlyFolderPickerRequest, FolderPickerResult>() {
    override fun createIntent(
        context: Context,
        input: ReadOnlyFolderPickerRequest,
    ): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input.initialUri != null) {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.initialUri)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): FolderPickerResult {
        if (resultCode != Activity.RESULT_OK) return FolderPickerResult.Cancelled
        val treeUri = intent?.data ?: return FolderPickerResult.Cancelled
        return FolderPickerResult.Selected(
            treeUri = treeUri,
            grantedFlags = intent.flags and URI_GRANT_FLAGS,
        )
    }

    private companion object {
        const val URI_GRANT_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
