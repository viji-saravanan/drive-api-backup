package com.aryasubramani.vijibackup.folderaccess.saf

data class PersistedFolderGrant(
    val treeUri: String,
    val hasReadAccess: Boolean,
    val hasWriteAccess: Boolean,
)

enum class AcquireReadGrantResult {
    Acquired,
    RejectedClean,
    CleanupRequired,
}

enum class GrantReleaseResult {
    Released,
    Failed,
}

enum class WriteGrantRemovalResult {
    ReadOnlyConfirmed,
    ReadAccessMissing,
    Failed,
}

interface LocalFolderGrantManager {
    suspend fun persistedGrants(): List<PersistedFolderGrant>

    suspend fun acquireReadGrant(
        treeUri: String,
        grantedFlags: Int,
    ): AcquireReadGrantResult

    suspend fun removePersistedWriteAccess(treeUri: String): WriteGrantRemovalResult

    suspend fun releaseGrant(treeUri: String): GrantReleaseResult
}
