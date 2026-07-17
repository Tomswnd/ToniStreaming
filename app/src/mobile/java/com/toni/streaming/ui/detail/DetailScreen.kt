package com.toni.streaming.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.model.RelatedAnime
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.download.DownloadMetadata
import com.toni.streaming.download.DownloadStatus
import com.toni.streaming.download.EpisodeDownloadUi
import com.toni.streaming.download.EpisodeDownloadsViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    animeUrl: String,
    animeId: String,
    repository: AnimeRepository,
    onEpisodeClick: (Episode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRelatedClick: (RelatedAnime) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { DetailViewModel(repository, animeUrl, animeId) }
    val uiState by viewModel.uiState.collectAsState()

    val downloadsViewModel = remember { EpisodeDownloadsViewModel(context, repository) }
    val downloadStates by downloadsViewModel.downloadStates.collectAsState()

    // Episode whose completed download the user asked to delete (shows the confirm sheet)
    var episodePendingDelete by remember { mutableStateOf<Episode?>(null) }

    // On Android 13+ the download progress notification needs POST_NOTIFICATIONS;
    // the download itself starts regardless of the user's choice.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun onDownloadAction(episode: Episode) {
        val status = downloadStates[episode.id]?.status ?: DownloadStatus.NOT_DOWNLOADED
        when (status) {
            DownloadStatus.NOT_DOWNLOADED, DownloadStatus.FAILED -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                downloadsViewModel.startDownload(
                    episode,
                    DownloadMetadata(
                        animeId = animeId,
                        episodeNumber = episode.number,
                        animeTitle = uiState.animeDetails?.title ?: "",
                        animeImageUrl = uiState.animeDetails?.imageUrl ?: ""
                    )
                )
            }
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING ->
                downloadsViewModel.removeDownload(episode.id)
            DownloadStatus.COMPLETED -> episodePendingDelete = episode
            DownloadStatus.PREPARING -> Unit
        }
    }

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
                                        text = "★ ${String.format("%.1f", details.score)}",
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
                            onClick = { onEpisodeClick(epProgress.episode) },
                            downloadUi = downloadStates[epProgress.episode.id],
                            onDownloadClick = { onDownloadAction(epProgress.episode) }
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

        // ===== DELETE DOWNLOAD CONFIRMATION SHEET =====
        episodePendingDelete?.let { episode ->
            val bytes = downloadStates[episode.id]?.bytesDownloaded ?: 0L
            ModalBottomSheet(
                onDismissRequest = { episodePendingDelete = null },
                containerColor = DarkSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                ) {
                    Text(
                        text = "Eliminare il download?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append("Episodio ${episode.number}")
                            if (bytes > 0) append(" · ${formatBytes(bytes)} verranno liberati")
                            append(". Potrai riscaricarlo quando vuoi.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { episodePendingDelete = null },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Annulla", color = TextPrimary)
                        }
                        Button(
                            onClick = {
                                downloadsViewModel.removeDownload(episode.id)
                                episodePendingDelete = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE24B4A)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Elimina", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single trailing action for episode downloads, cycling through four states:
 * download arrow → progress ring (tap = cancel) → green done (tap = delete) → retry.
 */
@Composable
private fun DownloadStateIcon(
    downloadUi: EpisodeDownloadUi?,
    onClick: () -> Unit
) {
    val status = downloadUi?.status ?: DownloadStatus.NOT_DOWNLOADED
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        when (status) {
            DownloadStatus.NOT_DOWNLOADED -> Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Scarica episodio",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            DownloadStatus.PREPARING, DownloadStatus.QUEUED -> CircularProgressIndicator(
                color = AccentPurple,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
            DownloadStatus.DOWNLOADING -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (downloadUi?.percent ?: 0f) / 100f },
                    color = AccentPurple,
                    trackColor = AccentPurple.copy(alpha = 0.25f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp)
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Annulla download",
                    tint = TextSecondary,
                    modifier = Modifier.size(11.dp)
                )
            }
            DownloadStatus.COMPLETED -> Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = "Elimina download",
                tint = SuccessGreen,
                modifier = Modifier.size(22.dp)
            )
            DownloadStatus.FAILED -> Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Riprova download",
                tint = Color(0xFFF09595),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return if (bytes >= 1_000_000_000) {
        String.format("%.1f GB", bytes / 1_000_000_000.0)
    } else {
        String.format("%.0f MB", bytes / 1_000_000.0)
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
    onClick: () -> Unit,
    downloadUi: EpisodeDownloadUi? = null,
    onDownloadClick: () -> Unit = {}
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
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "Episodio ${episode.number}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    if (isStarted && !isCompleted) {
                        val progressMinutes = watchedPositionMs / 60_000
                        val durationMinutes = totalDurationMs / 60_000
                        Text(
                            text = "Arrivato a ${String.format("%02d:%02d", progressMinutes, (watchedPositionMs % 60000) / 1000)} / ${String.format("%02d:%02d", durationMinutes, (totalDurationMs % 60000) / 1000)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    when (downloadUi?.status) {
                        DownloadStatus.PREPARING -> Text(
                            text = "Preparazione download…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        DownloadStatus.QUEUED -> Text(
                            text = "Download in coda",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        DownloadStatus.DOWNLOADING -> Text(
                            text = "Download in corso · ${downloadUi.percent.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        DownloadStatus.FAILED -> Text(
                            text = "Download non riuscito · tocca per riprovare",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF09595)
                        )
                        else -> Unit
                    }
                }

                DownloadStateIcon(
                    downloadUi = downloadUi,
                    onClick = onDownloadClick
                )
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
