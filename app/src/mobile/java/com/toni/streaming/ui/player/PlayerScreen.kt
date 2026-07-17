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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    animeId: String,
    episodeId: String,
    episodeUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    episodeNumber: Int = 0
) {
    val context = LocalContext.current
    val repository = remember { AnimeRepository.getInstance(context) }
    val viewModel: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlayerViewModel(repository, animeId, episodeId, episodeUrl, episodeNumber) }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

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
                        episodeNumber = uiState.currentEpisodeNumber,
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
    episodeNumber: Int,
    startPositionMs: Long,
    nextEpisode: Episode?,
    onPlayNext: (Episode) -> Unit,
    repository: AnimeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isControllerVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }

    val (exoPlayer, playerState) = rememberManagedExoPlayer(
        streamInfo = streamInfo,
        repository = repository,
        animeId = animeId,
        episodeId = episodeId,
        episodeNumber = episodeNumber,
        startPositionMs = startPositionMs,
        keepScreenOn = true
    )
    val hasNext = nextEpisode != null

    // Poll the player for the scrubber / play-state (UI only).
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            try {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                isPlaying = exoPlayer.isPlaying
            } catch (_: Exception) {}
            delay(400)
        }
    }

    // Auto-hide the controls a few seconds after they appear, while playing.
    LaunchedEffect(isControllerVisible, isPlaying, playerState.isPlaybackEnded) {
        if (isControllerVisible && isPlaying && !playerState.isPlaybackEnded) {
            delay(4000)
            isControllerVisible = false
        }
    }
    // Reveal the controls when the video ends so the next / back buttons are reachable.
    LaunchedEffect(playerState.isPlaybackEnded) {
        if (playerState.isPlaybackEnded) isControllerVisible = true
    }
    // Auto-advance to the next episode a few seconds after the video ends (mobile convenience).
    LaunchedEffect(playerState.isPlaybackEnded) {
        if (playerState.isPlaybackEnded && nextEpisode != null) {
            delay(5000)
            onPlayNext(nextEpisode)
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650)
            seekFeedback = null
        }
    }

    fun togglePlayPause() {
        try {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            isPlaying = exoPlayer.isPlaying
        } catch (_: Exception) {}
    }
    fun seekBy(deltaMs: Long) {
        try {
            val dur = exoPlayer.duration
            val target = (exoPlayer.currentPosition + deltaMs)
                .coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
            exoPlayer.seekTo(target)
            positionMs = target
        } catch (_: Exception) {}
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val view = android.view.LayoutInflater.from(ctx)
                    .inflate(com.toni.streaming.R.layout.custom_player_view, null) as PlayerView
                view.apply {
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Touch layer: single tap toggles the controls, double-tap on a side seeks ±10s.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { isControllerVisible = !isControllerVisible },
                        onDoubleTap = { offset ->
                            if (offset.x > size.width / 2f) {
                                seekBy(10_000); seekFeedback = "+10s"
                            } else {
                                seekBy(-10_000); seekFeedback = "-10s"
                            }
                        }
                    )
                }
        )

        // Brief visual feedback for the double-tap seek.
        seekFeedback?.let { label ->
            Box(
                modifier = Modifier
                    .align(if (label.startsWith("+")) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Shared controls overlay (same layout as TV; driven by touch here).
        AnimatedVisibility(
            visible = isControllerVisible || playerState.isPlaybackEnded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                hasNext = hasNext,
                isNearEnd = playerState.isNearEnd,
                isPlaybackEnded = playerState.isPlaybackEnded,
                timeRemainingSeconds = playerState.timeRemainingSeconds,
                onBack = onBack,
                onPlayPause = { togglePlayPause() },
                onRewind = { seekBy(-10_000) },
                onForward = { seekBy(10_000) },
                onNext = { nextEpisode?.let { onPlayNext(it) } },
                onSeekTo = { ms -> exoPlayer.seekTo(ms); positionMs = ms }
            )
        }
    }
}
