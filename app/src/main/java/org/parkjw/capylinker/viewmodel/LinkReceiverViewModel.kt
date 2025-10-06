package org.parkjw.capylinker.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.internal.Contexts.getApplication
import org.parkjw.capylinker.service.LinkAnalysisService
import javax.inject.Inject

class LinkReceiverViewModel @Inject constructor(
    private val context: Application
) : AndroidViewModel(context) {

    fun saveLink(url: String?) {
        if (url.isNullOrBlank()) {
            Log.d("LinkReceiver", "URL is null or blank")
            return
        }

        Log.d("LinkReceiverViewModel", "Starting service to save URL: $url")

        // 서비스를 시작하고 URL을 Intent로 전달
        Intent(getApplication(), LinkAnalysisService::class.java).also { intent ->
            intent.putExtra("URL", url)
            getApplication<Application>().startService(intent)
        }
    }
}
