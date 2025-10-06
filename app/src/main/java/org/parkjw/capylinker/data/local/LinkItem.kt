package org.parkjw.capylinker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "link_items")
data class LinkItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val summary: String,
    val tags: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)