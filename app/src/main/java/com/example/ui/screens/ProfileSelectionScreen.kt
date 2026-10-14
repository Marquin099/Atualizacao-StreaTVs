package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.example.data.model.UserProfile
import com.example.data.model.PlaylistItem
import com.example.ui.components.StreaTVLogo
import com.example.ui.components.LogoAsyncImage
import com.example.ui.components.getEffectiveTitleLogoUrl
import com.example.ui.components.toOriginalTmdbUrl
import com.example.ui.viewmodel.IPTVViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager

val profileFallbackBackgroundItems = listOf(
    PlaylistItem(
        id = 9901,
        title = "O DIA DA REVELAÇÃO",
        url = "",
        groupName = "Tendências",
        type = "movie",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/m3Pom6pbD51bBv3syz8NMHda3fz.jpg",
        titleLogoUrl = "https://image.tmdb.org/t/p/w500/jpLOFTHacWoxCTCLRePVlLHxrbo.png",
        description = "A existência de vida extraterrestre torna-se uma realidade inegável para todos os habitantes da Terra em um único instante...."
    ),
    PlaylistItem(
        id = 9921,
        title = "A Casa do Dragão",
        url = "",
        groupName = "Tendências",
        type = "series",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/577eXC8wFQT0eUrJcgznSiFPRmk.jpg",
        titleLogoUrl = "https://image.tmdb.org/t/p/w500/oKJDm4QCKbp6mR4FnxXrFlPJP8Y.png",
        description = "A história da Casa Targaryen, ambientada 200 anos antes dos eventos de Game of Thrones, lutando pelo Trono de Ferro."
    ),
    PlaylistItem(
        id = 9902,
        title = "Deadpool & Wolverine",
        url = "",
        groupName = "Tendências",
        type = "movie",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/yDHYTfA3R0jFYba16jBB128StOp.jpg",
        titleLogoUrl = "https://image.tmdb.org/t/p/w500/3kN9JhUAzzMLTdrSVd6ivigjh59.png",
        description = "Wolverine está se recuperando quando cruza o caminho do tagarela Deadpool. Eles se unem para derrotar um inimigo comum."
    ),
    PlaylistItem(
        id = 9903,
        title = "Duna: Parte 2",
        url = "",
        groupName = "Tendências",
        type = "movie",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/xOMo8A2W2fR9LSpA28A26PAt23.jpg",
        titleLogoUrl = "https://image.tmdb.org/t/p/w500/zH4h1hP7djDgh3oQDWRDOyhRXFx.png",
        description = "Paul Atreides se une a Chani e aos Fremen enquanto busca vingança contra os conspiradores que destruíram sua família."
    ),
    PlaylistItem(
        id = 9908,
        title = "Backrooms",
        url = "",
        groupName = "Tendências",
        type = "movie",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/mCpwRayjXMFzKHbjbzc5JRKfq1O.jpg",
        titleLogoUrl = "https://image.tmdb.org/t/p/w500/oHP5aXQ4gg15t9EsBhgKENeCKdG.png",
        description = "Um labirinto infinito de salas amarelas vazias habitadas por uma entidade misteriosa."
    )
)

// High-quality cinematic character avatar choices
data class AvatarOption(val id: String, val url: String, val name: String)

