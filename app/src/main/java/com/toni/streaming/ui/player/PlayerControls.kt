package com.toni.streaming.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toni.streaming.ui.theme.AccentPurple
import java.util.Locale

/**
 * D-pad focus requesters that wire the transport buttons together on TV. Pass `null` on touch
 * devices (mobile), where buttons are plain-clickable and the seek bar is draggable instead.
 */
class PlayerControlFocus(
    val back: FocusRequester,
    val next: FocusRequester,
    val rewind: FocusRequester,
    val play: FocusRequester,
    val forward: FocusRequester
)

/**
 * The shared video-controls overlay used by both the mobile and TV players so the two look and
 * behave consistently: a "back to episodes" button, an optional next-episode button, a scrubber
 * with elapsed/total time, and a −10s / play-pause / +10s transport row.
 *
 * Hardware adaptation is handled by the caller:
 *  - Mobile passes [onSeekTo] (the scrubber is tap/drag seekable) and leaves [focus] null.
 *  - TV passes [focus] (D-pad navigation between buttons) and usually no [onSeekTo] (it seeks with
 *    the ±10s buttons and remote media keys).
 */
@Composable
fun PlayerControlsOverlay(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasNext: Boolean,
    isNearEnd: Boolean,
    isPlaybackEnded: Boolean,
    timeRemainingSeconds: Int,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSeekTo: ((Long) -> Unit)? = null,
    focus: PlayerControlFocus? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Dark scrim so the controls stay legible over any frame.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Top-left: back to the episode list.
        val backModifier = if (focus != null) {
            Modifier
                .focusRequester(focus.back)
                .focusProperties {
                    right = if (hasNext) focus.next else FocusRequester.Default
                    down = focus.play
                }
        } else Modifier
        ControlButton(
            icon = Icons.Default.ArrowBack,
            label = "Episodi",
            onClick = onBack,
            focusable = focus != null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(28.dp)
                .then(backModifier)
        )

        // Top-right: next episode.
        if (hasNext) {
            val nextModifier = if (focus != null) {
                Modifier
                    .focusRequester(focus.next)
                    .focusProperties {
                        left = focus.back
                        down = focus.forward
                    }
            } else Modifier
            ControlButton(
                icon = Icons.Default.SkipNext,
                label = "Ep. successivo",
                highlighted = true,
                onClick = onNext,
                focusable = focus != null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(28.dp)
                    .then(nextModifier)
            )
        }

        // Near-end cue.
        if (hasNext && isNearEnd && !isPlaybackEnded) {
            Text(
                text = "Prossimo episodio tra ${timeRemainingSeconds}s",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
            )
        }

        // Bottom: scrubber + transport controls.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 30.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatPlayerTime(positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(16.dp))
                SeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = formatPlayerTime(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rewindModifier = if (focus != null) {
                    Modifier.focusRequester(focus.rewind).focusProperties { right = focus.play; up = focus.back }
                } else Modifier
                ControlButton(
                    icon = Icons.Default.FastRewind,
                    label = "-10s",
                    onClick = onRewind,
                    focusable = focus != null,
                    modifier = rewindModifier
                )
                Spacer(modifier = Modifier.width(28.dp))
                val playModifier = if (focus != null) {
                    Modifier.focusRequester(focus.play).focusProperties {
                        left = focus.rewind
                        right = focus.forward
                        up = if (hasNext) focus.next else focus.back
                    }
                } else Modifier
                ControlButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (isPlaying) "Pausa" else "Play",
                    big = true,
                    onClick = onPlayPause,
                    focusable = focus != null,
                    modifier = playModifier
                )
                Spacer(modifier = Modifier.width(28.dp))
                val forwardModifier = if (focus != null) {
                    Modifier.focusRequester(focus.forward).focusProperties {
                        left = focus.play
                        up = if (hasNext) focus.next else focus.back
                    }
                } else Modifier
                ControlButton(
                    icon = Icons.Default.FastForward,
                    label = "+10s",
                    onClick = onForward,
                    focusable = focus != null,
                    modifier = forwardModifier
                )
            }
        }
    }
}

/** Slim progress bar; tap or drag to seek when [onSeekTo] is provided (touch devices). */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: ((Long) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val seekModifier = if (onSeekTo != null && durationMs > 0) {
        Modifier
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    onSeekTo((offset.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures { change, _ ->
                    onSeekTo((change.position.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                }
            }
    } else Modifier
    Box(
        modifier = modifier
            .height(if (onSeekTo != null) 14.dp else 6.dp)
            .then(seekModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(AccentPurple)
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    highlighted: Boolean = false,
    focusable: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val circle = if (big) 68.dp else 52.dp
    val bg = when {
        focused -> Color.White
        highlighted -> AccentPurple
        else -> Color.Black.copy(alpha = 0.55f)
    }
    val tint = if (focused) AccentPurple else Color.White
    Column(
        modifier = modifier
            .then(if (focusable) Modifier.onFocusChanged { focused = it.isFocused } else Modifier)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(circle)
                .clip(RoundedCornerShape(circle / 2))
                .background(bg)
                .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(circle / 2)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(if (big) 36.dp else 26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** Formats a millisecond position as m:ss, or h:mm:ss for content an hour or longer. */
fun formatPlayerTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val s = totalSec % 60
    val m = (totalSec / 60) % 60
    val h = totalSec / 3600
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
