package com.example.ui.screens

import com.example.LocalIPTVViewModel
import com.example.ui.components.getCleanPtBrTitle
import com.example.ui.util.SharpenTransformation

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Size
import coil.imageLoader
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.model.PlaylistItem
import com.example.data.model.UserProfile
import com.example.data.util.EPGGenerator
import com.example.data.util.Program
import com.example.data.util.cleanSeriesName
import com.example.ui.components.StreaTVLogo
import com.example.ui.viewmodel.IPTVViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: IPTVViewModel
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isCategoryLoading by viewModel.isCategoryLoading.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val bannerItems by viewModel.bannerItems.collectAsState()
    val bannerIndex by viewModel.bannerIndex.collectAsState()

    val trendingItems by viewModel.trendingItems.collectAsState()
    val popularItems by viewModel.popularItems.collectAsState()
    val trailerItems by viewModel.trailerItems.collectAsState()

    val channels by viewModel.filteredChannels.collectAsState()
    val movies by viewModel.filteredMovies.collectAsState()
    val series by viewModel.filteredSeries.collectAsState()
    val groupedChannels by viewModel.groupedChannels.collectAsState()
    val groupedMovies by viewModel.groupedMovies.collectAsState()
    val groupedSeries by viewModel.groupedSeries.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val isSearchActive by viewModel.isSearchActive.collectAsState()

    var activeTrailerKey by remember { mutableStateOf<String?>(null) }
    var activeTrailerTitle by remember { mutableStateOf("") }
    var isFetchingTrailer by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val continueWatchingFirstItemFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val navBarFocusRequester = remember { FocusRequester() }
    val trendingFirstItemFocusRequester = remember { FocusRequester() }
    val trailersFirstItemFocusRequester = remember { FocusRequester() }
    val popularFirstItemFocusRequester = remember { FocusRequester() }

    val context = LocalContext.current

    val scrollState = rememberScrollState(initial = viewModel.mainScrollPosition)

    val onItemPlayOrDetail: (PlaylistItem) -> Unit = { item ->
        viewModel.mainScrollPosition = scrollState.value
        viewModel.lastClickedItemId = item.id
        if (item.type == "movie" || item.type == "series" || item.type == "series_episode") {
            viewModel.redirectDetailOnPlayerExit = item
            viewModel.navigateTo(Screen.Detail(item, autoPlay = false))
        } else {
            viewModel.navigateTo(Screen.Player(item))
        }
    }

    val onContinueWatchingClick: (PlaylistItem) -> Unit = { item ->
        viewModel.mainScrollPosition = scrollState.value
        viewModel.lastClickedItemId = item.id
        if (item.type == "series" || item.type == "series_episode") {
            viewModel.redirectDetailOnPlayerExit = item
            scope.launch {
                val lastEp = viewModel.getLastWatchedEpisode(item.id)
                if (lastEp != null) {
                    viewModel.navigateTo(
                        Screen.Player(
                            item.copy(
                                title = "${item.title} - ${lastEp.first}",
                                url = lastEp.second
                            )
                        )
                    )
                } else {
                    viewModel.navigateTo(Screen.Detail(item, autoPlay = false))
                }
            }
        } else if (item.type == "movie") {
            viewModel.redirectDetailOnPlayerExit = item
            scope.launch {
                val realMovie = viewModel.verifyAndGetRealPlaylistItem(item)
                if (realMovie != null) {
                    viewModel.navigateTo(Screen.Player(realMovie))
                } else {
                    viewModel.navigateTo(Screen.Player(item))
                }
            }
        } else {
            viewModel.navigateTo(Screen.Player(item))
        }
    }

    // Custom focused category tracker for the requested collapse transition
    var focusedCategoryIndex by remember(selectedTab) { mutableStateOf(viewModel.focusedCategoryIndex) }

    LaunchedEffect(focusedCategoryIndex) {
        viewModel.focusedCategoryIndex = focusedCategoryIndex
    }

    // Save scroll position when the user scrolls or when layout settles
    LaunchedEffect(scrollState.value, scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress || scrollState.value > 0) {
            viewModel.mainScrollPosition = scrollState.value
        }
    }

    // Explicitly restore scroll position when content layout becomes ready
    val isContentReady = remember(selectedTab, trendingItems, popularItems, movies, series, channels) {
        when (selectedTab) {
            "Inicio" -> trendingItems.isNotEmpty() || popularItems.isNotEmpty()
            "Filmes" -> movies.isNotEmpty()
            "Series" -> series.isNotEmpty()
            "Canais" -> channels.isNotEmpty()
            else -> true
        }
    }

    LaunchedEffect(isContentReady) {
        if (isContentReady && viewModel.mainScrollPosition > 0) {
            delay(150) // Tiny delay to allow full layout pass to execute
            try {
                scrollState.scrollTo(viewModel.mainScrollPosition)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fast scroll to top, reset category focus, and request focus on the header tabs
    val scrollToTopAndFocusHeader: () -> Unit = {
        scope.launch {
            scrollState.scrollTo(0)
            focusedCategoryIndex = 0
            try {
                navBarFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val isBackEnabled = scrollState.value > 0 || focusedCategoryIndex > 0 || selectedTab != "Inicio"

    BackHandler(enabled = isBackEnabled) {
        if (scrollState.value > 0 || focusedCategoryIndex > 0) {
            scrollToTopAndFocusHeader()
        } else if (selectedTab != "Inicio") {
            scope.launch {
                viewModel.setTab("Inicio")
                try {
                    navBarFocusRequester.requestFocus()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Dynamic background color state
    var currentBackgroundColor by remember { mutableStateOf(Color(0xFF050505)) }
    val animatedBgColor by animateColorAsState(
        targetValue = currentBackgroundColor,
        animationSpec = tween(1000),
        label = "DynamicBgColorAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (keyEvent.key == Key.Escape || keyEvent.key == Key.Back) {
                        if (scrollState.value > 0 || focusedCategoryIndex > 0) {
                            scrollToTopAndFocusHeader()
                            true // Consumes key event, prevents default back action
                        } else {
                            false // Let other back handlers handle it
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedBgColor.copy(alpha = 0.45f), // Subtle cinematic dynamic ambient glow
                        Color(0xFF050505)
                    ),
                    startY = 0f,
                    endY = 1200f
                )
            )
    ) {
        var activeFocusedItem by remember(selectedTab) { mutableStateOf<PlaylistItem?>(null) }

        // A. Bottom layer: Full Screen Blurred Backdrop (Movies & Series tabs) & Scrollable Content
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            MainScreenFullBackground(
                selectedTab = selectedTab,
                activeFocusedItem = activeFocusedItem,
                movies = movies,
                series = series
            )

            if (isSearchActive) {
                SearchScreen(
                    viewModel = viewModel,
                    onClose = {
                        viewModel.setSearchActive(false)
                        viewModel.updateSearchQuery("")
                    },
                    onPlay = onItemPlayOrDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp)
                )
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val tabs = listOf("Inicio", "Canais", "Filmes", "Series", "Minha Lista")
                        val initialIndex = tabs.indexOf(initialState)
                        val targetIndex = tabs.indexOf(targetState)
                        val movingRight = if (initialIndex != -1 && targetIndex != -1) targetIndex > initialIndex else true

                        val slideFraction = 0.08f
                        val durationIn = 280
                        val durationOut = 220

                        if (movingRight) {
                            (slideInHorizontally(
                                animationSpec = tween(durationIn, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> (fullWidth * slideFraction).toInt() }
                            ) + fadeIn(
                                animationSpec = tween(durationIn, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(durationOut, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> (-fullWidth * slideFraction).toInt() }
                                ) + fadeOut(
                                    animationSpec = tween(durationOut, easing = FastOutSlowInEasing)
                                )
                            )
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(durationIn, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> (-fullWidth * slideFraction).toInt() }
                            ) + fadeIn(
                                animationSpec = tween(durationIn, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(durationOut, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> (fullWidth * slideFraction).toInt() }
                                ) + fadeOut(
                                    animationSpec = tween(durationOut, easing = FastOutSlowInEasing)
                                )
                            )
                        }
                    },
                    label = "MainScreenTabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { currentTab ->
                    if (currentTab == "Canais") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 72.dp, bottom = 16.dp)
                        ) {
                            LiveChannelsTabContent(
                                groupedChannels = groupedChannels,
                                categories = categories,
                                contentFocusRequester = contentFocusRequester,
                                navBarFocusRequester = navBarFocusRequester,
                                onItemPlayOrDetail = onItemPlayOrDetail,
                                onItemFocused = { item -> activeFocusedItem = item },
                                viewModel = viewModel
                            )
                        }
                    } else {
                        val currentScrollState = if (currentTab == "Inicio") scrollState else remember(currentTab) { ScrollState(0) }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(currentScrollState)
                                .padding(bottom = 36.dp)
                        ) {
                            Spacer(modifier = Modifier.height(72.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                when (currentTab) {
                                    "Search" -> {
                                        SearchResultGrid(
                                            movies = movies,
                                            series = series,
                                            channels = channels,
                                            onPlay = onItemPlayOrDetail,
                                            firstItemFocusRequester = contentFocusRequester
                                        )
                                    }
                                    "Inicio" -> {
                                        HomeTabContent(
                                            viewModel = viewModel,
                                            trendingItems = trendingItems,
                                            popularItems = popularItems,
                                            bannerIndex = bannerIndex,
                                            onItemPlayOrDetail = onItemPlayOrDetail,
                                            onContinueWatchingClick = onContinueWatchingClick,
                                            onColorExtracted = { color -> currentBackgroundColor = color },
                                            navBarFocusRequester = navBarFocusRequester,
                                            contentFocusRequester = contentFocusRequester,
                                            continueWatchingFirstItemFocusRequester = continueWatchingFirstItemFocusRequester,
                                            trendingFirstItemFocusRequester = trendingFirstItemFocusRequester,
                                            popularFirstItemFocusRequester = popularFirstItemFocusRequester
                                        )
                                    }
                                    "Minha Lista" -> {
                                        MyListTabContent(
                                            viewModel = viewModel,
                                            onItemPlayOrDetail = onItemPlayOrDetail,
                                            contentFocusRequester = contentFocusRequester
                                        )
                                    }
                                    else -> {
                                        CategoryListTabContent(
                                            targetState = currentTab,
                                            channels = channels,
                                            movies = movies,
                                            series = series,
                                            groupedChannels = groupedChannels,
                                            groupedMovies = groupedMovies,
                                            groupedSeries = groupedSeries,
                                            categories = categories,
                                            focusedCategoryIndex = focusedCategoryIndex,
                                            onFocusedCategoryIndexChange = { focusedCategoryIndex = it },
                                            contentFocusRequester = contentFocusRequester,
                                            navBarFocusRequester = navBarFocusRequester,
                                            onItemPlayOrDetail = onItemPlayOrDetail,
                                            onItemFocused = { item ->
                                                activeFocusedItem = item
                                            },
                                            isLoading = isCategoryLoading,
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // B. Top layer: Floating Top Navigation & Overlays Container
        MainScreenTopBar(
            selectedTab = selectedTab,
            selectedProfile = selectedProfile,
            syncState = syncState,
            isSearchActive = isSearchActive,
            navBarFocusRequester = navBarFocusRequester,
            contentFocusRequester = contentFocusRequester,
            onTabSelected = { tab ->
                viewModel.setTab(tab)
                viewModel.setSearchActive(false)
                viewModel.updateSearchQuery("")
                viewModel.lastClickedItemId = null
                focusedCategoryIndex = 0
            },
            onSearchClick = {
                if (isSearchActive) {
                    viewModel.setSearchActive(false)
                    viewModel.updateSearchQuery("")
                } else {
                    viewModel.setSearchActive(true)
                }
            },
            onProfileClick = { viewModel.navigateTo(Screen.ProfileSelection) },
            onSyncClick = { viewModel.triggerManualSync() },
            onLogoutClick = { viewModel.logout() }
        )

        TrailerAndLoadingDialogs(
            activeTrailerKey = activeTrailerKey,
            activeTrailerTitle = activeTrailerTitle,
            isFetchingTrailer = isFetchingTrailer,
            onDismissTrailer = { activeTrailerKey = null },
            onDismissFetchingTrailer = { isFetchingTrailer = false }
        )
    }
}

@Composable
private fun MainScreenFullBackground(
    selectedTab: String,
    activeFocusedItem: PlaylistItem?,
    movies: List<PlaylistItem>,
    series: List<PlaylistItem>
) {
    val isMoviesOrSeries = selectedTab == "Filmes" || selectedTab == "Series" || selectedTab == "Minha Lista"
    val fallbackItem = if (selectedTab == "Filmes") movies.firstOrNull() else if (selectedTab == "Series") series.firstOrNull() else null
    val targetItem = activeFocusedItem ?: fallbackItem

    val bgImageUrl = if (isMoviesOrSeries) {
        targetItem?.let { item ->
            item.getEffectiveBackdropUrl()?.takeIf { it.isNotBlank() }
                ?: item.backdropUrl?.takeIf { it.isNotBlank() }
                ?: item.getEffectiveLogoUrl()?.takeIf { it.isNotBlank() }
                ?: item.logoUrl?.takeIf { it.isNotBlank() }
                ?: item.logo?.takeIf { it.isNotBlank() }
        }
    } else null

    AnimatedContent(
        targetState = bgImageUrl,
        transitionSpec = {
            fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
        },
        label = "fullScreenMoviesSeriesBackgroundFade"
    ) { currentBgUrl ->
        if (!currentBgUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val context = LocalContext.current
                val bgRequest = remember(currentBgUrl) {
                    ImageRequest.Builder(context)
                        .data(currentBgUrl)
                        .size(width = 480, height = 270) // Optimized low-res backdrop for fast hardware rendering without GPU strain
                        .bitmapConfig(Bitmap.Config.RGB_565) // 50% RAM savings for backdrop textures
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = bgRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.65f }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    viewModel: IPTVViewModel,
    trendingItems: List<PlaylistItem>,
    popularItems: List<PlaylistItem>,
    bannerIndex: Int,
    onItemPlayOrDetail: (PlaylistItem) -> Unit,
    onContinueWatchingClick: (PlaylistItem) -> Unit,
    onColorExtracted: (Color) -> Unit,
    navBarFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    continueWatchingFirstItemFocusRequester: FocusRequester,
    trendingFirstItemFocusRequester: FocusRequester,
    popularFirstItemFocusRequester: FocusRequester
) {
    val isHomeReady = trendingItems.isNotEmpty() || popularItems.isNotEmpty()

    if (isHomeReady) {
        Column {
            val continueWatchingList by viewModel.continueWatching.collectAsState(initial = emptyList())

            val activeBannerItems = trendingItems

            LaunchedEffect(activeBannerItems) {
                if (activeBannerItems.isNotEmpty()) {
                    viewModel.startBannerTimer(activeBannerItems.size)
                }
            }

            if (activeBannerItems.isNotEmpty()) {
                val safeIndex = if (activeBannerItems.isNotEmpty()) bannerIndex % activeBannerItems.size else 0
                val activeBanner = activeBannerItems[safeIndex]

                val nextIndex = (safeIndex + 1) % activeBannerItems.size
                val nextBannerItem = activeBannerItems[nextIndex]
                val nextBackdrop = nextBannerItem.getEffectiveBackdropUrl()
                val nextTitleLogo = nextBannerItem.titleLogoUrl?.takeIf { it.isNotBlank() }
                    ?: nextBannerItem.logo?.takeIf { it.isNotBlank() }
                    ?: nextBannerItem.getEffectiveTitleLogoUrl()
                if (!nextBackdrop.isNullOrEmpty() || !nextTitleLogo.isNullOrEmpty()) {
                    val context = LocalContext.current
                    LaunchedEffect(nextBackdrop, nextTitleLogo) {
                        listOfNotNull(nextBackdrop, nextTitleLogo)
                            .distinct()
                            .forEach { imageUrl ->
                                context.imageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .build()
                                )
                            }
                    }
                }

                FeaturedHeroBanner(
                    item = activeBanner,
                    onNext = { viewModel.nextBanner(activeBannerItems.size) },
                    viewModel = viewModel,
                    onPrev = { viewModel.prevBanner(activeBannerItems.size) },
                    onClick = { onItemPlayOrDetail(activeBanner) },
                    onColorExtracted = onColorExtracted,
                    onUpPressed = { navBarFocusRequester.requestFocus() },
                    onDownPressed = {
                        if (continueWatchingList.isNotEmpty()) {
                            continueWatchingFirstItemFocusRequester.requestFocus()
                        } else {
                            trendingFirstItemFocusRequester.requestFocus()
                        }
                    },
                    modifier = Modifier.focusRequester(contentFocusRequester)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ContinueWatchingRowSection(
                viewModel = viewModel,
                onPlay = onContinueWatchingClick,
                firstItemFocusRequester = continueWatchingFirstItemFocusRequester
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (trendingItems.isNotEmpty()) {
                TrendingSection(
                    trendingItems = trendingItems,
                    onPlay = onItemPlayOrDetail,
                    firstItemFocusRequester = trendingFirstItemFocusRequester,
                    downFocusRequester = popularFirstItemFocusRequester
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (popularItems.isNotEmpty()) {
                MostPopularSection(
                    popularItems = popularItems,
                    onPlay = onItemPlayOrDetail,
                    firstItemFocusRequester = popularFirstItemFocusRequester
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFE50914))
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Carregando tela inicial...",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun LiveChannelsTabContent(
    groupedChannels: Map<String, List<PlaylistItem>>,
    categories: List<String>,
    contentFocusRequester: FocusRequester,
    navBarFocusRequester: FocusRequester,
    onItemPlayOrDetail: (PlaylistItem) -> Unit,
    onItemFocused: (PlaylistItem) -> Unit,
    viewModel: IPTVViewModel
) {
    val availableCategories = remember(categories, groupedChannels) {
        val filtered = categories.filter { cat -> (groupedChannels[cat]?.isNotEmpty() == true) }
        if (filtered.isNotEmpty()) filtered
        else groupedChannels.keys.filter { groupedChannels[it]?.isNotEmpty() == true }
    }

    val savedCategoryName = viewModel.getSelectedCategoryName("Canais")
    val savedCategoryIndex = viewModel.getSelectedCategoryIndex("Canais")

    val initialCategoryIndex = remember(viewModel.lastClickedItemId, availableCategories, groupedChannels) {
        if (viewModel.lastClickedItemId != null) {
            val foundCat = availableCategories.indexOfFirst { cat ->
                groupedChannels[cat]?.any { it.id == viewModel.lastClickedItemId } == true
            }
            if (foundCat >= 0) return@remember foundCat
        }
        if (!savedCategoryName.isNullOrBlank()) {
            val foundByName = availableCategories.indexOf(savedCategoryName)
            if (foundByName >= 0) return@remember foundByName
        }
        if (savedCategoryIndex in availableCategories.indices) savedCategoryIndex else 0
    }

    var selectedCategoryIndex by remember(initialCategoryIndex) { mutableIntStateOf(initialCategoryIndex) }
    val activeCategory = availableCategories.getOrNull(selectedCategoryIndex)
        ?: availableCategories.firstOrNull() ?: ""

    LaunchedEffect(selectedCategoryIndex, activeCategory) {
        viewModel.setSelectedCategory("Canais", selectedCategoryIndex, activeCategory)
    }

    val channelItems = remember(groupedChannels, activeCategory) {
        groupedChannels[activeCategory] ?: emptyList()
    }

    val coroutineScope = rememberCoroutineScope()
    val categoryListState = rememberLazyListState()
    val channelGridState = rememberLazyGridState()
    val firstChannelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(initialCategoryIndex) {
        if (initialCategoryIndex in availableCategories.indices) {
            categoryListState.scrollToItem(initialCategoryIndex)
        }
    }

    val handleEscToTop = {
        try {
            coroutineScope.launch {
                navBarFocusRequester.requestFocus()
            }
        } catch (e: Exception) {
            android.util.Log.w("LiveChannels", "Failed to focus top nav bar: ${e.message}")
        }
    }

    BackHandler {
        handleEscToTop()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Escape || keyEvent.key == Key.Back)) {
                    handleEscToTop()
                    true
                } else false
            }
    ) {
        // LEFT PANEL: Categories List
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .padding(end = 16.dp)
        ) {
            Text(
                text = "Categorias (${availableCategories.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                state = categoryListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(availableCategories, key = { _, category -> category }) { idx, category ->
                    val isSelected = (idx == selectedCategoryIndex)
                    var isCategoryFocused by remember { mutableStateOf(false) }
                    val categoryFocusRequester = remember { FocusRequester() }
                    val categoryTargetFocusRequester = if (idx == selectedCategoryIndex) contentFocusRequester else categoryFocusRequester

                    val categoryScale by animateFloatAsState(
                        targetValue = if (isCategoryFocused) 1.08f else if (isSelected) 1.03f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    )

                    val catCount = groupedChannels[category]?.size ?: 0

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = categoryScale
                                scaleY = categoryScale
                            }
                            .focusRequester(categoryTargetFocusRequester)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isCategoryFocused -> Color(0xFFE50914).copy(alpha = 0.35f)
                                    isSelected -> Color.White.copy(alpha = 0.15f)
                                    else -> Color.Black.copy(alpha = 0.3f)
                                }
                            )
                            .border(
                                width = if (isCategoryFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                                color = if (isCategoryFocused) Color(0xFFE50914) else if (isSelected) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedCategoryIndex = idx
                                coroutineScope.launch {
                                    try {
                                        channelGridState.scrollToItem(0)
                                    } catch (e: Exception) { }
                                }
                            }
                            .onFocusChanged { focusState ->
                                isCategoryFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    selectedCategoryIndex = idx
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            if (idx == 0) {
                                                navBarFocusRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        Key.DirectionRight -> {
                                            coroutineScope.launch {
                                                try {
                                                    channelGridState.scrollToItem(0)
                                                    kotlinx.coroutines.delay(50)
                                                    firstChannelFocusRequester.requestFocus()
                                                } catch (e: Exception) { }
                                            }
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            selectedCategoryIndex = idx
                                            coroutineScope.launch {
                                                try {
                                                    channelGridState.scrollToItem(0)
                                                } catch (e: Exception) { }
                                            }
                                            true
                                        }
                                        Key.Escape, Key.Back -> {
                                            handleEscToTop()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = if (isCategoryFocused || isSelected) Color(0xFFE50914) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected || isCategoryFocused) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCategoryFocused || isSelected) Color.White else Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (catCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isCategoryFocused) Color(0xFFE50914) else Color.White.copy(alpha = 0.15f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$catCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // RIGHT PANEL: Horizontal Channels List
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = activeCategory,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AO VIVO",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${channelItems.size} canais disponíveis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            if (channelItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum canal disponível nesta categoria",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val availableWidth = maxWidth
                    val numColumns = remember(availableWidth) {
                        val itemMin = 170.dp
                        val spacing = 16.dp
                        val horizontalPadding = 12.dp
                        val effectiveWidth = availableWidth - horizontalPadding
                        var cols = ((effectiveWidth + spacing) / (itemMin + spacing)).toInt()
                        cols.coerceAtLeast(1)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        state = channelGridState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(channelItems, key = { _, item -> item.id }) { idx, item ->
                            val isTargetToFocus = remember(item.id, viewModel.lastClickedItemId) {
                                viewModel.lastClickedItemId != null && item.id == viewModel.lastClickedItemId
                            }
                            val itemFocusRequester = remember { FocusRequester() }
                            val cardFocusRequester = if (idx == 0) firstChannelFocusRequester else itemFocusRequester

                            LaunchedEffect(isTargetToFocus) {
                                if (isTargetToFocus) {
                                    delay(100)
                                    try {
                                        cardFocusRequester.requestFocus()
                                    } catch (e: Exception) { }
                                }
                            }

                            var isCardFocused by remember { mutableStateOf(false) }
                            val cardScale by animateFloatAsState(
                                targetValue = if (isCardFocused) 1.08f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "channelCardScale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                    }
                                    .focusRequester(cardFocusRequester)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF141414))
                                    .border(
                                        width = if (isCardFocused) 3.5.dp else 1.dp,
                                        color = if (isCardFocused) Color.White else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .onFocusChanged { focusState ->
                                        isCardFocused = focusState.isFocused
                                        if (focusState.isFocused) {
                                            onItemFocused(item)
                                        }
                                    }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onItemPlayOrDetail(item)
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionUp -> {
                                                    if (idx < numColumns) {
                                                        navBarFocusRequester.requestFocus()
                                                        true
                                                    } else false
                                                }
                                                Key.DirectionLeft -> {
                                                    if (idx % numColumns == 0) {
                                                        coroutineScope.launch {
                                                            try {
                                                                categoryListState.scrollToItem(selectedCategoryIndex)
                                                                delay(30)
                                                                contentFocusRequester.requestFocus()
                                                            } catch (e: Exception) {
                                                                handleEscToTop()
                                                            }
                                                        }
                                                        true
                                                    } else false
                                                }
                                                Key.Escape, Key.Back -> {
                                                    coroutineScope.launch {
                                                        try {
                                                            categoryListState.scrollToItem(selectedCategoryIndex)
                                                            delay(30)
                                                            contentFocusRequester.requestFocus()
                                                        } catch (e: Exception) {
                                                            handleEscToTop()
                                                        }
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (!item.logoUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = item.logoUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(bottom = 36.dp, top = 8.dp, start = 8.dp, end = 8.dp)
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(bottom = 34.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = Color(0xFFE50914),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    // Card Bottom Title Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.95f)
                                                    )
                                                )
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(0xFFE50914), CircleShape)
                                                )
                                            }

                                            var cardProg by remember(item.id) { mutableStateOf<Program?>(null) }
                                            LaunchedEffect(item.id) {
                                                val creds = viewModel.getSavedCredentials()
                                                val server = viewModel.getSavedServerUrl()
                                                val user = creds.first ?: ""
                                                val pass = creds.second ?: ""
                                                val streamId = EPGGenerator.extractStreamId(item)
                                                val list = EPGGenerator.generateEPG(user, pass, streamId, server)
                                                cardProg = list.firstOrNull()
                                            }
                                            val displayProg = cardProg ?: EPGGenerator.fallbackProgram
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (displayProg.startTime.isNotBlank()) "${displayProg.startTime} - ${displayProg.endTime}" else "",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFFFFC107),
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = displayProg.title,
                                                    fontSize = 9.sp,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryListTabContent(
    targetState: String,
    channels: List<PlaylistItem>,
    movies: List<PlaylistItem>,
    series: List<PlaylistItem>,
    groupedChannels: Map<String, List<PlaylistItem>>,
    groupedMovies: Map<String, List<PlaylistItem>>,
    groupedSeries: Map<String, List<PlaylistItem>>,
    categories: List<String>,
    focusedCategoryIndex: Int,
    onFocusedCategoryIndexChange: (Int) -> Unit,
    contentFocusRequester: FocusRequester,
    navBarFocusRequester: FocusRequester,
    onItemPlayOrDetail: (PlaylistItem) -> Unit,
    onItemFocused: (PlaylistItem) -> Unit,
    isLoading: Boolean = false,
    viewModel: IPTVViewModel
) {
    val itemsSource = when (targetState) {
        "Canais" -> channels
        "Filmes" -> movies
        "Series" -> series
        else -> emptyList()
    }
    val groupedSource = when (targetState) {
        "Canais" -> groupedChannels
        "Filmes" -> groupedMovies
        "Series" -> groupedSeries
        else -> emptyMap()
    }

    val hasItems = itemsSource.isNotEmpty() || groupedSource.values.any { it.isNotEmpty() }

    LaunchedEffect(targetState, itemsSource) {
        if ((targetState == "Filmes" || targetState == "Series") && itemsSource.isNotEmpty()) {
            viewModel.preloadCategoryItems(itemsSource)
        }
    }

    if (hasItems) {
        if (targetState == "Filmes" || targetState == "Series") {
            val availableCategories = remember(categories, groupedSource) {
                val filtered = categories.filter { cat -> (groupedSource[cat]?.isNotEmpty() == true) }
                if (filtered.isNotEmpty()) filtered
                else groupedSource.keys.filter { groupedSource[it]?.isNotEmpty() == true }
            }
            val activeCategory = availableCategories.getOrNull(focusedCategoryIndex)
                ?: availableCategories.firstOrNull()
                ?: ""
            val catItems = remember(groupedSource, activeCategory) {
                groupedSource[activeCategory]
                    ?: groupedSource.values.firstOrNull { it.isNotEmpty() }
                    ?: emptyList()
            }

            if (catItems.isNotEmpty()) {
                MoviesSeriesCategoryRow(
                    categoryTitle = activeCategory,
                    items = catItems,
                    categoryIndex = focusedCategoryIndex,
                    onPlay = onItemPlayOrDetail,
                    onItemFocused = { item ->
                        onItemFocused(item)
                    },
                    firstItemFocusRequester = contentFocusRequester,
                    onUpPressed = {
                        if (focusedCategoryIndex > 0) {
                            onFocusedCategoryIndexChange(focusedCategoryIndex - 1)
                        } else {
                            navBarFocusRequester.requestFocus()
                        }
                    },
                    onDownPressed = {
                        if (focusedCategoryIndex < availableCategories.size - 1) {
                            onFocusedCategoryIndexChange(focusedCategoryIndex + 1)
                        }
                    }
                )
            } else if (isLoading) {
                MoviesSeriesSkeletonLoader(categoryTitle = activeCategory)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        if (focusedCategoryIndex < categories.size - 1) {
                                            onFocusedCategoryIndexChange(focusedCategoryIndex + 1)
                                            true
                                        } else false
                                    }
                                    Key.DirectionUp -> {
                                        if (focusedCategoryIndex > 0) {
                                            onFocusedCategoryIndexChange(focusedCategoryIndex - 1)
                                            true
                                        } else {
                                            navBarFocusRequester.requestFocus()
                                            true
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    Text(
                        text = "$targetState • Categorias Disponíveis",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    categories.forEachIndexed { idx, category ->
                        val isVisible = idx >= focusedCategoryIndex

                        if (isVisible) {
                            val catItems = remember(groupedSource, category) {
                                groupedSource[category] ?: emptyList()
                            }
                            if (catItems.isNotEmpty()) {
                                CategoryRow(
                                    title = category,
                                    items = catItems,
                                    isSelectedHighlight = (idx == focusedCategoryIndex),
                                    onPlay = onItemPlayOrDetail,
                                    onItemFocused = { item ->
                                        onItemFocused(item)
                                    },
                                    firstItemFocusRequester = if (idx == focusedCategoryIndex) contentFocusRequester else null,
                                    onItemRendered = { item -> }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Tv, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Nenhum item carregado para a aba $targetState.", color = Color.Gray, fontSize = 14.sp)
                Text("Verifique sua lista de canais IPTV.", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MainScreenTopBar(
    selectedTab: String,
    selectedProfile: UserProfile?,
    syncState: SyncState,
    isSearchActive: Boolean,
    navBarFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onTabSelected: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF050505).copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                StreaTVLogo(
                    size = 38.dp,
                    animated = false
                )
            }

            TopNavBarTabs(
                selectedTab = selectedTab,
                navBarFocusRequester = navBarFocusRequester,
                contentFocusRequester = contentFocusRequester,
                onTabSelected = onTabSelected,
                modifier = Modifier.align(Alignment.Center)
            )

            TopNavBarActions(
                selectedProfile = selectedProfile,
                syncState = syncState,
                isSearchActive = isSearchActive,
                contentFocusRequester = contentFocusRequester,
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                onSyncClick = onSyncClick,
                onLogoutClick = onLogoutClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        if (syncState is SyncState.Syncing) {
            SyncProgressBar(syncState = syncState)
        }
    }
}

private data class TabBounds(val x: Float, val y: Float, val width: Float, val height: Float)

@Composable  
private fun TopNavBarTabs(
    selectedTab: String,
    navBarFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { listOf("Inicio", "Canais", "Filmes", "Series", "Minha Lista") }
    val viewModel = LocalIPTVViewModel.current
    val coroutineScope = rememberCoroutineScope()
    var isNavigatingTabsWithArrows by remember { mutableStateOf(false) }

    // Armazena as coordenadas do container e de cada item para animar a pill vermelha
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tabCoordinatesMap = remember { mutableStateMapOf<String, LayoutCoordinates>() }

    // Calcula a posição da aba selecionada
    val currentBounds = remember(selectedTab, containerCoordinates, tabCoordinatesMap[selectedTab]) {
        val parent = containerCoordinates
        val child = tabCoordinatesMap[selectedTab]
        if (parent != null && child != null && parent.isAttached && child.isAttached) {
            val pos = parent.localPositionOf(child, Offset.Zero)
            TabBounds(pos.x, pos.y, child.size.width.toFloat(), child.size.height.toFloat())
        } else null
    }

    // Animadores para deslocamento x, y, largura e altura
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    val animWidth = remember { Animatable(0f) }
    val animHeight = remember { Animatable(0f) }
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(currentBounds) {
        currentBounds?.let { bounds ->
            if (bounds.width > 0f && bounds.height > 0f) {
                if (!hasInitialized) {
                    animX.snapTo(bounds.x)
                    animY.snapTo(bounds.y)
                    animWidth.snapTo(bounds.width)
                    animHeight.snapTo(bounds.height)
                    hasInitialized = true
                } else {
                    val spec = spring<Float>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                    launch { animX.animateTo(bounds.x, spec) }
                    launch { animY.animateTo(bounds.y, spec) }
                    launch { animWidth.animateTo(bounds.width, spec) }
                    launch { animHeight.animateTo(bounds.height, spec) }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF141414).copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onGloballyPositioned { containerCoordinates = it },
        contentAlignment = Alignment.CenterStart
    ) {
        // Cápsula vermelha deslizante no fundo da aba ativa
        if (hasInitialized && animWidth.value > 0f && animHeight.value > 0f) {
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                    .size(
                        width = with(density) { animWidth.value.toDp() },
                        height = with(density) { animHeight.value.toDp() }
                    )
                    .clip(CircleShape)
                    .background(Color(0xFFE50914))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                val tabFocusRequester = remember(tab) { FocusRequester() }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .onGloballyPositioned { coords ->
                            tabCoordinatesMap[tab] = coords
                        }
                        .focusProperties {
                            canFocus = isSelected || isNavigatingTabsWithArrows
                        }
                        .focusRequester(if (isSelected) navBarFocusRequester else tabFocusRequester)
                        .clip(CircleShape)
                        // Sem fundo cinza no foco: o próprio fundo vermelho da pill desliza até aqui
                        .background(
                            if (!hasInitialized && isSelected) Color(0xFFE50914)
                            else Color.Transparent
                        )
                        .onFocusChanged { focusState ->
                            // Ao receber o foco das setas do controle remoto (navegação horizontal), seleciona a aba
                            if (focusState.isFocused && selectedTab != tab && isNavigatingTabsWithArrows && viewModel?.lastClickedItemId == null) {
                                onTabSelected(tab)
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionRight, Key.DirectionLeft -> {
                                        isNavigatingTabsWithArrows = true
                                        false
                                    }
                                    Key.DirectionDown -> {
                                        isNavigatingTabsWithArrows = false
                                        val success = try {
                                            contentFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                        if (!success) {
                                            coroutineScope.launch {
                                                delay(50)
                                                try {
                                                    contentFocusRequester.requestFocus()
                                                } catch (e: Exception) {
                                                    // Transient focus request error ignored
                                                }
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isNavigatingTabsWithArrows = false
                            onTabSelected(tab)
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color.LightGray.copy(alpha = 0.7f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TopNavBarActions(
    selectedProfile: UserProfile?,
    syncState: SyncState,
    isSearchActive: Boolean,
    contentFocusRequester: FocusRequester,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        var isSearchIconFocused by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSearchIconFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                .border(
                    width = if (isSearchIconFocused) 2.dp else 0.dp,
                    color = if (isSearchIconFocused) Color.White else Color.Transparent,
                    shape = CircleShape
                )
                .clickable { onSearchClick() }
                .onFocusChanged { isSearchIconFocused = it.isFocused }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                        try {
                            contentFocusRequester.requestFocus()
                        } catch (e: Exception) {
                            // Transient focus request error ignored
                        }
                        true
                    } else false
                }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Pesquisar",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        selectedProfile?.let { profile ->
            val avatarColor = try {
                Color(android.graphics.Color.parseColor(profile.avatarColorHex))
            } catch (e: Exception) {
                Color(0xFFE50914)
            }
            val context = LocalContext.current
            val svgImageLoader = remember {
                ImageLoader.Builder(context)
                    .components {
                        add(SvgDecoder.Factory())
                    }
                    .build()
            }
            var isProfileFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isProfileFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .border(
                        width = if (isProfileFocused) 2.dp else 0.dp,
                        color = if (isProfileFocused) Color.White else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onProfileClick() }
                    .onFocusChanged { isProfileFocused = it.isFocused }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                            try {
                                contentFocusRequester.requestFocus()
                            } catch (e: Exception) {
                                // Transient focus request error ignored
                            }
                            true
                        } else false
                    }
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.avatarIconName.startsWith("http")) {
                        SubcomposeAsyncImage(
                            model = profile.avatarIconName,
                            contentDescription = profile.name,
                            imageLoader = svgImageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 1.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            error = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = profile.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = profile.name,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        var isLogoutFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isLogoutFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                .border(
                    width = if (isLogoutFocused) 2.dp else 0.dp,
                    color = if (isLogoutFocused) Color.White else Color.Transparent,
                    shape = CircleShape
                )
                .clickable { onLogoutClick() }
                .onFocusChanged { isLogoutFocused = it.isFocused }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                        try {
                            contentFocusRequester.requestFocus()
                        } catch (e: Exception) {
                            // Transient focus request error ignored
                        }
                        true
                    } else false
                }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = "Sair",
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SyncProgressBar(syncState: SyncState.Syncing) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE50914))
            .padding(vertical = 4.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${syncState.message} (${(syncState.progress * 100).toInt()}%)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TrailerAndLoadingDialogs(
    activeTrailerKey: String?,
    activeTrailerTitle: String,
    isFetchingTrailer: Boolean,
    onDismissTrailer: () -> Unit,
    onDismissFetchingTrailer: () -> Unit
) {
    if (activeTrailerKey != null) {
        com.example.ui.components.TrailerPlayerDialog(
            youtubeKey = activeTrailerKey,
            title = activeTrailerTitle,
            onDismiss = onDismissTrailer
        )
    }

    if (isFetchingTrailer) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismissFetchingTrailer,
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFFE50914), modifier = Modifier.size(32.dp))
                    Text(
                        text = "Carregando Trailer...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// 2. Central Floating Card Banner (Enlarged and Cinematic)
fun PlaylistItem.getClassificationBadge(): String {
    val titleLower = title.lowercase()
    val ratingVal = rating?.toDoubleOrNull() ?: 8.0
    return when {
        titleLower.contains("furious", ignoreCase = true) ||
                titleLower.contains("obsessão", ignoreCase = true) ||
                titleLower.contains("demônio", ignoreCase = true) ||
                titleLower.contains("morte", ignoreCase = true) -> "16"

        titleLower.contains("backrooms", ignoreCase = true) ||
                titleLower.contains("rancho", ignoreCase = true) ||
                titleLower.contains("yellowstone", ignoreCase = true) -> "14"

        titleLower.contains("odisseia", ignoreCase = true) ||
                titleLower.contains("perder", ignoreCase = true) -> "12"

        titleLower.contains("moana", ignoreCase = true) ||
                titleLower.contains("pradaria", ignoreCase = true) -> "L"

        ratingVal >= 8.5 -> "14"
        ratingVal >= 8.0 -> "12"
        else -> "10"
    }
}

fun PlaylistItem.getClassificationColor(): Color {
    return when (getClassificationBadge()) {
        "L" -> Color(0xFF00A34F) // Green
        "10" -> Color(0xFF009FD6) // Blue
        "12" -> Color(0xFFF7BD15) // Yellow
        "14" -> Color(0xFFE87320) // Orange
        "16" -> Color(0xFFE31B23) // Red
        "18" -> Color(0xFF111111) // Black
        else -> Color.Gray
    }
}

fun PlaylistItem.getPortugueseTypeLabel(): String {
    return when (type) {
        "movie" -> "Filme"
        "series" -> "Série"
        "live" -> "Canal Ao Vivo"
        else -> "Destaque"
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FeaturedHeroBanner(
    item: PlaylistItem,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClick: () -> Unit,
    viewModel: IPTVViewModel,
    onColorExtracted: (Color) -> Unit = {},
    onUpPressed: () -> Unit = {},
    onDownPressed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isBannerFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val localDensity = androidx.compose.ui.platform.LocalDensity.current

    LaunchedEffect(isBannerFocused) {
        if (isBannerFocused) {
            try {
                val topOffset = with(localDensity) { -120.dp.toPx() } // Safe negative offset to leave 72.dp of space + extra padding for top bar integration
                bringIntoViewRequester.bringIntoView(
                    androidx.compose.ui.geometry.Rect(
                        left = 0f,
                        top = topOffset,
                        right = 0f,
                        bottom = 0f
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when scope leaves composition or focus shifts
            } catch (e: Exception) {
                // Ignore transient layout errors
            }
        }
    }

    val bannerScale by animateFloatAsState(
        targetValue = if (isBannerFocused) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val bannerTranslationY by animateFloatAsState(
        targetValue = if (isBannerFocused) -8f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val bannerElevation by animateDpAsState(
        targetValue = if (isBannerFocused) 16.dp else 2.dp,
        animationSpec = spring()
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(460.dp) // Made significantly larger and cinematic as requested!
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .graphicsLayer {
                scaleX = bannerScale
                scaleY = bannerScale
                translationY = bannerTranslationY * density
            }
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged {
                isBannerFocused = it.isFocused
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionRight -> {
                            onNext()
                            true
                        }
                        Key.DirectionLeft -> {
                            onPrev()
                            true
                        }
                        Key.DirectionUp -> {
                            onUpPressed()
                            true
                        }
                        Key.DirectionDown -> {
                            onDownPressed()
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .border(
                width = if (isBannerFocused) 2.dp else 0.dp,
                color = if (isBannerFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = bannerElevation)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Background dark cinematic gradient (always present as a premium fallback/placeholder!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF300000), Color(0xFF0F0F14))
                        )
                    )
            )

            // Transition Content: Slides and crossfades the backgrounds/details perfectly
            AnimatedContent(
                targetState = item,
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(500)) { it / 6 } + fadeIn(animationSpec = tween(500)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(500)) { -it / 6 } + fadeOut(animationSpec = tween(500)))
                },
                label = "HeroBannerTransition",
                modifier = Modifier.fillMaxSize()
            ) { currentItem ->
                var currentRealSeasonsCount by remember(currentItem.id) { mutableStateOf<Int?>(null) }
                var currentRealEpisodesCount by remember(currentItem.id) { mutableStateOf<Int?>(null) }

                LaunchedEffect(currentItem) {
                    if (currentItem.type == "series") {
                        try {
                            val seasonsList = viewModel.getRealSeasons(currentItem)
                            if (seasonsList.isNotEmpty()) {
                                currentRealSeasonsCount = seasonsList.size
                                currentRealEpisodesCount = seasonsList.sumOf { it.episodes?.size ?: 0 }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {

                    // Backdrop Image (High resolution with smart downscaling & memory cache)
                    val effectiveBackdrop = currentItem.getEffectiveBackdropUrl()
                    var isBackdropError by remember(currentItem.id) { mutableStateOf(false) }
                    if (!effectiveBackdrop.isNullOrEmpty() && !isBackdropError) {
                        val context = LocalContext.current
                        val backdropRequest = remember(effectiveBackdrop) {
                            ImageRequest.Builder(context)
                                .data(effectiveBackdrop)
                                .size(width = 1280, height = 720) // Optimized size for hero banner without texture memory overflow
                                .bitmapConfig(Bitmap.Config.ARGB_8888)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(false) // Required for Palette to read Bitmap pixels
                                .listener(
                                    onSuccess = { _, result ->
                                        val drawable = result.drawable
                                        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
                                            val bmp = Bitmap.createBitmap(
                                                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512,
                                                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 288,
                                                Bitmap.Config.ARGB_8888
                                            )
                                            val canvas = android.graphics.Canvas(bmp)
                                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                                            drawable.draw(canvas)
                                            bmp
                                        }
                                        Palette.from(bitmap).generate { palette ->
                                            palette?.let { p ->
                                                val extractedColor = p.getDarkVibrantColor(
                                                    p.getVibrantColor(
                                                        p.getDominantColor(0xFF050505.toInt())
                                                    )
                                                )
                                                onColorExtracted(Color(extractedColor))
                                            }
                                        }
                                    }
                                )
                                .build()
                        }
                        AsyncImage(
                            model = backdropRequest,
                            contentDescription = currentItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Error) {
                                    val throwable = state.result.throwable
                                    val isCancellation = throwable is kotlinx.coroutines.CancellationException ||
                                            throwable is java.io.InterruptedIOException ||
                                            (throwable is IllegalStateException && throwable.message?.contains("closed", ignoreCase = true) == true) ||
                                            (throwable is java.io.IOException && (
                                                throwable.message?.contains("Canceled", ignoreCase = true) == true ||
                                                throwable.message?.contains("Socket closed", ignoreCase = true) == true
                                            ))
                                    if (!isCancellation) {
                                        isBackdropError = true
                                        android.util.Log.w("HeroBanner", "Failed to load backdrop: $effectiveBackdrop, hiding image to prevent OpenGL texture crash: ${throwable.message}")
                                    }
                                }
                            }
                        )
                    }

                    // Horizontal gradient (Vignette left-to-right for readability)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.9f),
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Vertical gradient (Fade bottom-to-top for melting effect into screen)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // Metadata & Details Info at the Bottom Left
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.68f)
                            .padding(24.dp)
                            .align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (currentItem.type == "movie" || currentItem.type == "series") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                val (letter, word) = if (currentItem.type == "series") {
                                    "S" to "S É R I E"
                                } else {
                                    "F" to "F I L M E"
                                }
                                Text(
                                    text = letter,
                                    style = TextStyle(
                                        color = Color(0xFFE50914), // Netflix Red
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.SansSerif,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(1f, 1f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                                Text(
                                    text = word,
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(1f, 1f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                            }
                        }

                        // Prioridade: logo localizado retornado pelo enriquecimento (pt-BR),
                        // depois a URL legada e, por último, os fallbacks conhecidos. Se uma
                        // URL falhar, a próxima é tentada antes de mostrar o título em texto.
                        val titleLogoCandidates = remember(currentItem) {
                            listOfNotNull(
                                currentItem.titleLogoUrl?.takeIf { it.isNotBlank() },
                                currentItem.logo?.takeIf { it.isNotBlank() },
                                currentItem.getEffectiveTitleLogoUrl()
                            ).distinct()
                        }
                        // mutableStateOf é compatível com versões mais antigas do Compose.
                        var currentLogoIndex by remember(currentItem.id) { mutableStateOf(0) }
                        val titleLogo = titleLogoCandidates.getOrNull(currentLogoIndex)
                        // A URL pode chegar alguns segundos depois, quando o repositório termina
                        // a consulta ao TMDB. O estado precisa ser reiniciado pela URL — e não
                        // apenas pelo id — para que um timeout antigo não esconda o novo logo.
                        var isLogoLoadingError by remember(titleLogo) { mutableStateOf(false) }
                        var isLogoLoaded by remember(titleLogo) { mutableStateOf(false) }
                        var applyWhiteFilter by remember(titleLogo) { mutableStateOf(false) }

                        LaunchedEffect(titleLogo, currentLogoIndex) {
                            isLogoLoaded = false
                            isLogoLoadingError = false
                            applyWhiteFilter = false
                        }

                        val context = LocalContext.current
                        val logoRequest = remember(titleLogo) {
                            if (titleLogo.isNullOrEmpty()) null else {
                                ImageRequest.Builder(context)
                                    .data(titleLogo)
                                    .crossfade(true)
                                    // Não decodifique o PNG original (muitas logos têm milhares
                                    // de pixels). O AsyncImage usa o tamanho real do banner,
                                    // carrega muito mais rápido e evita o falso timeout.
                                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .allowHardware(false) // Mandatory to read pixels on CPU for palette extraction and prevent OpenGL leaks
                                    .listener(
                                        onSuccess = { _, result ->
                                            val drawable = result.drawable
                                            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
                                                val bmp = Bitmap.createBitmap(
                                                    if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 200,
                                                    if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100,
                                                    Bitmap.Config.ARGB_8888
                                                )
                                                val canvas = android.graphics.Canvas(bmp)
                                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                                drawable.draw(canvas)
                                                bmp
                                            }

                                            // Advanced pixel scanning to detect if the logo is predominantly black/dark grey
                                            var totalNonTransparent = 0
                                            var darkNonTransparent = 0
                                            var colorfulCount = 0

                                            val stepX = (bitmap.width / 15).coerceAtLeast(1)
                                            val stepY = (bitmap.height / 15).coerceAtLeast(1)

                                            for (x in 0 until bitmap.width step stepX) {
                                                for (y in 0 until bitmap.height step stepY) {
                                                    val pixel = bitmap.getPixel(x, y)
                                                    val alpha = android.graphics.Color.alpha(pixel)
                                                    if (alpha > 40) {
                                                        val r = android.graphics.Color.red(pixel)
                                                        val g = android.graphics.Color.green(pixel)
                                                        val b = android.graphics.Color.blue(pixel)

                                                        val max = maxOf(r, g, b)
                                                        val min = minOf(r, g, b)
                                                        val saturationDiff = max - min

                                                        totalNonTransparent++

                                                        if (saturationDiff < 40 && max < 75) {
                                                            darkNonTransparent++
                                                        } else if (saturationDiff > 45) {
                                                            colorfulCount++
                                                        }
                                                    }
                                                }
                                            }

                                            Palette.from(bitmap).generate { palette ->
                                                palette?.let { p ->
                                                    val dominantColorInt = p.getDominantColor(0xFFFFFFFF.toInt())
                                                    val dRed = android.graphics.Color.red(dominantColorInt)
                                                    val dGreen = android.graphics.Color.green(dominantColorInt)
                                                    val dBlue = android.graphics.Color.blue(dominantColorInt)
                                                    val dMax = maxOf(dRed, dGreen, dBlue)
                                                    val dMin = minOf(dRed, dGreen, dBlue)
                                                    val dSaturation = dMax - dMin

                                                    val isDominantBlack = dSaturation < 40 && dMax < 75
                                                    val isPixelScanBlack = if (totalNonTransparent > 0) {
                                                        val darkRatio = darkNonTransparent.toFloat() / totalNonTransparent
                                                        val colorfulRatio = colorfulCount.toFloat() / totalNonTransparent
                                                        darkRatio > 0.4f && colorfulRatio < 0.15f
                                                    } else {
                                                        false
                                                    }

                                                    applyWhiteFilter = isDominantBlack || isPixelScanBlack
                                                }
                                            }
                                        }
                                    )
                                    .build()
                            }
                        }

                        // Console Log check as requested: Log currentItem and check if 'logo' or 'titleLogoUrl' is present
                        LaunchedEffect(currentItem) {
                            android.util.Log.d("HeroBanner", "movie/series: id=${currentItem.id}, title='${currentItem.title}', logo='${currentItem.logo}', titleLogoUrl='${currentItem.titleLogoUrl}'")
                        }

                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                            // O texto só é mostrado quando não existe logo ou quando todas as
                            // URLs falharam de verdade. Durante o carregamento, o AsyncImage
                            // continua livre para desenhar assim que receber o bitmap.
                            if (titleLogo.isNullOrEmpty() || isLogoLoadingError) {
                                // Clean stylized Netflix-like movie/series text title (Plan B fallback / loading placeholder)
                                Text(
                                    text = currentItem.title,
                                    style = TextStyle(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.SansSerif,
                                        color = Color.White,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            offset = Offset(2f, 4f),
                                            blurRadius = 8f
                                        )
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!titleLogo.isNullOrEmpty() && !isLogoLoadingError) {
                                AsyncImage(
                                    model = logoRequest ?: titleLogo,
                                    contentDescription = currentItem.title,
                                    colorFilter = if (applyWhiteFilter && !currentItem.title.contains("Mestres do Universo", ignoreCase = true) && !currentItem.title.contains("Masters of the Universe", ignoreCase = true)) ColorFilter.tint(Color.White) else null,
                                    modifier = Modifier
                                        .widthIn(max = 350.dp)
                                        .heightIn(max = 120.dp)
                                        .graphicsLayer {
                                            // Standard drop-shadow approximation via graphicsLayer elevation/shadow
                                            shadowElevation = 8f
                                        },
                                    contentScale = ContentScale.Fit,
                                    onState = { state ->
                                        if (state is AsyncImagePainter.State.Success) {
                                            isLogoLoaded = true
                                        }
                                        if (state is AsyncImagePainter.State.Error) {
                                            val throwable = state.result.throwable
                                            val isCancellation = throwable is kotlinx.coroutines.CancellationException ||
                                                    throwable is java.io.InterruptedIOException ||
                                                    (throwable is IllegalStateException && throwable.message?.contains("closed", ignoreCase = true) == true) ||
                                                    (throwable is java.io.IOException && (
                                                        throwable.message?.contains("Canceled", ignoreCase = true) == true ||
                                                        throwable.message?.contains("Socket closed", ignoreCase = true) == true
                                                    ))
                                            if (!isCancellation) {
                                                android.util.Log.w("HeroBanner", "Failed to load logo: $titleLogo for ${currentItem.title}: ${throwable.message}")
                                                if (currentLogoIndex < titleLogoCandidates.lastIndex) {
                                                    currentLogoIndex++
                                                } else {
                                                    isLogoLoadingError = true
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // 2. Alinhamento de Informações (Estilo Netflix) - Ano • Gênero • Dados adicionais em texto corrido
                        val metadataParts = remember(currentItem, currentRealSeasonsCount, currentRealEpisodesCount) {
                            val parts = mutableListOf<String>()

                            // Year
                            currentItem.year?.let { y ->
                                val cleanYear = if (y.length > 4 && y.any { it.isDigit() }) {
                                    val match = Regex("\\d{4}").find(y)
                                    match?.value ?: y
                                } else y
                                if (cleanYear.isNotBlank()) parts.add(cleanYear)
                            }

                            // Genre
                            if (!currentItem.genre.isNullOrEmpty()) {
                                parts.add(currentItem.genre!!)
                            }

                            // Seasons & Episodes count if series
                            if (currentItem.type == "series") {
                                val seasons = currentRealSeasonsCount ?: currentItem.getSeriesSeasonsCount()
                                val episodes = currentRealEpisodesCount ?: currentItem.getSeriesEpisodesCount()
                                parts.add(if (seasons == 1) "1 Temporada" else "$seasons Temporadas")
                                parts.add(if (episodes == 1) "1 Episódio" else "$episodes Episódios")
                            }

                            // Classification Badge (e.g. TV-PG or 14+)
                            val ageBadge = currentItem.getClassificationBadge()
                            if (ageBadge.isNotEmpty()) {
                                parts.add(ageBadge)
                            }

                            // Rating
                            parts.add("★ ${currentItem.rating ?: "8.5"}")

                            parts
                        }

                        if (metadataParts.isNotEmpty()) {
                            Text(
                                text = metadataParts.joinToString("  •  "),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        offset = Offset(1f, 1f),
                                        blurRadius = 3f
                                    )
                                ),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        // Description Synopsis
                        Text(
                            text = currentItem.description ?: "Assista ao vivo aos canais do seu servidor StreaTV IPTV, com reprodução instantânea e guias de programação integrados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )

                        // 4. Aviso de Lançamento / Nova Temporada LOGO ABAIXO da descrição (Sinopse)
                        if (currentItem.type == "series" && currentItem.hasNewSeasonRelease()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "N O V A   T E M P O R A D A   D I S P O N Í V E L",
                                style = TextStyle(
                                    color = Color(0xFFE50914), // Netflix Red
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 3.sp, // alto tracking (espaçamento entre letras)
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        offset = Offset(1f, 1f),
                                        blurRadius = 3f
                                    )
                                )
                            )
                        }
                    }

                    // Aviso no canto inferior direito do cartão flutuante (estilo Netflix: Em alta no Brasil)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141414).copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔥",
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Em alta no Brasil",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Server Logo in the Top Right Corner (Watermark)
            StreaTVLogo(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                size = 28.dp,
                showText = true,
                animated = false
            )

            // Manual Navigation overlays (left/right icons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrev,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = if (isBannerFocused) 0.6f else 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (isBannerFocused) 0.3f else 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior", modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = onNext,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = if (isBannerFocused) 0.6f else 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (isBannerFocused) 0.3f else 0.1f),
                            shape = CircleShape
                        )
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Próximo", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// Live Channels Highlight (Canais ao vivo)
@Composable
fun LiveChannelsHighlights(
    channels: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit
) {
    if (channels.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Canais ao Vivo em Destaque",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow {
                items(channels.take(10), key = { channel -> channel.id }) { channel ->
                    LiveChannelCard(channel = channel, onClick = { onPlay(channel) })
                }
            }
        }
    }
}

// Popular Movies Highlights
@Composable
fun PopularMoviesHighlights(
    movies: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit
) {
    if (movies.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Filmes Recomendados para Você",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow {
                items(movies.take(10), key = { movie -> movie.id }) { movie ->
                    MovieCard(item = movie, onClick = { onPlay(movie) })
                }
            }
        }
    }
}

// Search Results Grid
@Composable
fun SearchResultGrid(
    movies: List<PlaylistItem>,
    series: List<PlaylistItem>,
    channels: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    val viewModel = LocalIPTVViewModel.current
    var selectedFilter by remember { mutableStateOf("Todos") } // "Todos", "Filmes", "Séries", "Canais"

    val filteredMovies = if (selectedFilter == "Todos" || selectedFilter == "Filmes") movies else emptyList()
    val filteredSeries = if (selectedFilter == "Todos" || selectedFilter == "Séries") series else emptyList()
    val filteredChannels = if (selectedFilter == "Todos" || selectedFilter == "Canais") channels else emptyList()

    val totalCount = filteredMovies.size + filteredSeries.size + filteredChannels.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Resultados da Pesquisa",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$totalCount item(s) encontrado(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Category Filter Chips: Todos, Filmes, Séries, Canais
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val filters = listOf("Todos", "Filmes", "Séries", "Canais")
                filters.forEach { filter ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) Color(0xFFE50914)
                                else if (isFocused) Color.White.copy(alpha = 0.15f)
                                else Color(0xFF222222)
                            )
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        var firstItemAssigned = false

        if (filteredChannels.isNotEmpty()) {
            Text(
                "Canais ao Vivo (${filteredChannels.size})",
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(filteredChannels, key = { _, channel -> channel.id }) { index, channel ->
                    val modifier = if (index == 0 && !firstItemAssigned && firstItemFocusRequester != null) {
                        firstItemAssigned = true
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                    LiveChannelCard(channel = channel, onClick = { onPlay(channel) }, modifier = modifier)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredMovies.isNotEmpty()) {
            Text(
                "Filmes (${filteredMovies.size})",
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(filteredMovies, key = { _, movie -> movie.id }) { index, movie ->
                    val modifier = if (index == 0 && !firstItemAssigned && firstItemFocusRequester != null) {
                        firstItemAssigned = true
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                    TMDBPosterCard(
                        item = movie,
                        onClick = { onPlay(movie) },
                        modifier = modifier
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredSeries.isNotEmpty()) {
            Text(
                "Séries (${filteredSeries.size})",
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(filteredSeries, key = { _, s -> s.id }) { index, s ->
                    val modifier = if (index == 0 && !firstItemAssigned && firstItemFocusRequester != null) {
                        firstItemAssigned = true
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                    TMDBPosterCard(
                        item = s,
                        onClick = { onPlay(s) },
                        modifier = modifier
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (totalCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Nenhum canal, filme ou série atende à pesquisa.",
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// Category Row Component (Standard)
@Composable
fun CategoryRow(
    title: String,
    items: List<PlaylistItem>,
    isSelectedHighlight: Boolean,
    onPlay: (PlaylistItem) -> Unit,
    onItemFocused: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    onItemRendered: (PlaylistItem) -> Unit = {}
) {
    // Add dynamic alpha scale to non-focused categories to focus beautifully on the highlighted one!
    val rowAlpha by animateFloatAsState(if (isSelectedHighlight) 1f else 0.4f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = if (isSelectedHighlight) Color.White else Color.LightGray,
                fontWeight = FontWeight.Bold
            )
            if (isSelectedHighlight) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFFE50914), CircleShape)
                )
            }
        }

        val viewModel = LocalIPTVViewModel.current
        val clickedIndex = remember(items, viewModel?.lastClickedItemId) {
            items.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        LazyRow(state = listState) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                MovieCard(
                    item = item,
                    onClick = { onPlay(item) },
                    onFocused = { onItemFocused(item) },
                    onItemRendered = onItemRendered,
                    modifier = if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

// Dedicated Movies & Series Category Row (2-card centered layout with title logos and synopsis below)
@Composable
fun MoviesSeriesCategoryRow(
    categoryTitle: String,
    items: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    onItemFocused: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    categoryIndex: Int = 0,
    onUpPressed: () -> Unit,
    onDownPressed: () -> Unit
) {
    val viewModel = LocalIPTVViewModel.current

    var focusedItemIndex by remember(categoryTitle) {
        val lastId = viewModel?.lastClickedItemId
        val idx = if (lastId != null) items.indexOfFirst { it.id == lastId } else -1
        mutableStateOf(if (idx >= 0) idx else 0)
    }

    val safeIndex = focusedItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val currentItem = items.getOrNull(safeIndex) ?: items.firstOrNull()

    LaunchedEffect(currentItem) {
        currentItem?.let { item ->
            onItemFocused(item)
            viewModel?.enrichItemOnDemand(item)
        }
    }

    val pageStartIndex = (safeIndex / 2) * 2
    val visibleItems = remember(items, pageStartIndex) {
        items.drop(pageStartIndex).take(2)
    }

    LaunchedEffect(visibleItems) {
        visibleItems.forEach { item ->
            viewModel?.enrichItemOnDemand(item)
        }
    }

    val slot0FocusRequester = remember { FocusRequester() }
    val slot1FocusRequester = remember { FocusRequester() }
    var isRowInteracted by remember { mutableStateOf(false) }
    var previousCategoryIndex by remember { mutableStateOf(categoryIndex) }

    // When navigating down or up between categories, automatically focus the first card in the new category
    LaunchedEffect(categoryIndex) {
        if (categoryIndex != previousCategoryIndex) {
            previousCategoryIndex = categoryIndex
            focusedItemIndex = 0
            isRowInteracted = true
            try {
                slot0FocusRequester.requestFocus()
            } catch (e: Exception) {
                try {
                    firstItemFocusRequester?.requestFocus()
                } catch (e2: Exception) {
                    delay(20)
                    try {
                        slot0FocusRequester.requestFocus()
                    } catch (e3: Exception) {
                        e3.printStackTrace()
                    }
                }
            }
        }
    }

    // Restore focus to last clicked item on back navigation
    LaunchedEffect(categoryTitle, items) {
        val lastId = viewModel?.lastClickedItemId
        if (lastId != null) {
            val idx = items.indexOfFirst { it.id == lastId }
            if (idx >= 0) {
                focusedItemIndex = idx
                isRowInteracted = true
                val slot = idx % 2
                delay(60)
                try {
                    if (slot == 0) {
                        slot0FocusRequester.requestFocus()
                    } else {
                        slot1FocusRequester.requestFocus()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModel?.lastClickedItemId = null
        }
    }

    // Switch focus between slots on arrow navigation without delay
    LaunchedEffect(safeIndex) {
        if (!isRowInteracted) return@LaunchedEffect
        val slot = safeIndex % 2
        try {
            if (slot == 0) {
                slot0FocusRequester.requestFocus()
            } else {
                slot1FocusRequester.requestFocus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = categoryTitle,
            fontSize = 24.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            val totalWidth = maxWidth
            val gap = 20.dp
            val cardWidth = (totalWidth - gap) / 2
            val cardHeight = cardWidth * 0.58f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                visibleItems.forEachIndexed { relativeIdx, item ->
                    val absoluteIdx = pageStartIndex + relativeIdx
                    val isFocused = (absoluteIdx == safeIndex)
                    val cardRequester = if (relativeIdx == 0) slot0FocusRequester else slot1FocusRequester

                    key(relativeIdx) {
                        MovieSeriesCardItem(
                            item = item,
                            isFocused = isFocused,
                            cardWidth = cardWidth,
                            cardHeight = cardHeight,
                            focusRequester = cardRequester,
                            firstItemFocusRequester = if (relativeIdx == 0) firstItemFocusRequester else null,
                            onClick = { onPlay(item) },
                            onFocusGained = {
                                isRowInteracted = true
                                focusedItemIndex = absoluteIdx
                            },
                            onKeyLeft = {
                                if (safeIndex > 0) {
                                    focusedItemIndex = safeIndex - 1
                                }
                            },
                            onKeyRight = {
                                if (safeIndex < items.size - 1) {
                                    focusedItemIndex = safeIndex + 1
                                }
                            },
                            onKeyUp = { onUpPressed() },
                            onKeyDown = { onDownPressed() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentItem != null) {
            var realSeasonsCount by remember(currentItem.id) { mutableStateOf<Int?>(null) }
            var realEpisodesCount by remember(currentItem.id) { mutableStateOf<Int?>(null) }

            LaunchedEffect(currentItem.id) {
                if (currentItem.type == "series" && viewModel != null) {
                    try {
                        val seasonsList = viewModel.getRealSeasons(currentItem)
                        if (seasonsList.isNotEmpty()) {
                            realSeasonsCount = seasonsList.size
                            realEpisodesCount = seasonsList.sumOf { it.episodes?.size ?: 0 }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                val metaParts = mutableListOf<String>()

                metaParts.add(if (currentItem.type == "series") "Série" else "Filme")

                currentItem.genre?.takeIf { it.isNotBlank() }?.let { metaParts.add(it) }
                currentItem.year?.takeIf { it.isNotBlank() }?.let { metaParts.add(it) }

                if (currentItem.type == "series") {
                    val sCount = realSeasonsCount ?: 1
                    val eCount = realEpisodesCount ?: 0
                    metaParts.add("$sCount ${if (sCount == 1) "Temporada" else "Temporadas"}")
                    if (eCount > 0) {
                        metaParts.add("$eCount Episódios")
                    }
                }

                currentItem.rating?.takeIf { it.isNotBlank() }?.let { metaParts.add("TMDB $it") }

                Text(
                    text = metaParts.joinToString(" • "),
                    color = Color(0xFFE0E0E0),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val synopsis = currentItem.description?.takeIf { it.isNotBlank() }
                    ?: "Sinopse não disponível para este título."

                Text(
                    text = synopsis,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MovieSeriesCardItem(
    item: PlaylistItem,
    isFocused: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester,
    firstItemFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onFocusGained: () -> Unit,
    onKeyLeft: () -> Unit,
    onKeyRight: () -> Unit,
    onKeyUp: () -> Unit,
    onKeyDown: () -> Unit
) {
    val viewModel = LocalIPTVViewModel.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "movieSeriesCardScale"
    )

    LaunchedEffect(item) {
        viewModel?.enrichItemOnDemand(item)
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181818))
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester)
                else Modifier
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged {
                if (it.isFocused) {
                    onFocusGained()
                    viewModel?.enrichItemOnDemand(item)
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onKeyLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onKeyRight()
                            true
                        }
                        Key.DirectionUp -> {
                            onKeyUp()
                            true
                        }
                        Key.DirectionDown -> {
                            onKeyDown()
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        val rawBackdrop = item.getEffectiveBackdropUrl()?.takeIf { it.isNotEmpty() }
            ?: item.backdropUrl?.takeIf { it.isNotEmpty() }
            ?: item.logoUrl?.takeIf { it.isNotEmpty() }
            ?: item.logo?.takeIf { it.isNotEmpty() }
            ?: item.getEffectiveLogoUrl()
        val effectiveBackdrop = rawBackdrop.toOriginalTmdbUrlOrNull()

        if (!effectiveBackdrop.isNullOrEmpty()) {
            val context = LocalContext.current
            val imageRequest = remember(effectiveBackdrop) {
                ImageRequest.Builder(context)
                    .data(effectiveBackdrop)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Image(
            painter = painterResource(id = R.drawable.streatv_logo), // Substitua "sua_logo" pelo nome do seu arquivo em res/drawable
            contentDescription = "Logo do App",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .size(28.dp) // Ajuste a largura/altura conforme o tamanho ideal para o seu card
        )

        val titleLogo = item.titleLogoUrl?.takeIf { it.isNotBlank() }
            ?: item.logo?.takeIf { it.isNotBlank() }
            ?: item.getEffectiveTitleLogoUrl()

        var isLogoFailed by remember(titleLogo) { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(0.70f)
                .height(54.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            if (!titleLogo.isNullOrEmpty() && !isLogoFailed) {
                com.example.ui.components.LogoAsyncImage(
                    logoUrl = titleLogo,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.fillMaxHeight(),
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            isLogoFailed = true
                        }
                    }
                )
            } else {
                Text(
                    text = item.getCleanPtBrTitle(),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Live Channel Card (EPG Integration) with Focus Zoom/Jump Effect
@Composable
fun LiveChannelCard(
    channel: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == channel.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    // Dynamically retrieve context EPG schedules!
    var currentProgram by remember(channel.id) { mutableStateOf<Program?>(null) }
    LaunchedEffect(channel.id) {
        if (viewModel != null) {
            val creds = viewModel.getSavedCredentials()
            val server = viewModel.getSavedServerUrl()
            val user = creds.first ?: ""
            val pass = creds.second ?: ""
            val streamId = EPGGenerator.extractStreamId(channel)
            val list = EPGGenerator.generateEPG(user, pass, streamId, server)
            currentProgram = list.firstOrNull()
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liveChannelCardScale"
    )

    Card(
        modifier = modifier
            .focusRequester(localFocusRequester)
            .width(180.dp)
            .padding(6.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .focusable()
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.small
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color.Black)
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val channelRequest = remember(channel.logoUrl) {
                        ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .size(width = 360, height = 180) // Proportional safe size for LiveChannelCard
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = channelRequest,
                        contentDescription = channel.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = channel.title,
                        tint = Color.DarkGray,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }

                // Live Badge Overlays
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Red, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // EPG Details
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = channel.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Show current EPG title
                currentProgram?.let { prog ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Agora: ${prog.title}",
                        fontSize = 9.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Program Timeline progress bar
                    LinearProgressIndicator(
                        progress = { prog.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFFE50914),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

// VOD Card (Movie or Series) with Horizontal (Deitado) Layout, containing backdrop poster and overlaid title logo
@Composable
fun MovieCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    onItemRendered: (PlaylistItem) -> Unit = {}
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
            viewModel?.enrichItemOnDemand(item)
        }
    }

    LaunchedEffect(item.id) {
        onItemRendered(item)
        viewModel?.enrichItemOnDemand(item)
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "movieCardScale"
    )

    Box(
        modifier = modifier
            .width(156.dp)
            .height(88.dp)
            .padding(4.dp) // Adjusted padding for smaller card size
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .focusable()
            .focusRequester(localFocusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .border(
                width = if (isFocusedState.value) 2.dp else 1.dp,
                color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.small
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = MaterialTheme.shapes.small
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val rawBackdrop = item.getEffectiveBackdropUrl()?.takeIf { it.isNotEmpty() }
                    ?: item.backdropUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logo?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveLogoUrl()
                val effectiveBackdrop = rawBackdrop.toOriginalTmdbUrlOrNull()

                if (!effectiveBackdrop.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val cardRequest = remember(effectiveBackdrop) {
                        ImageRequest.Builder(context)
                            .data(effectiveBackdrop)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = cardRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Dark elegant scrim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Overlaid Title Logo or Title Text
                val titleLogo = item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveTitleLogoUrl()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (!titleLogo.isNullOrEmpty()) {
                        com.example.ui.components.LogoAsyncImage(
                            logoUrl = titleLogo,
                            contentDescription = item.title,
                            modifier = Modifier
                                .width(90.dp)
                                .height(26.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column {
                            Text(
                                text = item.cleanTitle ?: item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.groupName.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.groupName,
                                    color = Color.LightGray.copy(alpha = 0.7f),
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (item.type != "live") {
                    val favoriteIds by viewModel?.favoriteItemIds?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
                    val isFav = favoriteIds.contains(item.id)

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { viewModel?.toggleFavorite(item.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFav) "Favorito" else "Adicionar aos Favoritos",
                            tint = if (isFav) Color(0xFFE50914) else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

// Continue Watching Section Layout
@Composable
fun ContinueWatchingRowSection(
    viewModel: IPTVViewModel,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    val continueWatching by viewModel.continueWatching.collectAsState(initial = emptyList())
    if (continueWatching.isNotEmpty()) {
        Column {
            Text(
                text = "Continue Assistindo",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            val clickedIndex = remember(continueWatching, viewModel.lastClickedItemId) {
                continueWatching.indexOfFirst { it.id == viewModel.lastClickedItemId }
            }
            val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(continueWatching, key = { _, item -> item.id }) { index, item ->
                    val cardFocusRequester = if (index == 0 && firstItemFocusRequester != null) {
                        firstItemFocusRequester
                    } else {
                        remember { FocusRequester() }
                    }
                    ContinueWatchingCard(
                        item = item,
                        viewModel = viewModel,
                        onClick = { onPlay(item) },
                        focusRequester = cardFocusRequester
                    )
                }
            }
        }
    }
}

// Continue Watching Card with Horizontal (Deitado) Layout, containing a Vertical Poster on the left, logo/details on the right, and remaining progress time.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    item: PlaylistItem,
    viewModel: IPTVViewModel,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }
    var progressFraction by remember { mutableStateOf(0f) }
    var elapsedText by remember { mutableStateOf("") }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (viewModel.lastClickedItemId == item.id) {
            focusRequester.requestFocus()
            viewModel.lastClickedItemId = null
            coroutineScope.launch {
                delay(50)
                try {
                    bringIntoViewRequester.bringIntoView()
                } catch (e: Exception) {}
            }
        }
    }

    LaunchedEffect(item.id) {
        val history = viewModel.getWatchProgress(item.id)
        if (history != null && history.duration > 0) {
            progressFraction = history.position.toFloat() / history.duration.toFloat()
            val secs = history.position / 1000
            val mins = secs / 60
            val hours = mins / 60
            elapsedText = if (hours > 0) {
                "${hours}h ${mins % 60}m"
            } else {
                String.format("%02d:%02d", mins, secs % 60)
            }
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "continueWatchingCardScale"
    )

    Card(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester)
            .width(260.dp)
            .height(135.dp)
            .padding(4.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        try {
                            bringIntoViewRequester.bringIntoView()
                        } catch (e: Exception) {}
                    }
                }
            }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocusedState.value) 2.5.dp else 1.dp,
                color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Left portion: Vertical Poster
                Box(
                    modifier = Modifier
                        .width(85.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                ) {
                    val effectiveLogo = item.logoUrl?.takeIf { it.isNotEmpty() }
                        ?: item.logo?.takeIf { it.isNotEmpty() }
                        ?: item.getEffectiveLogoUrl()

                    if (!effectiveLogo.isNullOrEmpty()) {
                        val context = LocalContext.current
                        val posterRequest = remember(effectiveLogo) {
                            ImageRequest.Builder(context)
                                .data(effectiveLogo)
                                .size(width = 240, height = 360)
                                .bitmapConfig(Bitmap.Config.RGB_565)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build()
                        }
                        AsyncImage(
                            model = posterRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Right portion: Details, Logo, and Pause timestamp
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        val titleLogo = item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                            ?: item.getEffectiveTitleLogoUrl()

                        if (!titleLogo.isNullOrEmpty()) {
                            com.example.ui.components.LogoAsyncImage(
                                logoUrl = titleLogo,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = item.cleanTitle ?: item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.groupName,
                            color = Color.Gray,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (elapsedText.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFE50914),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Parou em: $elapsedText",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Red Progress Bar along the bottom of the card
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFFE50914),
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

// ==========================================
// TMDB.ORG STYLE COMPONENT IMPLEMENTATIONS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TMDBWelcomeSection(
    onSearch: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isButtonFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF032541), Color(0xFF01b4e4))
                )
            )
            .padding(28.dp)
    ) {
        Column {
            Text(
                text = "Bem-Vindo(a).",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Text(
                text = "Milhões de Filmes, Séries e Pessoas para Descobrir. Explore já.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                fontSize = 16.sp
            )

            // Search input field with search button inside
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .onFocusChanged { isSearchFocused = it.isFocused },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar por um Filme, Série ou Pessoa...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                // Search button
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF1de9b6), Color(0xFF00b0ff))
                            )
                        )
                        .clickable { onSearch(searchQuery) }
                        .onFocusChanged { isButtonFocused = it.isFocused }
                        .border(
                            width = if (isButtonFocused) 2.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Buscar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrendingSection(
    trendingItems: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    var selectedPeriod by remember { mutableStateOf("Hoje") }
    val hojeFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header Row with Title + Pill Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "Tendências",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Toggle pill selector with dynamic remote control focus
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .border(1.dp, Color(0xFF032541), CircleShape)
                    .background(Color(0xFF051d2f), CircleShape)
                    .padding(2.dp)
                    .onFocusChanged {
                        if (it.isFocused) {
                            hojeFocusRequester.requestFocus()
                        }
                    }
                    .focusable(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hoje pill
                var isHojeFocused by remember { mutableStateOf(false) }
                val isHoje = selectedPeriod == "Hoje"
                Box(
                    modifier = Modifier
                        .focusRequester(hojeFocusRequester)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            brush = if (isHoje) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                            shape = CircleShape
                        )
                        .background(
                            color = if (!isHoje && isHojeFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .border(
                            width = if (isHojeFocused) 1.dp else 0.dp,
                            color = if (isHojeFocused) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .onFocusChanged {
                            isHojeFocused = it.isFocused
                            if (it.isFocused) {
                                selectedPeriod = "Hoje"
                            }
                        }
                        .focusable()
                        .clickable { selectedPeriod = "Hoje" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Hoje",
                        color = if (isHoje) Color(0xFF032541) else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Nesta Semana pill
                var isSemanaFocused by remember { mutableStateOf(false) }
                val isSemana = selectedPeriod == "Nesta Semana"
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            brush = if (isSemana) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                            shape = CircleShape
                        )
                        .background(
                            color = if (!isSemana && isSemanaFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .border(
                            width = if (isSemanaFocused) 1.dp else 0.dp,
                            color = if (isSemanaFocused) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .onFocusChanged {
                            isSemanaFocused = it.isFocused
                            if (it.isFocused) {
                                selectedPeriod = "Nesta Semana"
                            }
                        }
                        .focusable()
                        .clickable { selectedPeriod = "Nesta Semana" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nesta Semana",
                        color = if (isSemana) Color(0xFF032541) else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val viewModel = LocalIPTVViewModel.current

        val clickedIndex = remember(trendingItems, viewModel?.lastClickedItemId) {
            trendingItems.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        // LazyRow of movie posters
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sort or filter slightly based on period selection for realism!
            val sortedItems = if (selectedPeriod == "Hoje") trendingItems else trendingItems.reversed()
            itemsIndexed(sortedItems, key = { _, item -> item.id }) { index, item ->
                TMDBPosterCard(
                    item = item,
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        try {
                                            hojeFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (downFocusRequester != null) {
                                            try {
                                                downFocusRequester.requestFocus()
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LatestTrailersSection(
    trailerItems: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    var selectedTrailerTab by remember { mutableStateOf("Popular") }
    val tabs = listOf("Popular", "Streaming", "Na TV", "Para Alugar", "Nos Cinemas")
    val popularTabFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header
        Column(
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Últimos Trailers",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Toggle pill selectors row with dynamic remote control focus
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            popularTabFocusRequester.requestFocus()
                        }
                    }
                    .focusable()
            ) {
                items(tabs, key = { tab -> tab }) { tab ->
                    val isSelected = selectedTrailerTab == tab
                    var isTabFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .run {
                                if (tab == "Popular") focusRequester(popularTabFocusRequester) else this
                            }
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(
                                brush = if (isSelected) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                shape = CircleShape
                            )
                            .background(
                                color = if (!isSelected) {
                                    if (isTabFocused) Color.White.copy(alpha = 0.15f) else Color(0xFF0F172A)
                                } else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (isTabFocused) Color.White else if (isSelected) Color.Transparent else Color(0xFF032541),
                                shape = CircleShape
                            )
                            .onFocusChanged {
                                isTabFocused = it.isFocused
                                if (it.isFocused) {
                                    selectedTrailerTab = tab
                                }
                            }
                            .focusable()
                            .clickable { selectedTrailerTab = tab }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color(0xFF032541) else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val viewModel = LocalIPTVViewModel.current
        val clickedIndex = remember(trailerItems, viewModel?.lastClickedItemId) {
            trailerItems.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        // LazyRow of trailers
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val itemsToRender = when (selectedTrailerTab) {
                "Popular" -> trailerItems
                "Streaming" -> trailerItems.shuffled()
                else -> trailerItems.reversed()
            }
            itemsIndexed(itemsToRender, key = { _, item -> item.id }) { index, item ->
                TMDBTrailerCard(
                    item = item,
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstItemFocusRequester != null) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        try {
                                            popularTabFocusRequester.requestFocus()
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (downFocusRequester != null) {
                                            try {
                                                downFocusRequester.requestFocus()
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TMDBTrailerCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
            coroutineScope.launch {
                delay(50)
                try {
                    bringIntoViewRequester.bringIntoView()
                } catch (e: Exception) {}
            }
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "trailerCardScale"
    )

    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(localFocusRequester)
            .width(220.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        try {
                            bringIntoViewRequester.bringIntoView()
                        } catch (e: Exception) {}
                    }
                }
            }
            .focusable()
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .border(
                    width = if (isFocusedState.value) 2.dp else 1.dp,
                    color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val effectiveBackdrop = item.getEffectiveBackdropUrl()
                if (!effectiveBackdrop.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val trailerRequest = remember(effectiveBackdrop) {
                        ImageRequest.Builder(context)
                            .data(effectiveBackdrop)
                            .size(width = 440, height = 248)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = trailerRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF032541), Color(0xFF1A1A1A))
                                )
                            )
                    )
                }

                // Centered Semi-Translucent Play Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.75f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF032541),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val titleLogo = item.getEffectiveTitleLogoUrl()
        if (!titleLogo.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                com.example.ui.components.LogoAsyncImage(
                    logoUrl = titleLogo,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Text(
                text = item.cleanTitle ?: item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = item.description ?: "Trailer Oficial",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MostPopularSection(
    popularItems: List<PlaylistItem>,
    onPlay: (PlaylistItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    var selectedPopularTab by remember { mutableStateOf("Streaming") }
    val tabs = listOf("Streaming", "Na TV", "Para Alugar", "Nos Cinemas")
    val popularSectionTabFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "Os Mais Populares",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Toggle pill selector with dynamic remote control focus
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .border(1.dp, Color(0xFF032541), CircleShape)
                    .background(Color(0xFF051d2f), CircleShape)
                    .padding(2.dp)
                    .onFocusChanged {
                        if (it.isFocused) {
                            popularSectionTabFocusRequester.requestFocus()
                        }
                    }
                    .focusable(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedPopularTab == tab
                    var isTabFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .run {
                                if (tab == "Streaming") focusRequester(popularSectionTabFocusRequester) else this
                            }
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                brush = if (isSelected) Brush.horizontalGradient(listOf(Color(0xFFC0FECF), Color(0xFF1ED5A9)))
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                shape = CircleShape
                            )
                            .background(
                                color = if (!isSelected && isTabFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = if (isTabFocused) 1.dp else 0.dp,
                                color = if (isTabFocused) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .onFocusChanged {
                                isTabFocused = it.isFocused
                                if (it.isFocused) {
                                    selectedPopularTab = tab
                                }
                            }
                            .focusable()
                            .clickable { selectedPopularTab = tab }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color(0xFF032541) else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val viewModel = LocalIPTVViewModel.current

        val clickedIndex = remember(popularItems, viewModel?.lastClickedItemId) {
            popularItems.indexOfFirst { it.id == viewModel?.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        // LazyRow of movie posters
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val itemsToRender = when (selectedPopularTab) {
                "Streaming" -> popularItems
                "Na TV" -> popularItems.shuffled()
                else -> popularItems.reversed()
            }
            itemsIndexed(itemsToRender, key = { _, item -> item.id }) { index, item ->
                TMDBPosterCard(
                    item = item,
                    onClick = { onPlay(item) },
                    modifier = if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TMDBPosterCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = LocalIPTVViewModel.current
    val localFocusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedState = remember { derivedStateOf { isFocused } }

    LaunchedEffect(Unit) {
        if (viewModel != null && viewModel.lastClickedItemId == item.id) {
            localFocusRequester.requestFocus()
            viewModel.lastClickedItemId = null
            coroutineScope.launch {
                delay(50)
                try {
                    bringIntoViewRequester.bringIntoView()
                } catch (e: Exception) {}
            }
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isFocusedState.value) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "posterCardScale"
    )

    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(localFocusRequester)
            .width(115.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        try {
                            bringIntoViewRequester.bringIntoView()
                        } catch (e: Exception) {}
                    }
                }
            }
            .focusable()
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .border(
                    width = if (isFocusedState.value) 2.dp else 1.dp,
                    color = if (isFocusedState.value) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val effectiveLogo = item.logoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.logo?.takeIf { it.isNotEmpty() }
                    ?: item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                    ?: item.getEffectiveLogoUrl()

                if (!effectiveLogo.isNullOrEmpty()) {
                    val context = LocalContext.current
                    val posterRequest = remember(effectiveLogo) {
                        ImageRequest.Builder(context)
                            .data(effectiveLogo)
                            .size(width = 240, height = 360)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = posterRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF032541), Color(0xFF1A1A1A))
                                )
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val titleLogo = item.getEffectiveTitleLogoUrl()
                        if (!titleLogo.isNullOrEmpty()) {
                            com.example.ui.components.LogoAsyncImage(
                                logoUrl = titleLogo,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = item.cleanTitle ?: item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (item.type != "live") {
                    val favoriteIds by viewModel?.favoriteItemIds?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
                    val isFav = favoriteIds.contains(item.id)

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { viewModel?.toggleFavorite(item.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFav) "Favorito" else "Adicionar aos Favoritos",
                            tint = if (isFav) Color(0xFFE50914) else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val titleLogo = item.getEffectiveTitleLogoUrl()
        if (!titleLogo.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                com.example.ui.components.LogoAsyncImage(
                    logoUrl = titleLogo,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Text(
                text = item.cleanTitle ?: item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = item.year ?: "9 de julho de 2026",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

// ==========================================
// TMDB.ORG STATIC FALLBACK DATA
// ==========================================

val defaultTrending = listOf(
    PlaylistItem(id = 9901, title = "Obsessão", logoUrl = "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg", year = "14 de maio de 2026", description = "Uma presença obsessiva e aterrorizante que ameaça a sanidade de uma jovem herdeira.", rating = "8.2", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9902, title = "Uma Casa na Pradaria", logoUrl = "https://image.tmdb.org/t/p/w500/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg", year = "9 de julho de 2026", description = "Uma família busca recomeçar a vida em uma pacata região rural, mas forças misteriosas começam a afetar a plantação.", rating = "7.9", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9903, title = "The Furious", logoUrl = "https://image.tmdb.org/t/p/w500/k334z7zxOJlJvOsOwyRF6HClCvi.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg", year = "10 de junho de 2026", description = "Um piloto de fuga lendário é forçado a voltar à ativa para salvar seu irmão de uma gangue.", rating = "8.7", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9904, title = "Moana", logoUrl = "https://image.tmdb.org/t/p/w500/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg", year = "9 de julho de 2026", description = "Uma jovem navegadora embarca em uma ousada missão marítima para salvar seu povo.", rating = "9.0", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9915, title = "Silo", logoUrl = "https://image.tmdb.org/t/p/w500/u7vS96H96oI4UIs3pLI85gSIs4u.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/q7776Z9Yg58P6N8v7HkWK6fOunY.jpg", year = "4 de maio de 2023", description = "Em um futuro degradado e tóxico, uma comunidade existe em um gigantesco silo subterrâneo que se estende por centenas de andares abaixo da terra.", rating = "8.3", groupName = "Tendências", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9905, title = "A Morte do Demônio: Em Chamas", logoUrl = "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg", year = "7 de julho de 2026", description = "O despertar de uma força demoníaca ancestral liberta espíritos malignos.", rating = "8.5", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9916, title = "Trunfo", logoUrl = "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg", year = "10 de julho de 2026", description = "Um drama de alta espionagem e intriga política onde um agente secreto joga seu último trunfo para desmascarar uma conspiração global.", rating = "8.0", groupName = "Tendências", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9906, title = "A Odisseia", logoUrl = "https://image.tmdb.org/t/p/w500/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg", year = "16 de julho de 2026", description = "Uma jornada épica inspirada no clássico grego.", rating = "8.9", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9907, title = "Sem Nada a Perder", logoUrl = "https://image.tmdb.org/t/p/w500/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg", year = "8 de julho de 2026", description = "Dois detetives obstinados entram em um jogo perigoso de gato e rato.", rating = "8.1", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9908, title = "Backrooms", logoUrl = "https://image.tmdb.org/t/p/w500/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg", year = "28 de maio de 2026", description = "Um labirinto infinito de salas amarelas vazias habitadas por uma entidade misteriosa.", rating = "8.3", groupName = "Tendências", type = "movie", url = "http://streatv.elementfx.com/get.php")
)

val defaultTrailers = listOf(
    PlaylistItem(id = 9911, title = "O Fim da Rua", logoUrl = "https://image.tmdb.org/t/p/w500/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg", year = "Trailer Oficial Dublado", description = "Trailer Oficial Dublado", rating = "7.8", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9912, title = "A Morte do Demônio: Em Chamas", logoUrl = "https://image.tmdb.org/t/p/w500/fteLdvfRnltfLjAEnsl5E3vImnW.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg", year = "Teaser de Anúncio da Data", description = "Teaser de Anúncio da Data", rating = "8.2", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9913, title = "Hope", logoUrl = "https://image.tmdb.org/t/p/w500/7WOBeoaXUNGzbm79Umio2CcH941.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg", year = "Official US Trailer [Subtitled]", description = "Official US Trailer [Subtitled]", rating = "7.5", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9914, title = "Jackass: Best and Last", logoUrl = "https://image.tmdb.org/t/p/w500/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg", year = "Big Red Rocket", description = "Big Red Rocket", rating = "8.0", groupName = "Últimos Trailers", type = "movie", url = "http://streatv.elementfx.com/get.php")
)

val defaultPopular = listOf(
    PlaylistItem(id = 9921, title = "A Casa do Dragão", logoUrl = "https://image.tmdb.org/t/p/w500/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg", year = "21 de agosto de 2022", description = "A história da Casa Targaryen, ambientada 200 anos antes dos eventos de Game of Thrones.", rating = "9.1", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9922, title = "NCIS: Investigação Naval", logoUrl = "https://image.tmdb.org/t/p/w500/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg", year = "14 de maio de 2026", description = "Casos criminais envolvendo pessoal da Marinha americana.", rating = "8.4", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9923, title = "Na Zona Cinzenta", logoUrl = "https://image.tmdb.org/t/p/w500/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg", year = "14 de maio de 2026", description = "Agentes especiais operam nas fronteiras da legalidade internacional.", rating = "8.0", groupName = "Os Mais Populares", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9924, title = "O Limite do Prazer", logoUrl = "https://image.tmdb.org/t/p/w500/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg", year = "4 de setembro de 2025", description = "Um romance tórrido e perigoso entre um escritor intrigado e uma misteriosa milionária.", rating = "7.8", groupName = "Os Mais Populares", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9925, title = "Obsessão", logoUrl = "https://image.tmdb.org/t/p/w500/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg", year = "14 de maio de 2026", description = "Presença obsessiva e aterrorizante que ameaça a sanidade de uma jovem herdeira.", rating = "8.2", groupName = "Os Mais Populares", type = "movie", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9926, title = "Lei & Ordem", logoUrl = "https://image.tmdb.org/t/p/w500/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg", year = "13 de setembro de 1990", description = "Casos criminais sob a ótica dos detetives policiais.", rating = "8.8", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php"),
    PlaylistItem(id = 9927, title = "Rancho Dutton", logoUrl = "https://image.tmdb.org/t/p/w500/aJVroxZZKReiSlASpODQ23qrdGa.jpg", backdropUrl = "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg", year = "15 de outubro de 2026", description = "A luta diária de uma família proprietária de terras em Montana.", rating = "8.9", groupName = "Os Mais Populares", type = "series", url = "http://streatv.elementfx.com/get.php")
)

fun PlaylistItem.getSeriesSeasonsCount(): Int {
    return when {
        title.contains("Yellowstone", ignoreCase = true) || title.contains("Rancho", ignoreCase = true) -> 5
        title.contains("Grey", ignoreCase = true) || title.contains("Cinzenta", ignoreCase = true) -> 20
        title.contains("NCIS", ignoreCase = true) -> 21
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> 23
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> 2
        else -> 3
    }
}

fun PlaylistItem.getSeriesEpisodesCount(): Int {
    return when {
        title.contains("Yellowstone", ignoreCase = true) || title.contains("Rancho", ignoreCase = true) -> 47
        title.contains("Grey", ignoreCase = true) || title.contains("Cinzenta", ignoreCase = true) -> 430
        title.contains("NCIS", ignoreCase = true) -> 460
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> 540
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> 20
        else -> 30
    }
}

fun PlaylistItem.hasNewSeasonRelease(): Boolean {
    return title.contains("Yellowstone", ignoreCase = true) ||
            title.contains("Rancho", ignoreCase = true) ||
            title.contains("Grey", ignoreCase = true) ||
            title.contains("Cinzenta", ignoreCase = true) ||
            title.contains("NCIS", ignoreCase = true) ||
            title.contains("Dragão", ignoreCase = true) ||
            title.contains("Dragon", ignoreCase = true)
}

fun PlaylistItem.getEffectiveTitleLogoUrl(): String? {
    if (!titleLogoUrl.isNullOrEmpty()) return titleLogoUrl
    if (!logo.isNullOrEmpty()) return logo

    return when {
        title.contains("Passageiro do Mal", ignoreCase = true) || title.contains("Passenger", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/2wdMrwQHQ28q69IMvqmc3SUAL8W.png"
        title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/iJLKPeerez9GGa9kwtYsu6rsSla.png"
        title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rpNau5FSkTtAVCVORJpqUmSXAHJ.png"
        title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/msFWTNrFwfjsHKnMxDUexR9uofh.png"
        title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/megWWuC8tSODx90i0gP0fVM1wVo.png"
        title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/4jzFpQg7D0AB5GQq3lp8k4HTYxE.png"
        title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/jpLOFTHacWoxCTCLRePVlLHxrbo.png"
        title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/3pzH42BKQ0z30fM9DPwSewvWYLS.png"
        title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/oHP5aXQ4gg15t9EsBhgKENeCKdG.png"
        title.contains("Rua", ignoreCase = true) || title.contains("Oak Street", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/e0HtKXHvWKFiYmzP3G8xLo7jo5B.png"
        title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/iEYvlw4yqSp9Ck6Iz90V31QSD7D.png"
        title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/zdzXhbQgjOQzrQ6Yq5kWepYw7MJ.png"
        title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9w0aK6Vs1YqSp13EhrlZVQ4TJ1u.png"
        title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/gAwgHNn9bgNefFEzv2sY6Q2BRVo.png"
        title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/vIprMXDcEruwpW4aL5PMTQrkljb.png"
        title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/lrK7KGhdytsZGAggPU2N6fPMv4y.png"
        title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rj1CuaIo5BYr9XxlLztZMnWP3YT.png"
        title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wQ7FFixqLXD2aOqtoiawyz7IbVx.png"
        else -> null
    }
}

fun String.toOriginalTmdbUrl(): String {
    if (this.contains("image.tmdb.org/t/p/")) {
        val filename = this.substringAfterLast("/")
        if (filename.isNotEmpty()) {
            return "https://image.tmdb.org/t/p/w1280/$filename"
        }
    }
    return this
}

fun String?.toOriginalTmdbUrlOrNull(): String? {
    if (this.isNullOrEmpty()) return null
    return this.toOriginalTmdbUrl()
}

fun PlaylistItem.getEffectiveLogoUrl(): String? {
    val url = logoUrl
    if (url.isNullOrEmpty()) {
        val fallback = when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/k334z7zxOJlJvOsOwyRF6HClCvi.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fteLdvfRnltfLjAEnsl5E3vImnW.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/7WOBeoaXUNGzbm79Umio2CcH941.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/aJVroxZZKReiSlASpODQ23qrdGa.jpg"
            else -> null
        }
        return fallback?.toOriginalTmdbUrl()
    }

    val isBroken = url.contains("7v9vqu972mQQCHVgrmvD") ||
            url.contains("49W6xr693Y4Y9569668") ||
            url.contains("czembWB60b7Lg6uC6g6gD") ||
            url.contains("or06gK6Fx9gX6E8UuHIn9fR88Z6") ||
            url.contains("nBNZ9tPhv9vqu972mQQCHVgrmvD")

    if (isBroken) {
        val fixed = when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wUc6IDf5ChjM1UyQye21qFBeJY0.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/5U8WPMqnTx0xGQWwTVLTJGz8wqC.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/k334z7zxOJlJvOsOwyRF6HClCvi.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/tPDgn32iTONXzHo3a4Z0KAMOJfD.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fteLdvfRnltfLjAEnsl5E3vImnW.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/m0ehGErq8GTLK4WZxaq9QLGAR3u.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/h18Yg7onsoRDsOlkGhIiMCUT9W8.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/qEl4BDBTGnhLiadZx0c9nHM8vBF.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/3kN9JhUAzzMLTdrSVd6ivigjh59.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/7WOBeoaXUNGzbm79Umio2CcH941.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/w1wEjm4QeA6H3Jp63np7NmWecj5.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/abjNx4jqvaJn5UvsuLaBVRVndyJ.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/h1eUeDH35Tdvsfu7Ouefa7CPwLa.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fmLQaEPC9uRCbjPs1NI4S90qimH.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9ez0xyH6IIg8Ww4hNpiD9lHRRH7.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/aJVroxZZKReiSlASpODQ23qrdGa.jpg"
            else -> url
        }
        return fixed.toOriginalTmdbUrl()
    }
    return url.toOriginalTmdbUrl()
}

fun PlaylistItem.getEffectiveBackdropUrl(): String? {
    val url = backdropUrl
    if (url.isNullOrEmpty()) {
        return when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg"
            else -> null
        }
    }

    val isBroken = url.contains("9gp6Y08oIe9NaV7uXvAtOTo2yG0") ||
            url.contains("56vDgc1Uv9D9CHv4gN4EXS6fXmB") ||
            url.contains("lzWH0v0N7vIFvSBu7z0ST0as76K") ||
            url.contains("7Ry78r46S7L3I5C4N6O9X8Y7Z6W") ||
            url.contains("rAiWbyqUz9Z967S2g9E5QDQDMD0")

    if (isBroken) {
        val fixed = when {
            title.contains("Obsessão", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rZfmzpixLKLR3Hg2u0WgC7XLFl8.jpg"
            title.contains("Pradaria", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/fBieUo3SdItUrXZE16YxbpjwXIe.jpg"
            title.contains("Furious", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/1AVF2fAevpfi2HP6AEpptG1kg8R.jpg"
            title.contains("Moana", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/c6BPbkO5Npt1OdwttAxCFo06wtH.jpg"
            title.contains("Demônio", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/A5Tz6ogGt4VV8NESG9oWVct5bo1.jpg"
            title.contains("Odisseia", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg"
            title.contains("Perder", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/6tByIjKn3VLEVHhfpiNNWrnSIOM.jpg"
            title.contains("Backrooms", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg"
            title.contains("Rua", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/mJDCrEFKHUqoZe4xyI8CE47pw7P.jpg"
            title.contains("Hope", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/9zgnuwIK6JAc6giVsscq9H82li1.jpg"
            title.contains("Jackass", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/zqd39GO0GdO5TaO8HxnkmWwu2p8.jpg"
            title.contains("Dragão", ignoreCase = true) || title.contains("Dragon", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/577eXC8wFQT0eUrJcgznSiFPRmk.jpg"
            title.contains("NCIS", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/nn3SuLTO4hum8yAxaY4ql8h6kRk.jpg"
            title.contains("Cinzenta", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/qcIKxhqGMIj8uujsSoSMZWr8QqU.jpg"
            title.contains("Prazer", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/rHnANzYUmV3WZw3n0yWOLiR3pen.jpg"
            title.contains("Lei & Ordem", ignoreCase = true) || title.contains("Law & Order", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/tc7canPSAn2X14hYi6Rl3gZm1o4.jpg"
            title.contains("Rancho", ignoreCase = true) || title.contains("Dutton", ignoreCase = true) || title.contains("Yellowstone", ignoreCase = true) -> "https://image.tmdb.org/t/p/original/wh5agdl3b7fqC7mSjsCkHx4fMAs.jpg"
            else -> url
        }
        return fixed.toOriginalTmdbUrl()
    }
    return url.toOriginalTmdbUrl()
}

/**
 * Custom Modifier for Android TV to handle D-pad focus, scaling, borders,
 * and elevation transitions smoothly.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.tvCardFocus(
    scale: Float = 1.03f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    onFocusChange: (Boolean) -> Unit = {}
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) scale else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tvCardFocusScale"
    )

    val animatedBorderColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f)
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val elevation = if (isFocused) 8.dp else 0.dp

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .shadow(elevation = elevation, shape = shape)
        .border(width = borderWidth, color = animatedBorderColor, shape = shape)
        .onFocusChanged {
            isFocused = it.isFocused
            onFocusChange(it.isFocused)
            if (it.isFocused) {
                coroutineScope.launch {
                    try {
                        bringIntoViewRequester.bringIntoView()
                    } catch (e: Exception) {}
                }
            }
        }
        .focusable(interactionSource = interactionSource)
}

// My List (Minha Lista) Tab Content
@Composable
fun MyListTabContent(
    viewModel: IPTVViewModel,
    onItemPlayOrDetail: (PlaylistItem) -> Unit,
    contentFocusRequester: FocusRequester
) {
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    var selectedFilter by remember { mutableStateOf("Todos") }

    val filteredItems = remember(favoriteItems, selectedFilter) {
        when (selectedFilter) {
            "Filmes" -> favoriteItems.filter { it.type == "movie" }
            "Séries" -> favoriteItems.filter { it.type == "series" }
            else -> favoriteItems
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Minha Lista",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (favoriteItems.isNotEmpty()) {
                    Text(
                        text = "(${favoriteItems.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (favoriteItems.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Filmes", "Séries").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) Color(0xFFE50914)
                                    else Color.White.copy(alpha = 0.1f)
                                )
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = if (favoriteItems.isEmpty()) "Sua lista está vazia" else "Nenhum item nesta categoria",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Clique no ícone de coração nos cards de filmes e séries para salvá-los aqui.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val clickedIndex = remember(filteredItems, viewModel.lastClickedItemId) {
                if (viewModel.lastClickedItemId != null) {
                    filteredItems.indexOfFirst { it.id == viewModel.lastClickedItemId }
                } else -1
            }
            val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                state = gridState,
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 1000.dp)
            ) {
                itemsIndexed(
                    items = filteredItems,
                    key = { _, item -> item.id }
                ) { index, item ->
                    val focusRequester = if (index == 0) contentFocusRequester else remember { FocusRequester() }
                    TMDBPosterCard(
                        item = item,
                        onClick = { onItemPlayOrDetail(item) },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                }
            }
        }
    }
}

@Composable
fun MoviesSeriesSkeletonLoader(
    categoryTitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF2A2A2A),
                            Color(0xFF3A3A3A),
                            Color(0xFF2A2A2A)
                        )
                    )
                )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF2A2A2A),
                                    Color(0xFF3A3A3A),
                                    Color(0xFF2A2A2A)
                                )
                            )
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        )
        Spacer(modifier = Modifier.height(8.dp))
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A2A2A))
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

