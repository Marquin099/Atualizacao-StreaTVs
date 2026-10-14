package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Immutable
@Entity(tableName = "playlist_items")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val logoUrl: String? = null,
    val groupName: String,
    val type: String, // "live", "movie", "series", "series_episode"
    val backdropUrl: String? = null,
    val titleLogoUrl: String? = null,
    val clearLogoUrl: String? = null,
    val logo: String? = null,
    val posterUrl: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val cleanTitle: String? = null,
    val cast: String? = null,
    val runtime: String? = null,
    val tmdbId: String? = null,
    val metadataLoaded: Boolean = false,
    val lastMetadataUpdate: Long = 0L
) : Serializable {
    val displayPoster: String?
        get() = posterUrl?.takeIf { it.isNotBlank() }
            ?: logoUrl?.takeIf { it.isNotBlank() }
            ?: logo?.takeIf { it.isNotBlank() }

    val displayBackdrop: String?
        get() = backdropUrl?.takeIf { it.isNotBlank() }

    val displayClearLogo: String?
        get() = clearLogoUrl?.takeIf { it.isNotBlank() }
            ?: titleLogoUrl?.takeIf { it.isNotBlank() }
            ?: logo?.takeIf { it.isNotBlank() }
}

