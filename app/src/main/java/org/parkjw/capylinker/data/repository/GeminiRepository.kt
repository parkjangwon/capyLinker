package org.parkjw.capylinker.data.repository

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class AnalysisResult(
    val title: String,
    val summary: String,
    val tags: List<String>
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
            return AnalysisResult("No title", "API key not set", emptyList())
        }

        return withContext(Dispatchers.IO) {
            try {
                val content = fetchUrlContent(url)
                if (content.isBlank()) {
                    return@withContext AnalysisResult("No title", "Could not fetch content from URL", emptyList())
                }

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
                        return@withContext parseResponse(response)
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
                                    emptyList()
                                )
                            }
                        } else {
                            // 다른 에러는 즉시 반환
                            throw e
                        }
                    }
                }

                // 모든 재시도 실패
                AnalysisResult("Error", "Failed after $maxRetries attempts: ${lastException?.message}", emptyList())

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = when {
                    e.message?.contains("quota", ignoreCase = true) == true -> 
                        "API quota exceeded. Please try again later."
                    e.message?.contains("network", ignoreCase = true) == true -> 
                        "Network error. Please check your connection."
                    else -> "Failed to analyze: ${e.message}"
                }
                AnalysisResult("Error", errorMsg, emptyList())
            }
        }
    }

    private fun extractRetryDelay(errorMessage: String): Double {
        // "Please retry in 1.81658277s" 형식에서 숫자 추출
        val regex = """retry in (\d+\.?\d*)s""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(errorMessage)
        return match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 2.0
    }

    private fun parseResponse(response: GenerateContentResponse): AnalysisResult {
        val text = response.text ?: return AnalysisResult("", "Empty response from API", emptyList())
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

        return AnalysisResult(title, summary, tags)
    }

    private fun fetchUrlContent(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        val inputStream = connection.inputStream
        val reader = BufferedReader(InputStreamReader(inputStream))
        val content = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            content.append(line)
        }
        reader.close()
        return content.toString()
    }
}
