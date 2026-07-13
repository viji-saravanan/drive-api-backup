package com.aryasubramani.vijibackup.folderaccess.saf

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentResolverLocalFolderGrantManager(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalFolderGrantManager {
    override suspend fun persistedGrants(): List<PersistedFolderGrant> =
        withContext(ioDispatcher) {
            contentResolver.persistedUriPermissions
                .asSequence()
                .filter { permission -> permission.uri.isValidTreeUri() }
                .map { permission -> permission.toFolderGrant() }
                .toList()
        }

    override suspend fun acquireReadGrant(
        treeUri: String,
        grantedFlags: Int,
    ): AcquireReadGrantResult = withContext(ioDispatcher) {
        val uri = treeUri.toValidTreeUri() ?: return@withContext AcquireReadGrantResult.RejectedClean
        if (!grantedFlags.hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION) ||
            !grantedFlags.hasFlag(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        ) {
            return@withContext AcquireReadGrantResult.RejectedClean
        }

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Verification below decides whether the failed call changed persisted state.
        }

        when (val permission = exactPermission(uri)) {
            PermissionLookup.Failed -> AcquireReadGrantResult.CleanupRequired
            is PermissionLookup.Found -> {
                when {
                    permission.permission == null -> AcquireReadGrantResult.RejectedClean
                    permission.permission.isWritePermission -> when (
                        removePersistedWriteAccessInternal(uri)
                    ) {
                        WriteGrantRemovalResult.ReadOnlyConfirmed ->
                            AcquireReadGrantResult.Acquired
                        WriteGrantRemovalResult.ReadAccessMissing ->
                            AcquireReadGrantResult.RejectedClean
                        WriteGrantRemovalResult.Failed ->
                            AcquireReadGrantResult.CleanupRequired
                    }
                    permission.permission.isReadPermission -> AcquireReadGrantResult.Acquired
                    else -> AcquireReadGrantResult.RejectedClean
                }
            }
        }
    }

    override suspend fun removePersistedWriteAccess(
        treeUri: String,
    ): WriteGrantRemovalResult = withContext(ioDispatcher) {
        val uri = treeUri.toValidTreeUri() ?: return@withContext WriteGrantRemovalResult.Failed
        removePersistedWriteAccessInternal(uri)
    }

    override suspend fun releaseGrant(treeUri: String): GrantReleaseResult =
        withContext(ioDispatcher) {
            val uri = treeUri.toValidTreeUri() ?: return@withContext GrantReleaseResult.Failed
            val permission = when (val lookup = exactPermission(uri)) {
                PermissionLookup.Failed -> return@withContext GrantReleaseResult.Failed
                is PermissionLookup.Found -> lookup.permission
            } ?: return@withContext GrantReleaseResult.Released

            val modeFlags = permission.modeFlags()
            if (modeFlags == 0) return@withContext GrantReleaseResult.Released

            try {
                contentResolver.releasePersistableUriPermission(uri, modeFlags)
            } catch (_: Exception) {
                // An idempotent release can race provider revocation, so verify below.
            }

            when (val remaining = exactPermission(uri)) {
                PermissionLookup.Failed -> GrantReleaseResult.Failed
                is PermissionLookup.Found -> if (
                    remaining.permission == null || remaining.permission.modeFlags() == 0
                ) {
                    GrantReleaseResult.Released
                } else {
                    GrantReleaseResult.Failed
                }
            }
        }

    private fun removePersistedWriteAccessInternal(uri: Uri): WriteGrantRemovalResult {
        val before = when (val lookup = exactPermission(uri)) {
            PermissionLookup.Failed -> return WriteGrantRemovalResult.Failed
            is PermissionLookup.Found -> lookup.permission
        } ?: return WriteGrantRemovalResult.ReadAccessMissing

        if (!before.isWritePermission) {
            return if (before.isReadPermission) {
                WriteGrantRemovalResult.ReadOnlyConfirmed
            } else {
                WriteGrantRemovalResult.ReadAccessMissing
            }
        }

        try {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Verification below distinguishes a completed release from a live write grant.
        }

        return when (val lookup = exactPermission(uri)) {
            PermissionLookup.Failed -> WriteGrantRemovalResult.Failed
            is PermissionLookup.Found -> when {
                lookup.permission?.isWritePermission == true -> WriteGrantRemovalResult.Failed
                lookup.permission?.isReadPermission == true ->
                    WriteGrantRemovalResult.ReadOnlyConfirmed
                else -> WriteGrantRemovalResult.ReadAccessMissing
            }
        }
    }

    private fun exactPermission(uri: Uri): PermissionLookup = try {
        PermissionLookup.Found(
            contentResolver.persistedUriPermissions.firstOrNull { permission ->
                permission.uri == uri
            },
        )
    } catch (_: Exception) {
        PermissionLookup.Failed
    }

    private fun String.toValidTreeUri(): Uri? = try {
        Uri.parse(this).takeIf { uri -> uri.isValidTreeUri() }
    } catch (_: Exception) {
        null
    }

    private fun Uri.isValidTreeUri(): Boolean =
        scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(this)

    private fun UriPermission.toFolderGrant() = PersistedFolderGrant(
        treeUri = uri.toString(),
        hasReadAccess = isReadPermission,
        hasWriteAccess = isWritePermission,
    )

    private fun UriPermission.modeFlags(): Int =
        (if (isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)

    private fun Int.hasFlag(flag: Int): Boolean = this and flag == flag

    private sealed interface PermissionLookup {
        data class Found(val permission: UriPermission?) : PermissionLookup
        data object Failed : PermissionLookup
    }
}
