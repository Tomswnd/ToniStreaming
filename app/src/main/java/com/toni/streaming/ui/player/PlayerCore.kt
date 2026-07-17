package com.toni.streaming.ui.player

import android.app.Activity
import android.util.Log
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.toni.streaming.data.model.StreamInfo
import com.toni.streaming.data.repository.AnimeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Playback-derived state that both the mobile and TV player UIs observe.
 * Updated by [rememberManagedExoPlayer]; read-only for callers.
 */
@Stable
class ManagedPlayerState {
    var isPlaybackEnded by mutableStateOf(false)
        internal set
    var timeRemainingSeconds by mutableStateOf(0)
        internal set
    var isNearEnd by mutableStateOf(false)
        internal set
}

/**
 * Builds and manages an [ExoPlayer] for a resolved [streamInfo], sharing everything that used to be
 * duplicated between the mobile and TV `VideoPlayer` composables:
 *  - the OkHttp-backed data source + media source/item wiring,
 *  - a 1s loop that persists watch progress and tracks the "near end" window,
 *  - pausing when the app goes to the background,
 *  - and saving final progress + releasing the player on dispose.
 *
 * Flavor-specific UI (native controller vs. custom D-pad controls, next-episode overlays) stays in
 * each screen. [keepScreenOn] is the only behavioral difference: the mobile player keeps the screen
 * awake while playing; the TV player leaves it to the platform.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberManagedExoPlayer(
    streamInfo: StreamInfo,
    repository: AnimeRepository,
    animeId: String,
    episodeId: String,
    episodeNumber: Int,
    startPositionMs: Long,
    keepScreenOn: Boolean = false,
    enableTunneling: Boolean = false
): Pair<ExoPlayer, ManagedPlayerState> {
    val context = LocalContext.current
    val state = remember { ManagedPlayerState() }

    val dataSourceFactory = remember(streamInfo) {
        OkHttpDataSource.Factory(repository.httpClient)
            .setUserAgent(streamInfo.userAgent)
            .setDefaultRequestProperties(streamInfo.headers)
    }
    val mediaSourceFactory = remember(dataSourceFactory) {
        DefaultMediaSourceFactory(dataSourceFactory)
    }
    // Progressive fallback for devices whose decoder can't handle the source resolution (e.g. a
    // 1920x1440 anime stream on a MediaTek TV box): step the quality-labelled MP4 down
    // (720p → 480p → 360p) before finally forcing software decoding as a last resort. Devices that
    // decode the source fine never trigger this, so they keep full quality. All the decoder-specific
    // logic lives in PlaybackHardware.kt; here we just hold the current fallback state.
    val streamHasQualityTag = remember(streamInfo) { hasQualityTag(streamInfo.m3u8Url) }
    var fallback by remember(streamInfo) { mutableStateOf(DecoderFallback()) }

    val videoUrl = remember(streamInfo, fallback) { resolveVideoUrl(streamInfo.m3u8Url, fallback) }

    val mediaItem = remember(videoUrl, streamInfo.isHls) {
        val isHls = streamInfo.isHls ||
            videoUrl.contains(".m3u8", ignoreCase = true) ||
            videoUrl.contains("/playlist/", ignoreCase = true)
        val mimeType = if (isHls) "application/x-mpegURL" else "video/mp4"
        MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(mimeType)
            .build()
    }

    val renderersFactory = remember(fallback.forceSoftware) {
        buildRenderersFactory(context, forceSoftware = fallback.forceSoftware)
    }

    val exoPlayer = remember(fallback) {
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(buildTrackSelector(context, enableTunneling = enableTunneling))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("ToniPlayer", "ExoPlayer error: ${error.message}", error)
                        if (!error.isDecoderFailure()) return
                        val next = fallback.next(hasQualityTag = streamHasQualityTag)
                        if (next != null) {
                            Log.w("ToniPlayer", "Decoder rejected the stream; degrading playback: $next")
                            fallback = next
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        state.isPlaybackEnded = playbackState == Player.STATE_ENDED
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        // Keep the screen awake while actually playing (mobile only); let it time out
                        // normally when paused/stopped.
                        if (!keepScreenOn) return
                        val window = (context as? Activity)?.window ?: return
                        if (isPlaying) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    // Persist watch progress every second and track the "near end" window for the next-episode UI.
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(1000)
            val currentPos = try { exoPlayer.currentPosition } catch (_: Exception) { 0L }
            val duration = try { exoPlayer.duration } catch (_: Exception) { 0L }
            if (duration > 0 && currentPos > 0) {
                repository.saveWatchProgress(
                    episodeId = episodeId,
                    animeId = animeId,
                    position = currentPos,
                    duration = duration,
                    animeTitle = streamInfo.animeTitle,
                    animeImageUrl = streamInfo.animeImageUrl,
                    animeSlug = streamInfo.animeSlug,
                    episodeNumber = episodeNumber
                )
                val remainingMs = duration - currentPos
                state.timeRemainingSeconds = (remainingMs / 1000).toInt()
                state.isNearEnd = remainingMs < 30_000
            }
        }
    }

    // Pause playback when the app goes to the background so it doesn't keep playing off-screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                try { exoPlayer.pause() } catch (_: Exception) {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Save final progress and release the player when leaving the screen — or when it is rebuilt for
    // a lower-quality retry, so the previous (undecodable) instance is torn down instead of leaking.
    DisposableEffect(exoPlayer) {
        onDispose {
            val currentPos = try { exoPlayer.currentPosition } catch (_: Exception) { 0L }
            val duration = try { exoPlayer.duration } catch (_: Exception) { 0L }
            if (duration > 0 && currentPos > 0) {
                repository.saveWatchProgressAsync(
                    episodeId = episodeId,
                    animeId = animeId,
                    position = currentPos,
                    duration = duration,
                    animeTitle = streamInfo.animeTitle,
                    animeImageUrl = streamInfo.animeImageUrl,
                    animeSlug = streamInfo.animeSlug,
                    episodeNumber = episodeNumber
                )
            }
            try { exoPlayer.release() } catch (_: Exception) {}
            if (keepScreenOn) {
                (context as? Activity)?.window
                    ?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    return exoPlayer to state
}
