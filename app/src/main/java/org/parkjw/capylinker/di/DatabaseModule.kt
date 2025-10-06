package org.parkjw.capylinker.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.parkjw.capylinker.data.database.LinkDatabase
import org.parkjw.capylinker.data.database.LinkDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LinkDatabase {
        return Room.databaseBuilder(
            context,
            LinkDatabase::class.java,
            "link_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideLinkDao(database: LinkDatabase): LinkDao {
        return database.linkDao()
    }
}
