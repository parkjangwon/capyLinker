package org.parkjw.capylinker.data.repository

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.parkjw.capylinker.data.database.LinkEntity
import org.parkjw.capylinker.data.model.BackupData
import org.parkjw.capylinker.data.model.BackupLink
import org.parkjw.capylinker.data.model.BackupSettings
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linkRepository: LinkRepository,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun createBackup(): Pair<String, String> = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault())
        val fileName = "capyLinker-backup-${dateFormat.format(Date(timestamp))}.json"

        val links = linkRepository.getAllLinksOnce()
        val settings = settingsRepository.getAllSettings()

        val backupData = BackupData(
            version = 1,
            timestamp = timestamp,
            links = links.map { link ->
                BackupLink(
                    url = link.url,
                    title = link.title,
                    summary = link.summary,
                    tags = link.tags,
                    thumbnailUrl = link.thumbnailUrl,
                    timestamp = link.timestamp,
                    isAnalyzing = link.isAnalyzing
                )
            },
            settings = BackupSettings(
                apiKey = settings["apiKey"] as String,
                geminiModel = settings["geminiModel"] as String,
                language = settings["language"] as String,
                theme = settings["theme"] as String,
                clipboardAutoAdd = settings["clipboardAutoAdd"] as Boolean
            )
        )

        val jsonString = json.encodeToString(backupData)
        Pair(fileName, jsonString)
    }

    suspend fun restoreFromStream(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val backupData = json.decodeFromString<BackupData>(jsonString)

            // 기존 데이터 삭제
            linkRepository.deleteAllLinks()

            // 링크 복원
            val links = backupData.links.map { backupLink ->
                LinkEntity(
                    url = backupLink.url,
                    title = backupLink.title,
                    summary = backupLink.summary,
                    tags = backupLink.tags,
                    thumbnailUrl = backupLink.thumbnailUrl,
                    timestamp = backupLink.timestamp,
                    isAnalyzing = backupLink.isAnalyzing
                )
            }
            linkRepository.insertLinks(links)

            // 설정 복원
            val settingsMap = mapOf(
                "apiKey" to backupData.settings.apiKey,
                "geminiModel" to backupData.settings.geminiModel,
                "language" to backupData.settings.language,
                "theme" to backupData.settings.theme,
                "clipboardAutoAdd" to backupData.settings.clipboardAutoAdd
            )
            settingsRepository.restoreAllSettings(settingsMap)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
