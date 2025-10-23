package org.parkjw.capylinker.data.repository

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
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
    private val settingsRepository: SettingsRepository,
    private val webPageContentFetcher: WebPageContentFetcher
) {
    private var generativeModel: GenerativeModel? = null

    private suspend fun initializeModel() {
        val apiKey = settingsRepository.apiKey.first()
        val modelName = settingsRepository.geminiModel.first()
        if (apiKey.isNotBlank()) {
            generativeModel = GenerativeModel(
                modelName = modelName,
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

                // GitHub 리포지토리 확인
                val githubRepoInfo = extractGitHubRepoInfo(url)
                if (githubRepoInfo != null) {
                    return@withContext analyzeGitHubRepo(githubRepoInfo.first, githubRepoInfo.second, url, maxRetries)
                }

                // Reddit 링크 확인
                if (isRedditUrl(url)) {
                    return@withContext analyzeRedditPost(url, maxRetries)
                }

                // 다이나믹 링크 확인
                if (isDynamicContentUrl(url)) {
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

    private fun isDynamicContentUrl(url: String): Boolean {
        return url.contains("notion.so", ignoreCase = true) ||
               url.contains("notion.site", ignoreCase = true) ||
               url.contains("blog.naver.com", ignoreCase = true) ||
               url.contains("cafe.naver.com", ignoreCase = true) ||
               url.contains("brunch.co.kr", ignoreCase = true) ||
               url.contains("spotify.com", ignoreCase = true) ||
               url.contains("x.com", ignoreCase = true) ||
               url.contains("twitter.com", ignoreCase = true)
    }

    private fun isGitHubUrl(url: String): Boolean {
        return url.contains("github.com", ignoreCase = true)
    }

    private fun extractGitHubRepoInfo(url: String): Pair<String, String>? {
        return try {
            // https://github.com/owner/repo 형식
            val regex = """github\.com/([^/]+)/([^/?#]+)""".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(url) ?: return null
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]

            // owner나 repo가 비어있거나 특수 경로인 경우 제외
            if (owner.isBlank() || repo.isBlank() || 
                owner in listOf("features", "topics", "trending", "collections", "events", "explore", "marketplace", "pricing", "sponsors", "settings", "security", "about")) {
                return null
            }

            Pair(owner, repo)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchGitHubReadme(owner: String, repo: String): String? {
        return try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/readme"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // GitHub API 헤더 설정
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "CapyLinker-Android-App")

            if (connection.responseCode != 200) {
                android.util.Log.e("GitHubAPI", "Failed to fetch README: ${connection.responseCode}")
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
            val jsonResponse = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                jsonResponse.append(line)
            }
            reader.close()

            // JSON 파싱하여 content 필드 추출
            val jsonString = jsonResponse.toString()
            val contentRegex = """"content"\s*:\s*"([^"]+)"""".toRegex()
            val contentMatch = contentRegex.find(jsonString)
            val base64Content = contentMatch?.groupValues?.getOrNull(1) ?: return null

            // Base64 디코딩 (줄바꿈 제거 후)
            val cleanBase64 = base64Content.replace("\\n", "")
            val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("GitHubAPI", "Error fetching GitHub README", e)
            null
        }
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

    private suspend fun analyzeGitHubRepo(owner: String, repo: String, originalUrl: String, maxRetries: Int): AnalysisResult {
        return try {
            android.util.Log.d("GitHubAnalysis", "GitHub repository detected: $owner/$repo")

            // GitHub README 내용 가져오기
            val readmeContent = fetchGitHubReadme(owner, repo)

            if (readmeContent == null) {
                android.util.Log.e("GitHubAnalysis", "Failed to fetch README")
                return AnalysisResult(
                    title = "$owner/$repo",
                    summary = "Failed to fetch README from GitHub repository.",
                    tags = listOf("github", "repository"),
                    thumbnailUrl = "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"
                )
            }

            // README 내용이 너무 길면 자르기
            val truncatedReadme = if (readmeContent.length > 3000) {
                readmeContent.take(3000) + "..."
            } else {
                readmeContent
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

                Analyze the following GitHub repository README and provide:
                1. A short title that describes the project (one sentence)
                2. A detailed summary of what this project does (max 200 words)
                3. 3 relevant keywords or tags related to the project

                Format the output exactly as:
                TITLE: [Your title in the specified language]
                SUMMARY: [Your summary in the specified language]
                TAGS: [tag1,tag2,tag3]

                Repository: $owner/$repo
                URL: $originalUrl

                README Content:
                $truncatedReadme
            """.trimIndent()

            var lastException: Exception? = null
            repeat(maxRetries) { attempt ->
                try {
                    val response = generativeModel!!.generateContent(prompt)
                    return parseResponse(response, "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png")
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
                                "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"
                            )
                        }
                    } else {
                        throw e
                    }
                }
            }

            AnalysisResult("Error", "Failed after $maxRetries attempts: ${lastException?.message}", emptyList(), null)
        } catch (e: Exception) {
            android.util.Log.e("GitHubAnalysis", "Error analyzing GitHub repository", e)
            AnalysisResult(
                title = "GitHub Repository",
                summary = "Failed to analyze GitHub repository: ${e.message}",
                tags = listOf("github", "error"),
                thumbnailUrl = null
            )
        }
    }

    private suspend fun analyzeNotionPage(url: String, maxRetries: Int): AnalysisResult {
        return try {
            // URL 정리 (?source 등 제거). Notion은 SSR 힌트(pvs=4) 적용
            val cleanUrl = url.substringBefore("?")
            val isNotionDomain = cleanUrl.contains("notion.so", ignoreCase = true) ||
                cleanUrl.contains("notion.site", ignoreCase = true)
            val targetUrl = if (isNotionDomain) "$cleanUrl?pvs=4" else cleanUrl

            android.util.Log.d("NotionAnalysis", "Dynamic page detected: $targetUrl")

            // 0) r.jina.ai 가독화 텍스트 우선 시도 (가장 안정적으로 본문을 반환함)
            val jinaPrimary = fetchReadableContentViaJina(targetUrl)?.trim().orEmpty()
            val jinaText = if (jinaPrimary.isNotBlank()) jinaPrimary
            else fetchReadableContentViaJina(cleanUrl)?.trim().orEmpty()

            var bodyText = ""
            var doc: org.jsoup.nodes.Document? = null

            if (jinaText.isNotBlank()) {
                bodyText = jinaText
                // OG 메타 추출을 위해 가볍게 HTML도 시도 (실패해도 무시)
                try {
                    val html = fetchUrlContent(targetUrl)
                    doc = Jsoup.parse(html, targetUrl)
                } catch (_: Exception) { }
            } else {
                // 1) WebView 렌더링 결과 확보 (대기/타임아웃을 약간 늘림)
                val renderedHtml = try {
                    webPageContentFetcher.getFullPageHtml(
                        targetUrl,
                        waitAfterLoadMs = 1800L,
                        timeoutMs = 20000L
                    ) ?: ""
                } catch (e: Exception) {
                    android.util.Log.e("NotionAnalysis", "WebView render failed", e)
                    ""
                }

                // 2) 실패 시 정적 fetch 보조
                val htmlToUse = if (renderedHtml.isNotBlank()) renderedHtml else fetchUrlContent(targetUrl)
                doc = Jsoup.parse(htmlToUse, targetUrl)

                // 3) 본문 추출 파이프라인 (DOM → __NEXT_DATA__ → public recordMap → 좁은 셀렉터 → body)
                bodyText = extractTextFromNotionDom(doc)
                if (bodyText.isBlank()) {
                    bodyText = extractTextFromNextData(doc) ?: ""
                }
                if (bodyText.isBlank()) {
                    val pageId = extractNotionPageId(cleanUrl)
                    if (pageId != null) {
                        val recordMap = fetchNotionPublicRecordMap(pageId)
                        if (recordMap != null) {
                            val fromMap = flattenNotionBlocks(recordMap).trim()
                            if (fromMap.isNotBlank()) bodyText = fromMap
                        }
                    }
                }
                if (bodyText.isBlank()) {
                    val narrow = doc.select(
                        ".notion-page-content h1, .notion-page-content h2, .notion-page-content h3, " +
                            ".notion-page-content p, .notion-page-content li, [data-block-id]"
                    ).eachText().joinToString("\n") { it.trim() }.trim()
                    if (narrow.isNotBlank()) bodyText = narrow
                }
                if (bodyText.isBlank()) {
                    bodyText = doc.body()?.text()?.trim().orEmpty()
                }
            }

            // 제목/썸네일
            var title = doc?.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc?.selectFirst("meta[name=twitter:title]")?.attr("content")
                ?: doc?.selectFirst("title")?.text()
                ?: ""
            if (title.isBlank()) {
                // 본문 첫 줄을 보조 타이틀로 사용
                title = bodyText.lineSequence().firstOrNull { it.isNotBlank() }?.take(80)?.trim().orElse("")
            }
            if (title.isBlank()) title = "Web Page"

            val thumbnailUrl = doc?.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc?.selectFirst("meta[name=twitter:image]")?.attr("content")

            // 본문을 여전히 못 얻었다면 안내 반환
            if (bodyText.isBlank()) {
                val language = settingsRepository.language.first()
                val msg = when (language) {
                    "ko" -> "페이지의 본문을 가져오지 못했습니다. 브라우저에서 열어 확인해주세요."
                    "ja" -> "ページの本文を取得できませんでした。ブラウザで開いて確認してください。"
                    "zh-CN" -> "未能获取页面的正文。请在浏览器中查看。"
                    "zh-TW" -> "未能取得頁面的內文。請在瀏覽器中查看。"
                    "es" -> "No se pudo obtener el contenido de la página. Ábrela en el navegador."
                    "fr" -> "Impossible d’obtenir le contenu de la page. Ouvrez-la dans le navigateur."
                    "de" -> "Der Seiteninhalt konnte nicht abgerufen werden. Bitte im Browser prüfen."
                    "ru" -> "Не удалось получить содержимое страницы. Откройте в браузере."
                    "pt" -> "Não foi possível obter o conteúdo da página. Abra no navegador."
                    else -> "Could not fetch the page content. Please open it in the browser."
                }
                return AnalysisResult(title = title, summary = msg, tags = emptyList(), thumbnailUrl = thumbnailUrl)
            }

            // 길이 제한 후 요약
            val truncated = if (bodyText.length > 3500) bodyText.take(3500) + "..." else bodyText

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

                Analyze the following page content and provide:
                1. A short title (one sentence)
                2. A detailed summary (max 200 words)
                3. 3 relevant keywords or tags

                Format the output exactly as:
                TITLE: [Your title in the specified language]
                SUMMARY: [Your summary in the specified language]
                TAGS: [tag1,tag2,tag3]

                Page Title: $title
                Content:
                $truncated
                Source URL: $cleanUrl
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

            AnalysisResult(
                "Error",
                "Failed after $maxRetries attempts: ${lastException?.message}",
                emptyList(),
                thumbnailUrl
            )
        } catch (e: Exception) {
            android.util.Log.e("NotionAnalysis", "Error processing Notion page", e)
            AnalysisResult(
                title = "Notion Page",
                summary = "Failed to analyze Notion page: ${e.message}",
                tags = emptyList(),
                thumbnailUrl = null
            )
        }
    }

    // Kotlin String 보조 확장
    private fun String?.orElse(fallback: String): String = if (this.isNullOrBlank()) fallback else this

    // DOM 기반 Notion 텍스트 추출
    private fun extractTextFromNotionDom(doc: org.jsoup.nodes.Document): String {
        return try {
            val chunks = mutableListOf<String>()

            // 주요 SSR 컨테이너
            doc.select("main, article, .notion-page-content, .notion-frame, .notion, .notion-app-inner").forEach { el ->
                val t = el.text().trim()
                if (t.isNotBlank()) chunks += t
            }

            // 헤딩/문단/인용/목록 등 일반 구조
            if (chunks.isEmpty()) {
                val structured = doc.select(
                    "h1,h2,h3,h4,[role=heading],[role=paragraph],p,blockquote,ul,ol,li," +
                        ".notion-text,.notion-quote,.notion-numbered_list,.notion-bulleted_list,.notion-code"
                ).eachText().map { it.trim() }.filter { it.isNotBlank() }
                if (structured.isNotEmpty()) chunks += structured.joinToString("\n")
            }

            // Notion 블록 속성
            if (chunks.isEmpty()) {
                val blockTexts = doc.select("[data-block-id]").map { it.text().trim() }.filter { it.isNotBlank() }
                if (blockTexts.isNotEmpty()) chunks += blockTexts.joinToString("\n")
            }

            // 최후의 수단: body 전체
            if (chunks.isEmpty()) {
                val body = doc.body()?.text()?.trim().orEmpty()
                if (body.isNotBlank()) chunks += body
            }

            chunks
                .flatMap { it.lines() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
        } catch (e: Exception) {
            ""
        }
    }

    // __NEXT_DATA__ JSON에서 텍스트 추출
    private fun extractTextFromNextData(doc: org.jsoup.nodes.Document): String? {
        return try {
            val script = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: return null
            val root = JSONObject(script)
            val recordMap = root
                .optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("recordMap")
                ?: return null
            val flattened = flattenNotionBlocks(recordMap).trim()
            if (flattened.isBlank()) null else flattened
        } catch (e: Exception) {
            null
        }
    }

    // Notion recordMap.block을 순회하며 텍스트 평탄화
    private fun flattenNotionBlocks(recordMap: JSONObject): String {
        return try {
            val block = recordMap.optJSONObject("block") ?: return ""
            val keys = block.keys()
            val texts = mutableListOf<String>()

            while (keys.hasNext()) {
                val key = keys.next()
                val wrapper = block.optJSONObject(key) ?: continue
                val value = wrapper.optJSONObject("value") ?: continue
                val type = value.optString("type")

                val props = value.optJSONObject("properties")
                var line = ""

                // 대표적으로 title 속성에 텍스트가 존재하는 경우가 많음
                line = line.ifBlank { flattenRichText(props?.optJSONArray("title")) }

                // 캡션 등 보조 텍스트
                if (line.isBlank()) {
                    line = flattenRichText(props?.optJSONArray("caption"))
                }

                // 코드 블록도 title에 텍스트가 담기는 경우가 많음
                if (line.isNotBlank()) {
                    texts += line
                } else {
                    // 타입 기반 힌트 (헤더/목록 등도 title 사용)
                    if (type.contains("header") || type.contains("list") || type.contains("quote") || type.contains("paragraph")) {
                        val t = flattenRichText(props?.optJSONArray("title"))
                        if (t.isNotBlank()) texts += t
                    }
                }
            }

            texts
                .flatMap { it.lines() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
        } catch (e: Exception) {
            ""
        }
    }

    // 노션 공유 URL에서 pageId(UUID) 추출
    private fun extractNotionPageId(url: String): String? {
        return try {
            val hyphenRegex = """([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""".toRegex()
            val compactRegex = """([0-9a-fA-F]{32})""".toRegex()
            val hyphenMatch = hyphenRegex.find(url)?.groupValues?.getOrNull(1)
            val compactMatch = compactRegex.find(url)?.groupValues?.getOrNull(1)
            val raw = hyphenMatch ?: compactMatch ?: return null
            raw.replace("-", "").lowercase()
        } catch (e: Exception) {
            null
        }
    }

    private fun hyphenatePageId(id32: String): String {
        val clean = id32.replace("-", "").lowercase()
        return if (clean.length == 32) {
            "${clean.substring(0,8)}-${clean.substring(8,12)}-${clean.substring(12,16)}-${clean.substring(16,20)}-${clean.substring(20)}"
        } else clean
    }

    // 퍼블릭 Notion recordMap 가져오기 (비공식)
    private fun fetchNotionPublicRecordMap(pageId: String): JSONObject? {
        // 순차적으로 여러 엔드포인트/형식을 시도
        val id32 = pageId.replace("-", "").lowercase()
        val candidates = listOf(
            Pair("https://www.notion.so/api/v3/getPublicPageData", """{"pageId":"$id32"}"""),
            Pair("https://www.notion.so/api/v3/getPublicPageData", """{"pageId":"${hyphenatePageId(id32)}"}"""),
            Pair("https://www.notion.so/api/v3/loadCachedPageChunk", """{"pageId":"$id32","limit":100,"chunkNumber":0,"verticalColumns":false}""")
        )

        for ((endpoint, payload) in candidates) {
            val root = postJson(endpoint, payload) ?: continue
            val recordMap = root.optJSONObject("recordMap")
                ?: root.optJSONObject("data")?.optJSONObject("recordMap")
            if (recordMap != null) {
                return recordMap
            }
        }
        return null
    }

    // JSON POST 유틸
    private fun postJson(urlStr: String, body: String): JSONObject? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CapyLinker/1.0 (Android; NotionPublicAPI)")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ko;q=0.8")

            conn.connect()
            val writer = java.io.OutputStreamWriter(conn.outputStream, Charsets.UTF_8)
            writer.write(body)
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code !in 200..299) {
                android.util.Log.e("NotionAPI", "POST $urlStr failed: $code")
                return null
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            JSONObject(sb.toString())
        } catch (e: Exception) {
            android.util.Log.e("NotionAPI", "Error POST $urlStr", e)
            null
        }
    }

    // Notion RichText(JSONArray of Arrays) 단순 평탄화
    private fun flattenRichText(arr: JSONArray?): String {
        if (arr == null) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val span = arr.optJSONArray(i) ?: continue
            val first = span.optString(0)
            if (first.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(first)
            }
        }
        return sb.toString().trim()
    }

    private fun isRedditUrl(url: String): Boolean {
        return url.contains("reddit.com", ignoreCase = true) &&
               url.contains("/comments/", ignoreCase = true)
    }

    private fun buildRedditJsonUrl(originalUrl: String): String {
        val clean = originalUrl.substringBefore("?").removeSuffix("/")
        return "$clean.json?raw_json=1"
    }

    private fun fetchRedditPostJson(jsonUrl: String): String? {
        return try {
            val url = URL(jsonUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            // Reddit은 명시적인 User-Agent를 요구함
            connection.setRequestProperty("User-Agent", "CapyLinker/1.0 (Android; RedditPostSummary)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ko;q=0.8")
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != 200) {
                android.util.Log.e("RedditAPI", "Failed to fetch Reddit JSON: ${connection.responseCode}")
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val content = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                content.append(line)
            }
            reader.close()
            content.toString()
        } catch (e: Exception) {
            android.util.Log.e("RedditAPI", "Error fetching Reddit JSON", e)
            null
        }
    }

    private suspend fun analyzeRedditPost(url: String, maxRetries: Int): AnalysisResult {
        return try {
            val jsonUrl = buildRedditJsonUrl(url)
            val jsonString = fetchRedditPostJson(jsonUrl)
            if (jsonString.isNullOrBlank()) {
                return AnalysisResult(
                    title = "Reddit Post",
                    summary = "Failed to fetch Reddit post content.",
                    tags = listOf("reddit"),
                    thumbnailUrl = null
                )
            }

            val jsonArray = JSONArray(jsonString)
            if (jsonArray.length() == 0) {
                return AnalysisResult(
                    title = "Reddit Post",
                    summary = "No data in Reddit response.",
                    tags = listOf("reddit"),
                    thumbnailUrl = null
                )
            }

            val listing = jsonArray.getJSONObject(0)
            val children = listing.getJSONObject("data").getJSONArray("children")
            if (children.length() == 0) {
                return AnalysisResult(
                    title = "Reddit Post",
                    summary = "No post found in Reddit response.",
                    tags = listOf("reddit"),
                    thumbnailUrl = null
                )
            }

            val postData = children.getJSONObject(0).getJSONObject("data")
            val postTitle = postData.optString("title").ifBlank { "Reddit Post" }
            val subreddit = postData.optString("subreddit")
            val author = postData.optString("author")

            var body = postData.optString("selftext")
            if (body.isBlank()) {
                val selftextHtml = postData.optString("selftext_html")
                if (selftextHtml.isNotBlank()) {
                    body = Jsoup.parse(selftextHtml).text()
                }
            }

            // 썸네일 추출
            var thumbnailUrl: String? = null
            postData.optJSONObject("preview")?.let { preview ->
                val images = preview.optJSONArray("images")
                if (images != null && images.length() > 0) {
                    val source = images.getJSONObject(0).optJSONObject("source")
                    val urlStr = source?.optString("url")
                    if (!urlStr.isNullOrBlank()) {
                        thumbnailUrl = urlStr.replace("&amp;", "&")
                    }
                }
            }
            if (thumbnailUrl == null) {
                val thumb = postData.optString("thumbnail")
                if (thumb.startsWith("http")) {
                    thumbnailUrl = thumb
                }
            }

            val truncatedBody = if (body.length > 3000) body.take(3000) + "..." else body

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

                Analyze the following Reddit post and provide:
                1. A short title (one sentence)
                2. A detailed summary (max 200 words)
                3. 3 relevant keywords or tags

                Format the output exactly as:
                TITLE: [Your title in the specified language]
                SUMMARY: [Your summary in the specified language]
                TAGS: [tag1,tag2,tag3]

                Subreddit: r/$subreddit
                Author: u/$author
                Post Title: $postTitle
                Post Body: $truncatedBody
                Post URL: $url
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

            AnalysisResult(
                "Error",
                "Failed after $maxRetries attempts: ${lastException?.message}",
                emptyList(),
                thumbnailUrl
            )
        } catch (e: Exception) {
            android.util.Log.e("RedditAnalysis", "Error analyzing Reddit post", e)
            AnalysisResult(
                title = "Reddit Post",
                summary = "Failed to analyze Reddit post: ${e.message}",
                tags = listOf("reddit", "error"),
                thumbnailUrl = null
            )
        }
    }

    // 외부 프록시(r.jina.ai)를 사용해 SSR된 가독화 텍스트 가져오기
    private fun fetchReadableContentViaJina(originalUrl: String): String? {
        return try {
            val proxyUrl = "https://r.jina.ai/$originalUrl"
            val url = URL(proxyUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true

            // 텍스트 가독화 응답
            connection.setRequestProperty("User-Agent", "CapyLinker/1.0 (Android; NotionReadable)")
            connection.setRequestProperty("Accept", "text/plain, */*;q=0.8")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ko;q=0.8")

            connection.connect()
            if (connection.responseCode != 200) {
                android.util.Log.e("JinaReadable", "Failed: ${connection.responseCode}")
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            reader.close()
            sb.toString().trim()
        } catch (e: Exception) {
            android.util.Log.e("JinaReadable", "Error fetching readable content", e)
            null
        }
    }

}
