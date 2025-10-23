package org.parkjw.capylinker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.AndroidEntryPoint
import org.parkjw.capylinker.data.repository.SettingsRepository
import org.parkjw.capylinker.ui.AppNavHost
import org.parkjw.capylinker.ui.theme.CapyLinkerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this) { }

        setContent {
            val theme by settingsRepository.theme.collectAsState(initial = "system")
            val systemDarkTheme = isSystemInDarkTheme()

            val darkTheme = when (theme) {
                "light" -> false
                "dark" -> true
                else -> systemDarkTheme
            }

            CapyLinkerTheme(darkTheme = darkTheme) {
                Scaffold {
                    Column {
                        AppNavHost(
                            modifier = Modifier.weight(1f)
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { context ->
                                val adView = AdView(context)
                                adView.adUnitId = "ca-app-pub-5102109520705013/6345460267"

                                val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                                    context,
                                    (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
                                )
                                adView.setAdSize(adSize)
                                adView.loadAd(AdRequest.Builder().build())
                                adView
                            }
                        )
                    }
                }
            }
        }
    }
}
