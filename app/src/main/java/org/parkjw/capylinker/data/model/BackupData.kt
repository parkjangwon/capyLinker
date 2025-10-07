package org.parkjw.capylinker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val timestamp: Long,
    val links: List<BackupLink>,
    val settings: BackupSettings
)

@Serializable
data class BackupLink(
    val url: String,
    val title: String,
    val summary: String,
    val tags: List<String>,
    val thumbnailUrl: String?,
    val timestamp: Long,
    val isAnalyzing: Boolean
)

@Serializable
data class BackupSettings(
    val apiKey: String,
    val geminiModel: String,
    val language: String,
    val theme: String,
    val clipboardAutoAdd: Boolean
)
