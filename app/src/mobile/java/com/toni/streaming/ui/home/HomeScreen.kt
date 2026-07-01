package com.toni.streaming.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.toni.streaming.data.local.WatchHistoryEntity
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
import com.toni.streaming.ui.theme.TextPrimary
import com.toni.streaming.ui.theme.TextSecondary
import com.toni.streaming.ui.home.HomeViewModel
import com.toni.streaming.ui.home.HomeUiState

@Composable
fun HomeScreen(
    repository: AnimeRepository,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { HomeViewModel(repository) }
    val uiState by viewModel.uiState.collectAsState()
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
        ) {
            // ===== TOPBAR: LOGO + SEARCH FIELD (TOUCH ORIENTED) =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ToniStreaming",
                    style = MaterialTheme.typography.titleLarge.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(AccentGradientStart, AccentGradientEnd)
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )
                )

                // Compact Search bar on the right
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = "Cerca…",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cerca",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.clearSearch()
                                keyboardController?.hide()
                                focusManager.clearFocus()
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
                            keyboardController?.hide()
                            focusManager.clearFocus()
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(160.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                    // Search results (Touch-optimized mobile grids/rows)
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
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Prova con un altro titolo.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Column {
                                Text(
                                    text = "Risultati della ricerca",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
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
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                    // 1. ===== FEATURED BANNER CAROUSEL (In corso - status=In Corso) =====
                                    if (uiState.featuredList.isNotEmpty()) {
                                        item {
                                            Column {
                                                Text(
                                                    text = "In evidenza",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = TextPrimary,
                                                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                                                )
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    items(
                                                        items = uiState.featuredList.take(5),
                                                        key = { "featured_${it.id}" }
                                                    ) { anime ->
                                                        MobileHeroBanner(
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
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = TextPrimary,
                                                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                                                )
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = TextPrimary,
                                                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                                                )
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
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
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = TextPrimary,
                                                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                                                )
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
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
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = TextPrimary,
                                                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                                                )
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
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

/**
 * Premium compact hero banner designed specifically for Mobile portrait screen dimensions.
 */
@Composable
private fun MobileHeroBanner(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(340.dp)
            .height(170.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Prefer the wide banner (imageurl_cover). If the anime has no banner, show the
            // portrait cover as a small poster on the right (not stretched full-bleed).
            val banner = anime.coverUrl?.takeIf { it.isNotBlank() }
            if (banner != null) {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = anime.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.7f) // small portrait poster
                        .align(Alignment.CenterEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Dark fade gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DarkSurfaceVariant,
                                DarkSurfaceVariant.copy(alpha = 0.95f),
                                DarkSurfaceVariant.copy(alpha = 0.7f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 500f
                        )
                    )
            )

            // Text contents
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "IN EVIDENZA",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccentPurple
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (anime.synopsis.isNotBlank()) anime.synopsis else "Riproduci ora quest'opera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
