package org.parkjw.capylinker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "links")
data class LinkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val summary: String,
    val tags: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
    val isAnalyzing: Boolean = false,
    val thumbnailUrl: String? = null
)
