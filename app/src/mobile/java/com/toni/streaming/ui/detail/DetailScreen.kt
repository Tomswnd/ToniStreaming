package com.toni.streaming.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.model.RelatedAnime
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.ui.components.ErrorDisplay
import com.toni.streaming.ui.components.LoadingIndicator
import com.toni.streaming.ui.detail.DetailViewModel
import com.toni.streaming.ui.detail.DetailUiState
import com.toni.streaming.ui.theme.AccentPurple
import com.toni.streaming.ui.theme.DarkBackground
import com.toni.streaming.ui.theme.DarkSurfaceVariant
import com.toni.streaming.ui.theme.SuccessGreen
import com.toni.streaming.ui.theme.TextPrimary
import com.toni.streaming.ui.theme.TextSecondary

@Composable
fun DetailScreen(
    animeUrl: String,
    animeId: String,
    onEpisodeClick: (Episode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRelatedClick: (RelatedAnime) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AnimeRepository.getInstance(context) }
    val viewModel: DetailViewModel = viewModel(
        factory = viewModelFactory { initializer { DetailViewModel(repository, animeUrl, animeId) } }
    )
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when {
            uiState.isLoading -> {
                LoadingIndicator(message = "Caricamento dettagli opera…")
            }
            uiState.error != null -> {
                ErrorDisplay(
                    message = uiState.error ?: "Errore sconosciuto",
                    onRetry = { viewModel.retry() }
                )
            }
            uiState.animeDetails != null -> {
                val details = uiState.animeDetails!!
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // 1. ===== PREMIUM HEADER IMAGE (Backdrop) =====
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            // Prefer the wide banner for this backdrop; fall back to the
                            // portrait cover only if no banner is available.
                            AsyncImage(
                                model = details.coverUrl?.ifBlank { null } ?: details.imageUrl,
                                contentDescription = details.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Dark overlay gradient to fade poster into background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.5f),
                                                DarkBackground
                                            )
                                        )
                                    )
                            )
                            
                            // Floating back button
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(top = 40.dp, start = 16.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .size(40.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Indietro",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // 2. ===== INFO BLOCK =====
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = details.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (details.score > 0f) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "★ ${String.format(Locale.US, "%.1f", details.score)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFFFC107),
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleFavorite() }) {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (uiState.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                                        tint = if (uiState.isFavorite) AccentPurple else TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Badge bar (Episodes count)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (uiState.canLoadMore) {
                                        "${uiState.episodes.size}/${uiState.totalEpisodes} Episodi"
                                    } else {
                                        "${uiState.episodes.size} Episodi"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Synopsis/Plot description
                            Text(
                                text = details.plot.ifBlank { "Nessuna trama disponibile per quest'opera." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // 2b. ===== RELATED SEASONS / MOVIES (chronological) =====
                    if (details.related.isNotEmpty()) {
                        item(key = "related_section") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Stagioni e film correlati",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    items(items = details.related, key = { it.id }) { related ->
                                        RelatedCard(related = related, onClick = { onRelatedClick(related) })
                                    }
                                }
                            }
                        }
                    }

                    // 2c. ===== EPISODES HEADER =====
                    item(key = "episodes_header") {
                        Text(
                            text = "Episodi",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }

                    // 3. ===== EPISODES VERTICAL TOUCH LIST =====
                    items(items = uiState.episodes, key = { it.episode.id }) { epProgress ->
                        val progress = epProgress.progress
                        EpisodeRow(
                            episode = epProgress.episode,
                            watchedPositionMs = progress?.watchedPositionMs ?: 0L,
                            totalDurationMs = progress?.totalDurationMs ?: 0L,
                            onClick = { onEpisodeClick(epProgress.episode) }
                        )
                    }

                    // 4. ===== LOAD MORE BUTTON =====
                    if (uiState.canLoadMore) {
                        item(key = "load_more") {
                            Button(
                                onClick = { viewModel.loadMoreEpisodes() },
                                enabled = !uiState.isLoadingMore,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPurple,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = if (uiState.isLoadingMore) {
                                        "Caricamento…"
                                    } else {
                                        "Carica altri  (${uiState.nextRangeStart}–${uiState.nextRangeEnd} di ${uiState.totalEpisodes})"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact poster card for a related season/movie in the horizontal row.
 */
@Composable
private fun RelatedCard(
    related: RelatedAnime,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = related.imageUrl,
            contentDescription = related.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = related.title,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (related.info.isNotBlank()) {
            Text(
                text = related.info,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Mobile-friendly episode row item with touch target and clean progress bar.
 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    watchedPositionMs: Long,
    totalDurationMs: Long,
    onClick: () -> Unit
) {
    val isStarted = watchedPositionMs > 0 && totalDurationMs > 0
    val isCompleted = isStarted && (totalDurationMs - watchedPositionMs < 15_000 || watchedPositionMs * 100 / totalDurationMs > 80)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) DarkSurfaceVariant.copy(alpha = 0.4f) else DarkSurfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Icon
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCompleted) SuccessGreen else AccentPurple,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Episodio ${episode.number}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    if (isStarted && !isCompleted) {
                        Text(
                            text = "Arrivato a ${formatWatchTime(watchedPositionMs)} / ${formatWatchTime(totalDurationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Bottom progress bar for started episodes
            if (isStarted) {
                val ratio = watchedPositionMs.toFloat() / totalDurationMs.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(if (isCompleted) SuccessGreen else AccentPurple)
                    )
                }
            }
        }
    }
}

/**
 * Formats a millisecond position as m:ss, or h:mm:ss when the content is an hour or longer,
 * so episodes over 60 minutes no longer show a broken "75:12".
 */
private fun formatWatchTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
