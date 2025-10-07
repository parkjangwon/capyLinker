package org.parkjw.capylinker.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY timestamp DESC")
    fun getAllLinks(): Flow<List<LinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: LinkEntity)

    @Delete
    suspend fun deleteLink(link: LinkEntity)

    @Query("SELECT * FROM links ORDER BY timestamp DESC")
    suspend fun getAllLinksOnce(): List<LinkEntity>

    @Query("DELETE FROM links")
    suspend fun deleteAllLinks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<LinkEntity>)
}
