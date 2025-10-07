package org.parkjw.capylinker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LinkEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LinkDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
}
