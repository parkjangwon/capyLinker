package org.parkjw.capylinker.data.repository

import kotlinx.coroutines.flow.first
import org.parkjw.capylinker.data.database.LinkDao
import org.parkjw.capylinker.data.database.LinkEntity
import org.parkjw.capylinker.ui.strings.getStrings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkRepository @Inject constructor(
    private val linkDao: LinkDao,
    private val geminiRepository: GeminiRepository,
    private val settingsRepository: SettingsRepository
) {
    fun getAllLinks() = linkDao.getAllLinks()

    suspend fun isUrlExist(url: String): Boolean {
        return linkDao.getLinkByUrl(url) != null
    }

    suspend fun saveLink(url: String) {
        android.util.Log.d("LinkRepository", "Saving link: $url")
        val language = settingsRepository.language.first()
        val strings = getStrings(language)
        // 먼저 분석 중 상태로 저장
        val tempLink = LinkEntity(
            url = url,
            title = strings.analyzingTitle,
            summary = strings.analyzingSummary,
            tags = emptyList(),
            thumbnailUrl = null,
            isAnalyzing = true
        )
        linkDao.insertLink(tempLink)
        android.util.Log.d("LinkRepository", "Temp link inserted")

        // 백그라운드에서 분석
        try {
            val analysis = geminiRepository.analyzeUrl(url)
            analysis?.let {
                val analyzedLink = LinkEntity(
                    url = url,
                    title = it.title,
                    summary = it.summary,
                    tags = it.tags,
                    thumbnailUrl = it.thumbnailUrl,
                    timestamp = tempLink.timestamp,
                    isAnalyzing = false
                )
                linkDao.insertLink(analyzedLink)
            }
        } catch (e: Exception) {
            // 분석 실패 시 오류 상태로 업데이트
            val errorLink = LinkEntity(
                url = url,
                title = strings.analysisFailedTitle,
                summary = strings.analysisFailedSummary.format(e.message),
                tags = emptyList(),
                thumbnailUrl = null,
                timestamp = tempLink.timestamp,
                isAnalyzing = false
            )
            linkDao.insertLink(errorLink)
        }
    }

    suspend fun reSummarize(url: String) {
        val tempLink = linkDao.getLinkByUrl(url) ?: return
        val language = settingsRepository.language.first()
        val strings = getStrings(language)

        // 분석 중 상태로 업데이트
        linkDao.insertLink(tempLink.copy(isAnalyzing = true, title = strings.analyzingTitle, summary = strings.analyzingSummary))

        try {
            val analysis = geminiRepository.analyzeUrl(url)
            analysis?.let {
                val analyzedLink = tempLink.copy(
                    title = it.title,
                    summary = it.summary,
                    tags = it.tags,
                    thumbnailUrl = it.thumbnailUrl,
                    isAnalyzing = false
                )
                linkDao.insertLink(analyzedLink)
            }
        } catch (e: Exception) {
            val errorLink = tempLink.copy(
                title = strings.analysisFailedTitle,
                summary = strings.analysisFailedSummary.format(e.message),
                isAnalyzing = false
            )
            linkDao.insertLink(errorLink)
        }
    }

    suspend fun deleteLink(link: LinkEntity) {
        linkDao.deleteLink(link)
    }

    suspend fun insertLink(url: String, title: String, summary: String, tags: List<String>, thumbnailUrl: String? = null) {
        val newItem = LinkEntity(
            url = url,
            title = title,
            summary = summary,
            tags = tags,
            thumbnailUrl = thumbnailUrl
        )
        linkDao.insertLink(newItem)
    }

    suspend fun updateItem(item: LinkEntity) {
        linkDao.insertLink(item)
    }

    suspend fun getAllLinksOnce(): List<LinkEntity> {
        return linkDao.getAllLinksOnce()
    }



    suspend fun deleteAllLinks() {
        linkDao.deleteAllLinks()
    }

    suspend fun insertLinks(links: List<LinkEntity>) {
        linkDao.insertLinks(links)
    }
}
