package com.toni.streaming.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.ui.components.ErrorDisplay
import com.toni.streaming.ui.components.LoadingIndicator
import com.toni.streaming.ui.theme.AccentGradientEnd
import com.toni.streaming.ui.theme.AccentGradientStart
import com.toni.streaming.ui.theme.AccentPurple
import com.toni.streaming.ui.theme.DarkBackground
import com.toni.streaming.ui.theme.DarkSurfaceVariant
import com.toni.streaming.ui.theme.TextPrimary
import com.toni.streaming.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    animeId: String,
    animeUrl: String,
    repository: AnimeRepository,
    onEpisodeClick: (Episode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember { DetailViewModel(repository, animeUrl, animeId) }
    val uiState by viewModel.uiState.collectAsState()

    // Reload progress each time we return to this screen to show updated checkmarks/progress
    LaunchedEffect(Unit) {
        viewModel.loadDetails()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when {
            uiState.isLoading -> {
                LoadingIndicator(message = "Caricamento episodi…")
            }
            uiState.error != null -> {
                ErrorDisplay(
                    message = uiState.error ?: "Errore",
                    onRetry = { viewModel.retry() }
                )
            }
            uiState.animeDetails != null -> {
                val details = uiState.animeDetails!!
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // ================= LEFT PANEL: ANIME INFO =================
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                    ) {
                        // Back button and header
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Indietro",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Dettagli",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    brush = Brush.linearGradient(
                                        colors = listOf(AccentGradientStart, AccentGradientEnd)
                                    )
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Anime Poster - height restricted to 260.dp so it does not collapse the plot description!
                        AsyncImage(
                            model = details.imageUrl,
                            contentDescription = details.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(260.dp)
                                .aspectRatio(0.7f)
                                .align(Alignment.Start)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = AccentPurple.copy(alpha = 0.2f)
                                )
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Anime Title
                        Text(
                            text = details.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Anime Plot (Scrollable if too long) - Now gets plenty of room to render!
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = if (details.plot.isNotBlank()) details.plot else "Nessuna descrizione disponibile.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 20.sp,
                                    fontSize = 14.sp
                                ),
                                color = TextSecondary
                            )
                        }
                    }

                    // ================= RIGHT PANEL: EPISODES GRID =================
                    Column(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                    ) {
                        // Title for episodes list
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            Text(
                                text = "Episodi",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "(${uiState.episodes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                        }

                        // Episodes grid
                        AnimatedVisibility(
                            visible = uiState.episodes.isNotEmpty(),
                            enter = fadeIn() + slideInVertically { it / 3 }
                        ) {
                            TvLazyVerticalGrid(
                                columns = TvGridCells.Adaptive(180.dp),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = uiState.episodes,
                                    key = { it.episode.id }
                                ) { episodeWithProgress ->
                                    EpisodeCard(
                                        episodeWithProgress = episodeWithProgress,
                                        onClick = { onEpisodeClick(episodeWithProgress.episode) }
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episodeWithProgress: EpisodeWithProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val episode = episodeWithProgress.episode
    val progress = episodeWithProgress.progress

    // Determine state
    val isCompleted = progress != null && (progress.totalDurationMs - progress.watchedPositionMs < 15_000 || progress.watchedPositionMs * 100 / progress.totalDurationMs > 92)
    val isStarted = progress != null && !isCompleted

    // Styling properties based on watch progress state
    val iconColor = when {
        isFocused -> Color.White
        isCompleted -> Color(0xFF4CAF50) // Green for completed
        isStarted -> AccentPurple
        else -> AccentPurple
    }

    val textColor = when {
        isFocused -> Color.White
        isCompleted -> TextSecondary.copy(alpha = 0.8f) // Duller for completed episodes
        else -> TextPrimary
    }

    val buttonColors = ButtonDefaults.colors(
        containerColor = when {
            isCompleted -> DarkSurfaceVariant.copy(alpha = 0.3f)
            isStarted -> DarkSurfaceVariant.copy(alpha = 0.7f)
            else -> DarkSurfaceVariant.copy(alpha = 0.5f)
        },
        focusedContainerColor = AccentPurple
    )

    Button(
        onClick = onClick,
        colors = buttonColors,
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp) // Increased height to prevent bottom text clipping
            .onFocusChanged { isFocused = it.isFocused },
        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp), // Reduced vertical padding
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = if (isCompleted) "Completato" else "Riproduci",
                        modifier = Modifier.size(24.dp),
                        tint = iconColor
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Episodio ${episode.number}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = textColor
                        )

                        // Info text displaying watch time progress (if started)
                        if (isStarted && progress != null) {
                            val currentPosStr = formatTime(progress.watchedPositionMs)
                            val durationStr = formatTime(progress.totalDurationMs)
                            Text(
                                text = "Arrivato a $currentPosStr / $durationStr",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (isFocused) Color.White.copy(alpha = 0.8f) else AccentPurple
                            )
                        } else if (isCompleted) {
                            Text(
                                text = "Visto",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color(0xFF4CAF50)
                            )
                        } else if (episode.title.isNotEmpty()) {
                            Text(
                                text = episode.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                color = if (isFocused) Color.White.copy(alpha = 0.7f) else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Visual Progress bar at the bottom of the card for active watches
            if (isStarted && progress != null) {
                val progressRatio = progress.watchedPositionMs.toFloat() / progress.totalDurationMs.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio)
                        .height(3.dp)
                        .background(
                            if (isFocused) Color.White else AccentPurple,
                            shape = RoundedCornerShape(bottomStart = 12.dp)
                        )
                        .align(Alignment.BottomStart)
                )
            }
        }
    }
}

/**
 * Formats milliseconds to "mm:ss" or "hh:mm:ss" string.
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
