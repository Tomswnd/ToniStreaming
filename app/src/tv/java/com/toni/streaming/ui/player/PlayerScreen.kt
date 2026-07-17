package com.toni.streaming.ui.player

import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.model.StreamInfo
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.ui.components.ErrorDisplay
import com.toni.streaming.ui.components.LoadingIndicator
import com.toni.streaming.ui.theme.AccentBlue
import com.toni.streaming.ui.theme.AccentPurple
import com.toni.streaming.ui.theme.DarkBackground
import com.toni.streaming.ui.theme.TextPrimary
import com.toni.streaming.ui.theme.TextSecondary
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
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

    // Register BackHandler to cleanly pop back stack on system back button press
    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .focusable()
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
                // Key forces recomposition/recreation of VideoPlayer when the episode stream changes
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
    val context = LocalContext.current
    var isControllerVisible by remember { mutableStateOf(true) }

    val (exoPlayer, playerState) = rememberManagedExoPlayer(
        streamInfo = streamInfo,
        repository = repository,
        animeId = animeId,
        episodeId = episodeId,
        episodeNumber = episodeNumber,
        startPositionMs = startPositionMs,
        keepScreenOn = false,
        enableTunneling = true
    )

    // ===== Custom TV controls state (no native controller / MediaSession) =====
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }

    // Poll the player for the seek bar / play state (UI only). Guarded: never let a stray
    // read on a released/transitioning player crash the screen.
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

    // Auto-hide the controls a few seconds after the last interaction (only while playing).
    LaunchedEffect(interactionTick, isPlaying, playerState.isPlaybackEnded) {
        if (isControllerVisible && isPlaying && !playerState.isPlaybackEnded) {
            delay(4000)
            isControllerVisible = false
        }
    }

    fun showControls() {
        isControllerVisible = true
        interactionTick++
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


    // Requester to auto-focus the next episode button when video ends
    val rootFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val rewindFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val forwardFocus = remember { FocusRequester() }
    val hasNext = nextEpisode != null

    // The surface owns focus while controls are hidden, so ANY key reveals the UI.
    LaunchedEffect(Unit) { try { rootFocus.requestFocus() } catch (_: Exception) {} }

    // Move focus onto the controls when they appear; hand it back to the surface when they hide.
    LaunchedEffect(isControllerVisible, playerState.isPlaybackEnded) {
        try {
            if (isControllerVisible) {
                delay(60)
                if (playerState.isPlaybackEnded && hasNext) nextFocus.requestFocus() else playFocus.requestFocus()
            } else {
                rootFocus.requestFocus()
            }
        } catch (_: Exception) {}
    }

    // When the video ends, reveal the controls so the next episode / back buttons are reachable.
    LaunchedEffect(playerState.isPlaybackEnded) { if (playerState.isPlaybackEnded) isControllerVisible = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                if (code == android.view.KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (code) {
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { showControls(); togglePlayPause(); return@onPreviewKeyEvent true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> { showControls(); try { exoPlayer.play() } catch (_: Exception) {}; return@onPreviewKeyEvent true }
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { showControls(); try { exoPlayer.pause() } catch (_: Exception) {}; return@onPreviewKeyEvent true }
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> { showControls(); seekBy(-10_000L); return@onPreviewKeyEvent true }
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { showControls(); seekBy(10_000L); return@onPreviewKeyEvent true }
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> { nextEpisode?.let { onPlayNext(it) }; return@onPreviewKeyEvent true }
                    }
                    if (!isControllerVisible) {
                        showControls()
                        return@onPreviewKeyEvent true
                    }
                    interactionTick++
                }
                false
            }
    ) {
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

        AnimatedVisibility(
            visible = isControllerVisible,
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
                onPlayPause = { togglePlayPause(); showControls() },
                onRewind = { seekBy(-10_000L); showControls() },
                onForward = { seekBy(10_000L); showControls() },
                onNext = { nextEpisode?.let { onPlayNext(it) } },
                focus = PlayerControlFocus(backFocus, nextFocus, rewindFocus, playFocus, forwardFocus)
            )
        }
    }
}
