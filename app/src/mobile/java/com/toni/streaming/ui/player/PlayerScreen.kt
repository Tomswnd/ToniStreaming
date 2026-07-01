package com.toni.streaming.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.model.StreamInfo
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.ui.components.ErrorDisplay
import com.toni.streaming.ui.components.LoadingIndicator
import com.toni.streaming.ui.player.PlayerViewModel
import com.toni.streaming.ui.player.PlayerUiState
import com.toni.streaming.ui.theme.AccentBlue
import com.toni.streaming.ui.theme.AccentPurple
import com.toni.streaming.ui.theme.DarkBackground
import com.toni.streaming.ui.theme.TextPrimary
import com.toni.streaming.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    animeId: String,
    episodeId: String,
    episodeUrl: String,
    repository: AnimeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    episodeNumber: Int = 0
) {
    val viewModel = remember { PlayerViewModel(repository, animeId, episodeId, episodeUrl, episodeNumber) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Force Landscape orientation on mobile while watching video
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // Hide status bars and navigation bar (Immersive fullscreen)
        activity?.window?.let { window ->
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Restore orientation and system bars when leaving player screen
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when {
            uiState.isLoading -> {
                LoadingIndicator(message = "Estrazione stream in corso…")
            }
            uiState.error != null -> {
                ErrorDisplay(
                    message = uiState.error ?: "Errore",
                    onRetry = { viewModel.retry() }
                )
            }
            uiState.streamInfo != null -> {
                key(uiState.currentEpisodeId) {
                    VideoPlayer(
                        streamInfo = uiState.streamInfo!!,
                        animeId = animeId,
                        episodeId = uiState.currentEpisodeId,
                        startPositionMs = uiState.startPositionMs,
                        nextEpisode = uiState.nextEpisode,
                        onPlayNext = { viewModel.playEpisode(it) },
                        repository = repository,
                        onBack = onBack,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    streamInfo: StreamInfo,
    animeId: String,
    episodeId: String,
    startPositionMs: Long,
    nextEpisode: Episode?,
    onPlayNext: (Episode) -> Unit,
    repository: AnimeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isControllerVisible by remember { mutableStateOf(true) }

    var timeRemainingSeconds by remember { mutableStateOf(0) }
    var isNearEnd by remember { mutableStateOf(false) }
    var isPlaybackEnded by remember { mutableStateOf(false) }

    // Configure the OkHttp data source
    val dataSourceFactory = remember(streamInfo) {
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(repository.httpClient)
            .setUserAgent(streamInfo.userAgent)
            .setDefaultRequestProperties(streamInfo.headers)
    }

    val mediaSourceFactory = remember(dataSourceFactory) {
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
    }

    val mediaItem = remember(streamInfo) {
        MediaItem.fromUri(streamInfo.m3u8Url)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isPlaybackEnded = playbackState == androidx.media3.common.Player.STATE_ENDED
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        // Keep the screen awake while the video is actually playing;
                        // allow it to time out normally when paused/stopped.
                        val window = (context as? Activity)?.window ?: return
                        if (isPlaying) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                })
                setMediaItem(mediaItem)
                if (startPositionMs > 0) {
                    seekTo(startPositionMs)
                }
                prepare()
                playWhenReady = true
            }
    }

    // Save watch history progress and detect video ending
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(1000)
            val currentPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            if (duration > 0 && currentPos > 0) {
                repository.saveWatchProgress(
                    episodeId = episodeId,
                    animeId = animeId,
                    position = currentPos,
                    duration = duration,
                    animeTitle = streamInfo.animeTitle,
                    animeImageUrl = streamInfo.animeImageUrl,
                    animeSlug = streamInfo.animeSlug
                )

                val remainingMs = duration - currentPos
                timeRemainingSeconds = (remainingMs / 1000).toInt()
                isNearEnd = remainingMs < 30_000
            }
        }
    }

    // Pause playback when the app goes to the background so it doesn't keep playing off-screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            val currentPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            if (duration > 0 && currentPos > 0) {
                @Suppress("OPT_IN_USAGE")
                GlobalScope.launch(Dispatchers.IO) {
                    repository.saveWatchProgress(
                        episodeId = episodeId,
                        animeId = animeId,
                        position = currentPos,
                        duration = duration,
                        animeTitle = streamInfo.animeTitle,
                        animeImageUrl = streamInfo.animeImageUrl,
                        animeSlug = streamInfo.animeSlug
                    )
                }
            }
            exoPlayer.release()
            // Make sure the screen can time out again once we leave the player.
            (context as? Activity)?.window
                ?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        isControllerVisible = visibility == android.view.View.VISIBLE
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Back Button (visible when controllers are showing)
        AnimatedVisibility(
            visible = isControllerVisible || isPlaybackEnded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Indietro",
                    tint = Color.White
                )
            }
        }

        // ===== NEXT EPISODE TOUCH OVERLAYS =====
        if (nextEpisode != null) {
            // Case 1: Video ended entirely -> full screen overlay with auto countdown
            if (isPlaybackEnded) {
                // Auto play next episode after 5 seconds if user does not touch
                LaunchedEffect(Unit) {
                    delay(5000)
                    onPlayNext(nextEpisode)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Episodio Completato",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Large touch clickable button
                        Button(
                            onClick = { onPlayNext(nextEpisode) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .background(
                                    brush = Brush.horizontalGradient(colors = listOf(AccentPurple, AccentBlue)),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = "Prossimo Episodio: Ep. ${nextEpisode.number}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Riproduzione automatica tra pochi istanti…",
                            color = TextSecondary.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } 
            // Case 2: Video nearing end -> Netflix-like popup in bottom-right (touchable)
            else if (isNearEnd) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 64.dp, end = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlayNext(nextEpisode) }
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.horizontalGradient(colors = listOf(AccentPurple, AccentBlue)),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "PROSSIMO EPISODIO IN ARRIVO",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Episodio ${nextEpisode.number}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "tocca per avviare (tra ${timeRemainingSeconds}s)",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
