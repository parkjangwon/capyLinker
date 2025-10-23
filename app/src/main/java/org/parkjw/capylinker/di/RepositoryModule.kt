package org.parkjw.capylinker.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.parkjw.capylinker.data.repository.GeminiRepository
import org.parkjw.capylinker.data.repository.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideGeminiRepository(
        settingsRepository: SettingsRepository,
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): GeminiRepository {
        return GeminiRepository(
            settingsRepository,
            org.parkjw.capylinker.data.repository.WebPageContentFetcher(context)
        )
    }
}
