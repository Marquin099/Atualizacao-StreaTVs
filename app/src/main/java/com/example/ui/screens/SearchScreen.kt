package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.PlaylistItem
import com.example.ui.components.getCleanPtBrTitle
import com.example.ui.components.getEffectiveLogoUrl
import com.example.ui.viewmodel.IPTVViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@Composable
fun SearchScreen(
    viewModel: IPTVViewModel,
    onClose: () -> Unit,
    onPlay: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val recommendations by viewModel.searchRecommendations.collectAsState()
    val searchResults by viewModel.searchResultItems.collectAsState()

    val firstKeyFocusRequester = remember { FocusRequester() }
    var isRightGridFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.lastClickedItemId == null) {
            delay(120)
            try {
                firstKeyFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    val displayedItems = if (searchQuery.isBlank()) recommendations else searchResults

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Escape || keyEvent.key == Key.Back)
                ) {
                    if (isRightGridFocused) {
                        try {
                            firstKeyFocusRequester.requestFocus()
                            true
                        } catch (e: Exception) {
                            onClose()
                            true
                        }
                    } else {
                        onClose()
                        true
                    }
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 16.dp)
        ) {
            // ==========================================
            // LEFT COLUMN (~26% width): Keyboard & Categories
            // ==========================================
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
            ) {
                // Search Field (Pill Shape above keyboard)
                SearchInputBar(
                    searchQuery = searchQuery,
                    onClearQuery = { viewModel.updateSearchQuery("") },
                    focusRequester = firstKeyFocusRequester
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Keyboard Container
                SearchKeyboard(
                    onKeyClick = { char ->
                        viewModel.updateSearchQuery(searchQuery + char)
                    },
                    onBackspace = {
                        if (searchQuery.isNotEmpty()) {
                            viewModel.updateSearchQuery(searchQuery.dropLast(1))
                        }
                    },
                    onSpace = {
                        viewModel.updateSearchQuery("$searchQuery ")
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Categories List
                CategoryList(
                    onCategoryClick = { categoryName ->
                        viewModel.updateSearchQuery(categoryName)
                    }
                )
            }

            Spacer(modifier = Modifier.width(36.dp))

            // ==========================================
            // RIGHT COLUMN (~74% width): Title & 4x2 Grid
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            isRightGridFocused = true
                        }
                    }
            ) {
                // Header Title
                Text(
                    text = if (searchQuery.isBlank()) "Recomendações de Pesquisa" else "Resultados para \"$searchQuery\"",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Recommendation / Results Grid
                RecommendationGrid(
                    displayedItems = displayedItems,
                    viewModel = viewModel,
                    onItemClick = { item ->
                        onPlay(item)
                    }
                )
            }
        }
    }
}

// ==========================================
// COMPONENT 0: SEARCH INPUT BAR
// ==========================================
@Composable
fun SearchInputBar(
    searchQuery: String,
    onClearQuery: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (isFocused) Color(0xFF2E2E2E) else Color(0xFF1B1B1B))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF383838),
                shape = androidx.compose.foundation.shape.CircleShape
            )
            .padding(horizontal = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Lupa",
            tint = if (isFocused) Color.White else Color.LightGray,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = if (searchQuery.isBlank()) "Pesquisar..." else searchQuery,
            color = if (searchQuery.isBlank()) Color.Gray else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        if (searchQuery.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Limpar",
                tint = Color.LightGray,
                modifier = Modifier
                    .size(18.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { onClearQuery() }
            )
        }
    }
}

