package com.toni.streaming.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.toni.streaming.data.model.Anime
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.ui.components.AnimeCard
import com.toni.streaming.ui.components.ErrorDisplay
import com.toni.streaming.ui.components.LoadingIndicator
import com.toni.streaming.ui.theme.AccentGradientEnd
import com.toni.streaming.ui.theme.AccentGradientStart
import com.toni.streaming.ui.theme.AccentPurple
import com.toni.streaming.ui.theme.DarkBackground
import com.toni.streaming.ui.theme.DarkSurfaceVariant
import com.toni.streaming.ui.theme.FocusBorder
import com.toni.streaming.ui.theme.TextPrimary
import com.toni.streaming.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    repository: AnimeRepository,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { HomeViewModel(repository) }
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // The search field stays read-only (so the IME does NOT auto-open on focus); pressing OK
    // makes it editable and opens the keyboard. The actual show() runs in a LaunchedEffect after
    // the field becomes editable, otherwise the IME-show request races the recomposition on TV.
    var keyboardActive by remember { mutableStateOf(false) }

    LaunchedEffect(keyboardActive) {
        if (keyboardActive) {
            kotlinx.coroutines.delay(60)
            keyboardController?.show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp)
        ) {
            // ===== TOPBAR: LOGO + COMPACT SEARCH ROW =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo on the left
                Text(
                    text = "ToniStreaming",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(AccentGradientStart, AccentGradientEnd)
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                )

                // Compact Search bar on the right
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    readOnly = !keyboardActive, // don't auto-open the IME on focus; open on OK
                    placeholder = {
                        androidx.compose.material3.Text(
                            text = "Cerca anime…",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cerca",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.clearSearch()
                                keyboardActive = false
                                keyboardController?.hide()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Cancella",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardActive = false
                            keyboardController?.hide()
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentPurple,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = AccentPurple,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(320.dp)
                        .onFocusChanged { focusState ->
                            // Leaving the field resets it to read-only and hides the keyboard.
                            if (!focusState.isFocused) {
                                keyboardActive = false
                                keyboardController?.hide()
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            // Intercept OK/Select BEFORE the TextField consumes it as a click,
                            // so pressing OK on the focused field opens the keyboard.
                            val code = keyEvent.nativeKeyEvent.keyCode
                            if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                code == android.view.KeyEvent.KEYCODE_ENTER
                            ) {
                                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN && !keyboardActive) {
                                    keyboardActive = true
                                }
                                true
                            } else {
                                false
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ===== CONTENT AREA =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    // Loading state
                    uiState.isLoading && uiState.featuredList.isEmpty() -> {
                        LoadingIndicator(message = "Caricamento catalogo…")
                    }
                    // Searching state
                    uiState.isSearching -> {
                        LoadingIndicator(message = "Ricerca in corso…")
                    }
                    // Error state
                    uiState.error != null && uiState.featuredList.isEmpty() -> {
                        ErrorDisplay(
                            message = uiState.error ?: "Errore sconosciuto",
                            onRetry = { viewModel.retry() }
                        )
                    }
                    // Search results (Horizontal list with fixed height to prevent layout shifts)
                    uiState.searchQuery.length >= 2 -> {
                        val results = uiState.searchResults
                        if (results.isEmpty() && !uiState.isSearching) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Nessun risultato",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nessun risultato per \"${uiState.searchQuery}\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Column {
                                Text(
                                    text = "Risultati per \"${uiState.searchQuery}\"",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
                                )
                                TvLazyRow(
                                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(330.dp) // Height increased to 330.dp to give outer scaled box 100% stable bounds!
                                ) {
                                    items(items = results, key = { it.id }) { anime ->
                                        AnimeCard(
                                            anime = anime,
                                            onClick = { onAnimeClick(anime) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Normal state: Featured Banner Carousel + Continue Watching + Catalog
                    else -> {
                        if (uiState.featuredList.isEmpty() && uiState.popularList.isEmpty() && uiState.mostViewedList.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Catalogo vuoto",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Il catalogo è vuoto.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    TvLazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 80.dp),
                                        verticalArrangement = Arrangement.spacedBy(28.dp)
                                    ) {
                                        // 1. ===== FEATURED BANNER CAROUSEL (In corso - status=In Corso) =====
                                        if (uiState.featuredList.isNotEmpty()) {
                                            item {
                                                Column {
                                                    Text(
                                                        text = "In evidenza",
                                                        style = MaterialTheme.typography.headlineSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 20.sp
                                                        ),
                                                        color = TextPrimary,
                                                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                                                    )
                                                    TvLazyRow(
                                                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                                                        modifier = Modifier.fillMaxWidth().height(290.dp) // Height increased to 290.dp to completely lock carousel bounds vertically!
                                                    ) {
                                                        items(
                                                            items = uiState.featuredList,
                                                            key = { "featured_${it.id}" }
                                                        ) { anime ->
                                                            HeroFeaturedBanner(
                                                                anime = anime,
                                                                onClick = { onAnimeClick(anime) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 2. ===== CONTINUE WATCHING ROW =====
                                        if (uiState.continueWatchingList.isNotEmpty()) {
                                            item {
                                                Column {
                                                    Text(
                                                        text = "Continua a guardare",
                                                        style = MaterialTheme.typography.headlineSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 20.sp
                                                        ),
                                                        color = TextPrimary,
                                                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                                                    )
                                                    TvLazyRow(
                                                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.fillMaxWidth().height(330.dp) // Height increased to 330.dp to give outer scaled box 100% stable bounds!
                                                    ) {
                                                        items(
                                                            items = uiState.continueWatchingList,
                                                            key = { it.episodeId }
                                                        ) { entity ->
                                                            val anime = Anime(
                                                                id = entity.animeId,
                                                                title = entity.animeTitle,
                                                                imageUrl = entity.animeImageUrl,
                                                                episodeUrl = "https://www.animeunity.so/anime/${entity.animeId}-${entity.animeSlug}"
                                                            )
                                                            val progressRatio = if (entity.totalDurationMs > 0) {
                                                                entity.watchedPositionMs.toFloat() / entity.totalDurationMs.toFloat()
                                                            } else null

                                                            AnimeCard(
                                                                anime = anime,
                                                                progressRatio = progressRatio,
                                                                onClick = { onAnimeClick(anime) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 2b. ===== FAVORITES ROW =====
                                        if (uiState.favoritesList.isNotEmpty()) {
                                            item {
                                                Column {
                                                    Text(
                                                        text = "Preferiti",
                                                        style = MaterialTheme.typography.headlineSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 20.sp
                                                        ),
                                                        color = TextPrimary,
                                                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                                                    )
                                                    TvLazyRow(
                                                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.fillMaxWidth().height(330.dp)
                                                    ) {
                                                        items(
                                                            items = uiState.favoritesList,
                                                            key = { "fav_${it.animeId}" }
                                                        ) { fav ->
                                                            val anime = Anime(
                                                                id = fav.animeId,
                                                                title = fav.title,
                                                                imageUrl = fav.imageUrl,
                                                                episodeUrl = fav.animeUrl
                                                            )
                                                            AnimeCard(
                                                                anime = anime,
                                                                onClick = { onAnimeClick(anime) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 3. ===== POPULAR CATALOG ROW (popularList - popular=true) =====
                                        if (uiState.popularList.isNotEmpty()) {
                                            item {
                                                Column {
                                                    Text(
                                                        text = "Più popolari",
                                                        style = MaterialTheme.typography.headlineSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 20.sp
                                                        ),
                                                        color = TextPrimary,
                                                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                                                    )
                                                    TvLazyRow(
                                                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.fillMaxWidth().height(330.dp) // Height increased to 330.dp to give outer scaled box 100% stable bounds!
                                                    ) {
                                                        items(
                                                            items = uiState.popularList,
                                                            key = { "pop_${it.id}" }
                                                        ) { anime ->
                                                            AnimeCard(
                                                                anime = anime,
                                                                onClick = { onAnimeClick(anime) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 4. ===== MOST VIEWED CATALOG ROW (mostViewedList - order=most_viewed) =====
                                        if (uiState.mostViewedList.isNotEmpty()) {
                                            item {
                                                Column {
                                                    Text(
                                                        text = "Più visti",
                                                        style = MaterialTheme.typography.headlineSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 20.sp
                                                        ),
                                                        color = TextPrimary,
                                                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                                                    )
                                                    TvLazyRow(
                                                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        modifier = Modifier.fillMaxWidth().height(330.dp) // Height increased to 330.dp to give outer scaled box 100% stable bounds!
                                                    ) {
                                                        items(
                                                            items = uiState.mostViewedList,
                                                            key = { "view_${it.id}" }
                                                        ) { anime ->
                                                            AnimeCard(
                                                                anime = anime,
                                                                onClick = { onAnimeClick(anime) }
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
        }
    }
}

/**
 * Premium Hero Banner displaying a featured anime in the home screen carousel.
 * Image scales internally on focus while text and container boundaries stay completely still.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroFeaturedBanner(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Zoom scale applied ONLY inside the banner (to the image) to avoid layout shifts.
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 250),
        label = "heroImageScale"
    )

    Box(
        modifier = modifier
            .width(800.dp) // Fixed width for scrollable horizontal banner items
            .height(250.dp)
            .shadow(
                elevation = if (isFocused) 20.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = AccentPurple.copy(alpha = 0.2f)
            )
            .then(
                if (isFocused) Modifier.border(3.dp, AccentPurple, RoundedCornerShape(20.dp))
                else Modifier
            )
            .padding(if (isFocused) 3.dp else 0.dp) // Make room for border
    ) {
        Card(
            onClick = onClick,
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(
                border = androidx.tv.material3.Border.None,
                focusedBorder = androidx.tv.material3.Border.None,
                pressedBorder = androidx.tv.material3.Border.None
            ),
            colors = CardDefaults.colors(
                containerColor = DarkSurfaceVariant.copy(alpha = 0.4f),
                focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.7f)
            ),
            shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { isFocused = it.isFocused }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            ) {
                // Prefer the wide banner (imageurl_cover), full-bleed. If absent, show the
                // portrait cover on the right (faded to black on the left) as before.
                val banner = anime.coverUrl?.takeIf { it.isNotBlank() }
                if (banner != null) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale)
                    )
                } else {
                    AsyncImage(
                        model = anime.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .scale(scale)
                    )
                }

                // Dynamic dark gradient to overlay text cleanly on the left
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0D0D14),           // Full black background color
                                    Color(0xFF0D0D14).copy(alpha = 0.95f),
                                    Color(0xFF0D0D14).copy(alpha = 0.7f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 1400f
                            )
                        )
                )

                // Content Panel (Text stays perfectly still inside the Card layout bounds)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Tag "IN EVIDENZA"
                    Text(
                        text = "IN EVIDENZA",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = AccentPurple
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title
                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Real Anime Synopsis/Plot (decodificata)
                    Text(
                        text = if (anime.synopsis.isNotBlank()) anime.synopsis else "Nessuna descrizione disponibile per quest'opera.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Visual action button (non-focusable Box to prevent focus conflicts and layout shifts!)
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .background(
                                color = if (isFocused) Color.White else AccentPurple,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isFocused) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Guarda Ora",
                                color = if (isFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
