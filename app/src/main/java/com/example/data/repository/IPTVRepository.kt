package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.WatchHistoryDao
import com.example.data.model.FavoriteItem
import com.example.data.model.PlaylistItem
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistory
import com.example.data.model.CategoryEntity
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.Season
import com.example.data.util.M3UParser
import com.example.data.util.cleanSeriesName
import com.example.ui.components.getCleanPtBrTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class IPTVRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val profileDao = db.userProfileDao()
    private val playlistDao = db.playlistItemDao()
    private val watchHistoryDao = db.watchHistoryDao()

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("streatv_preferences", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", "IPTVSmartersPlayer")
                .header("Accept", "*/*")
            chain.proceed(requestBuilder.build())
        }
        .build()

    private fun parseJsonArraySafely(bodyStr: String?): JSONArray? {
        if (bodyStr.isNullOrBlank()) return null
        val firstBracket = bodyStr.indexOf('[')
        val lastBracket = bodyStr.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket > firstBracket) {
            val substring = bodyStr.substring(firstBracket, lastBracket + 1)
            return try {
                JSONArray(substring)
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error parsing JSONArray substring", e)
                null
            }
        }
        return null
    }

    private fun parseJsonObjectSafely(bodyStr: String?): JSONObject? {
        if (bodyStr.isNullOrBlank()) return null
        val firstBrace = bodyStr.indexOf('{')
        val lastBrace = bodyStr.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val substring = bodyStr.substring(firstBrace, lastBrace + 1)
            return try {
                JSONObject(substring)
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error parsing JSONObject substring", e)
                null
            }
        }
        return null
    }

    private val tmdbSemaphore = Semaphore(4)
    @Volatile private var isPrefetchRunning = false

    // Profiles Flow
    val allProfiles: Flow<List<UserProfile>> = profileDao.getAllProfiles()

    // Playlist Items Flows
    val allItems: Flow<List<PlaylistItem>> = playlistDao.getAllItems()
    val bannerItems: Flow<List<PlaylistItem>> = playlistDao.getBannerItems()

    val trendingItemsFlow: Flow<List<PlaylistItem>> = playlistDao.getItemsByGroupName("Tendências")
    val popularItemsFlow: Flow<List<PlaylistItem>> = playlistDao.getItemsByGroupName("Os Mais Populares")
    val trailerItemsFlow: Flow<List<PlaylistItem>> = playlistDao.getItemsByGroupName("Últimos Trailers")

    suspend fun getItemsCount(): Int = playlistDao.getItemsCount()

    fun getItemsByType(type: String): Flow<List<PlaylistItem>> =
        playlistDao.getItemsByType(type)

    fun getCategoriesByType(type: String): Flow<List<String>> = combine(
        playlistDao.getCategoriesFromCategoriesTable(type),
        playlistDao.getCategoriesByType(type)
    ) { catTableList, itemsCatList ->
        if (catTableList.isNotEmpty()) {
            val catSet = catTableList.toSet()
            val extra = itemsCatList.filter { it !in catSet }
            catTableList + extra
        } else {
            itemsCatList
        }
    }

    fun getItemsByTypeAndCategory(type: String, category: String): Flow<List<PlaylistItem>> =
        playlistDao.getItemsByTypeAndCategory(type, category)

    fun getFavorites(profileId: Int, type: String): Flow<List<PlaylistItem>> =
        playlistDao.getFavoriteItems(profileId, type)

    fun getAllFavorites(profileId: Int): Flow<List<PlaylistItem>> =
        playlistDao.getAllFavoriteItems(profileId)

    fun getFavoriteItemIds(profileId: Int): Flow<List<Int>> =
        playlistDao.getFavoriteItemIds(profileId)

    fun getContinueWatching(profileId: Int): Flow<List<PlaylistItem>> =
        watchHistoryDao.getContinueWatching(profileId)

    suspend fun saveWatchProgress(profileId: Int, itemId: Int, position: Long, duration: Long) {
        val watch = WatchHistory(
            profileId = profileId,
            itemId = itemId,
            position = position,
            duration = duration,
            lastWatched = System.currentTimeMillis()
        )
        watchHistoryDao.insertWatchHistory(watch)
    }

    suspend fun getWatchProgress(profileId: Int, itemId: Int): WatchHistory? {
        return watchHistoryDao.getWatchHistoryItem(profileId, itemId)
    }

    suspend fun deleteWatchProgress(profileId: Int, itemId: Int) {
        watchHistoryDao.deleteWatchHistory(profileId, itemId)
    }

    suspend fun saveLastWatchedEpisode(profileId: Int, seriesId: Int, episodeTitle: String, playUrl: String) = withContext(Dispatchers.IO) {
        sharedPrefs.edit()
            .putString("last_ep_title_${profileId}_${seriesId}", episodeTitle)
            .putString("last_ep_url_${profileId}_${seriesId}", playUrl)
            .apply()
    }

    suspend fun getLastWatchedEpisode(profileId: Int, seriesId: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        val title = sharedPrefs.getString("last_ep_title_${profileId}_${seriesId}", null)
        val url = sharedPrefs.getString("last_ep_url_${profileId}_${seriesId}", null)
        if (title != null && url != null) {
            Pair(title, url)
        } else {
            null
        }
    }

    suspend fun saveEpisodeWatchProgress(profileId: Int, seriesId: Int, episodeKey: String, position: Long, duration: Long) = withContext(Dispatchers.IO) {
        sharedPrefs.edit()
            .putLong("ep_pos_${profileId}_${seriesId}_${episodeKey}", position)
            .putLong("ep_dur_${profileId}_${seriesId}_${episodeKey}", duration)
            .apply()
    }

    suspend fun getEpisodeWatchProgress(profileId: Int, seriesId: Int, episodeKey: String): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        val pos = sharedPrefs.getLong("ep_pos_${profileId}_${seriesId}_${episodeKey}", -1L)
        val dur = sharedPrefs.getLong("ep_dur_${profileId}_${seriesId}_${episodeKey}", -1L)
        if (pos > 0 && dur > 0) {
            Pair(pos, dur)
        } else {
            null
        }
    }

    // User Credentials Handling
    fun saveCredentials(serverUrl: String, username: String, password: String) {
        sharedPrefs.edit()
            .putString("saved_server_url", serverUrl)
            .putString("saved_username", username)
            .putString("saved_password", password)
            .putBoolean("has_logged_in", true)
            .apply()
    }

    fun getSavedCredentials(): Pair<String?, String?> {
        val u = sharedPrefs.getString("saved_username", null)
        val p = sharedPrefs.getString("saved_password", null)
        return Pair(u, p)
    }

    private fun cleanServerUrl(url: String): String {
        var cleaned = url.trim()
        if (cleaned.isEmpty()) return ""
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            cleaned = "http://$cleaned"
        }
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        val endingsToRemove = listOf(
            "/player_api.php", "/get.php", "/xmltv.php",
            "player_api.php", "get.php", "xmltv.php"
        )
        for (ending in endingsToRemove) {
            if (cleaned.endsWith(ending)) {
                cleaned = cleaned.substring(0, cleaned.length - ending.length)
            }
        }
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        return cleaned
    }

    fun getSavedServerUrl(): String {
        val raw = sharedPrefs.getString("saved_server_url", "http://streatv.elementfx.com") ?: "http://streatv.elementfx.com"
        return cleanServerUrl(raw)
    }

    fun hasLoggedIn(): Boolean {
        return sharedPrefs.getBoolean("has_logged_in", false)
    }

    suspend fun syncNewItemsOnStartup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverUrl = getSavedServerUrl()
            val creds = getSavedCredentials()
            val username = creds.first
            val password = creds.second
            if (serverUrl.isEmpty() || username.isNullOrBlank() || password.isNullOrBlank()) {
                return@withContext false
            }

            var formattedServer = serverUrl.trim()
            if (!formattedServer.startsWith("http://") && !formattedServer.startsWith("https://")) {
                formattedServer = "http://$formattedServer"
            }
            if (formattedServer.endsWith("/")) {
                formattedServer = formattedServer.substring(0, formattedServer.length - 1)
            }

            Log.d("IPTVRepository", "syncNewItemsOnStartup: Starting fast JSON-based sync on startup")

            val fetchedItems = mutableListOf<PlaylistItem>()
            val categoriesToInsert = mutableListOf<CategoryEntity>()

            // 1. Fetch Series Categories
            val seriesCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series_categories"
            val seriesCategoryMap = mutableMapOf<String, String>()
            val seriesCategoryOrderList = mutableListOf<String>()
            try {
                val req = Request.Builder().url(seriesCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    seriesCategoryMap[catId] = catName
                                    seriesCategoryOrderList.add(catName)
                                    categoriesToInsert.add(
                                        CategoryEntity(
                                            id = "series_$catId",
                                            categoryId = catId,
                                            categoryName = catName,
                                            type = "series",
                                            displayOrder = i
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series categories in fast sync", e)
            }

            // 2. Fetch Series
            val seriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series"
            val rawSeries = mutableListOf<PlaylistItem>()
            try {
                val req = Request.Builder().url(seriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val seriesIdStr = obj.optString("series_id")
                                val seriesId = seriesIdStr.toIntOrNull() ?: 0
                                val finalId = if (seriesId > 0) seriesId + 10000000 else 0
                                val cover = obj.optString("cover")
                                val catId = obj.optString("category_id")
                                val plot = obj.optString("plot")
                                val rating = obj.optString("rating")
                                val releaseDate = obj.optString("releaseDate")
                                val genre = obj.optString("genre")

                                val groupName = seriesCategoryMap[catId] ?: "Outras Séries"

                                val seriesItem = PlaylistItem(
                                    id = finalId,
                                    title = name,
                                    url = "$formattedServer/series/$username/$password/$seriesIdStr.mp4",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "series",
                                    description = if (plot.isNullOrEmpty() || plot == "null") null else plot,
                                    rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                    year = if (releaseDate.isNullOrEmpty() || releaseDate == "null") null else releaseDate,
                                    genre = if (genre.isNullOrEmpty() || genre == "null") null else genre
                                )
                                val withClean = seriesItem.copy(cleanTitle = seriesItem.cleanSeriesName())
                                rawSeries.add(withClean)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series in fast sync", e)
            }

            val seriesOrderMap = seriesCategoryOrderList.mapIndexed { index, name -> name to index }.toMap()
            val rawSeriesWithIdx = rawSeries.mapIndexed { idx, item -> idx to item }
            val sortedSeries = rawSeriesWithIdx.sortedWith(
                compareBy<Pair<Int, PlaylistItem>> { seriesOrderMap[it.second.groupName] ?: 999999 }
                    .thenByDescending { it.second.id }
            ).map { it.second }
            fetchedItems.addAll(sortedSeries)

            // 3. Fetch VOD Categories
            val vodCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_vod_categories"
            val vodCategoryMap = mutableMapOf<String, String>()
            val vodCategoryOrderList = mutableListOf<String>()
            try {
                val req = Request.Builder().url(vodCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    vodCategoryMap[catId] = catName
                                    vodCategoryOrderList.add(catName)
                                    categoriesToInsert.add(
                                        CategoryEntity(
                                            id = "movie_$catId",
                                            categoryId = catId,
                                            categoryName = catName,
                                            type = "movie",
                                            displayOrder = i
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching VOD categories in fast sync", e)
            }

            // 4. Fetch VOD Streams (Movies)
            val vodStreamsUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_vod_streams"
            val rawMovies = mutableListOf<PlaylistItem>()
            try {
                val req = Request.Builder().url(vodStreamsUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val streamIdStr = obj.optString("stream_id")
                                val streamId = streamIdStr.toIntOrNull() ?: 0
                                val cover = obj.optString("stream_icon")
                                val catId = obj.optString("category_id")
                                val rating = obj.optString("rating")
                                val releaseDate = obj.optString("added")
                                val containerExtension = obj.optString("container_extension", "mp4")

                                val groupName = vodCategoryMap[catId] ?: "Outros Filmes"

                                val movieItem = PlaylistItem(
                                    id = streamId,
                                    title = name,
                                    url = "$formattedServer/movie/$username/$password/$streamIdStr.$containerExtension",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "movie",
                                    rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                    year = if (releaseDate.isNullOrEmpty() || releaseDate == "null") null else releaseDate,
                                    cleanTitle = name
                                )
                                rawMovies.add(movieItem)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching VOD streams in fast sync", e)
            }

            val vodOrderMap = vodCategoryOrderList.mapIndexed { index, name -> name to index }.toMap()
            val rawMoviesWithIdx = rawMovies.mapIndexed { idx, item -> idx to item }
            val sortedMovies = rawMoviesWithIdx.sortedWith(
                compareBy<Pair<Int, PlaylistItem>> { vodOrderMap[it.second.groupName] ?: 999999 }
                    .thenByDescending { it.second.id }
            ).map { it.second }
            fetchedItems.addAll(sortedMovies)

            // 5. Fetch Live Categories
            val liveCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_live_categories"
            try {
                val req = Request.Builder().url(liveCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("[")) {
                            val jsonArray = JSONArray(bodyStr)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    categoriesToInsert.add(
                                        CategoryEntity(
                                            id = "live_$catId",
                                            categoryId = catId,
                                            categoryName = catName,
                                            type = "live",
                                            displayOrder = i
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching Live categories in fast sync", e)
            }

            if (categoriesToInsert.isNotEmpty()) {
                playlistDao.insertCategories(categoriesToInsert)
            }

            // 5. Query existing URLs from the DB
            val existingUrls = playlistDao.getAllUrls().toSet()

            // 6. Filter only new items
            val newItems = fetchedItems.filter { it.url !in existingUrls }

            if (newItems.isNotEmpty()) {
                Log.d("IPTVRepository", "syncNewItemsOnStartup: Found ${newItems.size} new items. Inserting into DB...")
                newItems.chunked(500).forEach { chunk ->
                    playlistDao.insertItems(chunk)
                }
            } else {
                Log.d("IPTVRepository", "syncNewItemsOnStartup: No new items found on the server.")
            }
            CoroutineScope(Dispatchers.IO).launch {
                startBackgroundMetadataPrefetch()
            }

            true
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error during fast syncNewItemsOnStartup", e)
            false
        }
    }

    fun logout() {
        sharedPrefs.edit().clear().apply()
    }

    // Profile Actions
    suspend fun createProfile(name: String, colorHex: String, avatarIconName: String = "avatar_default"): Long = withContext(Dispatchers.IO) {
        profileDao.insertProfile(UserProfile(name = name, avatarColorHex = colorHex, avatarIconName = avatarIconName))
    }

    suspend fun deleteProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        profileDao.deleteProfile(profile)
    }

    // Playlist Actions
    suspend fun isFavorite(profileId: Int, itemId: Int): Boolean = withContext(Dispatchers.IO) {
        playlistDao.isFavorite(profileId, itemId)
    }

    suspend fun toggleFavorite(profileId: Int, itemId: Int) = withContext(Dispatchers.IO) {
        if (playlistDao.isFavorite(profileId, itemId)) {
            playlistDao.deleteFavorite(profileId, itemId)
        } else {
            playlistDao.insertFavorite(FavoriteItem(profileId, itemId))
        }
    }

    // Download & Stream Parser
    suspend fun loginAndDownloadPlaylist(
        serverUrl: String,
        username: String,
        password: String,
        isSilentSync: Boolean = false,
        onProgress: (String, Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Conectando ao servidor...", 0.1f)
            var formattedServer = serverUrl.trim()
            if (!formattedServer.startsWith("http://") && !formattedServer.startsWith("https://")) {
                formattedServer = "http://$formattedServer"
            }
            if (formattedServer.endsWith("/")) {
                formattedServer = formattedServer.substring(0, formattedServer.length - 1)
            }

            val parsedItems = mutableListOf<PlaylistItem>()
            val categoriesToInsert = mutableListOf<CategoryEntity>()

            // 1. Fetch VOD Categories
            onProgress("Baixando categorias de filmes...", 0.15f)
            val vodCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_vod_categories"
            val vodCategoryMap = mutableMapOf<String, String>()
            val vodCategoryOrderList = mutableListOf<String>()
            try {
                val req = Request.Builder().url(vodCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    vodCategoryMap[catId] = catName
                                    vodCategoryOrderList.add(catName)
                                    categoriesToInsert.add(
                                        CategoryEntity(
                                            id = "movie_$catId",
                                            categoryId = catId,
                                            categoryName = catName,
                                            type = "movie",
                                            displayOrder = i
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching VOD categories", e)
            }

            // 2. Fetch Series Categories
            onProgress("Baixando categorias de séries...", 0.25f)
            val seriesCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series_categories"
            val seriesCategoryMap = mutableMapOf<String, String>()
            val seriesCategoryOrderList = mutableListOf<String>()
            try {
                val req = Request.Builder().url(seriesCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    seriesCategoryMap[catId] = catName
                                    seriesCategoryOrderList.add(catName)
                                    categoriesToInsert.add(
                                        CategoryEntity(
                                            id = "series_$catId",
                                            categoryId = catId,
                                            categoryName = catName,
                                            type = "series",
                                            displayOrder = i
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series categories", e)
            }

            // 3. Fetch Live Categories
            onProgress("Baixando categorias de canais...", 0.35f)
            val liveCategoriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_live_categories"
            val liveCategoryMap = mutableMapOf<String, String>()
            val liveCategoryOrderList = mutableListOf<String>()
            try {
                val req = Request.Builder().url(liveCategoriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val catId = obj.optString("category_id")
                                val catName = obj.optString("category_name")
                                if (catId.isNotEmpty() && catName.isNotEmpty()) {
                                    liveCategoryMap[catId] = catName
                                    liveCategoryOrderList.add(catName)
                                    categoriesToInsert.add(
                                        CategoryEntity(
                                            id = "live_$catId",
                                            categoryId = catId,
                                            categoryName = catName,
                                            type = "live",
                                            displayOrder = i
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching live categories", e)
            }

            // 4. Fetch Series Streams
            onProgress("Baixando catálogo de séries...", 0.45f)
            val seriesUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_series"
            val rawSeries = mutableListOf<PlaylistItem>()
            try {
                val req = Request.Builder().url(seriesUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val seriesIdStr = obj.optString("series_id")
                                val seriesId = seriesIdStr.toIntOrNull() ?: 0
                                val finalId = if (seriesId > 0) seriesId + 10000000 else 0
                                val cover = obj.optString("cover")
                                val catId = obj.optString("category_id")
                                val plot = obj.optString("plot")
                                val rating = obj.optString("rating")
                                val releaseDate = obj.optString("releaseDate")
                                val genre = obj.optString("genre")

                                val groupName = seriesCategoryMap[catId] ?: "Outras Séries"

                                val seriesItem = PlaylistItem(
                                    id = finalId,
                                    title = name,
                                    url = "$formattedServer/series/$username/$password/$seriesIdStr.mp4",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "series",
                                    description = if (plot.isNullOrEmpty() || plot == "null") null else plot,
                                    rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                    year = if (releaseDate.isNullOrEmpty() || releaseDate == "null") null else releaseDate,
                                    genre = if (genre.isNullOrEmpty() || genre == "null") null else genre
                                )
                                rawSeries.add(seriesItem.copy(cleanTitle = seriesItem.cleanSeriesName()))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching series", e)
            }

            val seriesOrderMap = seriesCategoryOrderList.mapIndexed { index, name -> name to index }.toMap()
            val rawSeriesWithIdx = rawSeries.mapIndexed { idx, item -> idx to item }
            val sortedSeries = rawSeriesWithIdx.sortedWith(
                compareBy<Pair<Int, PlaylistItem>> { seriesOrderMap[it.second.groupName] ?: 999999 }
                    .thenByDescending { it.second.id }
            ).map { it.second }
            parsedItems.addAll(sortedSeries)

            // 5. Fetch VOD Streams (Movies)
            onProgress("Baixando catálogo de filmes...", 0.65f)
            val vodStreamsUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_vod_streams"
            val rawMovies = mutableListOf<PlaylistItem>()
            try {
                val req = Request.Builder().url(vodStreamsUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val streamIdStr = obj.optString("stream_id")
                                val streamId = streamIdStr.toIntOrNull() ?: 0
                                val cover = obj.optString("stream_icon")
                                val catId = obj.optString("category_id")
                                val rating = obj.optString("rating")
                                val releaseDate = obj.optString("added")
                                val containerExtension = obj.optString("container_extension", "mp4")

                                val groupName = vodCategoryMap[catId] ?: "Outros Filmes"

                                val movieItem = PlaylistItem(
                                    id = streamId,
                                    title = name,
                                    url = "$formattedServer/movie/$username/$password/$streamIdStr.$containerExtension",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "movie",
                                    rating = if (rating.isNullOrEmpty() || rating == "null") null else rating,
                                    year = if (releaseDate.isNullOrEmpty() || releaseDate == "null") null else releaseDate,
                                    cleanTitle = name
                                )
                                rawMovies.add(movieItem)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching VOD streams", e)
            }

            val vodOrderMap = vodCategoryOrderList.mapIndexed { index, name -> name to index }.toMap()
            val rawMoviesWithIdx = rawMovies.mapIndexed { idx, item -> idx to item }
            val sortedMovies = rawMoviesWithIdx.sortedWith(
                compareBy<Pair<Int, PlaylistItem>> { vodOrderMap[it.second.groupName] ?: 999999 }
                    .thenByDescending { it.second.id }
            ).map { it.second }
            parsedItems.addAll(sortedMovies)

            // 6. Fetch Live Streams (Channels)
            onProgress("Baixando canais ao vivo...", 0.80f)
            val liveStreamsUrl = "$formattedServer/player_api.php?username=$username&password=$password&action=get_live_streams"
            val rawLive = mutableListOf<PlaylistItem>()
            try {
                val req = Request.Builder().url(liveStreamsUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        val jsonArray = parseJsonArraySafely(bodyStr)
                        if (jsonArray != null) {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name")
                                val streamIdStr = obj.optString("stream_id")
                                val streamId = streamIdStr.toIntOrNull() ?: 0
                                val finalId = if (streamId > 0) streamId + 20000000 else 0
                                val cover = obj.optString("stream_icon")
                                val catId = obj.optString("category_id")

                                val groupName = liveCategoryMap[catId] ?: "Canais Ao Vivo"

                                val liveItem = PlaylistItem(
                                    id = finalId,
                                    title = name,
                                    url = "$formattedServer/live/$username/$password/$streamIdStr.ts",
                                    logoUrl = cover,
                                    groupName = groupName,
                                    type = "live",
                                    cleanTitle = name
                                )
                                rawLive.add(liveItem)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error fetching Live streams", e)
            }

            val liveOrderMap = liveCategoryOrderList.mapIndexed { index, name -> name to index }.toMap()
            val rawLiveWithIdx = rawLive.mapIndexed { idx, item -> idx to item }
            val sortedLive = rawLiveWithIdx.sortedWith(
                compareBy<Pair<Int, PlaylistItem>> { liveOrderMap[it.second.groupName] ?: 999999 }
                    .thenBy { it.first }
            ).map { it.second }
            parsedItems.addAll(sortedLive)

            // Fallback to M3U downloading if JSON API yielded no items or if movies/series were missed
            if (parsedItems.isEmpty() || rawMovies.isEmpty() || rawSeries.isEmpty()) {
                onProgress("Baixando lista M3U complementar...", 0.85f)
                val m3uUrl = "$formattedServer/get.php?username=$username&password=$password&type=m3u_plus&output=ts"
                try {
                    val request = Request.Builder().url(m3uUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body
                            if (body != null) {
                                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                                var extinfLine: String? = null
                                var line = reader.readLine()
                                val existingUrls = parsedItems.map { it.url }.toSet()
                                while (line != null) {
                                    val trimmed = line.trim()
                                    if (trimmed.startsWith("#EXTINF:")) {
                                        extinfLine = trimmed
                                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                        if (extinfLine != null) {
                                            val item = parseItemLine(extinfLine, trimmed)
                                            if (item.url !in existingUrls) {
                                                parsedItems.add(item)
                                            }
                                            extinfLine = null
                                        }
                                    }
                                    line = reader.readLine()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Error in M3U fallback download", e)
                }
            }

            onProgress("Salvando dados no aplicativo...", 0.90f)

            if (categoriesToInsert.isNotEmpty()) {
                playlistDao.clearAllCategories()
                playlistDao.insertCategories(categoriesToInsert)
            }

            val fullyParsed = parsedItems.map { item ->
                if (item.type == "series" || item.type == "series_episode" || item.type == "movie") {
                    item.copy(cleanTitle = item.cleanSeriesName())
                } else {
                    item.copy(cleanTitle = item.title)
                }
            }

            if (fullyParsed.isEmpty()) {
                Log.w("IPTVRepository", "No items parsed from server, checking database or populating fallback catalog")
                populateFallbackVodsIfNeeded()
                val existingCount = playlistDao.getItemsCount()
                if (existingCount > 0) {
                    saveCredentials(serverUrl, username, password)
                    onProgress("Pronto!", 1.0f)
                    return@withContext true
                }
                Log.e("IPTVRepository", "No items parsed from server")
                onProgress("Nenhum item foi encontrado no servidor. Verifique suas credenciais.", 0f)
                return@withContext false
            }

            if (isSilentSync) {
                val existingUrls = playlistDao.getAllUrls().toSet()
                val newItems = fullyParsed.filter { it.url !in existingUrls }
                if (newItems.isNotEmpty()) {
                    newItems.chunked(500).forEach { chunk ->
                        playlistDao.insertItems(chunk)
                    }
                }
            } else {
                playlistDao.replaceAll(fullyParsed)
            }
            onProgress("Lista salva com sucesso!", 0.95f)

            // Trigger background pre-fetch immediately without blocking login completion
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    startBackgroundMetadataPrefetch()
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Failed background pre-fetch right after processing", e)
                }
            }

            // Generate some Mock TMDB Items if none exist (or the list is empty/small) to ensure the banners look spectacular!
            populateFallbackVodsIfNeeded()

            saveCredentials(serverUrl, username, password)
            onProgress("Pronto!", 1.0f)
            return@withContext true
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error syncing playlist", e)
            onProgress("Falha na sincronização: ${e.localizedMessage}", 0f)
            return@withContext false
        }
    }

    private fun extractIdFromUrl(url: String): Int? {
        try {
            val lastSlash = url.lastIndexOf('/')
            if (lastSlash == -1) return null
            var segment = url.substring(lastSlash + 1)
            val lastDot = segment.lastIndexOf('.')
            if (lastDot != -1) {
                segment = segment.substring(0, lastDot)
            }
            val digits = segment.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                return digits.toIntOrNull()
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error extracting ID from URL: $url", e)
        }
        return null
    }

    private fun parseItemLine(extinf: String, url: String): PlaylistItem {
        val logoUrl = extractAttribute(extinf, "tvg-logo") ?: extractAttribute(extinf, "logo")
        var groupName = extractAttribute(extinf, "group-title") ?: "Geral"

        // Clean group name
        groupName = groupName.replace("\"", "").trim()

        val lastComma = extinf.lastIndexOf(',')
        var title = if (lastComma != -1 && lastComma < extinf.length - 1) {
            extinf.substring(lastComma + 1).trim()
        } else {
            extractAttribute(extinf, "tvg-name") ?: "Sem Nome"
        }

        title = title.replace("\"", "").trim()

        val groupUpper = groupName.uppercase()
        val urlUpper = url.uppercase()

        // Smarter classification: check for series indicators, movie indicators, and video extensions
        val hasSeriesIndicator = urlUpper.contains("/SERIES/") ||
                urlUpper.contains("/SERIE/") ||
                urlUpper.contains("/EPISODES/") ||
                urlUpper.contains("/EPISODE/") ||
                groupUpper.contains("SÉRIE") ||
                groupUpper.contains("SERIE") ||
                groupUpper.contains("SÉRIES") ||
                groupUpper.contains("SERIES") ||
                groupUpper.contains("EPISODIO") ||
                groupUpper.contains("EPISÓDIO") ||
                groupUpper.contains("TEMPORADA") ||
                groupUpper.contains("SEASON") ||
                groupUpper.contains("DORAMA") ||
                groupUpper.contains("NOVELA") ||
                groupUpper.contains("ANIME") ||
                groupUpper.contains("DESENHO") ||
                title.uppercase().contains("S0") ||
                title.uppercase().contains("S1") ||
                title.uppercase().contains("E0") ||
                title.uppercase().contains("E1") ||
                title.uppercase().contains("EP.") ||
                title.uppercase().contains("EPISÓDIO")

        val hasMovieIndicator = urlUpper.contains("/MOVIE/") ||
                urlUpper.contains("/MOVIES/") ||
                groupUpper.contains("FILME") ||
                groupUpper.contains("MOVIE") ||
                groupUpper.contains("VOD") ||
                groupUpper.contains("CINEMA") ||
                groupUpper.contains("LANÇAMENTOS") ||
                groupUpper.contains("ANIMACAO") ||
                groupUpper.contains("DOCUMENTARIOS") ||
                groupUpper.contains("TELECINE")

        val isVideoFile = urlUpper.contains(".MKV") ||
                urlUpper.contains(".MP4") ||
                urlUpper.contains(".AVI") ||
                urlUpper.contains(".M4V")

        val type = when {
            hasSeriesIndicator -> "series_episode"
            hasMovieIndicator || isVideoFile -> "movie"
            else -> "live"
        }

        val extractedId = extractIdFromUrl(url)
        val finalId = when (type) {
            "movie" -> extractedId ?: 0
            "series", "series_episode" -> if (extractedId != null) extractedId + 10000000 else 0
            "live" -> if (extractedId != null) extractedId + 20000000 else 0
            else -> 0
        }

        return PlaylistItem(
            id = finalId,
            title = title,
            url = url,
            logoUrl = logoUrl,
            groupName = groupName,
            type = type
        )
    }

    private fun extractAttribute(line: String, key: String): String? {
        val pattern = "$key=\""
        val startIdx = line.indexOf(pattern)
        if (startIdx == -1) return null
        val valueStart = startIdx + pattern.length
        val endIdx = line.indexOf('"', valueStart)
        if (endIdx == -1) return null
        return line.substring(valueStart, endIdx)
    }

    fun cleanTitleForSearch(title: String): String {
        var clean = title.replace(searchPatternsCombined, "")
        clean = clean.replace(spacesRegex, " ").trim()
        clean = clean.replace(dashEndRegex, "").trim()
        clean = clean.replace(hyphenEndRegex, "").trim()
        clean = clean.replace(colonEndRegex, "").trim()
        return if (clean.isEmpty()) title else clean
    }

    fun extractYearFromItem(item: PlaylistItem): Int? {
        val yearPattern = Regex("""\b(19\d\d|20\d\d)\b""")
        // 1. Check item.year field
        if (!item.year.isNullOrBlank()) {
            val m = yearPattern.find(item.year!!)
            if (m != null) return m.value.toIntOrNull()
        }
        // 2. Check title
        val mTitle = yearPattern.find(item.title)
        if (mTitle != null) return mTitle.value.toIntOrNull()

        // 3. Check groupName / category
        val mGroup = yearPattern.find(item.groupName)
        if (mGroup != null) return mGroup.value.toIntOrNull()

        return null
    }

    private fun pickBestTmdbSearchResult(results: JSONArray, targetYear: Int?, isSeries: Boolean): JSONObject? {
        if (results.length() == 0) return null
        if (targetYear != null) {
            val dateField = if (isSeries) "first_air_date" else "release_date"
            for (i in 0 until results.length()) {
                val candidate = results.optJSONObject(i) ?: continue
                val dateStr = candidate.optString(dateField, "")
                if (dateStr.startsWith(targetYear.toString())) {
                    return candidate
                }
            }
        }
        return results.optJSONObject(0)
    }

    fun getOverrideTmdbId(item: PlaylistItem, cleanTitle: String, isSeries: Boolean): Int? {
        val effectiveIsSeries = isSeries ||
                item.type == "series" ||
                item.type == "series_episode" ||
                item.groupName.contains("Série", ignoreCase = true) ||
                item.groupName.contains("Serie", ignoreCase = true) ||
                item.url.contains("/series/")
        return getOverrideTmdbId(item.title, cleanTitle, effectiveIsSeries, item.groupName, item.id, item.year)
    }

    fun getOverrideTmdbId(title: String, cleanTitle: String, isSeries: Boolean, groupName: String = "", itemId: Int = 0, year: String? = null): Int? {
        val lowerClean = cleanTitle.lowercase().trim()
        val lowerTitle = title.lowercase().trim()
        val lowerGroup = groupName.lowercase().trim()
        val effectiveYear = year?.trim() ?: ""

        // 1. "Um Mundo Diferente" (2026 - ID TMDB 305357)
        // O usuário tem a série de 2026 "Um mundo diferente" cadastrada no painel com TMDB ID 305357-a-different-world
        val isUmMundoDiferente = lowerClean == "um mundo diferente" ||
                lowerClean == "a different world" ||
                lowerClean.contains("305357") ||
                lowerTitle.contains("305357") ||
                lowerClean == "305357-a-different-world" ||
                lowerClean.startsWith("um mundo diferente") ||
                lowerTitle == "um mundo diferente" ||
                lowerTitle.startsWith("um mundo diferente") ||
                lowerTitle.contains("um mundo diferente") ||
                (lowerTitle.contains("mundo diferente") && !lowerTitle.contains("outro mundo")) ||
                (lowerClean.contains("mundo diferente") && !lowerClean.contains("outro mundo"))
        if (isUmMundoDiferente) {
            return 305357 // Um Mundo Diferente (2026)
        }

        // 2. "Colisão" / "Colisao" (2026 - ID TMDB 331033)
        // O usuário tem a série de 2026 "Colisao" cadastrada no painel com TMDB ID 331033-colision
        val isColisaoName = lowerClean == "colisao" ||
                lowerClean == "colisão" ||
                lowerClean == "colision" ||
                lowerClean == "collision" ||
                lowerClean == "331033-colision" ||
                lowerTitle.contains("331033") ||
                lowerClean.contains("331033") ||
                lowerTitle == "colisao" ||
                lowerTitle == "colisão" ||
                lowerTitle == "colision" ||
                lowerTitle.startsWith("colisao ") ||
                lowerTitle.startsWith("colisão ") ||
                lowerTitle.startsWith("colisao -") ||
                lowerTitle.startsWith("colisão -") ||
                lowerTitle.startsWith("colisao:") ||
                lowerTitle.startsWith("colisão:") ||
                lowerClean.startsWith("colisao ") ||
                lowerClean.startsWith("colisão ") ||
                lowerClean.startsWith("colisao") ||
                lowerClean.startsWith("colisão")
        if (isColisaoName) {
            if (isSeries || lowerGroup.contains("serie") || lowerGroup.contains("série") ||
                lowerGroup.contains("2026") || lowerTitle.contains("2026") || effectiveYear.contains("2026") ||
                lowerClean.contains("colision") || lowerTitle.contains("colision") ||
                !lowerGroup.contains("filme")
            ) {
                return 331033 // Colisão (2026)
            }
        }

        if (!isSeries) {
            // 1. "Não Identificada" / "Unidentified" (2026 - ID TMDB 1337438)
            val isNaoIdentificada = lowerClean == "nao identificada" ||
                    lowerClean == "não identificada" ||
                    lowerTitle == "nao identificada" ||
                    lowerTitle == "não identificada" ||
                    lowerClean.contains("1337438") ||
                    lowerTitle.contains("1337438") ||
                    lowerClean.contains("unidentified") ||
                    lowerTitle.contains("unidentified") ||
                    lowerClean.startsWith("nao identificada") ||
                    lowerClean.startsWith("não identificada") ||
                    lowerTitle.startsWith("nao identificada") ||
                    lowerTitle.startsWith("não identificada") ||
                    (lowerTitle.contains("identificada") && !lowerTitle.contains("chamada") && !lowerTitle.contains("prefiro"))
            if (isNaoIdentificada) {
                return 1337438 // Não Identificada / Unidentified (2026)
            }

            // 2. "As Loucuras" / "Las Locuras" (2025 - ID TMDB 1548092)
            // Substitui o ID incorreto 7552 (Fun with Dick and Jane / As Loucuras de Dick & Jane) pelo ID correto 1548092
            val isAsLoucuras = lowerClean == "as loucuras" ||
                    lowerClean == "las locuras" ||
                    lowerTitle == "as loucuras" ||
                    lowerTitle == "las locuras" ||
                    lowerClean.contains("1548092") ||
                    lowerTitle.contains("1548092") ||
                    lowerClean.contains("las-locuras") ||
                    lowerTitle.contains("las-locuras") ||
                    lowerClean.contains("7552") ||
                    lowerTitle.contains("7552") ||
                    (lowerClean.startsWith("as loucuras") && !lowerClean.contains("dick") && !lowerClean.contains("jane")) ||
                    (lowerTitle.startsWith("as loucuras") && !lowerTitle.contains("dick") && !lowerTitle.contains("jane")) ||
                    (lowerTitle.contains("loucuras") && !lowerTitle.contains("dick") && !lowerTitle.contains("jane") &&
                     (lowerGroup.contains("2025") || lowerGroup.contains("2026") || lowerGroup.contains("4k") ||
                      lowerTitle.contains("2025") || lowerTitle.contains("2026") || effectiveYear == "2025" || effectiveYear == "2026"))
            if (isAsLoucuras) {
                return 1548092 // As Loucuras / Las Locuras (2025)
            }

            // 3. "Céu de Fúria", "Ceu em Furia", "Céu em Fúria", "Ceu de Furia" ou se o filme for nomeado "Fúria" / "Furia"
            val isCeuDeFuria = (lowerTitle.contains("ceu") || lowerTitle.contains("céu")) &&
                    (lowerTitle.contains("furia") || lowerTitle.contains("fúria"))
            val isExactFuriaMovie = lowerClean == "furia" || lowerClean == "fúria" || lowerTitle == "furia" || lowerTitle == "fúria"

            if (isCeuDeFuria || isExactFuriaMovie) {
                return 1323244 // TMDB ID do filme "Céu em Fúria" / "Rage of Stars" (2026)
            }
            // Qualquer outro filme com a palavra furia ("Fúria de Titãs", "Fúria Silenciosa", "Fúria Incontrolável", etc.)
            // deve retornar null para obter seus metadados originais de filme via busca TMDB!
            return null
        }

        if (isSeries) {
            // Séries "Doc":
            // Disney+ tem a série "Doc" (2025 - ID 255055)
            // Netflix tem a série "DOC – Uma Nova Vida" (Doc - Nelle tue mani - ID 101048)
            val isDocSeries = lowerClean == "doc" || lowerTitle == "doc" ||
                    lowerTitle.startsWith("doc ") || lowerTitle.contains("doc -") || lowerTitle.contains("doc –")
            if (isDocSeries) {
                return if (lowerGroup.contains("netflix")) {
                    101048 // DOC – Uma Nova Vida (Netflix)
                } else if (lowerGroup.contains("disney")) {
                    255055 // Doc (Disney+)
                } else {
                    if (lowerTitle.contains("nova vida")) 101048 else 255055
                }
            }

            // Séries "Fúria":
            // Somente quando o título da série for especificamente "Fúria" / "Furia" (não títulos compostos)
            val isExactFuriaSeries = lowerClean == "furia" || lowerClean == "fúria" || lowerTitle == "furia" || lowerTitle == "fúria"
            if (isExactFuriaSeries) {
                return if (lowerGroup.contains("disney")) {
                    287238 // Série Fúria (Disney+)
                } else {
                    292533 // Série Fúria (Netflix / Geral)
                }
            }

            if (lowerClean == "james" ||
                lowerTitle.endsWith("james") ||
                (lowerTitle.contains("james") && !lowerTitle.contains("bond") && !lowerTitle.contains("may") && !lowerTitle.contains("brown"))
            ) {
                return 321596
            }
            if (lowerClean == "thunder" ||
                lowerClean == "thunder 3" ||
                lowerTitle.contains("thunder")
            ) {
                return 326119
            }
        }
        return null
    }

    /**
     * Seleciona o melhor logotipo de acordo com a prioridade estrita solicitada:
     * 1. Português do Brasil (pt-BR)
     * 2. Português geral (pt)
     * 3. Português de Portugal (pt-PT)
     * 4. Inglês (en / en-US) - SOMENTE se não houver NENHUMA versão em Português!
     * 5. Outros idiomas / neutro
     */
    fun selectBestTmdbLogo(logosArr: JSONArray?): String? {
        if (logosArr == null || logosArr.length() == 0) return null

        data class LogoCandidate(
            val path: String,
            val tier: Int, // 0 = pt-BR, 1 = pt (geral), 2 = pt-PT, 3 = en / en-US, 4 = outro/null
            val score: Double,
            val width: Int
        )

        val candidates = mutableListOf<LogoCandidate>()

        for (i in 0 until logosArr.length()) {
            val obj = logosArr.optJSONObject(i) ?: continue
            val filePath = obj.optString("file_path", "").trim()
            if (filePath.isEmpty()) continue

            val lang = obj.optString("iso_639_1", "").lowercase().trim()
            val country = obj.optString("iso_3166_1", "").uppercase().trim()
            val voteAvg = obj.optDouble("vote_average", 0.0)
            val voteCount = obj.optInt("vote_count", 0)
            val aspectRatio = obj.optDouble("aspect_ratio", 1.0)
            val width = obj.optInt("width", 0)

            // Classificação estrita de tier:
            // Tier 0: Português do Brasil (pt-BR)
            // Tier 1: Português (geral ou sem restrição)
            // Tier 2: Português de Portugal (pt-PT)
            // Tier 3: Inglês (en / en-US) - SÓ E APENAS SE NÃO EXISTIR NENHUM EM PORTUGUÊS!
            // Tier 4: Outros idiomas ou nulo
            val tier = when {
                (lang == "pt" && country == "BR") || lang == "pt-br" -> 0
                lang == "pt" && country.isEmpty() -> 1
                lang == "pt" && country != "PT" -> 1
                (lang == "pt" && country == "PT") || lang == "pt-pt" -> 2
                lang == "en" || lang == "en-us" -> 3
                else -> 4
            }

            // Bônus para logos horizontais (proporção típica de título 1.3 a 6.5)
            val aspectBonus = if (aspectRatio in 1.3..6.5) 5.0 else 0.0
            val score = (voteAvg * 2.0) + (voteCount.toDouble().coerceAtMost(10.0) * 0.5) + aspectBonus

            candidates.add(LogoCandidate(filePath, tier, score, width))
        }

        if (candidates.isEmpty()) return null

        // Ordenação estrita: Menor tier primeiro (0 ganha sempre), depois maior score, depois maior largura
        val best = candidates.minWithOrNull(
            compareBy<LogoCandidate> { it.tier }
                .thenByDescending { it.score }
                .thenByDescending { it.width }
        )

        return best?.path
    }

    private fun logoLanguagePriority(language: String): Int = when (language.lowercase()) {
        "pt-br" -> 0
        "pt" -> 1
        "en-us" -> 2
        "en" -> 3
        "" -> 4
        "pt-pt" -> 20
        else -> 30
    }

    fun getTmdbImageUrl(path: String?, size: String = "w780"): String? {
        if (path.isNullOrEmpty()) return null
        val filename = path.substringAfterLast("/")
        if (filename.isEmpty()) return null
        return "https://image.tmdb.org/t/p/$size/$filename"
    }

    private fun findPreferredTmdbLogo(
        mediaType: String,
        tmdbId: Int,
        apiKey: String
    ): String? {
        val imagesUrl =
            "https://api.themoviedb.org/3/$mediaType/$tmdbId/images" +
                    "?api_key=$apiKey&language=pt-BR&include_image_language=pt-BR,pt,en-US,en,null"

        try {
            client.newCall(Request.Builder().url(imagesUrl).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("IPTVRepository", "TMDB images failed for id=$tmdbId: HTTP ${response.code}")
                    return null
                }

                val logos = JSONObject(response.body?.string().orEmpty()).optJSONArray("logos") ?: return null
                val bestPath = selectBestTmdbLogo(logos)
                if (bestPath != null) {
                    return getTmdbImageUrl(bestPath, "w780")
                }
                return null
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error finding preferred logo for $mediaType/$tmdbId", e)
            return null
        }
    }

    fun isMetadataComplete(item: PlaylistItem): Boolean {
        val cleanTitle = cleanTitleForSearch(item.title)
        val isSeries = item.type == "series" || item.groupName.contains("Série", ignoreCase = true) || item.groupName.contains("Serie", ignoreCase = true)
        val overrideId = getOverrideTmdbId(item, cleanTitle, isSeries)

        if (overrideId != null && item.tmdbId != overrideId.toString()) {
            return false
        }

        // Se for um filme mas foi salvo anteriormente com ID de série (como 292533 ou 287238), não está completo!
        if (!isSeries && (item.tmdbId == "292533" || item.tmdbId == "287238")) {
            return false
        }

        // Se for a série Doc na Netflix mas pegou o ID da Disney (255055), não está completo!
        if (isSeries && item.groupName.contains("netflix", ignoreCase = true) &&
            (cleanTitle.lowercase() == "doc" || item.title.lowercase().trim() == "doc") &&
            item.tmdbId == "255055"
        ) {
            return false
        }

        // Se for a série Um Mundo Diferente e estiver com o ID antigo (115) ao invés do de 2026 (305357)
        if ((cleanTitle.contains("mundo diferente", ignoreCase = true) || item.title.contains("mundo diferente", ignoreCase = true)) &&
            item.tmdbId != "305357"
        ) {
            return false
        }

        // Se for a série Colisão e estiver com ID antigo (como 83584, 8344 ou 244891) ao invés do de 2026 (331033)
        if ((cleanTitle.contains("colis", ignoreCase = true) || item.title.contains("colis", ignoreCase = true)) &&
            item.tmdbId != "331033" && (isSeries || item.groupName.contains("serie", ignoreCase = true))
        ) {
            return false
        }

        // Se for o filme "As Loucuras" e estiver com o ID incorreto 7552 ou diferente de 1548092
        if (!isSeries && (cleanTitle.contains("loucuras", ignoreCase = true) || item.title.contains("loucuras", ignoreCase = true)) &&
            !item.title.contains("dick", ignoreCase = true) && !item.title.contains("jane", ignoreCase = true) &&
            item.tmdbId != "1548092"
        ) {
            return false
        }

        // Se for o filme "Não Identificada" e estiver com ID diferente de 1337438 ou sem poster/backdrop
        if (!isSeries && (cleanTitle.contains("identificada", ignoreCase = true) || item.title.contains("identificada", ignoreCase = true)) &&
            (item.tmdbId != "1337438" || item.posterUrl.isNullOrBlank() || item.backdropUrl.isNullOrBlank())
        ) {
            return false
        }

        return item.metadataLoaded || (
                !item.posterUrl.isNullOrBlank() &&
                        !item.backdropUrl.isNullOrBlank() &&
                        !item.description.isNullOrBlank() &&
                        !item.clearLogoUrl.isNullOrBlank() &&
                        !item.genre.isNullOrBlank() &&
                        !item.year.isNullOrBlank() &&
                        !item.rating.isNullOrBlank()
                )
    }

    suspend fun enrichItemWithTMDB(item: PlaylistItem, updateDb: Boolean = true): PlaylistItem = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitleForSearch(item.title)
        val isSeries = item.type == "series" || item.groupName.contains("Série", ignoreCase = true) || item.groupName.contains("Serie", ignoreCase = true)
        val overrideId = getOverrideTmdbId(item, cleanTitle, isSeries)

        if (isMetadataComplete(item) && (overrideId == null || item.tmdbId == overrideId.toString())) {
            return@withContext item
        }

        tmdbSemaphore.withPermit {
            val tmdbApiKey = if (BuildConfig.TMDB_API_KEY.isBlank() || BuildConfig.TMDB_API_KEY == "your_tmdb_api_key_here") {
                "355b405bb005248e382a8b400ebab70b"
            } else {
                BuildConfig.TMDB_API_KEY
            }
            try {
                val searchType = if (isSeries) "tv" else "movie"

                var foundId = 0
                var firstResult: JSONObject? = null

                if (overrideId != null && overrideId > 0) {
                    foundId = overrideId
                } else {
                    val targetYear = extractYearFromItem(item)
                    val queries = listOf(cleanTitle, item.title)
                    for (q in queries) {
                        if (q.isBlank()) continue
                        val searchUrls = mutableListOf<String>()
                        if (targetYear != null) {
                            val yearParam = if (isSeries) "first_air_date_year" else "primary_release_year"
                            searchUrls.add("https://api.themoviedb.org/3/search/$searchType?api_key=$tmdbApiKey&query=${java.net.URLEncoder.encode(q, "UTF-8")}&$yearParam=$targetYear&language=pt-BR")
                        }
                        searchUrls.add("https://api.themoviedb.org/3/search/$searchType?api_key=$tmdbApiKey&query=${java.net.URLEncoder.encode(q, "UTF-8")}&language=pt-BR")

                        for (url in searchUrls) {
                            val request = Request.Builder().url(url).build()
                            try {
                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val jsonStr = response.body?.string() ?: ""
                                        val root = JSONObject(jsonStr)
                                        val results = root.optJSONArray("results")
                                        if (results != null && results.length() > 0) {
                                            val chosen = pickBestTmdbSearchResult(results, targetYear, isSeries)
                                            if (chosen != null) {
                                                firstResult = chosen
                                                foundId = chosen.optInt("id")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("IPTVRepository", "TMDB search failed for $q", e)
                            }
                            if (foundId > 0) break
                        }
                        if (foundId > 0) break
                    }
                }

                if (foundId > 0) {
                    val id = foundId
                    var posterPath = firstResult?.optString("poster_path", "").orEmpty()
                    var backdropPath = firstResult?.optString("backdrop_path", "").orEmpty()
                    var description = firstResult?.optString("overview", item.description ?: "").orEmpty()
                    var rating = firstResult?.optDouble("vote_average", 0.0) ?: 0.0
                    val dateField = if (isSeries) "first_air_date" else "release_date"
                    var dateStr = firstResult?.optString(dateField, "").orEmpty()
                    var genreStr = item.genre ?: ""
                    var castStr = item.cast ?: ""
                    var runtimeStr = item.runtime ?: ""
                    var clearLogoUrl: String? = item.clearLogoUrl

                    // Fetch detailed endpoint with credits and images
                    val detailsUrl = "https://api.themoviedb.org/3/$searchType/$id?api_key=$tmdbApiKey&language=pt-BR&append_to_response=credits,images&include_image_language=pt-BR,pt,en-US,en,null"
                    try {
                        client.newCall(Request.Builder().url(detailsUrl).build()).execute().use { detailResp ->
                            if (detailResp.isSuccessful) {
                                val detailJson = JSONObject(detailResp.body?.string().orEmpty())
                                val genresArr = detailJson.optJSONArray("genres")
                                if (genresArr != null && genresArr.length() > 0) {
                                    val genreNames = mutableListOf<String>()
                                    for (g in 0 until genresArr.length()) {
                                        val gObj = genresArr.optJSONObject(g)
                                        val name = gObj?.optString("name")
                                        if (!name.isNullOrBlank()) genreNames.add(name)
                                    }
                                    if (genreNames.isNotEmpty()) {
                                        genreStr = genreNames.joinToString(" / ")
                                    }
                                }
                                val ov = detailJson.optString("overview")
                                if (!ov.isNullOrBlank()) description = ov
                                val va = detailJson.optDouble("vote_average", 0.0)
                                if (va > 0) rating = va
                                val d = detailJson.optString(if (isSeries) "first_air_date" else "release_date")
                                if (!d.isNullOrBlank()) dateStr = d
                                val pp = detailJson.optString("poster_path")
                                if (!pp.isNullOrBlank()) posterPath = pp
                                val bp = detailJson.optString("backdrop_path")
                                if (!bp.isNullOrBlank()) backdropPath = bp

                                // Runtime
                                if (isSeries) {
                                    val runTimes = detailJson.optJSONArray("episode_run_time")
                                    if (runTimes != null && runTimes.length() > 0) {
                                        runtimeStr = "${runTimes.optInt(0)} min"
                                    }
                                } else {
                                    val rt = detailJson.optInt("runtime", 0)
                                    if (rt > 0) runtimeStr = "$rt min"
                                }

                                // Cast
                                val creditsObj = detailJson.optJSONObject("credits")
                                if (creditsObj != null) {
                                    val castArr = creditsObj.optJSONArray("cast")
                                    if (castArr != null && castArr.length() > 0) {
                                        val names = mutableListOf<String>()
                                        val count = minOf(castArr.length(), 6)
                                        for (c in 0 until count) {
                                            val actorName = castArr.getJSONObject(c).optString("name")
                                            if (!actorName.isNullOrBlank()) names.add(actorName)
                                        }
                                        if (names.isNotEmpty()) castStr = names.joinToString(", ")
                                    }
                                }

                                // Logos
                                val imagesObj = detailJson.optJSONObject("images")
                                if (imagesObj != null) {
                                    val logosArr = imagesObj.optJSONArray("logos")
                                    val bestLogo = selectBestTmdbLogo(logosArr)
                                    if (!bestLogo.isNullOrEmpty()) {
                                        clearLogoUrl = getTmdbImageUrl(bestLogo, "w780")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("IPTVRepository", "Error fetching details for id=$id", e)
                    }

                    if (clearLogoUrl.isNullOrEmpty()) {
                        try {
                            clearLogoUrl = findPreferredTmdbLogo(searchType, id, tmdbApiKey)
                        } catch (e: Exception) {}
                    }

                    val hasWrongTmdbId = overrideId != null && item.tmdbId != null && item.tmdbId != overrideId.toString()
                    val fallbackPoster = if (hasWrongTmdbId) null else (item.posterUrl ?: item.logoUrl)
                    val fallbackBackdrop = if (hasWrongTmdbId) null else item.backdropUrl
                    val fallbackLogo = if (hasWrongTmdbId) null else (item.clearLogoUrl ?: item.titleLogoUrl ?: item.logo)

                    val year = if (dateStr.length >= 4) dateStr.substring(0, 4) else ""
                    val pUrl = getTmdbImageUrl(posterPath, "w500") ?: fallbackPoster ?: item.logoUrl
                    val bUrl = getTmdbImageUrl(backdropPath, "w1280") ?: fallbackBackdrop
                    val cLogo = getTmdbImageUrl(clearLogoUrl, "w500") ?: fallbackLogo

                    val enriched = item.copy(
                        posterUrl = pUrl,
                        logoUrl = pUrl,
                        backdropUrl = bUrl,
                        clearLogoUrl = cLogo,
                        titleLogoUrl = cLogo,
                        logo = cLogo,
                        description = if (description.isNotBlank()) description else item.description,
                        genre = if (genreStr.isNotBlank()) genreStr else item.genre,
                        year = if (year.isNotBlank()) year else item.year,
                        rating = if (rating > 0) String.format(java.util.Locale.US, "%.1f", rating) else item.rating,
                        cast = if (castStr.isNotBlank()) castStr else item.cast,
                        runtime = if (runtimeStr.isNotBlank()) runtimeStr else item.runtime,
                        tmdbId = id.toString(),
                        metadataLoaded = true,
                        lastMetadataUpdate = System.currentTimeMillis()
                    )
                    if (updateDb) {
                        playlistDao.updateItem(enriched)
                    }
                    return@withPermit enriched
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "TMDB enrichment failed for ${item.title}", e)
            }
            item
        }
    }

    // Real-time Gemini metadata enrichment for movies and series
    suspend fun enrichItemWithGemini(item: PlaylistItem): PlaylistItem = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("IPTVRepository", "Gemini API Key missing, returning default item")
            return@withContext item
        }

        try {
            val prompt = """
                Você é um enriquecedor de metadados do TMDB. Dado o seguinte título de filme ou série: '${item.title}' (categoria: '${item.groupName}'), retorne um objeto JSON estritamente no seguinte formato:
                {
                  "posterUrl": "https://image.tmdb.org/t/p/w500/...jpg",
                  "backdropUrl": "https://image.tmdb.org/t/p/original/...jpg",
                  "titleLogoUrl": "https://image.tmdb.org/t/p/original/...png",
                  "description": "Sinopse do filme...",
                  "year": "2024",
                  "genre": "Ação / Aventura",
                  "rating": "8.5"
                }
                Requisitos críticos:
                1. Use imagens válidas e reais de posters/backdrops do TMDB. Se não encontrar uma imagem TMDB real exata, use imagens em alta resolução do Unsplash de temas semelhantes.
                2. Para "titleLogoUrl", use um link de imagem real de logo transparente (clearlogo ou hdclearart do TMDB), ou um link de banner de alta qualidade.
                3. Não inclua NENHUM text adicional, nem mesmo blocos de código ```json ou marcações markdown. Apenas a string de JSON bruta.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().apply {
                        put("text", prompt)
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(responseBodyStr)
                val candidates = rootJson.getJSONArray("candidates")
                val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                val rawJsonStr = parts.getJSONObject(0).getString("text").trim()

                val resultJson = JSONObject(rawJsonStr)

                val enrichedItem = item.copy(
                    logoUrl = resultJson.optString("posterUrl", item.logoUrl),
                    backdropUrl = resultJson.optString("backdropUrl", item.backdropUrl),
                    titleLogoUrl = resultJson.optString("titleLogoUrl", item.titleLogoUrl),
                    logo = resultJson.optString("logo", resultJson.optString("titleLogoUrl", item.logo)),
                    description = resultJson.optString("description", item.description),
                    year = resultJson.optString("year", item.year),
                    genre = resultJson.optString("genre", item.genre),
                    rating = resultJson.optString("rating", item.rating)
                )

                // Cache back into Room
                playlistDao.insertAll(listOf(enrichedItem))
                return@withContext enrichedItem
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Gemini Enrichment failed for ${item.title}", e)
        }
        return@withContext item
    }

    // Populate standard high quality cinematic content to guarantee it looks 100% like TMDB out of the box
    suspend fun populateFallbackVodsIfNeeded() {
        val featuredItems = listOf(
            // 1. TENDÊNCIAS
            PlaylistItem(
                title = "Obsessão",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png",
                logo = "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png",
                description = "Uma jovem mulher herda uma antiga mansão isolada, apenas para descobrir que o local esconde segredos obscuros e uma presença obsessiva e aterrorizante que ameaça sua sanidade.",
                year = "14 de maio de 2026",
                genre = "Drama / Suspense",
                rating = "8.2",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Uma Casa na Pradaria",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/rpNau5FSkTtAVCVORJpqUmSXAHJ.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/rpNau5FSkTtAVCVORJpqUmSXAHJ.png",
                logo = "https://image.tmdb.org/t/p/original/rpNau5FSkTtAVCVORJpqUmSXAHJ.png",
                description = "Uma família busca recomeçar a vida em uma pacata região rural, mas forças misteriosas começam a afetar a plantação e o comportamento dos moradores.",
                year = "9 de julho de 2026",
                genre = "Drama / Mistério",
                rating = "7.9",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "The Furious",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/k334z7zxOJlJvOsOwyRF6HClCvi.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/k334z7zxOJlJvOsOwyRF6HClCvi.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/msFWTNrFwfjsHKnMxDUexR9uofh.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/msFWTNrFwfjsHKnMxDUexR9uofh.png",
                logo = "https://image.tmdb.org/t/p/original/msFWTNrFwfjsHKnMxDUexR9uofh.png",
                description = "Um piloto de fuga lendário é forçado a voltar à ativa para salvar seu irmão de uma gangue de criminosos internacionais em alta velocidade.",
                year = "10 de junho de 2026",
                genre = "Ação / Policial",
                rating = "8.7",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Moana",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/megWWuC8tSODx90i0gP0fVM1wVo.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/megWWuC8tSODx90i0gP0fVM1wVo.png",
                logo = "https://image.tmdb.org/t/p/original/megWWuC8tSODx90i0gP0fVM1wVo.png",
                description = "Uma jovem navegadora embarca em uma ousada missão marítima para salvar seu povo e descobrir sua verdadeira identidade lendária.",
                year = "9 de julho de 2026",
                genre = "Animação / Aventura",
                rating = "9.0",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Silo",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/u7vS96H96oI4UIs3pLI85gSIs4u.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/u7vS96H96oI4UIs3pLI85gSIs4u.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/q7776Z9Yg58P6N8v7HkWK6fOunY.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/2wdMrwQHQ28q69IMvqmc3SUAL8W.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/2wdMrwQHQ28q69IMvqmc3SUAL8W.png",
                logo = "https://image.tmdb.org/t/p/original/2wdMrwQHQ28q69IMvqmc3SUAL8W.png",
                description = "Em um futuro degradado e tóxico, uma comunidade existe em um gigantesco silo subterrâneo que se estende por centenas de andares abaixo da terra.",
                year = "4 de maio de 2023",
                genre = "Ficção Científica / Drama",
                rating = "8.3",
                groupName = "Tendências",
                type = "series"
            ),
            PlaylistItem(
                title = "A Morte do Demônio: Em Chamas",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/fteLdvfRnltfLjAEnsl5E3vImnW.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/fteLdvfRnltfLjAEnsl5E3vImnW.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png",
                logo = "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png",
                description = "O despertar de uma força demoníaca ancestral em uma floresta remota liberta espíritos malignos que possuem os corpos dos vivos em um pesadelo implacável.",
                year = "7 de julho de 2026",
                genre = "Terror / Sobrenatural",
                rating = "8.5",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Trunfo",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png",
                logo = "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png",
                description = "Um drama de alta espionagem e intriga política onde um agente secreto joga seu último trunfo para desmascarar uma conspiração global.",
                year = "10 de julho de 2026",
                genre = "Drama / Suspense",
                rating = "8.0",
                groupName = "Tendências",
                type = "series"
            ),
            PlaylistItem(
                title = "A Odisseia",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/jpLOFTHacWoxCTCLRePVlLHxrbo.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/jpLOFTHacWoxCTCLRePVlLHxrbo.png",
                logo = "https://image.tmdb.org/t/p/original/jpLOFTHacWoxCTCLRePVlLHxrbo.png",
                description = "Uma jornada épica inspirada no clássico grego, onde um guerreiro enfrenta monstros mitológicos e a fúria dos deuses para retornar ao seu lar.",
                year = "16 de julho de 2026",
                genre = "Épico / Fantasia",
                rating = "8.9",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Sem Nada a Perder",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/3pzH42BKQ0z30fM9DPwSewvWYLS.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/3pzH42BKQ0z30fM9DPwSewvWYLS.png",
                logo = "https://image.tmdb.org/t/p/original/3pzH42BKQ0z30fM9DPwSewvWYLS.png",
                description = "Dois detetives obstinados entram em um jogo perigoso de gato e rato com um criminoso genial que planeja o maior assalto da história.",
                year = "8 de julho de 2026",
                genre = "Policial / Suspense",
                rating = "8.1",
                groupName = "Tendências",
                type = "movie"
            ),
            PlaylistItem(
                title = "Backrooms",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/oHP5aXQ4gg15t9EsBhgKENeCKdG.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/oHP5aXQ4gg15t9EsBhgKENeCKdG.png",
                logo = "https://image.tmdb.org/t/p/original/oHP5aXQ4gg15t9EsBhgKENeCKdG.png",
                description = "Um jovem cientista acidentalmente escorrega para fora da realidade e acorda em um labirinto infinito de salas amarelas vazias habitadas por uma entidade misteriosa.",
                year = "28 de maio de 2026",
                genre = "Terror / Sci-Fi",
                rating = "8.3",
                groupName = "Tendências",
                type = "movie"
            ),

            // 2. ÚLTIMOS TRAILERS
            PlaylistItem(
                title = "O Fim da Rua",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/e0HtKXHvWKFiYmzP3G8xLo7jo5B.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/e0HtKXHvWKFiYmzP3G8xLo7jo5B.png",
                logo = "https://image.tmdb.org/t/p/original/e0HtKXHvWKFiYmzP3G8xLo7jo5B.png",
                description = "Trailer Oficial Dublado",
                year = "Trailer Oficial Dublado",
                genre = "Ação / Suspense",
                rating = "7.8",
                groupName = "Últimos Trailers",
                type = "movie"
            ),
            PlaylistItem(
                title = "A Morte do Demônio: Em Chamas",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/fteLdvfRnltfLjAEnsl5E3vImnW.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/fteLdvfRnltfLjAEnsl5E3vImnW.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png",
                logo = "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png",
                description = "Teaser de Anúncio da Data",
                year = "Teaser de Anúncio da Data",
                genre = "Terror / Sobrenatural",
                rating = "8.2",
                groupName = "Últimos Trailers",
                type = "movie"
            ),
            PlaylistItem(
                title = "Hope",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/7WOBeoaXUNGzbm79Umio2CcH941.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/7WOBeoaXUNGzbm79Umio2CcH941.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/iEYvlw4yqSp9Ck6Iz90V31QSD7D.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/iEYvlw4yqSp9Ck6Iz90V31QSD7D.png",
                logo = "https://image.tmdb.org/t/p/original/iEYvlw4yqSp9Ck6Iz90V31QSD7D.png",
                description = "Official US Trailer [Subtitled]",
                year = "Official US Trailer [Subtitled]",
                genre = "Drama / Sci-Fi",
                rating = "7.5",
                groupName = "Últimos Trailers",
                type = "movie"
            ),
            PlaylistItem(
                title = "Jackass: Best and Last",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/zdzXhbQgjOQzrQ6Yq5kWepYw7MJ.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/zdzXhbQgjOQzrQ6Yq5kWepYw7MJ.png",
                logo = "https://image.tmdb.org/t/p/original/zdzXhbQgjOQzrQ6Yq5kWepYw7MJ.png",
                description = "Big Red Rocket",
                year = "Big Red Rocket",
                genre = "Comédia / Reality",
                rating = "8.0",
                groupName = "Últimos Trailers",
                type = "movie"
            ),

            // 3. OS MAIS POPULARES
            PlaylistItem(
                title = "A Casa do Dragão",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/vIprMXDcEruwpW4aL5PMTQrkljb.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/vIprMXDcEruwpW4aL5PMTQrkljb.png",
                logo = "https://image.tmdb.org/t/p/original/vIprMXDcEruwpW4aL5PMTQrkljb.png",
                description = "A história da Casa Targaryen, ambientada 200 anos antes dos eventos de Game of Thrones.",
                year = "21 de agosto de 2022",
                genre = "Aventura / Drama",
                rating = "9.1",
                groupName = "Os Mais Populares",
                type = "series"
            ),
            PlaylistItem(
                title = "NCIS: Investigação Naval",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png",
                logo = "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png",
                description = "Casos criminais envolvendo pessoal da Marinha americana investigados por agentes especiais.",
                year = "14 de maio de 2026",
                genre = "Policial / Ação",
                rating = "8.4",
                groupName = "Os Mais Populares",
                type = "series"
            ),
            PlaylistItem(
                title = "Na Zona Cinzenta",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/9w0aK6Vs1YqSp13EhrlZVQ4TJ1u.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/9w0aK6Vs1YqSp13EhrlZVQ4TJ1u.png",
                logo = "https://image.tmdb.org/t/p/original/9w0aK6Vs1YqSp13EhrlZVQ4TJ1u.png",
                description = "Agentes especiais operam nas fronteiras da legalidade internacional para combater terroristas.",
                year = "14 de maio de 2026",
                genre = "Ação / Suspense",
                rating = "8.0",
                groupName = "Os Mais Populares",
                type = "movie"
            ),
            PlaylistItem(
                title = "O Limite do Prazer",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/gAwgHNn9bgNefFEzv2sY6Q2BRVo.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/gAwgHNn9bgNefFEzv2sY6Q2BRVo.png",
                logo = "https://image.tmdb.org/t/p/original/gAwgHNn9bgNefFEzv2sY6Q2BRVo.png",
                description = "Um romance tórrido e perigoso entre um escritor intrigado e uma misteriosa milionária.",
                year = "4 de setembro de 2025",
                genre = "Drama / Romance",
                rating = "7.8",
                groupName = "Os Mais Populares",
                type = "movie"
            ),
            PlaylistItem(
                title = "Obsessão",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png",
                logo = "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png",
                description = "Presença obsessiva e aterrorizante que ameaça a sanidade de uma jovem herdeira.",
                year = "14 de maio de 2026",
                genre = "Drama / Suspense",
                rating = "8.2",
                groupName = "Os Mais Populares",
                type = "movie"
            ),
            PlaylistItem(
                title = "Lei & Ordem",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/rj1CuaIo5BYr9XxlLztZMnWP3YT.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/rj1CuaIo5BYr9XxlLztZMnWP3YT.png",
                logo = "https://image.tmdb.org/t/p/original/rj1CuaIo5BYr9XxlLztZMnWP3YT.png",
                description = "Casos criminais sob a ótica dos detetives policiais e dos promotores de justiça de Nova York.",
                year = "13 de setembro de 1990",
                genre = "Drama / Policial",
                rating = "8.8",
                groupName = "Os Mais Populares",
                type = "series"
            ),
            PlaylistItem(
                title = "Rancho Dutton",
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = "https://image.tmdb.org/t/p/original/aJVroxZZKReiSlASpODQ23qrdGa.jpg",
                logoUrl = "https://image.tmdb.org/t/p/original/aJVroxZZKReiSlASpODQ23qrdGa.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg",
                titleLogoUrl = "https://image.tmdb.org/t/p/original/wQ7FFixqLXD2aOqtoiawyz7IbVx.png",
                clearLogoUrl = "https://image.tmdb.org/t/p/original/wQ7FFixqLXD2aOqtoiawyz7IbVx.png",
                logo = "https://image.tmdb.org/t/p/original/wQ7FFixqLXD2aOqtoiawyz7IbVx.png",
                description = "A luta diária de uma família proprietária de terras em Montana contra construtoras e o governo.",
                year = "15 de outubro de 2026",
                genre = "Drama / Faroeste",
                rating = "8.9",
                groupName = "Os Mais Populares",
                type = "series"
            )
        )

        // Since we want these items to ALWAYS be available for our TMDB categories, we check and insert them
        playlistDao.insertAll(featuredItems)

        val fallbackCategories = featuredItems.map { it.groupName to it.type }.distinct().mapIndexed { idx, (catName, type) ->
            CategoryEntity(
                id = "${type}_fallback_$idx",
                categoryId = "fallback_$idx",
                categoryName = catName,
                type = type,
                displayOrder = idx
            )
        }
        playlistDao.insertCategories(fallbackCategories)

        // Asynchronously enrich fallback items and fetch real-time TMDB content in the background without blocking login
        CoroutineScope(Dispatchers.IO).launch {
            featuredItems.forEach { item ->
                try {
                    enrichItemWithTMDB(item)
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Error background enriching fallback item ${item.title}", e)
                }
            }
            try {
                fetchRealtimeTMDBContent()
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error performing real-time TMDB content refresh", e)
            }
        }
    }

    suspend fun fetchTmdbTrailerKey(item: PlaylistItem): String? = withContext(Dispatchers.IO) {
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val cleanTitle = item.getCleanPtBrTitle()
        val isSeries = item.type == "series" || item.groupName.contains("Série", ignoreCase = true)

        val overrideId = getOverrideTmdbId(item, cleanTitle, isSeries)
        var tmdbId = overrideId ?: 0

        if (tmdbId <= 0 && item.id in 1..999999) {
            tmdbId = item.id
        }

        if (tmdbId <= 0 || tmdbId >= 9900) {
            val mediaType = if (isSeries) "tv" else "movie"
            try {
                val targetYear = extractYearFromItem(item)
                val encodedQuery = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrls = mutableListOf<String>()
                if (targetYear != null) {
                    val yearParam = if (isSeries) "first_air_date_year" else "primary_release_year"
                    searchUrls.add("https://api.themoviedb.org/3/search/$mediaType?api_key=$tmdbApiKey&query=$encodedQuery&$yearParam=$targetYear&language=pt-BR")
                }
                searchUrls.add("https://api.themoviedb.org/3/search/$mediaType?api_key=$tmdbApiKey&query=$encodedQuery&language=pt-BR")

                for (searchUrl in searchUrls) {
                    val request = Request.Builder().url(searchUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrEmpty()) {
                                val results = JSONObject(body).optJSONArray("results")
                                if (results != null && results.length() > 0) {
                                    val chosen = pickBestTmdbSearchResult(results, targetYear, isSeries)
                                    if (chosen != null) {
                                        tmdbId = chosen.optInt("id", 0)
                                    }
                                }
                            }
                        }
                    }
                    if (tmdbId > 0) break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (tmdbId <= 0) return@withContext null

        val mediaType = if (isSeries) "tv" else "movie"
        val videoEndpoints = listOf(
            "https://api.themoviedb.org/3/$mediaType/$tmdbId/videos?api_key=$tmdbApiKey&language=pt-BR",
            "https://api.themoviedb.org/3/$mediaType/$tmdbId/videos?api_key=$tmdbApiKey"
        )

        for (endpoint in videoEndpoints) {
            try {
                val request = Request.Builder().url(endpoint).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val results = JSONObject(body).optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                var fallbackKey: String? = null
                                for (i in 0 until results.length()) {
                                    val videoObj = results.getJSONObject(i)
                                    val site = videoObj.optString("site", "")
                                    val type = videoObj.optString("type", "")
                                    val key = videoObj.optString("key", "")
                                    if (site.equals("YouTube", ignoreCase = true) && key.isNotEmpty()) {
                                        if (type.equals("Trailer", ignoreCase = true)) {
                                            return@withContext key
                                        } else if (fallbackKey == null) {
                                            fallbackKey = key
                                        }
                                    }
                                }
                                if (!fallbackKey.isNullOrEmpty()) {
                                    return@withContext fallbackKey
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext null
    }

    suspend fun fetchRealtimeTMDBContent() = withContext(Dispatchers.IO) {
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val listToInsert = mutableListOf<PlaylistItem>()

        // Helper to enrich a single TMDB item with logo concurrently
        suspend fun buildEnrichedPlaylistItem(
            title: String,
            posterPath: String,
            backdropPath: String,
            overview: String,
            vote: Double,
            date: String,
            groupName: String,
            mediaType: String,
            tmdbId: Int
        ): PlaylistItem = coroutineScope {
            val isMovie = mediaType == "movie"
            val yearVal = if (date.length >= 4) date.substring(0, 4) else ""
            val posterUrl = getTmdbImageUrl(posterPath).orEmpty()
            val backdropUrl = getTmdbImageUrl(backdropPath).orEmpty()

            // Fetch clear logo PNG concurrently for this tmdbId
            val logoPath = if (tmdbId > 0) {
                try {
                    findPreferredTmdbLogo(if (isMovie) "movie" else "tv", tmdbId, tmdbApiKey)
                } catch (e: Exception) {
                    null
                }
            } else null
            val clearLogoUrl = getTmdbImageUrl(logoPath)

            PlaylistItem(
                title = title,
                url = "http://streatv.elementfx.com/get.php",
                posterUrl = posterUrl,
                logoUrl = posterUrl,
                backdropUrl = backdropUrl,
                titleLogoUrl = clearLogoUrl,
                clearLogoUrl = clearLogoUrl,
                logo = clearLogoUrl ?: posterUrl,
                description = overview,
                year = yearVal,
                genre = if (groupName == "Tendências") {
                    if (isMovie) "Filme / Tendência" else "Série / Tendência"
                } else if (groupName == "Últimos Trailers") {
                    "Último Trailer"
                } else {
                    if (isMovie) "Filme Popular" else "Série Popular"
                },
                rating = String.format(java.util.Locale.US, "%.1f", vote),
                groupName = groupName,
                type = if (isMovie) "movie" else "series",
                tmdbId = if (tmdbId > 0) tmdbId.toString() else null
            )
        }

        // 1. Trending (All: Movies & TV) -> "Tendências"
        val trendingList = mutableListOf<PlaylistItem>()
        try {
            val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            val tasks = (0 until results.length()).mapNotNull { i ->
                                val item = results.getJSONObject(i)
                                val mediaType = item.optString("media_type", "movie")
                                if (mediaType != "movie" && mediaType != "tv") return@mapNotNull null

                                val isMovie = mediaType == "movie"
                                val tmdbId = item.optInt("id", 0)
                                val title = if (isMovie) {
                                    item.optString("title", item.optString("original_title", "Filme TMDB"))
                                } else {
                                    item.optString("name", item.optString("original_name", "Série TMDB"))
                                }

                                val posterPath = item.optString("poster_path", "")
                                val backdropPath = item.optString("backdrop_path", "")
                                val overview = item.optString("overview", "")
                                val vote = item.optDouble("vote_average", 0.0)
                                val date = if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")

                                suspend {
                                    buildEnrichedPlaylistItem(
                                        title = title,
                                        posterPath = posterPath,
                                        backdropPath = backdropPath,
                                        overview = overview,
                                        vote = vote,
                                        date = date,
                                        groupName = "Tendências",
                                        mediaType = mediaType,
                                        tmdbId = tmdbId
                                    )
                                }
                            }
                            val items = coroutineScope {
                                tasks.map { task -> async { task() } }.awaitAll()
                            }
                            trendingList.addAll(items)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching trending from TMDB", e)
        }

        // 2. Upcoming Movies -> "Últimos Trailers"
        val upcomingList = mutableListOf<PlaylistItem>()
        try {
            val url = "https://api.themoviedb.org/3/movie/upcoming?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            val tasks = (0 until results.length()).map { i ->
                                val movie = results.getJSONObject(i)
                                val tmdbId = movie.optInt("id", 0)
                                val title = movie.optString("title", movie.optString("original_title", "Trailer TMDB"))
                                val posterPath = movie.optString("poster_path", "")
                                val backdropPath = movie.optString("backdrop_path", "")
                                val overview = movie.optString("overview", "")
                                val vote = movie.optDouble("vote_average", 0.0)
                                val date = movie.optString("release_date", "")

                                suspend {
                                    buildEnrichedPlaylistItem(
                                        title = title,
                                        posterPath = posterPath,
                                        backdropPath = backdropPath,
                                        overview = overview,
                                        vote = vote,
                                        date = date,
                                        groupName = "Últimos Trailers",
                                        mediaType = "movie",
                                        tmdbId = tmdbId
                                    )
                                }
                            }
                            val items = coroutineScope {
                                tasks.map { task -> async { task() } }.awaitAll()
                            }
                            upcomingList.addAll(items)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching upcoming trailers from TMDB", e)
        }

        // 3. Popular Movies & TV Shows -> "Os Mais Populares"
        val popularList = mutableListOf<PlaylistItem>()
        val popularMovies = mutableListOf<PlaylistItem>()
        val popularTV = mutableListOf<PlaylistItem>()

        // 3a. Popular Movies
        try {
            val url = "https://api.themoviedb.org/3/movie/popular?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            val tasks = (0 until results.length()).map { i ->
                                val movie = results.getJSONObject(i)
                                val tmdbId = movie.optInt("id", 0)
                                val title = movie.optString("title", movie.optString("original_title", "Filme TMDB"))
                                val posterPath = movie.optString("poster_path", "")
                                val backdropPath = movie.optString("backdrop_path", "")
                                val overview = movie.optString("overview", "")
                                val vote = movie.optDouble("vote_average", 0.0)
                                val date = movie.optString("release_date", "")

                                suspend {
                                    buildEnrichedPlaylistItem(
                                        title = title,
                                        posterPath = posterPath,
                                        backdropPath = backdropPath,
                                        overview = overview,
                                        vote = vote,
                                        date = date,
                                        groupName = "Os Mais Populares",
                                        mediaType = "movie",
                                        tmdbId = tmdbId
                                    )
                                }
                            }
                            val items = coroutineScope {
                                tasks.map { task -> async { task() } }.awaitAll()
                            }
                            popularMovies.addAll(items)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching popular movies from TMDB", e)
        }

        // 3b. Popular TV Shows
        try {
            val url = "https://api.themoviedb.org/3/tv/popular?api_key=$tmdbApiKey&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            val tasks = (0 until results.length()).map { i ->
                                val tv = results.getJSONObject(i)
                                val tmdbId = tv.optInt("id", 0)
                                val title = tv.optString("name", tv.optString("original_name", "Série TMDB"))
                                val posterPath = tv.optString("poster_path", "")
                                val backdropPath = tv.optString("backdrop_path", "")
                                val overview = tv.optString("overview", "")
                                val vote = tv.optDouble("vote_average", 0.0)
                                val date = tv.optString("first_air_date", "")

                                suspend {
                                    buildEnrichedPlaylistItem(
                                        title = title,
                                        posterPath = posterPath,
                                        backdropPath = backdropPath,
                                        overview = overview,
                                        vote = vote,
                                        date = date,
                                        groupName = "Os Mais Populares",
                                        mediaType = "tv",
                                        tmdbId = tmdbId
                                    )
                                }
                            }
                            val items = coroutineScope {
                                tasks.map { task -> async { task() } }.awaitAll()
                            }
                            popularTV.addAll(items)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching popular series from TMDB", e)
        }

        // Interleave popular movies and series to create a balanced popularList
        val maxPopularLen = maxOf(popularMovies.size, popularTV.size)
        for (i in 0 until maxPopularLen) {
            if (i < popularMovies.size) {
                popularList.add(popularMovies[i])
            }
            if (i < popularTV.size) {
                popularList.add(popularTV[i])
            }
        }

        // Combine them all in a single list to insert
        listToInsert.addAll(trendingList)
        listToInsert.addAll(upcomingList)
        listToInsert.addAll(popularList)

        // Only clear and write if we successfully fetched at least some lists to avoid leaving it blank!
        if (trendingList.isNotEmpty() || upcomingList.isNotEmpty() || popularList.isNotEmpty()) {
            playlistDao.replaceMainSectionsItems(listToInsert)
            Log.d("IPTVRepository", "Successfully fetched, cleared, and inserted ${listToInsert.size} new real-time TMDB items in exact order!")
        } else {
            Log.w("IPTVRepository", "Could not fetch any items from TMDB API; keeping existing cached items.")
        }
    }

    suspend fun syncMainSectionsMetadata() = withContext(Dispatchers.IO) {
        try {
            Log.d("IPTVRepository", "Starting asynchronous background metadata re-fetch for main sections...")
            val items = playlistDao.getMainSectionsItems()
            if (items.isEmpty()) {
                Log.d("IPTVRepository", "No items found in main sections for metadata sync.")
                return@withContext
            }

            // Partition to prioritize "texto puro" (items currently without a title logo)
            val (withoutLogo, withLogo) = items.partition { it.titleLogoUrl.isNullOrEmpty() && it.logo.isNullOrEmpty() }

            Log.d("IPTVRepository", "Sync partitioned: ${withoutLogo.size} items without logo, ${withLogo.size} items with logo.")

            // 1. Process those without logo first
            withoutLogo.forEach { item ->
                try {
                    enrichItemWithTMDB(item)
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Error background sync for ${item.title}", e)
                }
            }

            // 2. Refresh items with logos to guarantee fresh data
            withLogo.forEach { item ->
                try {
                    enrichItemWithTMDB(item)
                } catch (e: Exception) {
                    Log.e("IPTVRepository", "Error background sync refresh for ${item.title}", e)
                }
            }

            Log.d("IPTVRepository", "Asynchronous metadata sync for main sections completed successfully.")
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Failed to run syncMainSectionsMetadata", e)
        }
    }

    suspend fun getRealCastFromTMDB(item: PlaylistItem): List<CastMember> = withContext(Dispatchers.IO) {
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val cleanTitle = cleanTitleForSearch(item.title)
        val isSeries = item.type == "series" || item.groupName.contains("Série", ignoreCase = true) || item.groupName.contains("Serie", ignoreCase = true)
        val searchType = if (isSeries) "tv" else "movie"

        try {
            val overrideId = getOverrideTmdbId(item, cleanTitle, isSeries)
            var tmdbId = overrideId ?: item.tmdbId?.toIntOrNull() ?: 0

            if (tmdbId == 0) {
                val targetYear = extractYearFromItem(item)
                val encodedQuery = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrls = mutableListOf<String>()
                if (targetYear != null) {
                    val yearParam = if (isSeries) "first_air_date_year" else "primary_release_year"
                    searchUrls.add("https://api.themoviedb.org/3/search/$searchType?api_key=$tmdbApiKey&query=$encodedQuery&$yearParam=$targetYear&language=pt-BR")
                }
                searchUrls.add("https://api.themoviedb.org/3/search/$searchType?api_key=$tmdbApiKey&query=$encodedQuery&language=pt-BR")

                for (searchUrl in searchUrls) {
                    val searchRequest = Request.Builder().url(searchUrl).build()
                    client.newCall(searchRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val results = JSONObject(body).optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val chosen = pickBestTmdbSearchResult(results, targetYear, isSeries)
                                if (chosen != null) {
                                    tmdbId = chosen.optInt("id")
                                }
                            }
                        }
                    }
                    if (tmdbId > 0) break
                }
            }

            if (tmdbId > 0) {
                // Fetch credits for this tmdbId
                val creditsUrl = "https://api.themoviedb.org/3/$searchType/$tmdbId/credits?api_key=$tmdbApiKey&language=pt-BR"
                val creditsRequest = Request.Builder().url(creditsUrl).build()
                client.newCall(creditsRequest).execute().use { credResponse ->
                    if (credResponse.isSuccessful) {
                        val credBody = credResponse.body?.string() ?: ""
                        val castArray = JSONObject(credBody).optJSONArray("cast")
                        if (castArray != null) {
                            val castList = mutableListOf<CastMember>()
                            val count = minOf(castArray.length(), 15)
                            for (i in 0 until count) {
                                val member = castArray.getJSONObject(i)
                                val name = member.optString("name", "")
                                val character = member.optString("character", "")
                                val profilePath = member.optString("profile_path", "")
                                val photoUrl = getTmdbImageUrl(profilePath) ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
                                castList.add(CastMember(name = name, character = character, photoUrl = photoUrl))
                            }
                            if (castList.isNotEmpty()) {
                                return@withContext castList
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error getting real cast from TMDB for ${item.title}", e)
        }

        // Procedural Fallback of 4 prominent actors
        return@withContext listOf(
            CastMember("Christian Bale", "Protagonista", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Margot Robbie", "Co-Protagonista", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80"),
            CastMember("Cillian Murphy", "Mentor", "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=150&q=80"),
            CastMember("Florence Pugh", "Rival", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80")
        )
    }

    suspend fun getRealSeasonsFromTMDB(item: PlaylistItem): List<Season> = withContext(Dispatchers.IO) {
        if (item.type != "series") return@withContext emptyList()
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val cleanTitle = cleanTitleForSearch(item.title)

        // 1. Resolve real series ID for Xtream URL mappings
        val realSeries = verifyAndGetRealPlaylistItem(item)
        val seriesId = if (realSeries != null && realSeries.url.contains("/series/")) {
            try {
                realSeries.url.substringAfterLast("/").substringBefore(".")
            } catch (e: Exception) {
                null
            }
        } else if (item.url.contains("/series/")) {
            try {
                item.url.substringAfterLast("/").substringBefore(".")
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val xtreamEpisodes = if (seriesId != null) {
            getXtreamEpisodesMap(seriesId)
        } else {
            emptyMap()
        }

        try {
            val overrideId = getOverrideTmdbId(item, cleanTitle, true)
            var tmdbId = overrideId ?: item.tmdbId?.toIntOrNull() ?: 0

            if (tmdbId == 0) {
                val targetYear = extractYearFromItem(item)
                val encodedQuery = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val searchUrls = mutableListOf<String>()
                if (targetYear != null) {
                    searchUrls.add("https://api.themoviedb.org/3/search/tv?api_key=$tmdbApiKey&query=$encodedQuery&first_air_date_year=$targetYear&language=pt-BR")
                }
                searchUrls.add("https://api.themoviedb.org/3/search/tv?api_key=$tmdbApiKey&query=$encodedQuery&language=pt-BR")

                for (searchUrl in searchUrls) {
                    val searchRequest = Request.Builder().url(searchUrl).build()
                    client.newCall(searchRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val results = JSONObject(body).optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val chosen = pickBestTmdbSearchResult(results, targetYear, true)
                                if (chosen != null) {
                                    tmdbId = chosen.optInt("id")
                                }
                            }
                        }
                    }
                    if (tmdbId > 0) break
                }
            }

            if (tmdbId > 0) {
                // Fetch TV Details to get the list of seasons
                val detailsUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey&language=pt-BR"
                val detailsRequest = Request.Builder().url(detailsUrl).build()
                client.newCall(detailsRequest).execute().use { detResponse ->
                    if (detResponse.isSuccessful) {
                        val detBody = detResponse.body?.string() ?: ""
                        val seasonsArray = JSONObject(detBody).optJSONArray("seasons")
                        if (seasonsArray != null) {
                            val seasonsList = mutableListOf<Season>()
                            for (s in 0 until seasonsArray.length()) {
                                val seasonObj = seasonsArray.getJSONObject(s)
                                val seasonNumber = seasonObj.optInt("season_number", -1)
                                val seasonName = seasonObj.optString("name", "Temporada $seasonNumber")
                                if (seasonNumber <= 0 && seasonsArray.length() > 1) {
                                    continue // Skip specials / season 0
                                }

                                try {
                                    val seasonDetailsUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey&language=pt-BR"
                                    val seasonDetailsRequest = Request.Builder().url(seasonDetailsUrl).build()
                                    client.newCall(seasonDetailsRequest).execute().use { sDetResponse ->
                                        if (sDetResponse.isSuccessful) {
                                            val sDetBody = sDetResponse.body?.string() ?: ""
                                            val episodesArray = JSONObject(sDetBody).optJSONArray("episodes")
                                            if (episodesArray != null) {
                                                val episodesList = mutableListOf<Episode>()
                                                for (e in 0 until episodesArray.length()) {
                                                    val epObj = episodesArray.getJSONObject(e)
                                                    val epName = epObj.optString("name", "Episódio ${e + 1}")
                                                    val epOverview = epObj.optString("overview", "Sem sinopse disponível.")
                                                    val runtime = epObj.optInt("runtime", 45)
                                                    val stillPath = epObj.optString("still_path", "")

                                                    val airDateStr = epObj.optString("air_date", "").let { if (it.equals("null", ignoreCase = true)) "" else it }
                                                    var isReleased = true
                                                    var formattedDate: String? = null
                                                    if (airDateStr.isNotEmpty()) {
                                                        try {
                                                            val sdfTmdb = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                            val dateObj = sdfTmdb.parse(airDateStr)
                                                            if (dateObj != null) {
                                                                val sdfApp = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                                                formattedDate = sdfApp.format(dateObj)
                                                                isReleased = dateObj.time <= System.currentTimeMillis()
                                                            }
                                                        } catch (ex: Exception) {
                                                            Log.e("IPTVRepository", "Error parsing date: $airDateStr", ex)
                                                        }
                                                    }

                                                    val thumbUrl = getTmdbImageUrl(stillPath) ?: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80"

                                                    val playUrl = xtreamEpisodes[Pair(seasonNumber, e + 1)]
                                                    episodesList.add(
                                                        Episode(
                                                            title = epName,
                                                            duration = "$runtime min",
                                                            synopsis = epOverview,
                                                            thumbnailUrl = thumbUrl,
                                                            releaseDate = if (isReleased) null else formattedDate,
                                                            isReleased = isReleased,
                                                            playUrl = playUrl
                                                        )
                                                    )
                                                }
                                                if (episodesList.isNotEmpty()) {
                                                    seasonsList.add(Season(name = seasonName, episodes = episodesList))
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("IPTVRepository", "Error fetching season $seasonNumber for ${item.title}", e)
                                }
                            }
                            if (seasonsList.isNotEmpty()) {
                                return@withContext seasonsList
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error getting real seasons from TMDB for ${item.title}", e)
        }

        // Procedural Fallback if TMDB request fails
        return@withContext generateFallbackSeasons(item, xtreamEpisodes)
    }

    private fun generateFallbackSeasons(item: PlaylistItem, xtreamEpisodes: Map<Pair<Int, Int>, String>): List<Season> {
        val totalSeasons = if (item.id % 2 == 0) 3 else 2
        return List(totalSeasons) { sIdx ->
            val seasonNum = sIdx + 1
            val totalEp = 12
            Season(
                name = "Temporada $seasonNum",
                episodes = List(totalEp) { eIdx ->
                    val epNum = eIdx + 1
                    val playUrl = xtreamEpisodes[Pair(seasonNum, epNum)]
                    Episode(
                        title = "Episódio $epNum: Mistérios Revelados",
                        duration = "45 min",
                        synopsis = "As consequências das escolhas passadas vêm à tona neste capítulo eletrizante de ${item.title}.",
                        thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=300&q=80",
                        releaseDate = null,
                        isReleased = true,
                        playUrl = playUrl
                    )
                }
            )
        }
    }

    suspend fun findRealEpisodeItem(seriesTitle: String, seasonName: String, episodeIndex: Int, episodeTitle: String): PlaylistItem? = withContext(Dispatchers.IO) {
        var allSeriesItems = playlistDao.getItemsByType("series_episode").firstOrNull() ?: emptyList()
        if (allSeriesItems.isEmpty()) {
            allSeriesItems = playlistDao.getItemsByType("series").firstOrNull() ?: emptyList()
        }
        if (allSeriesItems.isEmpty()) return@withContext null

        val cleanSeriesTitle = cleanTitleForSearch(seriesTitle).lowercase()
        val seasonNumMatch = digitsRegex.find(seasonName)
        val seasonNum = seasonNumMatch?.value?.toIntOrNull() ?: 1
        val epNum = episodeIndex + 1

        // Formato de padrões comuns em arquivos de listas IPTV
        val s0e0Pattern = "s%02de%02d".format(seasonNum, epNum) // s01e01
        val s_e_Pattern = "s%de%d".format(seasonNum, epNum)     // s1e1
        val s_space_e_Pattern = "s%d e%d".format(seasonNum, epNum) // s1 e1
        val s0_space_e0_Pattern = "s%02d e%02d".format(seasonNum, epNum) // s01 e01
        val crossPattern = "%dx%02d".format(seasonNum, epNum) // 1x01
        val crossPatternSimple = "%dx%d".format(seasonNum, epNum) // 1x1
        val epWordPattern = "ep %d".format(epNum) // ep 1
        val epWordZeroPattern = "ep %02d".format(epNum) // ep 01
        val epFullWordPattern = "episódio %d".format(epNum) // episódio 1
        val epFullWordZeroPattern = "episódio %02d".format(epNum) // episódio 01

        val candidate = allSeriesItems.firstOrNull { item ->
            val itemTitleLower = item.title.lowercase()
            val containsSeriesName = itemTitleLower.contains(cleanSeriesTitle) ||
                    cleanSeriesTitle.contains(itemTitleLower) ||
                    cleanTitleForSearch(item.title).lowercase().contains(cleanSeriesTitle)

            containsSeriesName && (
                    itemTitleLower.contains(s0e0Pattern) ||
                            itemTitleLower.contains(s_e_Pattern) ||
                            itemTitleLower.contains(s_space_e_Pattern) ||
                            itemTitleLower.contains(s0_space_e0_Pattern) ||
                            itemTitleLower.contains(crossPattern) ||
                            itemTitleLower.contains(crossPatternSimple) ||
                            itemTitleLower.contains("e%02d".format(epNum)) ||
                            (itemTitleLower.contains(epWordPattern) && itemTitleLower.contains("temporada $seasonNum")) ||
                            (itemTitleLower.contains(epWordZeroPattern) && itemTitleLower.contains("temporada $seasonNum")) ||
                            (itemTitleLower.contains(epFullWordPattern) && itemTitleLower.contains("temporada $seasonNum")) ||
                            (itemTitleLower.contains(epFullWordZeroPattern) && itemTitleLower.contains("temporada $seasonNum"))
                    )
        }

        if (candidate != null) {
            Log.d("IPTVRepository", "Found synchronized panel episode item: ${candidate.title} -> ${candidate.url}")
            return@withContext candidate
        }

        val looseCandidate = allSeriesItems.firstOrNull { item ->
            val itemTitleLower = item.title.lowercase()
            val containsSeriesName = itemTitleLower.contains(cleanSeriesTitle) ||
                    cleanSeriesTitle.contains(itemTitleLower)

            containsSeriesName && (
                    itemTitleLower.contains(s0e0Pattern) ||
                            itemTitleLower.contains(s_e_Pattern) ||
                            itemTitleLower.contains(crossPattern) ||
                            itemTitleLower.contains("e%02d".format(epNum)) ||
                            itemTitleLower.contains("ep %d".format(epNum)) ||
                            itemTitleLower.contains("ep %02d".format(epNum)) ||
                            itemTitleLower.contains("ep%d".format(epNum)) ||
                            itemTitleLower.contains("ep%02d".format(epNum))
                    )
        }

        if (looseCandidate != null) {
            Log.d("IPTVRepository", "Found loose synchronized panel episode item: ${looseCandidate.title} -> ${looseCandidate.url}")
            return@withContext looseCandidate
        }

        return@withContext null
    }

    private fun normalizeString(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        val withoutAccents = normalized.replace(accentRegex, "")
        return withoutAccents.lowercase()
            .replace(specialCharsRegex, " ") // replace special chars with spaces
            .replace(spacesRegex, " ")         // normalize spaces
            .trim()
    }

    private fun getSequelNumbers(s: String): Set<String> {
        return digitsRegex.findAll(s.lowercase())
            .map { it.value }
            .filter { it.length < 4 } // Ignore 4-digit years
            .toSet()
    }

    private fun sequelNumbersMatch(title1: String, title2: String): Boolean {
        val seq1 = getSequelNumbers(title1)
        val seq2 = getSequelNumbers(title2)
        return seq1 == seq2
    }

    private fun cleanIptvTitle(title: String): String {
        val temp = normalizeString(title)
        return temp.replace(keywordsRegex, "").replace(spacesRegex, " ").trim()
    }

    private fun titlesMatchFuzzy(title1: String, title2: String): Boolean {
        val t1 = cleanIptvTitle(title1)
        val t2 = cleanIptvTitle(title2)
        if (t1.isEmpty() || t2.isEmpty()) return false
        if (t1 == t2 || t1.contains(t2) || t2.contains(t1)) {
            return sequelNumbersMatch(title1, title2)
        }

        val stopwords = setOf(
            "filme", "filmes", "movie", "movies", "serie", "series", "season", "temporada",
            "legendado", "dublado", "completo", "hd", "fhd", "4k", "sd", "trailer", "teaser", "oficial",
            "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "para"
        )

        // Word overlap checking - we must include single-character digits (like "4" or "5") so sequel suffixes are not ignored
        val words1 = t1.split(" ").filter { (it.length >= 2 || it.any { c -> c.isDigit() }) && !stopwords.contains(it) }.toSet()
        val words2 = t2.split(" ").filter { (it.length >= 2 || it.any { c -> c.isDigit() }) && !stopwords.contains(it) }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return false

        val intersection = words1.intersect(words2)
        val minSize = minOf(words1.size, words2.size)
        // If 75% or more of the words in the shorter title match the longer title
        return (intersection.size.toFloat() / minSize.toFloat() >= 0.75f) && sequelNumbersMatch(title1, title2)
    }

    suspend fun getXtreamEpisodesMap(seriesId: String): Map<Pair<Int, Int>, String> = withContext(Dispatchers.IO) {
        val resultMap = mutableMapOf<Pair<Int, Int>, String>()
        val creds = getSavedCredentials()
        val username = creds.first ?: return@withContext emptyMap()
        val password = creds.second ?: return@withContext emptyMap()
        val rawServer = getSavedServerUrl()
        var serverUrl = rawServer.trim()
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://$serverUrl"
        }
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length - 1)
        }

        val url = "$serverUrl/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"
        Log.d("IPTVRepository", "Fetching Xtream series info from: $url")
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.trim().startsWith("{")) {
                        val jsonObject = JSONObject(bodyStr)
                        val episodesObj = jsonObject.optJSONObject("episodes")
                        if (episodesObj != null) {
                            val keys = episodesObj.keys()
                            while (keys.hasNext()) {
                                val seasonKey = keys.next()
                                val seasonNum = seasonKey.toIntOrNull() ?: continue
                                val epArray = episodesObj.optJSONArray(seasonKey) ?: continue
                                for (i in 0 until epArray.length()) {
                                    val epObj = epArray.getJSONObject(i)
                                    val epId = epObj.optString("id")
                                    val epNumStr = epObj.optString("episode_num")
                                    val epNum = epNumStr.substringBefore(".").toIntOrNull() ?: (i + 1)
                                    val containerExt = epObj.optString("container_extension", "mp4")

                                    if (epId.isNotEmpty()) {
                                        val playUrl = "$serverUrl/series/$username/$password/$epId.$containerExt"
                                        resultMap[Pair(seasonNum, epNum)] = playUrl
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching/parsing Xtream episodes", e)
        }
        return@withContext resultMap
    }

    suspend fun verifyAndGetRealPlaylistItem(item: PlaylistItem): PlaylistItem? = withContext(Dispatchers.IO) {
        val allItems = playlistDao.getAllItems().firstOrNull() ?: emptyList()

        // 0. Exact ID match
        val exactIdMatch = allItems.firstOrNull { dbItem -> dbItem.id == item.id && dbItem.url != "http://streatv.elementfx.com/get.php" && dbItem.url.isNotBlank() }
        if (exactIdMatch != null) return@withContext exactIdMatch

        if (item.url != "http://streatv.elementfx.com/get.php" && item.url.isNotBlank()) {
            return@withContext item
        }

        // 1. Exact title match
        val exactMatch = allItems.firstOrNull { dbItem ->
            dbItem.url != "http://streatv.elementfx.com/get.php" &&
                    dbItem.url.isNotBlank() &&
                    dbItem.type == item.type &&
                    dbItem.title.equals(item.title, ignoreCase = true)
        }
        if (exactMatch != null) return@withContext exactMatch

        return@withContext item
    }

    suspend fun searchTmdbSuggestions(query: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val tmdbApiKey = "355b405bb005248e382a8b400ebab70b"
        val resultsList = mutableListOf<PlaylistItem>()
        try {
            val encodedQuery = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&query=$encodedQuery&language=pt-BR"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val results = JSONObject(body).optJSONArray("results")
                    if (results != null) {
                        val maxCount = minOf(results.length(), 20)
                        for (i in 0 until maxCount) {
                            val item = results.getJSONObject(i)
                            val mediaType = item.optString("media_type", "")
                            if (mediaType != "movie" && mediaType != "tv") continue

                            val isMovie = mediaType == "movie"
                            val title = if (isMovie) {
                                item.optString("title", item.optString("original_title", ""))
                            } else {
                                item.optString("name", item.optString("original_name", ""))
                            }

                            if (title.isBlank()) continue

                            val posterPath = item.optString("poster_path", "")
                            val backdropPath = item.optString("backdrop_path", "")
                            val overview = item.optString("overview", "")
                            val vote = item.optDouble("vote_average", 0.0)
                            val date = if (isMovie) item.optString("release_date", "") else item.optString("first_air_date", "")
                            val yearVal = if (date.length >= 4) date.substring(0, 4) else ""

                            val logoUrl = getTmdbImageUrl(posterPath).orEmpty()
                            val backdropUrl = getTmdbImageUrl(backdropPath).orEmpty()

                            resultsList.add(
                                PlaylistItem(
                                    title = title,
                                    url = "http://streatv.elementfx.com/get.php",
                                    logoUrl = logoUrl,
                                    backdropUrl = backdropUrl,
                                    titleLogoUrl = null,
                                    description = overview,
                                    year = yearVal,
                                    genre = if (isMovie) "Filme TMDB" else "Série TMDB",
                                    rating = if (vote > 0) String.format(java.util.Locale.US, "%.1f", vote) else "TMDB",
                                    groupName = "Sugestões TMDB",
                                    type = if (isMovie) "movie" else "series"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error searching TMDB suggestions for $query", e)
        }
        resultsList
    }

    suspend fun startBackgroundMetadataPrefetch() = withContext(Dispatchers.IO) {
        if (isPrefetchRunning) {
            Log.d("IPTVRepository", "Background metadata prefetch already running.")
            return@withContext
        }
        isPrefetchRunning = true
        Log.d("IPTVRepository", "Starting intelligent parallel background metadata prefetch...")

        try {
            // Phase 0: Override items & collision fixes ("Fúria", "Doc", Céu de Fúria, "Um mundo diferente", "Colisão")
            try {
                val furiaItems = playlistDao.getItemsByTitleQuery("%furia%") + playlistDao.getItemsByTitleQuery("%fúria%")
                val docItems = playlistDao.getItemsByTitleQuery("%doc%")
                val ruaItems = playlistDao.getItemsByTitleQuery("%fim da rua%") + playlistDao.getItemsByTitleQuery("%oak street%")
                val mundoItems = playlistDao.getItemsByTitleQuery("%mundo diferente%") + playlistDao.getItemsByTitleQuery("%different world%")
                val colisaoItems = playlistDao.getItemsByTitleQuery("%colis%") + playlistDao.getItemsByTitleQuery("%collision%")
                val candidates = (furiaItems + docItems + ruaItems + mundoItems + colisaoItems).distinctBy { it.id }

                val itemsToUpdate = mutableListOf<PlaylistItem>()
                for (candidate in candidates) {
                    val isSeries = candidate.type == "series" || candidate.groupName.contains("Série", ignoreCase = true) || candidate.groupName.contains("Serie", ignoreCase = true)
                    val cleanTitle = cleanTitleForSearch(candidate.title)
                    val overrideId = getOverrideTmdbId(candidate, cleanTitle, isSeries)

                    val hasWrongSeriesId = !isSeries && (candidate.tmdbId == "292533" || candidate.tmdbId == "287238")
                    val hasWrongDocId = isSeries && candidate.groupName.contains("netflix", ignoreCase = true) && candidate.tmdbId == "255055"
                    val hasWrongOverride = overrideId != null && candidate.tmdbId != overrideId.toString()
                    val hasWrongMundoId = (cleanTitle.contains("mundo diferente", ignoreCase = true) || candidate.title.contains("mundo diferente", ignoreCase = true)) && candidate.tmdbId != "305357"
                    val hasWrongColisaoId = (cleanTitle.contains("colis", ignoreCase = true) || candidate.title.contains("colis", ignoreCase = true)) && candidate.tmdbId != "331033" && (isSeries || candidate.groupName.contains("serie", ignoreCase = true))
                    val hasEnglishOakLogo = candidate.clearLogoUrl?.contains("8VDfGY16827yyWH0R8itxLtfgK1") == true ||
                            candidate.titleLogoUrl?.contains("8VDfGY16827yyWH0R8itxLtfgK1") == true

                    if (hasWrongSeriesId || hasWrongDocId || hasWrongOverride || hasWrongMundoId || hasWrongColisaoId || hasEnglishOakLogo || !candidate.metadataLoaded) {
                        val itemToEnrich = candidate.copy(
                            tmdbId = overrideId?.toString() ?: if (hasWrongSeriesId) null else candidate.tmdbId,
                            metadataLoaded = false,
                            posterUrl = if (hasWrongSeriesId || hasWrongOverride || hasWrongMundoId || hasWrongColisaoId) null else candidate.posterUrl,
                            backdropUrl = if (hasWrongSeriesId || hasWrongOverride || hasWrongMundoId || hasWrongColisaoId) null else candidate.backdropUrl,
                            clearLogoUrl = if (hasWrongSeriesId || hasEnglishOakLogo || hasWrongOverride || hasWrongMundoId || hasWrongColisaoId) null else candidate.clearLogoUrl,
                            titleLogoUrl = if (hasWrongSeriesId || hasEnglishOakLogo || hasWrongOverride || hasWrongMundoId || hasWrongColisaoId) null else candidate.titleLogoUrl
                        )
                        try {
                            val enriched = enrichItemWithTMDB(itemToEnrich, updateDb = false)
                            if (enriched.metadataLoaded) {
                                itemsToUpdate.add(enriched)
                            }
                        } catch (e: Exception) {
                            Log.e("IPTVRepository", "Error healing candidate ${candidate.title}", e)
                        }
                    }
                }
                if (itemsToUpdate.isNotEmpty()) {
                    playlistDao.updateItems(itemsToUpdate)
                }
            } catch (e: Exception) {
                Log.e("IPTVRepository", "Error querying override collision items", e)
            }

            // Phase 1: Priority Prefetching (Visible items: Tendências, Os Mais Populares, Banners)
            val priorityItems = playlistDao.getPriorityUnenrichedItems().take(30)
            Log.d("IPTVRepository", "Phase 1: Found ${priorityItems.size} priority items for background metadata enrichment")

            if (priorityItems.isNotEmpty()) {
                priorityItems.chunked(3).forEach { chunk ->
                    if (!isActive) return@withContext
                    val enrichedChunk = chunk.map { item ->
                        async(Dispatchers.IO) {
                            try {
                                enrichItemWithTMDB(item, updateDb = false)
                            } catch (e: Exception) {
                                item
                            }
                        }
                    }.awaitAll()
                    val toUpdate = enrichedChunk.filter { it.metadataLoaded }
                    if (toUpdate.isNotEmpty()) {
                        playlistDao.updateItems(toUpdate)
                    }
                    delay(100)
                }
            }

            // Phase 2: Gentle Catalog Prefetching (Limited batch with low CPU/RAM footprint for TV devices)
            val remainingItems = playlistDao.getAllUnenrichedItems().take(20)
            Log.d("IPTVRepository", "Phase 2: Processing up to ${remainingItems.size} catalog items gently")

            if (remainingItems.isNotEmpty()) {
                remainingItems.chunked(3).forEach { chunk ->
                    if (!isActive) return@withContext
                    val enrichedChunk = chunk.map { item ->
                        async(Dispatchers.IO) {
                            try {
                                enrichItemWithTMDB(item, updateDb = false)
                            } catch (e: Exception) {
                                item
                            }
                        }
                    }.awaitAll()
                    val toUpdate = enrichedChunk.filter { it.metadataLoaded }
                    if (toUpdate.isNotEmpty()) {
                        playlistDao.updateItems(toUpdate)
                    }
                    delay(300)
                }
            }
            Log.d("IPTVRepository", "Gentle background metadata prefetch completed successfully!")
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error during background metadata prefetch", e)
        } finally {
            isPrefetchRunning = false
        }
    }

    companion object {
        private val accentRegex = Regex("\\p{M}")
        private val specialCharsRegex = Regex("[^a-z0-9\\s]")
        private val spacesRegex = Regex("\\s+")
        private val keywordsRegex = Regex("\\b(?:dublado|legendado|dual|audio|multiaudio|4k|fhd|hdtv|hd|sd|uhd|hevc|h264|h265|x264|x265|completo|temporada|s0|s1|s2|s3|s4|s5|ep|episodio|capitulo|mkv|mp4|avi)\\b")

        private val searchPatternsCombined = Regex("\\[.*?\\]|\\(.*?\\)|(?i)1080p|(?i)720p|(?i)2160p|(?i)4k|(?i)fhd|(?i)hd|(?i)sd|(?i)uhd|(?i)dublado|(?i)legendado|(?i)dual|(?i)audio|(?i)h264|(?i)h265|(?i)hevc|(?i)x264|(?i)s\\d{1,2}e\\d{1,2}|(?i)s\\d{1,2} e\\d{1,2}|(?i)temporada|(?i)ep\\s?\\d{1,3}|(?i)episodio\\s?\\d{1,3}")
        private val dashEndRegex = Regex(" - $")
        private val hyphenEndRegex = Regex("-$")
        private val colonEndRegex = Regex(" :$")
        private val digitsRegex = Regex("\\d+")
    }
}
