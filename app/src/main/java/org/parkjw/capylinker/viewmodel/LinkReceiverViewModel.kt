package org.parkjw.capylinker.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import org.parkjw.capylinker.service.LinkAnalysisService
import javax.inject.Inject

class LinkReceiverViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    fun saveLink(url: String?) {
        if (url.isNullOrBlank()) {
            Log.d("LinkReceiver", "URL is null or blank")
            return
        }

        Log.d("LinkReceiverViewModel", "Starting service to save URL: $url")

        // 서비스를 시작하고 URL을 Intent로 전달
        Intent(getApplication<Application>(), LinkAnalysisService::class.java).also { intent ->
            intent.putExtra("URL", url)
            getApplication<Application>().startService(intent)
        }
    }
}
