package com.toni.streaming.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.repository.AnimeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class DownloadStatus {
    NOT_DOWNLOADED,
    PREPARING,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class EpisodeDownloadUi(
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val percent: Float = 0f,
    val bytesDownloaded: Long = 0L
)

/**
 * Tracks the download state of every episode (keyed by Episode.id) and exposes
 * start/cancel/remove actions. Backed by the Media3 DownloadManager, which
 * persists state on its own — no extra Room table needed.
 */
@OptIn(UnstableApi::class)
class EpisodeDownloadsViewModel(
    context: Context,
    private val repository: AnimeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EpisodeDownloadsVM"
    }

    private val appContext = context.applicationContext
    private val downloadManager: DownloadManager

    private val _downloadStates = MutableStateFlow<Map<String, EpisodeDownloadUi>>(emptyMap())
    val downloadStates: StateFlow<Map<String, EpisodeDownloadUi>> = _downloadStates.asStateFlow()

    private var progressJob: Job? = null

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            updateFromDownload(download)
            if (download.state == Download.STATE_DOWNLOADING) startProgressPolling()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            _downloadStates.update { it - download.request.id }
        }
    }

    init {
        // Give the DownloadManager the scraper's HTTP client (with CF cookies) before
        // it is lazily created, then load the already-persisted downloads.
        DownloadCenter.httpClient = repository.httpClient
        downloadManager = DownloadCenter.getDownloadManager(appContext)
        downloadManager.addListener(listener)
        loadPersistedDownloads()
    }

    /** Starts downloading an episode at the best available quality. */
    fun startDownload(episode: Episode, metadata: DownloadMetadata) {
        val current = _downloadStates.value[episode.id]?.status
        if (current != null && current != DownloadStatus.NOT_DOWNLOADED && current != DownloadStatus.FAILED) {
            return
        }

        setStatus(episode.id, DownloadStatus.PREPARING)

        viewModelScope.launch {
            try {
                // The Vixcloud URL is tokenized and short-lived: always resolve it
                // fresh at download time instead of reusing a cached StreamInfo.
                val streamInfo = repository.getStreamInfo(episode.url).getOrThrow()

                val fullMetadata = metadata.copy(
                    animeTitle = metadata.animeTitle.ifBlank { streamInfo.animeTitle },
                    animeImageUrl = metadata.animeImageUrl.ifBlank { streamInfo.animeImageUrl },
                    animeSlug = metadata.animeSlug.ifBlank { streamInfo.animeSlug }
                )

                val request = buildDownloadRequest(episode.id, streamInfo.m3u8Url, fullMetadata)

                ensureDownloadNotificationChannel(appContext)
                DownloadService.sendAddDownload(
                    appContext,
                    EpisodeDownloadService::class.java,
                    request,
                    /* foreground= */ true
                )
                Log.i(TAG, "Download queued for episode ${episode.id}: ${streamInfo.m3u8Url}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download for episode ${episode.id}", e)
                setStatus(episode.id, DownloadStatus.FAILED)
            }
        }
    }

    /** Cancels an in-progress download or deletes a completed one (frees disk space). */
    fun removeDownload(episodeId: String) {
        DownloadService.sendRemoveDownload(
            appContext,
            EpisodeDownloadService::class.java,
            episodeId,
            /* foreground= */ false
        )
        _downloadStates.update { it - episodeId }
    }

    private suspend fun buildDownloadRequest(
        episodeId: String,
        videoUrl: String,
        metadata: DownloadMetadata
    ): DownloadRequest {
        if (!videoUrl.contains(".m3u8")) {
            // Direct MP4: a plain progressive download, no track selection needed.
            return DownloadRequest.Builder(episodeId, Uri.parse(videoUrl))
                .setMimeType(MimeTypes.VIDEO_MP4)
                .setData(metadata.toJsonBytes())
                .build()
        }

        // HLS: DownloadHelper analyzes the master playlist and selects a single
        // variant (best quality within device capabilities) instead of
        // downloading every rendition in the playlist.
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        val helper = DownloadHelper.forMediaItem(
            appContext,
            mediaItem,
            DefaultRenderersFactory(appContext),
            DownloadCenter.buildDownloadCacheDataSourceFactory(appContext)
        )
        try {
            helper.prepareSuspend()
            return helper.getDownloadRequest(episodeId, metadata.toJsonBytes())
        } finally {
            helper.release()
        }
    }

    private suspend fun DownloadHelper.prepareSuspend() {
        suspendCancellableCoroutine { continuation ->
            prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            })
        }
    }

    private fun setStatus(episodeId: String, status: DownloadStatus) {
        _downloadStates.update { states ->
            states + (episodeId to (states[episodeId] ?: EpisodeDownloadUi()).copy(status = status))
        }
    }

    private fun updateFromDownload(download: Download) {
        val ui = EpisodeDownloadUi(
            status = when (download.state) {
                Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadStatus.QUEUED
                Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
                Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
                Download.STATE_FAILED -> DownloadStatus.FAILED
                Download.STATE_STOPPED -> DownloadStatus.QUEUED
                else -> DownloadStatus.NOT_DOWNLOADED
            },
            percent = download.percentDownloaded.coerceAtLeast(0f),
            bytesDownloaded = download.bytesDownloaded
        )
        if (ui.status == DownloadStatus.NOT_DOWNLOADED) {
            _downloadStates.update { it - download.request.id }
        } else {
            _downloadStates.update { it + (download.request.id to ui) }
        }
    }

    private fun loadPersistedDownloads() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        updateFromDownload(cursor.download)
                    }
                }
                if (_downloadStates.value.values.any { it.status == DownloadStatus.DOWNLOADING }) {
                    startProgressPolling()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load persisted downloads", e)
            }
        }
    }

    private fun startProgressPolling() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (isActive) {
                val active = downloadManager.currentDownloads
                active.forEach { updateFromDownload(it) }
                if (active.none { it.state == Download.STATE_DOWNLOADING }) break
                delay(500)
            }
        }
    }

    override fun onCleared() {
        downloadManager.removeListener(listener)
        super.onCleared()
    }
}
