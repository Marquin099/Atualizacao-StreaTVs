package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity

@Immutable
@Entity(
    tableName = "favorite_items",
    primaryKeys = ["profileId", "itemId"]
)
data class FavoriteItem(
    val profileId: Int,
    val itemId: Int
)
