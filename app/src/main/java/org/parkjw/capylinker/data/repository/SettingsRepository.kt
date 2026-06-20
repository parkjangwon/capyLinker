package org.parkjw.capylinker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsCipher: SettingsCipher
) {
    private object PreferencesKeys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val CLIPBOARD_AUTO_ADD = booleanPreferencesKey("clipboard_auto_add")
    }

    val apiKey: Flow<String> = context.dataStore.data
        .map { preferences ->
            decryptApiKey(preferences[PreferencesKeys.GEMINI_API_KEY])
        }

    val geminiModel: Flow<String> = context.dataStore.data
        .map { preferences ->
            normalizeGeminiModel(preferences[PreferencesKeys.GEMINI_MODEL])
        }

    val language: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LANGUAGE] ?: "en"
        }

    val theme: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.THEME] ?: "system"
        }

    val clipboardAutoAdd: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CLIPBOARD_AUTO_ADD] ?: true
        }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_API_KEY] = settingsCipher.encrypt(apiKey)
        }
    }

    suspend fun encryptStoredApiKeyIfNeeded() {
        context.dataStore.edit { preferences ->
            val storedApiKey = preferences[PreferencesKeys.GEMINI_API_KEY]
            if (!storedApiKey.isNullOrBlank() && !settingsCipher.isEncrypted(storedApiKey)) {
                preferences[PreferencesKeys.GEMINI_API_KEY] = settingsCipher.encrypt(storedApiKey)
            }
        }
    }

    suspend fun saveGeminiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_MODEL] = model
        }
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }

    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme
        }
    }

    suspend fun saveClipboardAutoAdd(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIPBOARD_AUTO_ADD] = enabled
        }
    }

    suspend fun getAllSettings(): Map<String, Any> {
        val preferences = context.dataStore.data.first()
        return mapOf(
            "apiKey" to decryptApiKey(preferences[PreferencesKeys.GEMINI_API_KEY]),
            "geminiModel" to normalizeGeminiModel(preferences[PreferencesKeys.GEMINI_MODEL]),
            "language" to (preferences[PreferencesKeys.LANGUAGE] ?: "en"),
            "theme" to (preferences[PreferencesKeys.THEME] ?: "system"),
            "clipboardAutoAdd" to (preferences[PreferencesKeys.CLIPBOARD_AUTO_ADD] ?: true)
        )
    }

    suspend fun restoreAllSettings(settings: Map<String, Any>) {
        context.dataStore.edit { preferences ->
            (settings["apiKey"] as? String)?.let { preferences[PreferencesKeys.GEMINI_API_KEY] = settingsCipher.encrypt(it) }
            (settings["geminiModel"] as? String)?.let { preferences[PreferencesKeys.GEMINI_MODEL] = it }
            (settings["language"] as? String)?.let { preferences[PreferencesKeys.LANGUAGE] = it }
            (settings["theme"] as? String)?.let { preferences[PreferencesKeys.THEME] = it }
            (settings["clipboardAutoAdd"] as? Boolean)?.let { preferences[PreferencesKeys.CLIPBOARD_AUTO_ADD] = it }
        }
    }

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
        val SUPPORTED_GEMINI_MODELS = setOf(
            "gemini-3.1-flash-lite",
            DEFAULT_GEMINI_MODEL
        )
    }

    private fun normalizeGeminiModel(model: String?): String {
        return model?.takeIf { it in SUPPORTED_GEMINI_MODELS } ?: DEFAULT_GEMINI_MODEL
    }

    private fun decryptApiKey(storedValue: String?): String {
        return settingsCipher.decrypt(storedValue)
    }
}