// ==========================================
// COMPONENT 1: SEARCH KEYBOARD
// ==========================================
@Composable
fun SearchKeyboard(
    onKeyClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit
) {
    val keyboardRows = remember {
        listOf(
            listOf("a", "b", "c", "d", "e", "f"),
            listOf("g", "h", "i", "j", "k", "l"),
            listOf("m", "n", "o", "p", "q", "r"),
            listOf("s", "t", "u", "v", "w", "x"),
            listOf("y", "z", "1", "2", "3", "4"),
            listOf("5", "6", "7", "8", "9", "0")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Row: Space key (left half) and Backspace key (right half)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Space key
                var isSpaceFocused by remember { mutableStateOf(false) }
                val spaceScale by animateFloatAsState(
                    targetValue = if (isSpaceFocused) 1.05f else 1.0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "spaceScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .graphicsLayer {
                            scaleX = spaceScale
                            scaleY = spaceScale
                        }
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isSpaceFocused) Color.White else Color(0xFF383838))
                        .border(
                            width = if (isSpaceFocused) 2.dp else 0.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .onFocusChanged { isSpaceFocused = it.isFocused }
                        .focusable()
                        .clickable { onSpace() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "␣",
                        color = if (isSpaceFocused) Color.Black else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Backspace key
                var isBackspaceFocused by remember { mutableStateOf(false) }
                val backspaceScale by animateFloatAsState(
                    targetValue = if (isBackspaceFocused) 1.05f else 1.0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "backspaceScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .graphicsLayer {
                            scaleX = backspaceScale
                            scaleY = backspaceScale
                        }
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isBackspaceFocused) Color.White else Color(0xFF383838))
                        .border(
                            width = if (isBackspaceFocused) 2.dp else 0.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .onFocusChanged { isBackspaceFocused = it.isFocused }
                        .focusable()
                        .clickable { onBackspace() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Apagar",
                        tint = if (isBackspaceFocused) Color.Black else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Letter and Number Rows (6 cols)
            keyboardRows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { char ->
                        var isKeyFocused by remember { mutableStateOf(false) }
                        val keyScale by animateFloatAsState(
                            targetValue = if (isKeyFocused) 1.08f else 1.0f,
                            animationSpec = tween(durationMillis = 180),
                            label = "keyScale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .graphicsLayer {
                                    scaleX = keyScale
                                    scaleY = keyScale
                                }
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isKeyFocused) Color.White else Color(0xFF383838))
                                .border(
                                    width = if (isKeyFocused) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .onFocusChanged { isKeyFocused = it.isFocused }
                                .focusable()
                                .clickable { onKeyClick(char) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                color = if (isKeyFocused) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isKeyFocused) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENT 2: CATEGORY LIST
// ==========================================
@Composable
fun CategoryList(
    onCategoryClick: (String) -> Unit
) {
    val categories = remember {
        listOf(
            "Comédias",
            "Ação",
            "Ficção Científica",
            "Terror",
            "Documentários",
            "Animes",
            "Romance"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(categories, key = { category -> category }) { category ->
            var isFocused by remember { mutableStateOf(false) }
            val itemScale by animateFloatAsState(
                targetValue = if (isFocused) 1.05f else 1.0f,
                animationSpec = tween(durationMillis = 180),
                label = "categoryScale"
            )

            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .graphicsLayer {
                        scaleX = itemScale
                        scaleY = itemScale
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFocused) Color.White else Color.Transparent)
                    .border(
                        width = if (isFocused) 1.5.dp else 0.dp,
                        color = if (isFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onCategoryClick(category) }
                    .padding(vertical = 6.dp, horizontal = 14.dp)
            ) {
                Text(
                    text = category,
                    color = if (isFocused) Color.Black else Color(0xFFB0B0B0),
                    fontSize = 14.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ==========================================
// COMPONENT 3: RECOMMENDATION GRID
// ==========================================
@Composable
fun RecommendationGrid(
    displayedItems: List<PlaylistItem>,
    viewModel: IPTVViewModel,
    onItemClick: (PlaylistItem) -> Unit
) {
    if (displayedItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nenhum resultado encontrado.",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }
    } else {
        val clickedIndex = remember(displayedItems, viewModel.lastClickedItemId) {
            displayedItems.indexOfFirst { it.id == viewModel.lastClickedItemId }
        }
        val initialIndex = if (clickedIndex >= 0) clickedIndex else 0
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
            initialFirstVisibleItemIndex = initialIndex
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(displayedItems, key = { index, item -> "${item.id}_$index" }) { _, item ->
                PosterCard(
                    item = item,
                    viewModel = viewModel,
                    onClick = { targetItem -> onItemClick(targetItem) }
                )
            }
        }
    }
}

// ==========================================
// COMPONENT 4: POSTER CARD
// ==========================================
@Composable
fun PosterCard(
    item: PlaylistItem,
    viewModel: IPTVViewModel,
    onClick: (PlaylistItem) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val isLastClicked = viewModel.lastClickedItemId == item.id
    val isSelected = isFocused || isLastClicked

    LaunchedEffect(item.id, viewModel.lastClickedItemId) {
        if (viewModel.lastClickedItemId == item.id) {
            delay(120)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "posterScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable {
                viewModel.lastClickedItemId = item.id
                onClick(item)
            }
            .shadow(
                elevation = if (isSelected) 16.dp else 4.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .border(
                width = if (isSelected) 3.5.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val effectiveLogo = item.logoUrl?.takeIf { it.isNotEmpty() }
                ?: item.logo?.takeIf { it.isNotEmpty() }
                ?: item.titleLogoUrl?.takeIf { it.isNotEmpty() }
                ?: item.getEffectiveLogoUrl()

            if (!effectiveLogo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(effectiveLogo)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build(),
                    contentDescription = item.getCleanPtBrTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFF2E2E2E), Color(0xFF141414))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = item.getCleanPtBrTitle(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Red Netflix Emblem 'N'
            Image(
                painter = painterResource(id = com.example.R.drawable.streatv_logo),
                contentDescription = "Logo do Servidor",
                modifier = Modifier
                    .padding(top = 8.dp, start = 8.dp)
                    .size(32.dp),
                contentScale = ContentScale.Fit
            )

            if (item.type != "live") {
                val favoriteIds by viewModel.favoriteItemIds.collectAsState()
                val isFav = favoriteIds.contains(item.id)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { viewModel.toggleFavorite(item.id) },
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
}
