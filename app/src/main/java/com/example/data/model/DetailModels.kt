package com.example.data.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
data class CastMember(
    val name: String,
    val character: String,
    val photoUrl: String
) : Serializable

@Immutable
data class Episode(
    val title: String,
    val duration: String,
    val synopsis: String,
    val thumbnailUrl: String,
    val releaseDate: String? = null, // Format: "dd/MM/yyyy" or null if released
    val isReleased: Boolean = true,
    val playUrl: String? = null
) : Serializable

@Immutable
data class Season(
    val name: String,
    val episodes: List<Episode>
) : Serializable
