package org.parkjw.capylinker.data.repository

import org.parkjw.capylinker.data.database.LinkDao
import org.parkjw.capylinker.data.database.LinkEntity
import org.parkjw.capylinker.ui.screens.Link
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkRepository @Inject constructor(
    private val linkDao: LinkDao,
    private val geminiRepository: GeminiRepository
) {
    fun getAllLinks() = linkDao.getAllLinks()

    suspend fun saveLink(url: String) {
        android.util.Log.d("LinkRepository", "Saving link: $url")
        // 먼저 분석 중 상태로 저장
        val tempLink = LinkEntity(
            url = url,
            title = "Analyzing...",
            summary = "AI analysis in progress...",
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
                title = "Analysis Failed",
                summary = "Failed to analyze: ${e.message}",
                tags = emptyList(),
                thumbnailUrl = null,
                timestamp = tempLink.timestamp,
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