val avatarOptions = listOf(
    // OpenPeeps
    AvatarOption("openpeeps_alex", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Alex", "Alex"),
    AvatarOption("openpeeps_jordan", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Jordan", "Jordan"),
    AvatarOption("openpeeps_taylor", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Taylor", "Taylor"),
    AvatarOption("openpeeps_morgan", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Morgan", "Morgan"),
    AvatarOption("openpeeps_sam", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Sam", "Sam"),
    AvatarOption("openpeeps_casey", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Casey", "Casey"),
    AvatarOption("openpeeps_robin", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Robin", "Robin"),
    AvatarOption("openpeeps_jamie", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Jamie", "Jamie"),
    AvatarOption("openpeeps_chris", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Chris", "Chris"),
    AvatarOption("openpeeps_jesse", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Jesse", "Jesse"),

    // FunEmoji
    AvatarOption("funemoji_happy", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Happy", "Happy"),
    AvatarOption("funemoji_cool", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Cool", "Cool"),
    AvatarOption("funemoji_smile", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Smile", "Smile"),
    AvatarOption("funemoji_wink", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Wink", "Wink"),
    AvatarOption("funemoji_love", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Love", "Love"),
    AvatarOption("funemoji_smart", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Smart", "Smart"),
    AvatarOption("funemoji_star", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Star", "Star"),
    AvatarOption("funemoji_angel", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Angel", "Angel"),
    AvatarOption("funemoji_laugh", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Laugh", "Laugh"),
    AvatarOption("funemoji_party", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Party", "Party"),

    // BigSmile
    AvatarOption("bigsmile_avatara", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarA", "AvatarA"),
    AvatarOption("bigsmile_avatarb", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarB", "AvatarB"),
    AvatarOption("bigsmile_avatarc", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarC", "AvatarC"),
    AvatarOption("bigsmile_avatard", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarD", "AvatarD"),
    AvatarOption("bigsmile_avatare", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarE", "AvatarE"),
    AvatarOption("bigsmile_avatarf", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarF", "AvatarF"),
    AvatarOption("bigsmile_avatarg", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarG", "AvatarG"),
    AvatarOption("bigsmile_avatarh", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarH", "AvatarH"),
    AvatarOption("bigsmile_avatari", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarI", "AvatarI"),
    AvatarOption("bigsmile_avatarj", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarJ", "AvatarJ")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectionScreen(
    viewModel: IPTVViewModel
) {
    val profiles by viewModel.profiles.collectAsState()
    val trendingItems by viewModel.trendingItems.collectAsState()
    val popularItems by viewModel.popularItems.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val series by viewModel.series.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val backgroundItems = remember(trendingItems, popularItems, movies, series) {
        val serverItems = (trendingItems + popularItems + movies.take(30) + series.take(30))
            .filter { !it.backdropUrl.isNullOrBlank() }
            .distinctBy { it.title.trim().lowercase() }
        if (serverItems.isNotEmpty()) serverItems else profileFallbackBackgroundItems
    }

    var currentBgIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(backgroundItems) {
        if (backgroundItems.isNotEmpty()) {
            while (true) {
                delay(8000)
                currentBgIndex = (currentBgIndex + 1) % backgroundItems.size
            }
        }
    }

    val currentFeaturedItem = backgroundItems.getOrNull(currentBgIndex) ?: backgroundItems.firstOrNull()

    var isInCreateMode by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }
    
    // Create Profile Form States
    var newProfileName by remember { mutableStateOf("") }
    var selectedAvatarUrl by remember { mutableStateOf("") }
    
    // Focus Requesters for Leanback remote flow
    val nameInputFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }
    val cancelButtonFocusRequester = remember { FocusRequester() }
    
    // Selection state for click animation feedback
    var selectedProfileId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        selectedProfileId = null
    }
    val coroutineScope = rememberCoroutineScope()
    val isLandscape = LocalContext.current.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val svgImageLoader = remember(context) {
        context.imageLoader
    }
    var selectedStyle by remember { mutableStateOf("OpenPeeps") }
    val displayedAvatars = remember(selectedStyle) {
        avatarOptions.filter { 
            when (selectedStyle) {
                "OpenPeeps" -> it.id.startsWith("openpeeps")
                "FunEmoji" -> it.id.startsWith("funemoji")
                "BigSmile" -> it.id.startsWith("bigsmile")
                else -> true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF0C0707),
                        0.5f to Color(0xFF030101),
                        1.0f to Color(0xFF1D0303)
                    )
                )
            )
    ) {
        // Global Top Bar (Logo on left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = 24.dp)
        ) {
            StreaTVLogo(
                size = 46.dp,
                showText = true
            )
        }

        AnimatedContent(
            targetState = isInCreateMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "screen_transition"
        ) { createMode ->
            if (createMode) {
                // Profile Creation Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLandscape) {
                        // Side-by-side design for widescreen/tablets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Column: Form Info
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Novo Perfil",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = (-1).sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Adicione um novo perfil para personalizar sua experiência cinematográfica no StreaTV.",
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(36.dp))
                                
                                val isAvatarSelected = selectedAvatarUrl.isNotEmpty()
                                val isSaveEnabled = isAvatarSelected && newProfileName.isNotBlank()

                                Text(
                                    text = if (isAvatarSelected) "NOME DO PERFIL" else "NOME DO PERFIL (Selecione um avatar para liberar)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAvatarSelected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f),
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    enabled = isAvatarSelected,
                                    placeholder = { Text(if (isAvatarSelected) "Ex: João Silva" else "Selecione um avatar primeiro", color = Color.White.copy(alpha = if (isAvatarSelected) 0.35f else 0.15f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        disabledTextColor = Color.White.copy(alpha = 0.3f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                                        disabledContainerColor = Color.White.copy(alpha = 0.01f),
                                        focusedBorderColor = Color(0xFFFF0D1A),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        disabledBorderColor = Color.White.copy(alpha = 0.05f)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(nameInputFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && 
                                                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                                if (isSaveEnabled) {
                                                    saveButtonFocusRequester.requestFocus()
                                                }
                                                true
                                            } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                                if (isSaveEnabled) {
                                                    saveButtonFocusRequester.requestFocus()
                                                } else {
                                                    cancelButtonFocusRequester.requestFocus()
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            if (isSaveEnabled) {
                                                saveButtonFocusRequester.requestFocus()
                                            }
                                        }
                                    )
                                )
                                Spacer(modifier = Modifier.height(36.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val saveInteractionSource = remember { MutableInteractionSource() }
                                    val isSaveHovered by saveInteractionSource.collectIsHoveredAsState()
                                    var isSaveFocused by remember { mutableStateOf(false) }
                                    val isSaveFocusedOrHovered = (isSaveFocused || isSaveHovered) && isSaveEnabled

                                    val saveScale by animateFloatAsState(
                                        targetValue = if (isSaveFocusedOrHovered) 1.08f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "save_scale"
                                    )

                                    Button(
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            if (isSaveEnabled) {
                                                viewModel.addNewProfile(newProfileName.trim(), selectedAvatarUrl)
                                                isInCreateMode = false
                                                newProfileName = ""
                                                selectedAvatarUrl = ""
                                            }
                                        },
                                        enabled = isSaveEnabled,
                                        interactionSource = saveInteractionSource,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF0D1A),
                                            disabledContainerColor = Color(0xFFFF0D1A).copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .graphicsLayer {
                                                scaleX = saveScale
                                                scaleY = saveScale
                                            }
                                            .hoverable(saveInteractionSource)
                                            .onFocusChanged { isSaveFocused = it.isFocused }
                                            .focusRequester(saveButtonFocusRequester)
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                    nameInputFocusRequester.requestFocus()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .focusable()
                                    ) {
                                        Text("Salvar", color = Color.White.copy(alpha = if (isSaveEnabled) 1.0f else 0.5f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    val cancelInteractionSource = remember { MutableInteractionSource() }
                                    val isCancelHovered by cancelInteractionSource.collectIsHoveredAsState()
                                    var isCancelFocused by remember { mutableStateOf(false) }
                                    val isCancelFocusedOrHovered = isCancelFocused || isCancelHovered

                                    val cancelScale by animateFloatAsState(
                                        targetValue = if (isCancelFocusedOrHovered) 1.08f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "cancel_scale"
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            isInCreateMode = false
                                            newProfileName = ""
                                            selectedAvatarUrl = ""
                                        },
                                        interactionSource = cancelInteractionSource,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isCancelFocusedOrHovered) 0.5f else 0.2f)),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .graphicsLayer {
                                                scaleX = cancelScale
                                                scaleY = cancelScale
                                            }
                                            .hoverable(cancelInteractionSource)
                                            .onFocusChanged { isCancelFocused = it.isFocused }
                                            .focusRequester(cancelButtonFocusRequester)
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                    nameInputFocusRequester.requestFocus()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .focusable()
                                    ) {
                                        Text("Cancelar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    }
                                }
                            }

                            // Right Column: Avatar Grid Card
                            Column(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .background(Color(0xFF0F0F0F).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(16.dp))
                                    .padding(24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ESCOLHA SEU AVATAR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White.copy(alpha = 0.8f),
                                        letterSpacing = 1.5.sp
                                    )
                                }

                                // Category selection chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    listOf("OpenPeeps", "FunEmoji", "BigSmile").forEach { style ->
                                        val isStyleSelected = selectedStyle == style
                                        val chipInteractionSource = remember { MutableInteractionSource() }
                                        val isChipHovered by chipInteractionSource.collectIsHoveredAsState()
                                        var isChipFocused by remember { mutableStateOf(false) }
                                        val isChipFocusedOrHovered = isChipFocused || isChipHovered

                                        val chipScale by animateFloatAsState(
                                            targetValue = if (isChipFocusedOrHovered) 1.12f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_scale_land"
                                        )
                                        val chipTranslationY by animateDpAsState(
                                            targetValue = if (isChipFocusedOrHovered) (-2).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_jump_land"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = chipScale
                                                    scaleY = chipScale
                                                    translationY = chipTranslationY.toPx()
                                                }
                                                .hoverable(chipInteractionSource)
                                                .onFocusChanged { isChipFocused = it.isFocused }
                                                .focusable()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(
                                                    if (isStyleSelected) Color(0xFFFF0D1A) 
                                                    else if (isChipFocusedOrHovered) Color.White.copy(alpha = 0.15f) 
                                                    else Color.White.copy(alpha = 0.08f)
                                                )
                                                .clickable(
                                                    interactionSource = chipInteractionSource,
                                                    indication = null
                                                ) { selectedStyle = style }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = style,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                                    modifier = Modifier.height(235.dp)
                                ) {
                                    items(displayedAvatars.take(8)) { avatar ->
                                        val isSelected = selectedAvatarUrl == avatar.url
                                        val avatarInteractionSource = remember { MutableInteractionSource() }
                                        val isAvatarHovered by avatarInteractionSource.collectIsHoveredAsState()
                                        var isAvatarFocused by remember { mutableStateOf(false) }
                                        val isAvatarFocusedOrHovered = isAvatarFocused || isAvatarHovered

                                        val avatarScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.06f else if (isAvatarFocusedOrHovered) 1.04f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_scale_land"
                                        )
                                        val avatarTranslationY by animateDpAsState(
                                            targetValue = if (isAvatarFocusedOrHovered) (-2).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_jump_land"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .graphicsLayer {
                                                    scaleX = avatarScale
                                                    scaleY = avatarScale
                                                    translationY = avatarTranslationY.toPx()
                                                }
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(
                                                    width = if (isSelected) 3.dp else if (isAvatarFocusedOrHovered) 2.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFFFF0D1A) else if (isAvatarFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.15f),
                                                    shape = CircleShape
                                                )
                                                .hoverable(avatarInteractionSource)
                                                .onFocusChanged { isAvatarFocused = it.isFocused }
                                                .focusable()
                                                .clickable(
                                                    interactionSource = avatarInteractionSource,
                                                    indication = null
                                                ) {
                                                    selectedAvatarUrl = avatar.url
                                                    coroutineScope.launch {
                                                        try {
                                                            nameInputFocusRequester.requestFocus()
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                        ) {
                                            AsyncImage(
                                                model = avatar.url,
                                                contentDescription = avatar.name,
                                                imageLoader = svgImageLoader,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Portrait Scrollable layout for Phones
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Novo Perfil",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Adicione um novo perfil para personalizar sua experiência no StreaTV.",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            val isAvatarSelected = selectedAvatarUrl.isNotEmpty()
                            val isSaveEnabled = isAvatarSelected && newProfileName.isNotBlank()

                            Text(
                                text = if (isAvatarSelected) "NOME DO PERFIL" else "NOME DO PERFIL (Selecione um avatar para liberar)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAvatarSelected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f),
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = newProfileName,
                                onValueChange = { newProfileName = it },
                                enabled = isAvatarSelected,
                                placeholder = { Text(if (isAvatarSelected) "Ex: João Silva" else "Selecione um avatar primeiro", color = Color.White.copy(alpha = if (isAvatarSelected) 0.35f else 0.15f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color.White.copy(alpha = 0.3f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                                    disabledContainerColor = Color.White.copy(alpha = 0.01f),
                                    focusedBorderColor = Color(0xFFFF0D1A),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    disabledBorderColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(nameInputFocusRequester)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && 
                                            (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                            if (isSaveEnabled) {
                                                saveButtonFocusRequester.requestFocus()
                                            }
                                            true
                                        } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                            if (isSaveEnabled) {
                                                saveButtonFocusRequester.requestFocus()
                                            } else {
                                                cancelButtonFocusRequester.requestFocus()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (isSaveEnabled) {
                                            saveButtonFocusRequester.requestFocus()
                                        }
                                    }
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Avatar Grid Panel
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F0F0F).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "ESCOLHA SEU AVATAR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.8f),
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                // Category selection chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    listOf("OpenPeeps", "FunEmoji", "BigSmile").forEach { style ->
                                        val isStyleSelected = selectedStyle == style
                                        val chipInteractionSource = remember { MutableInteractionSource() }
                                        val isChipHovered by chipInteractionSource.collectIsHoveredAsState()
                                        var isChipFocused by remember { mutableStateOf(false) }
                                        val isChipFocusedOrHovered = isChipFocused || isChipHovered

                                        val chipScale by animateFloatAsState(
                                            targetValue = if (isChipFocusedOrHovered) 1.12f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_scale_port"
                                        )
                                        val chipTranslationY by animateDpAsState(
                                            targetValue = if (isChipFocusedOrHovered) (-2).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_jump_port"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = chipScale
                                                    scaleY = chipScale
                                                    translationY = chipTranslationY.toPx()
                                                }
                                                .hoverable(chipInteractionSource)
                                                .onFocusChanged { isChipFocused = it.isFocused }
                                                .focusable()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(
                                                    if (isStyleSelected) Color(0xFFFF0D1A) 
                                                    else if (isChipFocusedOrHovered) Color.White.copy(alpha = 0.15f) 
                                                    else Color.White.copy(alpha = 0.08f)
                                                )
                                                .clickable(
                                                    interactionSource = chipInteractionSource,
                                                    indication = null
                                                ) { selectedStyle = style }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = style,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                                    modifier = Modifier.height(195.dp)
                                ) {
                                    items(displayedAvatars) { avatar ->
                                        val isSelected = selectedAvatarUrl == avatar.url
                                        val avatarInteractionSource = remember { MutableInteractionSource() }
                                        val isAvatarHovered by avatarInteractionSource.collectIsHoveredAsState()
                                        var isAvatarFocused by remember { mutableStateOf(false) }
                                        val isAvatarFocusedOrHovered = isAvatarFocused || isAvatarHovered

                                        val avatarScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.06f else if (isAvatarFocusedOrHovered) 1.04f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_scale_port"
                                        )
                                        val avatarTranslationY by animateDpAsState(
                                            targetValue = if (isAvatarFocusedOrHovered) (-2).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_jump_port"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .graphicsLayer {
                                                    scaleX = avatarScale
                                                    scaleY = avatarScale
                                                    translationY = avatarTranslationY.toPx()
                                                }
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(
                                                    width = if (isSelected) 3.dp else if (isAvatarFocusedOrHovered) 2.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFFFF0D1A) else if (isAvatarFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.15f),
                                                    shape = CircleShape
                                                )
                                                .hoverable(avatarInteractionSource)
                                                .onFocusChanged { isAvatarFocused = it.isFocused }
                                                .focusable()
                                                .clickable(
                                                    interactionSource = avatarInteractionSource,
                                                    indication = null
                                                ) {
                                                    selectedAvatarUrl = avatar.url
                                                    coroutineScope.launch {
                                                        try {
                                                            nameInputFocusRequester.requestFocus()
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                        ) {
                                            AsyncImage(
                                                model = avatar.url,
                                                contentDescription = avatar.name,
                                                imageLoader = svgImageLoader,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            // Buttons Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val cancelInteractionSource = remember { MutableInteractionSource() }
                                val isCancelHovered by cancelInteractionSource.collectIsHoveredAsState()
                                var isCancelFocused by remember { mutableStateOf(false) }
                                val isCancelFocusedOrHovered = isCancelFocused || isCancelHovered

                                val cancelScale by animateFloatAsState(
                                    targetValue = if (isCancelFocusedOrHovered) 1.08f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "cancel_scale_port"
                                )

                                OutlinedButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        isInCreateMode = false
                                        newProfileName = ""
                                        selectedAvatarUrl = ""
                                    },
                                    interactionSource = cancelInteractionSource,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = if (isCancelFocusedOrHovered) 0.5f else 0.2f)),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .graphicsLayer {
                                            scaleX = cancelScale
                                            scaleY = cancelScale
                                        }
                                        .hoverable(cancelInteractionSource)
                                        .onFocusChanged { isCancelFocused = it.isFocused }
                                        .focusRequester(cancelButtonFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                nameInputFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .focusable()
                                ) {
                                    Text("Cancelar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }

                                val saveInteractionSource = remember { MutableInteractionSource() }
                                val isSaveHovered by saveInteractionSource.collectIsHoveredAsState()
                                var isSaveFocused by remember { mutableStateOf(false) }
                                val isSaveFocusedOrHovered = (isSaveFocused || isSaveHovered) && isSaveEnabled

                                val saveScale by animateFloatAsState(
                                    targetValue = if (isSaveFocusedOrHovered) 1.08f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "save_scale_port"
                                )

                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        if (isSaveEnabled) {
                                            viewModel.addNewProfile(newProfileName.trim(), selectedAvatarUrl)
                                            isInCreateMode = false
                                            newProfileName = ""
                                            selectedAvatarUrl = ""
                                        }
                                    },
                                    enabled = isSaveEnabled,
                                    interactionSource = saveInteractionSource,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF0D1A),
                                        disabledContainerColor = Color(0xFFFF0D1A).copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .graphicsLayer {
                                            scaleX = saveScale
                                            scaleY = saveScale
                                        }
                                        .hoverable(saveInteractionSource)
                                        .onFocusChanged { isSaveFocused = it.isFocused }
                                        .focusRequester(saveButtonFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                nameInputFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .focusable()
                                ) {
                                    Text("Salvar", color = Color.White.copy(alpha = if (isSaveEnabled) 1.0f else 0.5f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            } else {
                // Profile Selection View matching the provided image layout
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Fullscreen Cinematic Background Image cycling through featured movies/series
                    val currentBackdrop = currentFeaturedItem?.backdropUrl?.toOriginalTmdbUrl()
                        ?: currentFeaturedItem?.backdropUrl
                        ?: "https://image.tmdb.org/t/p/original/m3Pom6pbD51bBv3syz8NMHda3fz.jpg"

                    Crossfade(
                        targetState = currentBackdrop,
                        animationSpec = tween(1200),
                        label = "profile_bg_crossfade"
                    ) { bgUrl ->
                        AsyncImage(
                            model = bgUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Cinematic Dark Blue Gradient Vignette Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color(0xFF030712),
                                        0.42f to Color(0xFF070E20).copy(alpha = 0.92f),
                                        0.70f to Color(0xFF0F172A).copy(alpha = 0.45f),
                                        1.0f to Color.Transparent
                                    )
                                )
                            )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color(0xFF020617).copy(alpha = 0.82f),
                                        0.25f to Color.Transparent,
                                        0.75f to Color.Transparent,
                                        1.0f to Color(0xFF020617).copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )

                    // Main Layout Content
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 48.dp, vertical = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side Column: Heading, Profile Cards, Add Profile Button
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Heading Text
                            Text(
                                text = "Quem está assistindo?",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Vertical Profiles Pill Cards
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.widthIn(max = 380.dp)
                            ) {
                                profiles.forEach { profile ->
                                    val cardInteractionSource = remember { MutableInteractionSource() }
                                    val isCardHovered by cardInteractionSource.collectIsHoveredAsState()
                                    var isCardFocused by remember { mutableStateOf(false) }

                                    val trashInteractionSource = remember { MutableInteractionSource() }
                                    val isTrashHovered by trashInteractionSource.collectIsHoveredAsState()
                                    var isTrashFocused by remember { mutableStateOf(false) }

                                    val isAnyFocusedOrHovered = isCardFocused || isTrashFocused || isCardHovered || isTrashHovered
                                    val isSelected = selectedProfileId == profile.id

                                    val cardScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.05f else if (isAnyFocusedOrHovered) 1.03f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "card_scale"
                                    )

                                    val colorHex = try {
                                        Color(android.graphics.Color.parseColor(profile.avatarColorHex))
                                    } catch (e: Exception) {
                                        Color(0xFF2563EB)
                                    }

                                    // Outer container row styled as the pill card
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .graphicsLayer {
                                                scaleX = cardScale
                                                scaleY = cardScale
                                            }
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isAnyFocusedOrHovered) Color(0xFF1E40AF).copy(alpha = 0.55f)
                                                else Color.Black.copy(alpha = 0.45f)
                                            )
                                            .border(
                                                width = if (isAnyFocusedOrHovered) 2.dp else 1.dp,
                                                color = if (isAnyFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(start = 12.dp, end = 8.dp)
                                    ) {
                                        // Left Profile Main Clickable & Focusable Area (Avatar + Name)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(12.dp))
                                                .hoverable(cardInteractionSource)
                                                .onFocusChanged { isCardFocused = it.isFocused }
                                                .focusable(interactionSource = cardInteractionSource)
                                                .clickable(
                                                    interactionSource = cardInteractionSource,
                                                    indication = null
                                                ) {
                                                    if (isDeleteMode) {
                                                        profileToDelete = profile
                                                    } else {
                                                        selectedProfileId = profile.id
                                                        coroutineScope.launch {
                                                            delay(300)
                                                            viewModel.selectProfile(profile)
                                                        }
                                                    }
                                                }
                                        ) {
                                            // Avatar container
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(colorHex),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (profile.avatarIconName.startsWith("http")) {
                                                    SubcomposeAsyncImage(
                                                        model = profile.avatarIconName,
                                                        contentDescription = profile.name,
                                                        imageLoader = svgImageLoader,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                        error = {
                                                            Text(
                                                                text = profile.name.take(1).uppercase(),
                                                                color = Color.White,
                                                                fontSize = 20.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = profile.name,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            // Profile Name
                                            Text(
                                                text = profile.name,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCardFocused) Color.White else Color.White.copy(alpha = 0.9f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 16.dp)
                                            )
                                        }

                                        // Right Trash / Delete Button (Independently focusable via D-Pad Right Arrow)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isTrashFocused) Color(0xFFDC2626)
                                                    else if (isTrashHovered) Color.White.copy(alpha = 0.2f)
                                                    else Color.Transparent
                                                )
                                                .border(
                                                    width = if (isTrashFocused) 2.dp else 0.dp,
                                                    color = if (isTrashFocused) Color.White else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .hoverable(trashInteractionSource)
                                                .onFocusChanged { isTrashFocused = it.isFocused }
                                                .focusable(interactionSource = trashInteractionSource)
                                                .clickable(
                                                    interactionSource = trashInteractionSource,
                                                    indication = null
                                                ) {
                                                    profileToDelete = profile
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Excluir perfil",
                                                tint = if (isTrashFocused) Color.White else Color.White.copy(alpha = 0.75f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // "Adicionar Perfil" Button Row
                                if (profiles.size < 5) {
                                    val addInteractionSource = remember { MutableInteractionSource() }
                                    val isAddHovered by addInteractionSource.collectIsHoveredAsState()
                                    var isAddFocused by remember { mutableStateOf(false) }
                                    val isAddFocusedOrHovered = isAddFocused || isAddHovered

                                    val addScale by animateFloatAsState(
                                        targetValue = if (isAddFocusedOrHovered) 1.05f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "add_scale"
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .graphicsLayer {
                                                scaleX = addScale
                                                scaleY = addScale
                                            }
                                            .clip(RoundedCornerShape(16.dp))
                                            .hoverable(addInteractionSource)
                                            .onFocusChanged { isAddFocused = it.isFocused }
                                            .focusable()
                                            .clickable(
                                                interactionSource = addInteractionSource,
                                                indication = null
                                            ) { isInCreateMode = true }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isAddFocusedOrHovered) Color.White.copy(alpha = 0.35f)
                                                    else Color.White.copy(alpha = 0.18f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Adicionar Perfil",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Text(
                                            text = "Adicionar Perfil",
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Right Side Column: Featured Movie/Series Title Logo & Description
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .padding(bottom = 28.dp),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.End
                        ) {
                            Crossfade(
                                targetState = currentFeaturedItem,
                                animationSpec = tween(800),
                                label = "profile_meta_crossfade"
                            ) { featured ->
                                if (featured != null) {
                                    val titleLogoUrl = featured.getEffectiveTitleLogoUrl()
                                    var isLogoFailed by remember(titleLogoUrl) { mutableStateOf(false) }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Title Logo or Bold Title Text
                                        if (!titleLogoUrl.isNullOrEmpty() && !isLogoFailed) {
                                            Box(
                                                modifier = Modifier
                                                    .height(95.dp)
                                                    .widthIn(max = 400.dp),
                                                contentAlignment = Alignment.BottomEnd
                                            ) {
                                                LogoAsyncImage(
                                                    logoUrl = titleLogoUrl,
                                                    contentDescription = featured.title,
                                                    contentScale = ContentScale.Fit,
                                                    alignment = Alignment.BottomEnd,
                                                    modifier = Modifier.fillMaxHeight(),
                                                    onState = { state ->
                                                        if (state is coil.compose.AsyncImagePainter.State.Error) {
                                                            isLogoFailed = true
                                                        }
                                                    }
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = featured.title.uppercase(),
                                                fontSize = 38.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                textAlign = TextAlign.Right,
                                                letterSpacing = 2.sp,
                                                modifier = Modifier.widthIn(max = 420.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        val desc = featured.description?.takeIf { it.isNotBlank() }
                                            ?: "Aproveite os melhores conteúdos, filmes, séries e canais ao vivo."

                                        Text(
                                            text = desc,
                                            fontSize = 14.sp,
                                            color = Color.White.copy(alpha = 0.82f),
                                            textAlign = TextAlign.Right,
                                            lineHeight = 20.sp,
                                            maxLines = 3,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 420.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cinematic Material 3 Deletion Confirmation Dialog
        if (profileToDelete != null) {
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = {
                    Text(
                        text = "Excluir Perfil?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Text(
                        text = "Tem certeza de que deseja excluir permanentemente o perfil \"${profileToDelete?.name}\"? Esta ação não pode ser desfeita.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                },
                containerColor = Color(0xFF141414),
                shape = RoundedCornerShape(16.dp),
                confirmButton = {
                    Button(
                        onClick = {
                            profileToDelete?.let {
                                viewModel.removeProfile(it)
                            }
                            profileToDelete = null
                            isDeleteMode = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0D1A)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { profileToDelete = null },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}
