package org.parkjw.capylinker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.parkjw.capylinker.R
import org.parkjw.capylinker.data.repository.GeminiRepository
import org.parkjw.capylinker.data.repository.LinkRepository
import javax.inject.Inject

@AndroidEntryPoint
class LinkAnalysisService : Service() {
    @Inject
    lateinit var geminiRepository: GeminiRepository

    @Inject
    lateinit var linkRepository: LinkRepository

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

        createNotificationChannel()
        val notification = createNotification("Analyzing link...")
        startForeground(NOTIFICATION_ID, notification)

        intent?.getStringExtra("URL")?.let { url ->
            Log.d("LinkAnalysisService", "Received URL: $url")
            analyzeAndSaveLink(url, startId)
        } ?: run {
            Log.e("LinkAnalysisService", "No URL in intent")
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun analyzeAndSaveLink(url: String, startId: Int) {
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
                val thumbnailUrl = analysisResult?.thumbnailUrl

                withContext(Dispatchers.IO) {
                    linkRepository.insertLink(url, title, summary, tags, thumbnailUrl)
                }
                Log.d("LinkAnalysis", "Successfully saved link with title: $title")
            } catch (e: Exception) {
                Log.e("LinkAnalysis", "Error processing link", e)
            } finally {
                stopForeground(true)
                stopSelf(startId)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Link Analysis"
            val descriptionText = "Notifications for link analysis status"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CapyLinker")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher) // 실제 앱에 맞는 아이콘으로 변경해야 합니다.
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LinkAnalysisService", "Service destroyed")
    }

    companion object {
        private const val CHANNEL_ID = "LinkAnalysisChannel"
        private const val NOTIFICATION_ID = 1
    }
}
