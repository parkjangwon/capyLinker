package org.parkjw.capylinker.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiApiClient @Inject constructor() {
    suspend fun generateContent(
        apiKey: String,
        modelName: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val connection = URL("$BASE_URL/$modelName:generateContent").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("x-goog-api-key", apiKey)

        connection.outputStream.use { stream ->
            stream.write(buildRequestBody(prompt).toByteArray(Charsets.UTF_8))
        }

        val statusCode = connection.responseCode
        val responseBody = readBody(connection, statusCode)
        if (statusCode !in 200..299) {
            throw IOException("Gemini API $statusCode: ${responseBody.take(500)}")
        }

        parseText(responseBody)
    }

    private fun buildRequestBody(prompt: String): String {
        val part = JSONObject().put("text", prompt)
        val content = JSONObject().put("parts", JSONArray().put(part))
        return JSONObject().put("contents", JSONArray().put(content)).toString()
    }

    private fun readBody(connection: HttpURLConnection, statusCode: Int): String {
        val input = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        if (input == null) return ""
        return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
    }

    private fun parseText(responseBody: String): String {
        val root = JSONObject(responseBody)
        val parts = root
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        return parts
            ?.let { indices ->
                buildString {
                    for (index in 0 until indices.length()) {
                        val text = indices.optJSONObject(index)?.optString("text").orEmpty()
                        if (text.isNotBlank()) append(text)
                    }
                }
            }
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Empty response from Gemini API")
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}
