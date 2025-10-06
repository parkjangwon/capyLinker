package org.parkjw.capylinker.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.parkjw.capylinker.data.repository.GeminiRepository
import org.parkjw.capylinker.data.repository.LinkRepository
import javax.inject.Inject

@AndroidEntryPoint
class LinkAnalysisService : Service() {
    @Inject
    lateinit var geminiRepository: GeminiRepository

    @Inject
    lateinit var linkRepository: LinkRepository

    override fun onCreate() {
        super.onCreate()
        Log.d("LinkAnalysisService", "Service created")
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LinkAnalysisService = this@LinkAnalysisService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LinkAnalysisService", "onStartCommand called")

        intent?.getStringExtra("URL")?.let { url ->
            Log.d("LinkAnalysisService", "Received URL: $url")
            analyzeAndSaveLink(url)
        } ?: run {
            Log.e("LinkAnalysisService", "No URL in intent")
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun analyzeAndSaveLink(url: String) {
        serviceScope.launch {
            try {
                Log.d("LinkAnalysis", "Analyzing URL with Gemini: $url")
                val analysisResult = withContext(Dispatchers.IO) {
                    geminiRepository.analyzeUrl(url)
                }

                Log.d("LinkAnalysis", "Analysis completed, result: $analysisResult")

                val title = analysisResult?.title ?: url
                val summary = analysisResult?.summary ?: "Could not analyze URL."
                val tags = analysisResult?.tags ?: emptyList()

                withContext(Dispatchers.IO) {
                    linkRepository.insertLink(url, title, summary, tags)
                }
                Log.d("LinkAnalysis", "Successfully saved link with title: $title")
            } catch (e: Exception) {
                Log.e("LinkAnalysis", "Error processing link", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LinkAnalysisService", "Service destroyed")
    }
}
