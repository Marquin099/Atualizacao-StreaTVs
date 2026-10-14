package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PlaylistItem
import com.example.data.model.UserProfile
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.Season
import com.example.data.repository.IPTVRepository
import com.example.data.util.cleanSeriesName
import com.example.data.util.groupSeriesList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed class Screen {
    object Splash : Screen()
    object Login : Screen()
    object ProfileSelection : Screen()
    object Main : Screen()
    data class Player(val item: PlaylistItem) : Screen()
    data class Detail(val item: PlaylistItem, val autoPlay: Boolean = false) : Screen()
}

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String, val progress: Float) : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

class IPTVViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IPTVRepository(application)
    private val attemptedEnrichments = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    // Screen State Machine
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Sync / Login State
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Profiles Flow
    val profiles: StateFlow<List<UserProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile: StateFlow<UserProfile?> = _selectedProfile.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<PlaylistItem>> = _selectedProfile
        .flatMapLatest { profile ->
            if (profile != null) {
                repository.getContinueWatching(profile.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Navigation Tab: "Inicio", "Canais", "Filmes", "Series"
    private val _selectedTab = MutableStateFlow("Inicio")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    // Playlist Content Flows (Filtered dynamically)
    val allItems: StateFlow<List<PlaylistItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search Query & Active State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
    }

    // TMDB Banner Slideshow State
    private val _bannerIndex = MutableStateFlow(0)
    val bannerIndex: StateFlow<Int> = _bannerIndex.asStateFlow()

    private var bannerJob: Job? = null

    // TMDB Curated Banner List (Populates dynamically from Tendências category)
    val bannerItems: StateFlow<List<PlaylistItem>> = repository.bannerItems
        .map { list ->
            list.distinctBy { it.title.trim().lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteItems: StateFlow<List<PlaylistItem>> = _selectedProfile
        .flatMapLatest { profile ->
            if (profile != null) repository.getAllFavorites(profile.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteItemIds: StateFlow<Set<Int>> = _selectedProfile
        .flatMapLatest { profile ->
            if (profile != null) repository.getFavoriteItemIds(profile.id).map { it.toSet() }
            else flowOf(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Helper to detect live TV channels or live category groups
    private fun isLiveCategory(group: String): Boolean {
        val upper = group.uppercase()
        return upper.contains("CANAIS") ||
                upper.contains("LIVE") ||
                upper.contains("TV ") ||
                upper.contains(" TV") ||
                upper.contains("TELEVISÃO") ||
                upper.contains("TELEVISAO") ||
                upper.contains("EPG") ||
                upper.contains("NOTICIAS") ||
                upper.contains("ESPORTES") ||
                upper.contains("AO VIVO") ||
                upper.contains("NEWS") ||
                upper.contains("SPORTS") ||
                upper.contains("RADIOS") ||
                upper.contains("RÁDIOS") ||
                upper.contains("24H") ||
                upper.contains("24/7") ||
                upper.contains("TELECINE") ||
                upper.contains("HBO") ||
                upper.contains("GLOBOPLAY") ||
                upper.contains("NETFLIX") ||
                upper.contains("24 HORAS") ||
                upper.contains("CAMPEONATO") ||
                upper.contains("FUTEBOL") ||
                upper.contains("PREMIERE") ||
                upper.contains("ESPORTE") ||
                upper.contains("BRASILEIRÃO") ||
                upper.contains("BRASILEIRAO") ||
                upper.contains("COPA")
    }

    // Live Channels Flow
    val channels: StateFlow<List<PlaylistItem>> = repository.getItemsByType("live")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedChannels: StateFlow<Map<String, List<PlaylistItem>>> = combine(
        channels,
        repository.getCategoriesByType("live")
    ) { channelList, catList ->
        val groups = channelList.groupBy { it.groupName }
        val combinedCatList = (catList + groups.keys).distinct()
        val resultMap = LinkedHashMap<String, List<PlaylistItem>>()
        for (cat in combinedCatList) {
            val items = groups[cat]
            if (!items.isNullOrEmpty()) {
                resultMap[cat] = items
            }
        }
        resultMap
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Movies Flow (Only show provider's films by filtering out TMDB-specific group names and live channels/categories)
    val movies: StateFlow<List<PlaylistItem>> = repository.getItemsByType("movie")
        .map { list ->
            list.filter { item ->
                item.groupName !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedMovies: StateFlow<Map<String, List<PlaylistItem>>> = combine(movies, repository.getCategoriesByType("movie")) { movieList, catList ->
        val groups = movieList.groupBy { it.groupName }
        val combinedCatList = (catList + groups.keys).distinct().filter {
            it !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
        }
        val resultMap = LinkedHashMap<String, List<PlaylistItem>>()
        for (cat in combinedCatList) {
            val items = groups[cat]
            if (!items.isNullOrEmpty()) {
                resultMap[cat] = items
            }
        }
        resultMap
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Series Flow (Only show provider's series by filtering out TMDB-specific group names and live channels/categories)
    val series: StateFlow<List<PlaylistItem>> = combine(
        repository.getItemsByType("series"),
        repository.getItemsByType("series_episode")
    ) { seriesList, episodesList ->
        val combined = seriesList + episodesList
        val filtered = combined.filter { item ->
            item.groupName !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
        }
        groupSeriesList(filtered)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedSeries: StateFlow<Map<String, List<PlaylistItem>>> = combine(
        series,
        repository.getCategoriesByType("series"),
        repository.getCategoriesByType("series_episode")
    ) { seriesList, catList1, catList2 ->
        val groups = seriesList.groupBy { it.groupName }
        val combinedCatList = (catList1 + catList2 + groups.keys).distinct().filter {
            it !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
        }
        val resultMap = LinkedHashMap<String, List<PlaylistItem>>()
        for (cat in combinedCatList) {
            val items = groups[cat]
            if (!items.isNullOrEmpty()) {
                resultMap[cat] = items
            }
        }
        resultMap
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredMovies: StateFlow<List<PlaylistItem>> = combine(movies, searchQuery) { list, query ->
        if (query.isBlank()) list else {
            list.filter { it.title.contains(query, ignoreCase = true) || it.genre?.contains(query, ignoreCase = true) == true }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSeries: StateFlow<List<PlaylistItem>> = combine(series, searchQuery) { list, query ->
        if (query.isBlank()) list else {
            list.filter { it.title.contains(query, ignoreCase = true) || it.genre?.contains(query, ignoreCase = true) == true }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredChannels: StateFlow<List<PlaylistItem>> = combine(channels, searchQuery) { list, query ->
        if (query.isBlank()) list else {
            list.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val tmdbSuggestions: StateFlow<List<PlaylistItem>> = searchQuery
        .debounce(300)
        .mapLatest { query ->
            if (query.trim().length >= 2) {
                repository.searchTmdbSuggestions(query.trim())
            } else {
                emptyList()
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // TMDB Sections for Home Screen (Inicio)
    val trendingItems: StateFlow<List<PlaylistItem>> = repository.trendingItemsFlow
        .map { list -> list.distinctBy { it.title.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val popularItems: StateFlow<List<PlaylistItem>> = repository.popularItemsFlow
        .map { list -> list.distinctBy { it.title.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trailerItems: StateFlow<List<PlaylistItem>> = repository.trailerItemsFlow
        .map { list -> list.distinctBy { it.title.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getTmdbTrailerKey(item: PlaylistItem): String? {
        return repository.fetchTmdbTrailerKey(item)
    }

    private fun isMovieOrSeriesItem(item: PlaylistItem): Boolean {
        val lowerType = item.type.lowercase()
        val lowerUrl = item.url.lowercase()
        val lowerGroup = item.groupName.lowercase()

        // Strictly exclude live streams or m3u8 or channel groups
        if (lowerType == "live" || lowerType == "channel" || lowerType == "tv") return false
        if (lowerUrl.contains(".m3u8") || lowerUrl.contains("/live/")) return false
        if (lowerGroup.contains("canal") || lowerGroup.contains("canais") || lowerGroup.contains("ao vivo") || lowerGroup.contains("live tv") || lowerGroup.contains("televisao")) return false

        // Allow mp4, mkv, avi, ts or type == movie/series
        if (lowerType == "movie" || lowerType == "series") return true
        if (lowerUrl.contains(".mp4") || lowerUrl.contains(".mkv") || lowerUrl.contains(".avi") || lowerUrl.contains("/movie/") || lowerUrl.contains("/series/")) return true

        return false
    }

    val searchRecommendations: StateFlow<List<PlaylistItem>> = combine(
        popularItems,
        trendingItems,
        movies,
        series
    ) { popular, trending, moviesList, seriesList ->
        val pool = (popular + trending + moviesList.take(60) + seriesList.take(60))
        pool.filter { isMovieOrSeriesItem(it) }
            .distinctBy { it.title.trim().lowercase() }
            .sortedByDescending { item ->
                val hasLogo = !item.logoUrl.isNullOrEmpty() || !item.logo.isNullOrEmpty() || !item.backdropUrl.isNullOrEmpty()
                if (hasLogo) 1 else 0
            }
            .take(60)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResultItems: StateFlow<List<PlaylistItem>> = combine(
        filteredMovies,
        filteredSeries,
        tmdbSuggestions,
        searchQuery
    ) { moviesList, seriesList, tmdb, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val localList = (moviesList + seriesList).filter { isMovieOrSeriesItem(it) }.take(100)
            val localTitles = localList.map { it.title.trim().lowercase() }.toSet()
            val uniqueTmdb = tmdb.filter { tmdbItem ->
                isMovieOrSeriesItem(tmdbItem) && !localTitles.contains(tmdbItem.title.trim().lowercase())
            }
            val combined = (localList + uniqueTmdb)
            combined.sortedByDescending { item ->
                val hasLogo = !item.logoUrl.isNullOrEmpty() || !item.logo.isNullOrEmpty() || !item.backdropUrl.isNullOrEmpty()
                if (hasLogo) 1 else 0
            }.take(150)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic Category headers based on current tab type
    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<String>> = _selectedTab
        .flatMapLatest { tab ->
            when (tab) {
                "Series" -> {
                    combine(
                        repository.getCategoriesByType("series"),
                        repository.getCategoriesByType("series_episode"),
                        series
                    ) { cat1, cat2, seriesList ->
                        val seriesCatSet = seriesList.map { it.groupName }.toSet()
                        (cat1 + cat2 + seriesCatSet).distinct().filter { category ->
                            category !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
                        }
                    }
                }
                "Filmes" -> {
                    combine(
                        repository.getCategoriesByType("movie"),
                        movies
                    ) { catList, movieList ->
                        val movieCatSet = movieList.map { it.groupName }.toSet()
                        (catList + movieCatSet).distinct().filter { category ->
                            category !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers")
                        }
                    }
                }
                "Canais" -> {
                    combine(
                        repository.getCategoriesByType("live"),
                        channels
                    ) { catList, channelList ->
                        val liveCatSet = channelList.map { it.groupName }.toSet()
                        (catList + liveCatSet).distinct()
                    }
                }
                else -> {
                    flowOf(listOf("Favoritos", "Recentes", "Destaques"))
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<String>> = combine(
        repository.getCategoriesByType("movie"),
        repository.getCategoriesByType("series"),
        repository.getCategoriesByType("live")
    ) { moviesCats, seriesCats, liveCats ->
        (moviesCats + seriesCats + liveCats)
            .filter { it.isNotBlank() && it !in listOf("Tendências", "Tendencias", "Os Mais Populares", "Últimos Trailers") }
            .distinct()
            .sorted()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Start background auto banner updates every 5 seconds
        startBannerTimer()

        // Guarantee TMDB curated VODs are populated and fetch fresh real-time TMDB content
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.populateFallbackVodsIfNeeded()
                repository.fetchRealtimeTMDBContent()
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed to populate and fetch TMDB content", e)
            }
        }

        // Trigger background TMDB validation/sync for curated sections on launch to get fresh logos
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.syncMainSectionsMetadata()
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed to sync main sections metadata", e)
            }
        }

    }

    fun syncNewContentOnStartup(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val completed = java.util.concurrent.atomic.AtomicBoolean(false)
            fun notifyComplete() {
                if (completed.compareAndSet(false, true)) {
                    viewModelScope.launch(Dispatchers.Main) {
                        onComplete()
                    }
                }
            }

            try {
                if (repository.hasLoggedIn()) {
                    val count = repository.getItemsCount()
                    if (count > 0) {
                        // Se já possui itens salvos localmente, libera a tela de splash IMEDIATAMENTE (em menos de 2s)
                        notifyComplete()
                        // Sincroniza novos itens em segundo plano sem travar a navegação
                        try {
                            repository.syncNewItemsOnStartup()
                        } catch (e: Exception) {
                            Log.e("IPTVViewModel", "Erro na sincronização em segundo plano", e)
                        }
                        return@launch
                    } else {
                        // Primeira vez sem banco populado: realiza a sincronização inicial
                        try {
                            repository.syncNewItemsOnStartup()
                        } catch (e: Exception) {
                            Log.e("IPTVViewModel", "Erro no sync inicial", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Erro na sincronização de inicialização", e)
            } finally {
                notifyComplete()
            }
        }
    }

    fun hasLoggedIn(): Boolean = repository.hasLoggedIn()

    // Banner Autoplay management
    fun startBannerTimer(customTotal: Int = 0) {
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (_currentScreen.value is Screen.Main && _selectedTab.value == "Inicio") {
                    val totalBanners = if (customTotal > 0) customTotal else bannerItems.value.size
                    if (totalBanners > 0) {
                        _bannerIndex.value = (_bannerIndex.value + 1) % totalBanners
                    }
                }
            }
        }
    }

    fun nextBanner(customTotal: Int = 0) {
        val totalBanners = if (customTotal > 0) customTotal else bannerItems.value.size
        if (totalBanners > 0) {
            _bannerIndex.value = (_bannerIndex.value + 1) % totalBanners
            startBannerTimer(totalBanners) // Reset rotation clock on manual click
        }
    }

    fun prevBanner(customTotal: Int = 0) {
        val totalBanners = if (customTotal > 0) customTotal else bannerItems.value.size
        if (totalBanners > 0) {
            _bannerIndex.value = if (_bannerIndex.value - 1 < 0) totalBanners - 1 else _bannerIndex.value - 1
            startBannerTimer(totalBanners) // Reset rotation clock on manual click
        }
    }

    fun getSavedServerUrl(): String {
        return repository.getSavedServerUrl()
    }

    fun getSavedCredentials(): Pair<String?, String?> {
        return repository.getSavedCredentials()
    }

    // Login logic
    fun performLogin(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing("Conectando ao servidor...", 0.1f)
                val success = withContext(Dispatchers.IO) {
                    repository.loginAndDownloadPlaylist(serverUrl, username, password) { msg, progress ->
                        _syncState.value = SyncState.Syncing(msg, progress)
                    }
                }
                if (success) {
                    _syncState.value = SyncState.Syncing("Pronto!", 1.0f)
                    delay(300)
                    _syncState.value = SyncState.Success
                    _currentScreen.value = Screen.ProfileSelection
                } else {
                    _syncState.value = SyncState.Error("Falha na conexão. Verifique se a URL do servidor, o usuário e a senha estão corretos e se o servidor está ativo.")
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in performLogin", e)
                _syncState.value = SyncState.Error("Erro inesperado: ${e.localizedMessage}")
            }
        }
    }

    // Profile handling
    fun selectProfile(profile: UserProfile) {
        _selectedProfile.value = profile
        _currentScreen.value = Screen.Main
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.startBackgroundMetadataPrefetch()
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed starting background metadata prefetch on profile selection", e)
            }
        }
    }

    fun addNewProfile(name: String, avatarIconName: String = "avatar_default") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Generate a beautiful, high-contrast random pastel background color for user avatars
                val colors = listOf("#E50914", "#1E3C72", "#1E824C", "#6C3483", "#D35400", "#16A085")
                val selectedColor = colors.random()
                repository.createProfile(name, selectedColor, avatarIconName)
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in addNewProfile", e)
            }
        }
    }

    fun removeProfile(profile: UserProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteProfile(profile)
                if (_selectedProfile.value?.id == profile.id) {
                    _selectedProfile.value = null
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in removeProfile", e)
            }
        }
    }

    // Favorites Handler
    fun toggleFavorite(itemId: Int) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.toggleFavorite(currentProf.id, itemId)
        }
    }

    var focusedCategoryIndex: Int = 0

    private val selectedCategoryIndexPerTab = mutableMapOf<String, Int>()
    private val selectedCategoryNamePerTab = mutableMapOf<String, String>()

    fun getSelectedCategoryIndex(tab: String): Int {
        return selectedCategoryIndexPerTab[tab] ?: 0
    }

    fun getSelectedCategoryName(tab: String): String? {
        return selectedCategoryNamePerTab[tab]
    }

    fun setSelectedCategory(tab: String, index: Int, categoryName: String) {
        selectedCategoryIndexPerTab[tab] = index
        if (categoryName.isNotBlank()) {
            selectedCategoryNamePerTab[tab] = categoryName
        }
    }

    fun setTab(tab: String) {
        if (_selectedTab.value != tab) {
            _selectedTab.value = tab
            focusedCategoryIndex = 0
            mainScrollPosition = 0
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private var previousScreen: Screen? = null
    var mainScrollPosition: Int = 0
    var lastClickedItemId: Int? = null
    var redirectDetailOnPlayerExit: PlaylistItem? = null

    fun navigateTo(screen: Screen) {
        val current = _currentScreen.value
        if (screen != Screen.Splash && screen != Screen.Login && screen != Screen.ProfileSelection && screen != current) {
            previousScreen = current
        }
        _currentScreen.value = screen
    }

    fun navigateBack() {
        val current = _currentScreen.value
        val redirectItem = redirectDetailOnPlayerExit
        if (current is Screen.Detail) {
            redirectDetailOnPlayerExit = null
            val prev = previousScreen
            if (prev != null && prev !is Screen.Detail) {
                _currentScreen.value = prev
                previousScreen = null
            } else {
                _currentScreen.value = Screen.Main
            }
        } else if (redirectItem != null) {
            redirectDetailOnPlayerExit = null
            previousScreen = Screen.Main
            _currentScreen.value = Screen.Detail(redirectItem)
        } else {
            val prev = previousScreen
            if (prev != null) {
                _currentScreen.value = prev
                previousScreen = null
            } else {
                _currentScreen.value = Screen.Main
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _selectedProfile.value = null
            _currentScreen.value = Screen.Login
            _syncState.value = SyncState.Idle
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            val creds = repository.getSavedCredentials()
            val serverUrl = repository.getSavedServerUrl()
            if (creds.first != null && creds.second != null) {
                performLogin(serverUrl, creds.first!!, creds.second!!)
            }
        }
    }

    // Async TMDB details loading when selecting/viewing details of any film/series
    fun enrichItemOnDemand(item: PlaylistItem) {
        val cleanTitle = repository.cleanTitleForSearch(item.title)
        val isSeries = item.type == "series" || item.groupName.contains("Série", ignoreCase = true) || item.groupName.contains("Serie", ignoreCase = true)
        val overrideId = repository.getOverrideTmdbId(item, cleanTitle, isSeries)
        val needsOverrideFix = overrideId != null && item.tmdbId != overrideId.toString()

        if (!needsOverrideFix) {
            if (enrichedItemCache.contains(item.id.toString())) {
                return
            }
            if (repository.isMetadataComplete(item)) return
            if (!attemptedEnrichments.add(item.id)) return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val itemToEnrich = if (needsOverrideFix) {
                    item.copy(
                        tmdbId = overrideId?.toString(),
                        metadataLoaded = false,
                        posterUrl = null,
                        backdropUrl = null,
                        clearLogoUrl = null,
                        titleLogoUrl = null
                    )
                } else item
                repository.enrichItemWithTMDB(itemToEnrich)
                enrichedItemCache.add(item.id.toString())
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Failed to enrich item ${item.title}", e)
            }
        }
    }

    fun extractEpisodeKey(title: String): String? {
        val regex = Regex("S\\d{1,2}E\\d{1,2}", RegexOption.IGNORE_CASE)
        val match = regex.find(title)
        if (match != null) return match.value.uppercase()
        if (title.contains(" - ")) {
            val part = title.substringAfter(" - ").trim()
            if (part.startsWith("Episódio", ignoreCase = true) || part.startsWith("Ep", ignoreCase = true)) {
                return part.lowercase()
            }
        }
        return null
    }

    fun saveWatchProgress(item: PlaylistItem, position: Long, duration: Long) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.saveWatchProgress(currentProf.id, item.id, position, duration)
            val episodeKey = extractEpisodeKey(item.title)
            if (episodeKey != null) {
                repository.saveEpisodeWatchProgress(currentProf.id, item.id, episodeKey, position, duration)
            }
        }
    }

    fun saveWatchProgress(itemId: Int, position: Long, duration: Long) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.saveWatchProgress(currentProf.id, itemId, position, duration)
        }
    }

    suspend fun getWatchProgress(item: PlaylistItem): com.example.data.model.WatchHistory? {
        val currentProf = _selectedProfile.value ?: return null
        val episodeKey = extractEpisodeKey(item.title)
        if (episodeKey != null) {
            val epProg = repository.getEpisodeWatchProgress(currentProf.id, item.id, episodeKey)
            return if (epProg != null) {
                com.example.data.model.WatchHistory(
                    profileId = currentProf.id,
                    itemId = item.id,
                    position = epProg.first,
                    duration = epProg.second,
                    lastWatched = System.currentTimeMillis()
                )
            } else {
                null
            }
        }
        return repository.getWatchProgress(currentProf.id, item.id)
    }

    suspend fun getWatchProgress(itemId: Int): com.example.data.model.WatchHistory? {
        val currentProf = _selectedProfile.value ?: return null
        return repository.getWatchProgress(currentProf.id, itemId)
    }

    fun deleteWatchProgress(itemId: Int) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.deleteWatchProgress(currentProf.id, itemId)
        }
    }

    fun saveLastWatchedEpisode(seriesId: Int, episodeTitle: String, playUrl: String) {
        val currentProf = _selectedProfile.value ?: return
        viewModelScope.launch {
            repository.saveLastWatchedEpisode(currentProf.id, seriesId, episodeTitle, playUrl)
        }
    }

    suspend fun getLastWatchedEpisode(seriesId: Int): Pair<String, String>? {
        val currentProf = _selectedProfile.value ?: return null
        return repository.getLastWatchedEpisode(currentProf.id, seriesId)
    }

    // Helper to fetch favorite items of active profile
    fun getFavoritesFlow(type: String): Flow<List<PlaylistItem>> {
        val currentProf = _selectedProfile.value ?: return flowOf(emptyList())
        return repository.getFavorites(currentProf.id, type)
            .flowOn(Dispatchers.IO)
    }

    // Get specific category items dynamically filtered and grouped for Series
    fun getCategoryItems(type: String, categoryName: String): Flow<List<PlaylistItem>> {
        if (type == "series") {
            return combine(
                repository.getItemsByTypeAndCategory("series", categoryName),
                repository.getItemsByTypeAndCategory("series_episode", categoryName)
            ) { seriesList, episodesList ->
                val combined = seriesList + episodesList
                groupSeriesList(combined)
            }
                .flowOn(Dispatchers.IO)
        } else {
            return repository.getItemsByTypeAndCategory(type, categoryName)
                .flowOn(Dispatchers.IO)
        }
    }

    suspend fun getRealCast(item: PlaylistItem): List<CastMember> {
        return repository.getRealCastFromTMDB(item)
    }

    suspend fun getRealSeasons(item: PlaylistItem): List<Season> {
        return repository.getRealSeasonsFromTMDB(item)
    }

    suspend fun findRealEpisodeItem(seriesTitle: String, seasonName: String, episodeIndex: Int, episodeTitle: String): PlaylistItem? {
        return repository.findRealEpisodeItem(seriesTitle, seasonName, episodeIndex, episodeTitle)
    }

    suspend fun verifyAndGetRealPlaylistItem(item: PlaylistItem): PlaylistItem? {
        return repository.verifyAndGetRealPlaylistItem(item)
    }

    private val _isCategoryLoading = MutableStateFlow(false)
    val isCategoryLoading: StateFlow<Boolean> = _isCategoryLoading

    private val _categoryLoadProgress = MutableStateFlow(0f)
    val categoryLoadProgress: StateFlow<Float> = _categoryLoadProgress

    private val enrichedItemCache = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun preloadCategoryItems(items: List<PlaylistItem>) {
        viewModelScope.launch {
            _isCategoryLoading.value = true
            _categoryLoadProgress.value = 0f
            val deferredItems = items.take(6).map { item ->
                async {
                    try {
                        val cleanTitle = repository.cleanTitleForSearch(item.title)
                        val isSeries = item.type == "series" || item.groupName.contains("Série", ignoreCase = true) || item.groupName.contains("Serie", ignoreCase = true)
                        val overrideId = repository.getOverrideTmdbId(item, cleanTitle, isSeries)
                        val needsOverrideFix = overrideId != null && item.tmdbId != overrideId.toString()
                        if (!enrichedItemCache.contains(item.id.toString()) || needsOverrideFix) {
                            enrichItemOnDemand(item)
                            enrichedItemCache.add(item.id.toString())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            deferredItems.forEachIndexed { index, deferred ->
                deferred.await()
                _categoryLoadProgress.value = (index + 1).toFloat() / deferredItems.size
            }
            _isCategoryLoading.value = false
        }
    }
}
