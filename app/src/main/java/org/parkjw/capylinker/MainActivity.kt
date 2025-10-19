package org.parkjw.capylinker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                    AppNavHost(Modifier.padding(it))
                }
            }
        }
    }
}
