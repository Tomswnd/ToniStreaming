package com.toni.streaming.ui.player

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Device- and decoder-specific playback plumbing, kept out of the player composable.
 *
 * Two jobs live here:
 *  1. Progressive fallback when a device's video decoder can't handle the source: step the
 *     resolution down first (which keeps *hardware* decoding), and only force software decoding
 *     as a genuine last resort.
 *  2. Building a [DefaultRenderersFactory] tuned for low-power TV boxes — Fire TV in particular —
 *     where the default pipeline is prone to audio drifting ahead of the video.
 *
 * All of this used to be inline in `rememberManagedExoPlayer` as loose `var`s, an inline
 * `MediaCodecSelector` lambda and ad-hoc string matching. It's isolated here so the composable
 * only deals with playback state, and so the decoder logic is unit-testable on its own.
 */

/** Matches a `/720p.mp4`-style quality tag in a stream URL. */
private val QUALITY_TAG = Regex("""/\d+p\.mp4""", RegexOption.IGNORE_CASE)

/** Resolution ladder walked, in order, when the hardware decoder rejects the source. */
private val QUALITY_LADDER = listOf("720p", "480p", "360p")

/**
 * How far decoding has had to degrade for the current stream.
 *
 * [qualityStep] 0 means source quality; 1..[QUALITY_LADDER].size index into the ladder.
 * [forceSoftware] is the last-resort switch to software video decoding.
 */
data class DecoderFallback(
    val qualityStep: Int = 0,
    val forceSoftware: Boolean = false,
) {
    private val canStepQuality: Boolean get() = qualityStep < QUALITY_LADDER.size

    /**
     * The next degradation to try after a decoder failure, or `null` when everything has already
     * been tried (nothing left to fall back to).
     *
     * @param hasQualityTag whether the source URL carries a rewritable `/NNNp.mp4` tag.
     */
    fun next(hasQualityTag: Boolean): DecoderFallback? = when {
        hasQualityTag && canStepQuality -> copy(qualityStep = qualityStep + 1)
        !forceSoftware -> copy(forceSoftware = true)
        else -> null
    }
}

/** True when [url] carries a `/NNNp.mp4` quality tag we can rewrite to step resolution down. */
fun hasQualityTag(url: String): Boolean = QUALITY_TAG.containsMatchIn(url)

/** Rewrites [sourceUrl] to the resolution selected by [fallback]; unchanged at step 0. */
fun resolveVideoUrl(sourceUrl: String, fallback: DecoderFallback): String {
    val target = QUALITY_LADDER.getOrNull(fallback.qualityStep - 1) ?: return sourceUrl
    return QUALITY_TAG.replace(sourceUrl, "/$target.mp4")
}

/** True if [this] looks like a decoder/codec failure rather than a network or parsing error. */
@OptIn(UnstableApi::class)
fun PlaybackException.isDecoderFailure(): Boolean {
    if (errorCodeName.contains("DECODER", ignoreCase = true) ||
        errorCodeName.contains("DECODING", ignoreCase = true)
    ) return true
    val cause = cause
    return cause is IllegalArgumentException ||
        cause?.message?.contains("MediaCodec", ignoreCase = true) == true
}

/**
 * Audio offload preferences that keep audio playing through the normal PCM [android.media.AudioTrack]
 * instead of the DSP offload path. ExoPlayer tracks the AudioTrack's playback position precisely and
 * syncs video to it; the offload path only *estimates* its position, which is a common source of
 * "audio runs ahead of video" drift on Fire TV. Disabling offload trades a little battery for tight
 * sync — the right call for a video app.
 */
@OptIn(UnstableApi::class)
val PcmAudioOffloadPreferences: AudioOffloadPreferences =
    AudioOffloadPreferences.Builder()
        .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
        .build()

/**
 * A [MediaCodecSelector] that prefers software decoders, used as the last-resort fallback for
 * sources no hardware decoder on the device will accept. Software detection keys off the codec name
 * (Google/Android reference decoders are always software) plus [MediaCodecInfo.hardwareAccelerated]
 * where the platform reports it (API 29+).
 */
@OptIn(UnstableApi::class)
val SoftwarePreferredCodecSelector = MediaCodecSelector { mimeType, secure, tunneling ->
    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
    all.filter { it.isLikelySoftware() }.ifEmpty { all }
}

@OptIn(UnstableApi::class)
private fun MediaCodecInfo.isLikelySoftware(): Boolean {
    val lowerName = name.lowercase()
    if (lowerName.startsWith("omx.google.") || lowerName.startsWith("c2.android.")) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return !hardwareAccelerated
    return lowerName.contains(".sw.") || lowerName.endsWith(".sw")
}

/**
 * Builds a [DefaultRenderersFactory] tuned to avoid audio drifting ahead of video on low-power
 * TV boxes:
 *  - Asynchronous MediaCodec queueing smooths frame pacing on the Android 7–11 (API 24–30) range
 *    most Fire TV devices ship with; on API 31+ it's already the default.
 *  - Decoder fallback stays on, so a rejected hardware decoder degrades instead of hard-failing.
 *  - When [forceSoftware] is set (last resort) it swaps in [SoftwarePreferredCodecSelector].
 */
@OptIn(UnstableApi::class)
fun buildRenderersFactory(context: Context, forceSoftware: Boolean): DefaultRenderersFactory =
    DefaultRenderersFactory(context).apply {
        setEnableDecoderFallback(true)
        forceEnableMediaCodecAsynchronousQueueing()
        if (forceSoftware) setMediaCodecSelector(SoftwarePreferredCodecSelector)
    }

/**
 * Builds a [DefaultTrackSelector] tuned for the current device.
 *
 * [enableTunneling] is the real fix for Fire TV lip-sync. On this hardware `AudioTrack.getTimestamp()`
 * reports *retrograde* positions after a seek/resume (verified in logcat: "retrograde timestamp
 * position corrected, -27795"), which corrupts ExoPlayer's audio-mastered clock and leaves audio
 * offset from video for the rest of playback. Tunneled playback hands audio/video sync to the
 * platform's hardware pipeline, so ExoPlayer's unreliable software clock is no longer in the loop.
 * ExoPlayer only engages tunneling when a compatible decoder pair is available and otherwise plays
 * normally, so this is safe to leave on for devices that don't support it.
 *
 * Audio offload is disabled regardless (see [PcmAudioOffloadPreferences]).
 */
@OptIn(UnstableApi::class)
fun buildTrackSelector(context: Context, enableTunneling: Boolean): DefaultTrackSelector =
    DefaultTrackSelector(context).apply {
        val params = buildUponParameters()
            .setTunnelingEnabled(enableTunneling)
            .setAudioOffloadPreferences(PcmAudioOffloadPreferences)
            .build()
        setParameters(params)
    }
