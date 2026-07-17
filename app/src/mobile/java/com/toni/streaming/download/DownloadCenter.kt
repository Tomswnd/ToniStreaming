package com.toni.streaming.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.toni.streaming.data.model.StreamInfo
import com.toni.streaming.data.scraper.AnimeUnityScraper
import com.toni.streaming.data.scraper.VixcloudExtractor
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Metadata embedded in each DownloadRequest (as JSON in request.data) so that
 * downloaded episodes can be listed and played offline without re-scraping.
 */
data class DownloadMetadata(
    val animeId: String = "",
    val episodeNumber: Int = 0,
    val animeTitle: String = "",
    val animeImageUrl: String = "",
    val animeSlug: String = ""
) {
    fun toJsonBytes(): ByteArray = JSONObject().apply {
        put("animeId", animeId)
        put("episodeNumber", episodeNumber)
        put("animeTitle", animeTitle)
        put("animeImageUrl", animeImageUrl)
        put("animeSlug", animeSlug)
    }.toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun fromJsonBytes(bytes: ByteArray): DownloadMetadata = try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            DownloadMetadata(
                animeId = json.optString("animeId"),
                episodeNumber = json.optInt("episodeNumber"),
                animeTitle = json.optString("animeTitle"),
                animeImageUrl = json.optString("animeImageUrl"),
                animeSlug = json.optString("animeSlug")
            )
        } catch (e: Exception) {
            DownloadMetadata()
        }
    }
}

/**
 * App-wide singleton owning the download cache and the Media3 DownloadManager.
 * Downloads are keyed by Episode.id (same identifier used by watch history/favorites).
 */
@OptIn(UnstableApi::class)
object DownloadCenter {

    const val NOTIFICATION_CHANNEL_ID = "episode_downloads"
    const val FOREGROUND_NOTIFICATION_ID = 1001

    /**
     * Optionally set by the UI layer with the scraper's OkHttpClient (which carries
     * Cloudflare clearance cookies). If the DownloadService restarts on its own
     * before any UI is shown, a plain client is used as fallback.
     */
    @Volatile
    var httpClient: OkHttpClient? = null

    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var cache: SimpleCache? = null
    private var downloadManager: DownloadManager? = null
    private var notificationHelper: DownloadNotificationHelper? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val appContext = context.applicationContext
            cache = SimpleCache(
                File(appContext.filesDir, "downloads"),
                NoOpCacheEvictor(),
                getDatabaseProvider(appContext)
            )
        }
        return cache!!
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val appContext = context.applicationContext
            downloadManager = DownloadManager(
                appContext,
                getDatabaseProvider(appContext),
                getCache(appContext),
                buildUpstreamFactory(),
                Executors.newFixedThreadPool(4)
            ).apply {
                maxParallelDownloads = 2
            }
        }
        return downloadManager!!
    }

    @Synchronized
    fun getNotificationHelper(context: Context): DownloadNotificationHelper {
        if (notificationHelper == null) {
            notificationHelper = DownloadNotificationHelper(
                context.applicationContext,
                NOTIFICATION_CHANNEL_ID
            )
        }
        return notificationHelper!!
    }

    /**
     * DataSource factory for the player: reads downloaded segments from the cache
     * and falls back to the network for regular streaming. Cache writes are disabled
     * so that plain streaming never fills the download cache (NoOpCacheEvictor
     * would never free that space).
     */
    fun buildPlayerDataSourceFactory(
        context: Context,
        upstreamClient: OkHttpClient,
        userAgent: String,
        headers: Map<String, String>
    ): CacheDataSource.Factory {
        val upstream = OkHttpDataSource.Factory(upstreamClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Factory used both by the DownloadManager and by DownloadHelper at preparation time. */
    fun buildDownloadCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(buildUpstreamFactory())
    }

    /** Returns the completed download for an episode, or null if absent/incomplete. */
    fun getCompletedDownload(context: Context, episodeId: String): Download? {
        val download = try {
            getDownloadManager(context).downloadIndex.getDownload(episodeId)
        } catch (e: Exception) {
            null
        }
        return download?.takeIf { it.state == Download.STATE_COMPLETED }
    }

    /**
     * Builds a StreamInfo pointing at the locally cached copy of a downloaded episode.
     * The URI is the one stored at download time, so cache keys match and playback
     * works fully offline (the expired token in the URL is never used).
     */
    fun buildOfflineStreamInfo(context: Context, episodeId: String): StreamInfo? {
        val download = getCompletedDownload(context, episodeId) ?: return null
        val metadata = DownloadMetadata.fromJsonBytes(download.request.data)
        return StreamInfo(
            m3u8Url = download.request.uri.toString(),
            referer = VixcloudExtractor.VIXCLOUD_REFERER,
            userAgent = AnimeUnityScraper.USER_AGENT,
            headers = defaultVixcloudHeaders(),
            animeTitle = metadata.animeTitle,
            animeImageUrl = metadata.animeImageUrl,
            animeSlug = metadata.animeSlug
        )
    }

    fun defaultVixcloudHeaders(): Map<String, String> = mapOf(
        "Referer" to VixcloudExtractor.VIXCLOUD_REFERER,
        "Origin" to VixcloudExtractor.VIXCLOUD_ORIGIN,
        "User-Agent" to AnimeUnityScraper.USER_AGENT
    )

    private fun buildUpstreamFactory(): OkHttpDataSource.Factory {
        val client = httpClient ?: OkHttpClient()
        return OkHttpDataSource.Factory(client)
            .setUserAgent(AnimeUnityScraper.USER_AGENT)
            .setDefaultRequestProperties(defaultVixcloudHeaders())
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
        }
        return databaseProvider!!
    }
}
