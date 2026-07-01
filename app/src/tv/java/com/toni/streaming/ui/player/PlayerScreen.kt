package com.toni.streaming.ui.player

import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    var isControllerVisible by remember { mutableStateOf(false) }

    // Next episode states
    var timeRemainingSeconds by remember { mutableStateOf(0) }
    var isNearEnd by remember { mutableStateOf(false) }
    var isPlaybackEnded by remember { mutableStateOf(false) }

    // Configure the OkHttp data source with the shared httpClient and dynamic headers
    val dataSourceFactory = remember(streamInfo) {
        Log.d("ToniPlayer", "Configuring OkHttpDataSource.Factory for stream")
        Log.d("ToniPlayer", "Target playlist URL: ${streamInfo.m3u8Url}")
        
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(repository.httpClient)
            .setUserAgent(streamInfo.userAgent)
            .setDefaultRequestProperties(streamInfo.headers)
    }

    // Create the media source factory that automatically detects the format (HLS or MP4)
    val mediaSourceFactory = remember(dataSourceFactory) {
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
    }

    // Create the media item
    val mediaItem = remember(streamInfo) {
        MediaItem.fromUri(streamInfo.m3u8Url)
    }

    // Create and configure ExoPlayer with error listener
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("ToniPlayer", "ExoPlayer error: ${error.message}", error)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isPlaybackEnded = playbackState == androidx.media3.common.Player.STATE_ENDED
                        Log.d("ToniPlayer", "Playback state changed: $playbackState")
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

    // Periodically save watch history progress and detect video ending (every 1 second)
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(1000)
            val currentPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            if (duration > 0 && currentPos > 0) {
                // Save watch history progress database-side
                repository.saveWatchProgress(
                    episodeId = episodeId,
                    animeId = animeId,
                    position = currentPos,
                    duration = duration,
                    animeTitle = streamInfo.animeTitle,
                    animeImageUrl = streamInfo.animeImageUrl,
                    animeSlug = streamInfo.animeSlug
                )

                // Detect if the video is nearing its end (e.g. less than 30 seconds left)
                val remainingMs = duration - currentPos
                timeRemainingSeconds = (remainingMs / 1000).toInt()
                isNearEnd = remainingMs < 30_000
            }
        }
    }

    // Pause playback when the app goes to the background, so audio/video does not keep
    // running off-screen. Playback stays paused until the user resumes it manually.
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

    // Save final watch progress and release the player when leaving the screen
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
        }
    }

    // Requester to auto-focus the next episode button when video ends
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { keyEvent ->
                // Shortcut: If we are near the end (<30s) and user presses OK/Select on the remote,
                // immediately transition to the next episode! This avoids D-pad focus lock issues.
                if (isNearEnd && nextEpisode != null) {
                    if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                            onPlayNext(nextEpisode)
                        }
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        // Render AndroidView PlayerView only if playback is not ended.
        // If playback ends, removing the AndroidView releases focus from ExoPlayer control views,
        // allowing the D-pad to smoothly select the Next Episode Compose button!
        if (!isPlaybackEnded) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        
                        // Track when the playback controller is shown or hidden
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            isControllerVisible = visibility == android.view.View.VISIBLE
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Clean black screen when playback has fully ended
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        // Overlay Back Button (visible when player controls are shown)
        AnimatedVisibility(
            visible = isControllerVisible || isPlaybackEnded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
        ) {
            androidx.tv.material3.IconButton(
                onClick = onBack,
                colors = androidx.tv.material3.IconButtonDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    focusedContainerColor = AccentPurple
                ),
                modifier = Modifier.size(48.dp)
            ) {
                androidx.tv.material3.Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Indietro",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ===== NEXT EPISODE POPUP OVERLAY =====
        if (nextEpisode != null) {
            // Case 1: Video has ended entirely -> Show giant next episode card in the center with auto-focus
            if (isPlaybackEnded) {
                LaunchedEffect(isPlaybackEnded) {
                    if (isPlaybackEnded) {
                        delay(300)
                        try {
                            focusRequester.requestFocus()
                            Log.d("ToniPlayer", "Requested focus on Next Episode giant button")
                        } catch (e: Exception) {
                            Log.e("ToniPlayer", "Request focus failed", e)
                        }
                    }
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
                        Spacer(modifier = Modifier.height(18.dp))

                        var isBtnFocused by remember { mutableStateOf(false) }

                        // Giant Gradient purple D-pad focusable button
                        Box(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .clickable { onPlayNext(nextEpisode) }
                                .focusable()
                                .onFocusChanged { isBtnFocused = it.isFocused }
                                .shadow(
                                    elevation = if (isBtnFocused) 20.dp else 4.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = AccentPurple.copy(alpha = 0.5f)
                                )
                                .then(
                                    if (isBtnFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp))
                                    else Modifier
                                )
                                .background(
                                    brush = Brush.horizontalGradient(colors = listOf(AccentPurple, AccentBlue)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 36.dp, vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                androidx.tv.material3.Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Prossimo Episodio: Ep. ${nextEpisode.number}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "Oppure premi INDIETRO per uscire",
                            color = TextSecondary.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } 
            // Case 2: Video is nearing end (last 30s) -> Show Netflix-style compact popup in the bottom right corner
            else if (isNearEnd) {
                var isPopupFocused by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 96.dp, end = 48.dp) // Avoid covering system navigation elements
                        .clickable { onPlayNext(nextEpisode) }
                        .focusable()
                        .onFocusChanged { isPopupFocused = it.isFocused }
                        .shadow(
                            elevation = if (isPopupFocused) 16.dp else 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = AccentPurple.copy(alpha = 0.3f)
                        )
                        .then(
                            if (isPopupFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .background(
                            brush = Brush.horizontalGradient(colors = listOf(AccentPurple, AccentBlue)),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "PROSSIMO EPISODIO IN ARRIVO",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Episodio ${nextEpisode.number}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "tra ${timeRemainingSeconds}s",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Premi OK sul telecomando per riprodurre ora",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
