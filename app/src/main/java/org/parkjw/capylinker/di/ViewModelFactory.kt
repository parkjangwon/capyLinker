package org.parkjw.capylinker.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.parkjw.capylinker.data.local.AppDatabase
import org.parkjw.capylinker.data.repository.GeminiRepository
import org.parkjw.capylinker.data.repository.LinkRepository
import org.parkjw.capylinker.data.repository.SettingsRepository
import org.parkjw.capylinker.viewmodel.LinkReceiverViewModel
import org.parkjw.capylinker.viewmodel.MainViewModel
import org.parkjw.capylinker.viewmodel.SettingsViewModel

class ViewModelFactory(private val context: Context, private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(db.linkDao()) as T
            }
            modelClass.isAssignableFrom(LinkReceiverViewModel::class.java) -> {
                LinkReceiverViewModel(context.applicationContext as Application) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(SettingsRepository(context)) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
