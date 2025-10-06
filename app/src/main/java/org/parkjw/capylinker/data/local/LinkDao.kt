package org.parkjw.capylinker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LinkItem)

    @Delete
    suspend fun delete(item: LinkItem)

    @Query("SELECT * FROM link_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<LinkItem>>
}
