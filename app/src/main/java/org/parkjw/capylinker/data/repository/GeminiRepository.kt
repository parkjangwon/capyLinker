package org.parkjw.capylinker.data.repository

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class AnalysisResult(
    val title: String,
    val summary: String,
    val tags: List<String>,
    val thumbnailUrl: String? = null
)

@Singleton
class GeminiRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private var generativeModel: GenerativeModel? = null

    private suspend fun initializeModel() {
        val apiKey = settingsRepository.apiKey.first()
        if (apiKey.isNotBlank()) {
            generativeModel = GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = apiKey
            )
        }
    }

    suspend fun analyzeUrl(url: String, maxRetries: Int = 3): AnalysisResult? {
        if (generativeModel == null) {
            initializeModel()
        }
        if (generativeModel == null) {
            return AnalysisResult("No title", "API key not set", emptyList(), null)
        }

        return withContext(Dispatchers.IO) {
            try {
                // YouTube 링크 확인
                val youtubeVideoId = extractYouTubeVideoId(url)
                if (youtubeVideoId != null) {
                    return@withContext analyzeYouTubeVideo(youtubeVideoId, url, maxRetries)
                }

                // Notion 링크 확인
                if (isNotionUrl(url)) {
                    return@withContext analyzeNotionPage(url, maxRetries)
                }

                val content = fetchUrlContent(url)
                if (content.isBlank()) {
                    return@withContext AnalysisResult("No title", "Could not fetch content from URL", emptyList(), null)
                }

                // 썸네일 이미지 URL 추출
                val thumbnailUrl = extractThumbnailUrl(content, url)

                // 콘텐츠 길이 제한 (토큰 사용량 절감)
                val truncatedContent = if (content.length > 3000) {
                    content.take(3000) + "..."
                } else {
                    content
                }

                val language = settingsRepository.language.first()
                val languageInstruction = when (language) {
                    "ko" -> "한국어로 답변해주세요."
                    "en" -> "Please respond in English."
                    "ja" -> "日本語で回答してください。"
                    "zh-CN" -> "请用简体中文回答。"
                    "zh-TW" -> "請用繁體中文回答。"
                    "es" -> "Por favor, responde en español."
                    "fr" -> "Veuillez répondre en français."
                    "de" -> "Bitte antworten Sie auf Deutsch."
                    "ru" -> "Пожалуйста, отвечайте на русском языке."
                    "pt" -> "Por favor, responda em português."
                    else -> "Please respond in English."
                }

                val prompt = """
                    $languageInstruction

                    Analyze the following content and provide:
                    1. A short title (one sentence)
                    2. A detailed summary (max 200 words)
                    3. 3 relevant keywords or tags

                    Format the output exactly as:
                    TITLE: [Your title in the specified language]
                    SUMMARY: [Your summary in the specified language]
                    TAGS: [tag1,tag2,tag3]

                    Content: $truncatedContent
                """.trimIndent()

                // 재시도 로직
                var lastException: Exception? = null
                repeat(maxRetries) { attempt ->
                    try {
                        val response = generativeModel!!.generateContent(prompt)
                        return@withContext parseResponse(response, thumbnailUrl)
                    } catch (e: Exception) {
                        lastException = e
                        val errorMessage = e.message ?: ""

                        // 할당량 초과 에러 체크
                        if (errorMessage.contains("quota", ignoreCase = true) || 
                            errorMessage.contains("rate limit", ignoreCase = true)) {

                            // 재시도 지연 시간 추출 (예: "Please retry in 1.81658277s")
                            val retryDelay = extractRetryDelay(errorMessage)

                            if (attempt < maxRetries - 1) {
                                kotlinx.coroutines.delay((retryDelay * 1000).toLong())
                            } else {
                                return@withContext AnalysisResult(
                                    "Quota Exceeded",
                                    "API quota exceeded. Please try again later or upgrade your plan.",
                                    emptyList(),
                                    thumbnailUrl
                                )
                            }
                        } else {
                            // 다른 에러는 즉시 반환
                            throw e
                        }
                    }
                }

                // 모든 재시도 실패
                AnalysisResult("Error", "Failed after $maxRetries attempts: ${lastException?.message}", emptyList(), thumbnailUrl)

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = when {
                    e.message?.contains("quota", ignoreCase = true) == true -> 
                        "API quota exceeded. Please try again later."
                    e.message?.contains("network", ignoreCase = true) == true -> 
                        "Network error. Please check your connection."
                    else -> "Failed to analyze: ${e.message}"
                }
                AnalysisResult("Error", errorMsg, emptyList(), null)
            }
        }
    }

    private fun extractRetryDelay(errorMessage: String): Double {
        // "Please retry in 1.81658277s" 형식에서 숫자 추출
        val regex = """retry in (\d+\.?\d*)s""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(errorMessage)
        return match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 2.0
    }

    private fun parseResponse(response: GenerateContentResponse, thumbnailUrl: String?): AnalysisResult {
        val text = response.text ?: return AnalysisResult("", "Empty response from API", emptyList(), thumbnailUrl)
        val titlePrefix = "TITLE:"
        val summaryPrefix = "SUMMARY:"
        val tagsPrefix = "TAGS:"

        val titleIndex = text.indexOf(titlePrefix)
        val summaryIndex = text.indexOf(summaryPrefix)
        val tagsIndex = text.indexOf(tagsPrefix)

        val title = if (titleIndex != -1) {
            val start = titleIndex + titlePrefix.length
            val end = if (summaryIndex != -1) summaryIndex else text.length
            text.substring(start, end).trim()
        } else {
            "No title"
        }

        val summary = if (summaryIndex != -1) {
            val start = summaryIndex + summaryPrefix.length
            val end = if (tagsIndex != -1) tagsIndex else text.length
            text.substring(start, end).trim()
        } else {
            "Summary not found."
        }

        val tags = if (tagsIndex != -1) {
            val start = tagsIndex + tagsPrefix.length
            text.substring(start).split(",").map { it.trim() }
        } else {
            emptyList()
        }

        return AnalysisResult(title, summary, tags, thumbnailUrl)
    }

    private fun extractThumbnailUrl(htmlContent: String, baseUrl: String): String? {
        try {
            // Open Graph 이미지 추출
            val ogImageRegex = """<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            ogImageRegex.find(htmlContent)?.groupValues?.getOrNull(1)?.let { return resolveUrl(it, baseUrl) }

            // Twitter Card 이미지 추출
            val twitterImageRegex = """<meta\s+name=["']twitter:image["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            twitterImageRegex.find(htmlContent)?.groupValues?.getOrNull(1)?.let { return resolveUrl(it, baseUrl) }

            // 일반 meta image 태그
            val metaImageRegex = """<meta\s+property=["']image["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            metaImageRegex.find(htmlContent)?.groupValues?.getOrNull(1)?.let { return resolveUrl(it, baseUrl) }

            // link rel="image_src"
            val linkImageRegex = """<link\s+rel=["']image_src["']\s+href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            linkImageRegex.find(htmlContent)?.groupValues?.getOrNull(1)?.let { return resolveUrl(it, baseUrl) }

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun resolveUrl(imageUrl: String, baseUrl: String): String {
        return when {
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> imageUrl
            imageUrl.startsWith("//") -> "https:$imageUrl"
            imageUrl.startsWith("/") -> {
                val url = URL(baseUrl)
                "${url.protocol}://${url.host}$imageUrl"
            }
            else -> {
                val url = URL(baseUrl)
                val path = url.path.substringBeforeLast('/')
                "${url.protocol}://${url.host}$path/$imageUrl"
            }
        }
    }

    private fun fetchUrlContent(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        // 더 나은 콘텐츠 접근을 위한 헤더 설정
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ko;q=0.8")
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
        connection.setRequestProperty("Connection", "keep-alive")
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
        connection.instanceFollowRedirects = true

        connection.connect()

        // gzip 압축 응답 처리
        val encoding = connection.contentEncoding
        val rawInputStream = connection.inputStream
        val inputStream = if (encoding?.equals("gzip", ignoreCase = true) == true) {
            java.util.zip.GZIPInputStream(rawInputStream)
        } else {
            rawInputStream
        }

        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        val content = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            content.append(line).append("\n")
        }
        reader.close()
        return content.toString()
    }

    private fun isNotionUrl(url: String): Boolean {
        return url.contains("notion.so", ignoreCase = true) || 
               url.contains("notion.site", ignoreCase = true)
    }

    private fun extractYouTubeVideoId(url: String): String? {
        return try {
            // youtu.be/VIDEO_ID 형식
            val shortFormRegex = """youtu\.be/([a-zA-Z0-9_-]{11})""".toRegex()
            shortFormRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }

            // youtube.com/watch?v=VIDEO_ID 형식
            val longFormRegex = """youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})""".toRegex()
            longFormRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }

            // youtube.com/embed/VIDEO_ID 형식
            val embedRegex = """youtube\.com/embed/([a-zA-Z0-9_-]{11})""".toRegex()
            embedRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getYouTubeThumbnailUrl(videoId: String): String {
        // 고화질 썸네일 우선 시도
        return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
    }

    private suspend fun analyzeYouTubeVideo(videoId: String, originalUrl: String, maxRetries: Int): AnalysisResult {
        return try {
            val thumbnailUrl = getYouTubeThumbnailUrl(videoId)

            // YouTube 페이지에서 메타데이터 가져오기
            val htmlContent = fetchUrlContent("https://www.youtube.com/watch?v=$videoId")
            val videoTitle = extractYouTubeTitle(htmlContent)
            val videoDescription = extractYouTubeDescription(htmlContent)

            val language = settingsRepository.language.first()
            val languageInstruction = when (language) {
                "ko" -> "한국어로 답변해주세요."
                "en" -> "Please respond in English."
                "ja" -> "日本語で回答してください。"
                "zh-CN" -> "请用简体中文回答。"
                "zh-TW" -> "請用繁體中文回答。"
                "es" -> "Por favor, responde en español."
                "fr" -> "Veuillez répondre en français."
                "de" -> "Bitte antworten Sie auf Deutsch."
                "ru" -> "Пожалуйста, отвечайте на русском языке."
                "pt" -> "Por favor, responda em português."
                else -> "Please respond in English."
            }

            val prompt = """
                $languageInstruction

                Analyze the following YouTube video information and provide:
                1. A short title (one sentence)
                2. A detailed summary (max 200 words)
                3. 3 relevant keywords or tags

                Format the output exactly as:
                TITLE: [Your title in the specified language]
                SUMMARY: [Your summary in the specified language]
                TAGS: [tag1,tag2,tag3]

                Video Title: $videoTitle
                Video Description: ${videoDescription.take(1000)}
                Video URL: $originalUrl
            """.trimIndent()

            var lastException: Exception? = null
            repeat(maxRetries) { attempt ->
                try {
                    val response = generativeModel!!.generateContent(prompt)
                    return parseResponse(response, thumbnailUrl)
                } catch (e: Exception) {
                    lastException = e
                    val errorMessage = e.message ?: ""

                    if (errorMessage.contains("quota", ignoreCase = true) || 
                        errorMessage.contains("rate limit", ignoreCase = true)) {
                        val retryDelay = extractRetryDelay(errorMessage)
                        if (attempt < maxRetries - 1) {
                            kotlinx.coroutines.delay((retryDelay * 1000).toLong())
                        } else {
                            return AnalysisResult(
                                "Quota Exceeded",
                                "API quota exceeded. Please try again later.",
                                emptyList(),
                                thumbnailUrl
                            )
                        }
                    } else {
                        throw e
                    }
                }
            }

            AnalysisResult("Error", "Failed after $maxRetries attempts: ${lastException?.message}", emptyList(), thumbnailUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            AnalysisResult("Error", "Failed to analyze YouTube video: ${e.message}", emptyList(), null)
        }
    }

    private fun extractYouTubeTitle(htmlContent: String): String {
        return try {
            val titleRegex = """<meta\s+name=["']title["']\s+content=["']([^"']+)["']""".toRegex()
            val title = titleRegex.find(htmlContent)?.groupValues?.getOrNull(1)
            if (title != null) return title

            val ogTitleRegex = """<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""".toRegex()
            ogTitleRegex.find(htmlContent)?.groupValues?.getOrNull(1) ?: "YouTube Video"
        } catch (e: Exception) {
            "YouTube Video"
        }
    }

    private fun extractYouTubeDescription(htmlContent: String): String {
        return try {
            val descRegex = """<meta\s+name=["']description["']\s+content=["']([^"']+)["']""".toRegex()
            val desc = descRegex.find(htmlContent)?.groupValues?.getOrNull(1)
            if (desc != null) return desc

            val ogDescRegex = """<meta\s+property=["']og:description["']\s+content=["']([^"']+)["']""".toRegex()
            ogDescRegex.find(htmlContent)?.groupValues?.getOrNull(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun analyzeNotionPage(url: String, maxRetries: Int): AnalysisResult {
        return try {
            // 노션 URL 정리 (쿼리 파라미터 제거)
            val cleanUrl = url.split("?").first()

            android.util.Log.d("NotionAnalysis", "Notion page detected: $cleanUrl")

            // Notion 페이지의 HTML 가져오기
            val htmlContent = fetchUrlContent(cleanUrl)
            val doc = Jsoup.parse(htmlContent, cleanUrl)

            // 제목 추출 시도
            var title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:title]")?.attr("content")
                ?: doc.selectFirst("title")?.text()
                ?: ""

            // 제목이 비어있으면 URL에서 추출
            if (title.isBlank()) {
                title = cleanUrl.substringAfterLast("/")
                    .substringBefore("-")
                    .replace("-", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }

            if (title.isBlank()) {
                title = "Notion Page"
            }

            // 썸네일 추출
            val thumbnailUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")

            val language = settingsRepository.language.first()

            // Gemini 호출 없이 간단한 메시지만 반환
            android.util.Log.d("NotionAnalysis", "Returning simple message for Notion page")
            AnalysisResult(
                title = title,
                summary = when (language) {
                    "ko" -> "노션 페이지는 동적 콘텐츠로 제한된 정보만 제공될 수 있습니다."
                    "ja" -> "Notionページは動的コンテンツのため、限られた情報のみ利用可能です。"
                    "zh-CN" -> "Notion页面具有动态内容，可能只能提供有限的信息。"
                    "zh-TW" -> "Notion頁面具有動態內容，可能只能提供有限的資訊。"
                    "es" -> "Las páginas de Notion tienen contenido dinámico. Puede haber información limitada disponible."
                    "fr" -> "Les pages Notion ont du contenu dynamique. Des informations limitées peuvent être disponibles."
                    "de" -> "Notion-Seiten haben dynamische Inhalte. Möglicherweise sind nur begrenzte Informationen verfügbar."
                    "ru" -> "Страницы Notion имеют динамический контент. Может быть доступна ограниченная информация."
                    "pt" -> "As páginas do Notion têm conteúdo dinâmico. Informações limitadas podem estar disponíveis."
                    else -> "Notion pages have dynamic content. Limited information may be available."
                },
                tags = emptyList(),
                thumbnailUrl = thumbnailUrl
            )
        } catch (e: Exception) {
            android.util.Log.e("NotionAnalysis", "Error processing Notion page", e)
            AnalysisResult(
                title = "Notion Page",
                summary = "Failed to load Notion page information.",
                tags = emptyList(),
                thumbnailUrl = null
            )
        }
    }

}
