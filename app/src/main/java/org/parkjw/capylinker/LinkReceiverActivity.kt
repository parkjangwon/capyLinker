package org.parkjw.capylinker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.parkjw.capylinker.data.repository.SettingsRepository
import org.parkjw.capylinker.viewmodel.LinkReceiverViewModel
import javax.inject.Inject

@AndroidEntryPoint
class LinkReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val viewModel: LinkReceiverViewModel by viewModels()

    private fun extractUrl(text: String?): String? {
        if (text == null) return null

        // URL 패턴 찾기
        val urlPattern = """https?://[^\s]+""".toRegex()
        val match = urlPattern.find(text)
        return match?.value ?: text.takeIf { it.startsWith("http") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("LinkReceiver", "=== LinkReceiverActivity onCreate ===")
        android.util.Log.d("LinkReceiver", "Action: ${intent?.action}")
        android.util.Log.d("LinkReceiver", "Type: ${intent?.type}")

        try {
            android.util.Log.d("LinkReceiver", "Extras keys: ${intent?.extras?.keySet()?.joinToString()}")
            intent?.extras?.keySet()?.forEach { key ->
                android.util.Log.d("LinkReceiver", "Extra[$key]: ${intent.extras?.get(key)}")
            }
        } catch (e: Exception) {
            android.util.Log.e("LinkReceiver", "Error reading extras", e)
        }

        if (intent?.action == Intent.ACTION_SEND) {
            android.util.Log.d("LinkReceiver", "Intent action is SEND")

            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            android.util.Log.d("LinkReceiver", "EXTRA_TEXT: $sharedText")

            // URL 추출
            val sharedUrl = extractUrl(sharedText)
            android.util.Log.d("LinkReceiver", "Extracted URL: $sharedUrl")

            if (sharedUrl != null) {
                // 언어 설정에 따른 메시지 표시
                lifecycleScope.launch {
                    val language = settingsRepository.language.first()
                    val message = if (language == "ko") {
                        "링크 저장 중..."
                    } else {
                        "Saving link..."
                    }
                    android.widget.Toast.makeText(
                        this@LinkReceiverActivity,
                        message,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

                // 링크 저장
                viewModel.saveLink(sharedUrl)
                android.util.Log.d("LinkReceiver", "Save link called, finishing activity")

                finish()
            } else {
                android.util.Log.e("LinkReceiver", "No valid URL found")
                android.widget.Toast.makeText(this, "No valid URL found", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            android.util.Log.e("LinkReceiver", "Intent action is not SEND: ${intent?.action}")
            finish()
        }
    }
}
