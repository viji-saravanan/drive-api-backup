package com.aryasubramani.vijibackup.folderaccess.saf

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentResolverLocalFolderGrantManagerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val contentResolver = context.contentResolver
    private lateinit var grantManager: ContentResolverLocalFolderGrantManager

    @Before
    fun setUp() {
        grantManager = ContentResolverLocalFolderGrantManager(
            contentResolver = contentResolver,
            ioDispatcher = Dispatchers.IO,
        )
    }

    @Test
    fun persistedGrantsMirrorTheCallingAppsRealTreeGrantTable() = runTest {
        val expected = contentResolver.persistedUriPermissions
            .filter { DocumentsContract.isTreeUri(it.uri) }
            .map { Triple(it.uri.toString(), it.isReadPermission, it.isWritePermission) }
            .toSet()

        val actual = grantManager.persistedGrants()
            .map { Triple(it.treeUri, it.hasReadAccess, it.hasWriteAccess) }
            .toSet()

        assertTrue(expected == actual)
    }

    @Test
    fun invalidSelectionFlagsAreRejectedWithoutMutatingRealGrants() = runTest {
        val before = permissionSnapshot()

        val result = grantManager.acquireReadGrant(
            treeUri = SYNTHETIC_TREE_URI,
            grantedFlags = 0,
        )

        assertEquals(AcquireReadGrantResult.RejectedClean, result)
        assertTrue(before == permissionSnapshot())
    }

    @Test
    fun nonTreeUriIsRejectedWithoutMutatingRealGrants() = runTest {
        val before = permissionSnapshot()

        val result = grantManager.acquireReadGrant(
            treeUri = "content://provider.test/document/not-a-tree",
            grantedFlags = REQUIRED_RETURNED_FLAGS,
        )

        assertEquals(AcquireReadGrantResult.RejectedClean, result)
        assertTrue(before == permissionSnapshot())
    }

    @Test
    fun releasingAnUnknownTreeGrantIsIdempotent() = runTest {
        val before = permissionSnapshot()

        assertEquals(
            GrantReleaseResult.Released,
            grantManager.releaseGrant(SYNTHETIC_TREE_URI),
        )
        assertTrue(before == permissionSnapshot())
    }

    @Test
    fun removingWriteFromAnUnknownTreeReportsMissingReadAccess() = runTest {
        val before = permissionSnapshot()

        assertEquals(
            WriteGrantRemovalResult.ReadAccessMissing,
            grantManager.removePersistedWriteAccess(SYNTHETIC_TREE_URI),
        )
        assertTrue(before == permissionSnapshot())
    }

    private fun permissionSnapshot(): Set<Triple<String, Boolean, Boolean>> =
        contentResolver.persistedUriPermissions
            .map { Triple(it.uri.toString(), it.isReadPermission, it.isWritePermission) }
            .toSet()

    private companion object {
        const val SYNTHETIC_TREE_URI = "content://provider.test/tree/not-persisted"
        const val REQUIRED_RETURNED_FLAGS =
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
