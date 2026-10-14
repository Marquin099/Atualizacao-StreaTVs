package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_categories")
data class CategoryEntity(
    @PrimaryKey val id: String, // e.g., "movie_74", "series_734", "live_628"
    val categoryId: String,
    val categoryName: String,
    val type: String, // "movie", "series", "live"
    val displayOrder: Int
)
