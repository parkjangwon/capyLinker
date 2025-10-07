package org.parkjw.capylinker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.parkjw.capylinker.data.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val apiKey: Flow<String> = settingsRepository.apiKey
    val geminiModel: Flow<String> = settingsRepository.geminiModel
    val language: Flow<String> = settingsRepository.language
    val theme: Flow<String> = settingsRepository.theme
    val clipboardAutoAdd: Flow<Boolean> = settingsRepository.clipboardAutoAdd

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.saveApiKey(key)
        }
    }

    fun saveGeminiModel(model: String) {
        viewModelScope.launch {
            settingsRepository.saveGeminiModel(model)
        }
    }

    fun saveLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.saveLanguage(language)
        }
    }

    fun saveTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.saveTheme(theme)
        }
    }

    fun setClipboardAutoAdd(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveClipboardAutoAdd(enabled)
        }
    }
}
