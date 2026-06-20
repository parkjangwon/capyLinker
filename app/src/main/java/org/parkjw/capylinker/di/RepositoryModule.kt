package org.parkjw.capylinker.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.parkjw.capylinker.data.repository.GeminiApiClient
import org.parkjw.capylinker.data.repository.GeminiRepository
import org.parkjw.capylinker.data.repository.SettingsCipher
import org.parkjw.capylinker.data.repository.SettingsRepository
import org.parkjw.capylinker.data.repository.WebPageContentFetcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        settingsCipher: SettingsCipher
    ): SettingsRepository {
        return SettingsRepository(context, settingsCipher)
    }

    @Provides
    @Singleton
    fun provideGeminiRepository(
        settingsRepository: SettingsRepository,
        geminiApiClient: GeminiApiClient,
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): GeminiRepository {
        return GeminiRepository(
            settingsRepository,
            WebPageContentFetcher(context),
            geminiApiClient
        )
    }
}
