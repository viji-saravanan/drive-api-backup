package com.aryasubramani.vijibackup.app

import android.os.Bundle
import java.util.UUID

internal data class FolderPickerLaunchRegistration(
    val registryKey: String,
    val requestToken: String,
)

internal class FolderPickerRequestState private constructor(
    private val launches: LinkedHashMap<String, StoredLaunch>,
) {
    val currentToken: String?
        get() = launches.values.singleOrNull(StoredLaunch::acceptsResult)?.requestToken

    val currentRegistryKey: String?
        get() = launches.values.singleOrNull(StoredLaunch::acceptsResult)?.registryKey

    val outstandingLaunches: List<FolderPickerLaunchRegistration>
        get() = launches.values.map(StoredLaunch::toRegistration)

    fun stageForLaunch(requestToken: String): FolderPickerLaunchRegistration? {
        if (requestToken.isBlank() || currentRegistryKey != null) return null
        val registryKey = REGISTRY_KEY_PREFIX + UUID.randomUUID()
        val launch = StoredLaunch(
            registryKey = registryKey,
            requestToken = requestToken,
            acceptsResult = true,
        )
        launches[registryKey] = launch
        return launch.toRegistration()
    }

    fun retireCurrent(): Boolean {
        val registryKey = currentRegistryKey ?: return false
        launches[registryKey] = checkNotNull(launches[registryKey]).copy(acceptsResult = false)
        return true
    }

    fun consumeResult(registryKey: String): String? {
        val launch = launches.remove(registryKey) ?: return null
        return launch.requestToken.takeIf { launch.acceptsResult }
    }

    fun hasLaunch(registryKey: String): Boolean = launches.containsKey(registryKey)

    fun saveTo(outState: Bundle) {
        if (launches.isEmpty()) {
            outState.remove(SAVED_REGISTRY_KEYS_KEY)
            outState.remove(SAVED_REQUEST_TOKENS_KEY)
            outState.remove(SAVED_ACTIVE_REGISTRY_KEY)
            return
        }
        outState.putStringArrayList(
            SAVED_REGISTRY_KEYS_KEY,
            ArrayList(launches.keys),
        )
        outState.putStringArrayList(
            SAVED_REQUEST_TOKENS_KEY,
            ArrayList(launches.values.map(StoredLaunch::requestToken)),
        )
        currentRegistryKey?.let { activeKey ->
            outState.putString(SAVED_ACTIVE_REGISTRY_KEY, activeKey)
        } ?: outState.remove(SAVED_ACTIVE_REGISTRY_KEY)
    }

    private data class StoredLaunch(
        val registryKey: String,
        val requestToken: String,
        val acceptsResult: Boolean,
    ) {
        fun toRegistration(): FolderPickerLaunchRegistration =
            FolderPickerLaunchRegistration(
                registryKey = registryKey,
                requestToken = requestToken,
            )
    }

    companion object {
        internal const val SAVED_REGISTRY_KEYS_KEY = "folder_picker_registry_keys"
        internal const val SAVED_REQUEST_TOKENS_KEY = "folder_picker_request_tokens"
        private const val SAVED_ACTIVE_REGISTRY_KEY = "folder_picker_active_registry_key"
        private const val REGISTRY_KEY_PREFIX = "folder_picker_request:"

        fun restore(savedInstanceState: Bundle?): FolderPickerRequestState {
            val restored = restoreLaunches(savedInstanceState)
            return FolderPickerRequestState(restored ?: linkedMapOf())
        }

        private fun restoreLaunches(savedInstanceState: Bundle?): LinkedHashMap<String, StoredLaunch>? {
            if (savedInstanceState == null || !savedInstanceState.containsKey(SAVED_REGISTRY_KEYS_KEY)) {
                return linkedMapOf()
            }
            return try {
                val registryKeys = savedInstanceState.getStringArrayList(SAVED_REGISTRY_KEYS_KEY)
                    ?: return null
                val requestTokens = savedInstanceState.getStringArrayList(SAVED_REQUEST_TOKENS_KEY)
                    ?: return null
                val activeRegistryKey = savedInstanceState.getString(SAVED_ACTIVE_REGISTRY_KEY)
                if (registryKeys.size != requestTokens.size) return null
                if (registryKeys.toSet().size != registryKeys.size) return null
                if (registryKeys.any { it.isBlank() || !it.startsWith(REGISTRY_KEY_PREFIX) }) return null
                if (requestTokens.any(String::isBlank)) return null
                if (activeRegistryKey != null && activeRegistryKey !in registryKeys) return null

                LinkedHashMap<String, StoredLaunch>(registryKeys.size).apply {
                    registryKeys.indices.forEach { index ->
                        val registryKey = registryKeys[index]
                        put(
                            registryKey,
                            StoredLaunch(
                                registryKey = registryKey,
                                requestToken = requestTokens[index],
                                acceptsResult = registryKey == activeRegistryKey,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
