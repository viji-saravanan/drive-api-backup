package com.aryasubramani.vijibackup.core

object AppIdentity {
    const val displayName = "Viji Backup"
    const val baseApplicationId = "com.aryasubramani.vijibackup"
    const val phaseLabel = "Foundation"

    val releaseChannels = setOf("internal", "public")

    fun isSupportedReleaseChannel(channel: String): Boolean =
        channel in releaseChannels
}
