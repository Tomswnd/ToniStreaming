package com.toni.streaming.data.repository

import android.content.Context
import android.util.Log
import com.toni.streaming.data.local.AppDatabase
import com.toni.streaming.data.local.FavoriteEntity
import com.toni.streaming.data.local.WatchHistoryEntity
import com.toni.streaming.data.model.Anime
import com.toni.streaming.data.model.AnimeDetails
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.model.StreamInfo
import com.toni.streaming.data.scraper.AnimeUnityScraper
import com.toni.streaming.data.scraper.CloudflareBypass
import com.toni.streaming.data.scraper.ScrapingException
import com.toni.streaming.data.scraper.TopAnimeType
import com.toni.streaming.data.scraper.VixcloudExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for all anime data.
 * Coordinates between the scraper, extractor, Cloudflare bypass, and local database.
 */
class AnimeRepository private constructor(
    context: Context
) {
    companion object {
        private const val TAG = "AnimeRepository"

        @Volatile
        private var INSTANCE: AnimeRepository? = null

        /**
         * App-wide singleton. Backed by the application context, so a single [AnimeRepository]
         * (and its OkHttp client / cookie jar / DB handles) is shared for the whole process
         * instead of being rebuilt per screen.
         */
        fun getInstance(context: Context): AnimeRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnimeRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    // Hold the application context only, never an Activity, to avoid leaking it via the
    // long-lived scraper / Cloudflare WebView.
    private val appContext = context.applicationContext

    // Application-scoped IO scope for fire-and-forget writes (e.g. saving watch progress when the
    // player screen is disposed and no ViewModel/composition scope is available).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scraper = AnimeUnityScraper()
    val httpClient: okhttp3.OkHttpClient
        get() = scraper.httpClient
    private val extractor = VixcloudExtractor(scraper.httpClient)
    private val cloudflareBypass = CloudflareBypass(appContext)
    private val database = AppDatabase.getInstance(appContext)
    private val watchHistoryDao = database.watchHistoryDao()
    private val favoriteDao = database.favoriteDao()

    private var cfCookiesObtained = false
    private var bypassAttempted = false

    /**
     * Fetches the anime list. Tries popular anime (most viewed) first, and falls back to alphabetical listing.
     */
    suspend fun getAnimeList(page: Int = 1): Result<List<Anime>> {
        return getTopAnimeList(TopAnimeType.MOST_VIEWED, page)
    }

    /**
     * Fetches top anime lists based on type (most viewed, popular, in progress).
     */
    suspend fun getTopAnimeList(type: TopAnimeType, page: Int = 1): Result<List<Anime>> {
        return executeWithCfRetry("getTopAnimeList(type=${type.name}, page=$page)") {
            scraper.getTopAnimeList(type, page)
        }
    }

    /**
     * Searches anime by title.
     */
    suspend fun searchAnime(query: String): Result<List<Anime>> {
        return executeWithCfRetry("searchAnime(query=$query)") {
            scraper.searchAnime(query)
        }
    }

    /**
     * Fetches episode list for a given anime.
     */
    suspend fun getEpisodes(animeUrl: String, animeId: String): Result<List<Episode>> {
        return executeWithCfRetry("getEpisodes(id=$animeId)") {
            scraper.getEpisodes(animeUrl, animeId)
        }
    }

    /**
     * Fetches detailed information including metadata and the first page of episodes.
     */
    suspend fun getAnimeDetails(animeUrl: String, animeId: String): Result<AnimeDetails> {
        return executeWithCfRetry("getAnimeDetails(id=$animeId)") {
            scraper.getAnimeDetails(animeUrl, animeId)
        }
    }

    /**
     * Lazily fetches a further page of episodes (by episode number) on demand.
     */
    suspend fun getEpisodesRange(
        animeId: String,
        animeUrl: String,
        start: Int,
        end: Int
    ): Result<List<Episode>> {
        return executeWithCfRetry("getEpisodesRange(id=$animeId, $start-$end)") {
            scraper.getEpisodesRange(animeId, animeUrl, start, end)
        }
    }

    /**
     * Full streaming pipeline: extracts the .m3u8 URL for a given episode.
     * Step 1: Extract iframe URL from AnimeUnity page
     * Step 2: Resolve .m3u8 from Vixcloud embed
     */
    suspend fun getStreamInfo(episodeUrl: String): Result<StreamInfo> {
        return executeWithCfRetry("getStreamInfo") {
            // Step 1: Get episode details (iframe URL + anime metadata)
            val details = scraper.getEpisodePageDetails(episodeUrl)
            Log.d(TAG, "Iframe URL extracted: ${details.iframeUrl} for anime: ${details.animeTitle}")

            // Step 2: Dynamically bypass Cloudflare/obtain cookies for Vixcloud domain
            Log.d(TAG, "Obtaining Vixcloud clearance cookies for: ${details.iframeUrl}")
            try {
                val vixCookies = cloudflareBypass.obtainClearanceCookies(details.iframeUrl)
                Log.d(TAG, "Vixcloud cookies obtained: ${vixCookies.size} cookies")
                scraper.injectCookies("vixcloud.co", vixCookies)
                scraper.injectCookies("www.vixcloud.co", vixCookies)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to obtain Vixcloud clearance cookies (non-fatal)", e)
            }

            // Step 3: Resolve video stream (.mp4 or .m3u8)
            val baseStreamInfo = extractor.extractM3u8Url(details.iframeUrl)
            Log.d(TAG, "Video URL resolved: ${baseStreamInfo.m3u8Url}")

            // Attach anime metadata
            baseStreamInfo.copy(
                animeTitle = details.animeTitle,
                animeImageUrl = details.animeImageUrl,
                animeSlug = details.animeSlug
            )
        }
    }

    /**
     * Force a Cloudflare bypass attempt and retry loading.
     * Useful when the automatic bypass failed.
     */
    suspend fun forceRetryWithBypass(): Result<List<Anime>> {
        Log.i(TAG, "Forcing Cloudflare bypass...")
        return try {
            val cookies = cloudflareBypass.obtainClearanceCookies()
            scraper.injectCookies("www.animeunity.so", cookies)
            scraper.injectCookies("animeunity.so", cookies)
            cfCookiesObtained = true
            Log.i(TAG, "Forced bypass successful, retrying...")
            Result.success(scraper.getAnimeList(1))
        } catch (e: Exception) {
            Log.e(TAG, "Forced Cloudflare bypass failed", e)
            Result.failure(
                ScrapingException.ParsingException(
                    "Impossibile superare la protezione Cloudflare. " +
                    "Verifica la connessione internet e riprova."
                )
            )
        }
    }

    // --- Watch History ---

    suspend fun saveWatchProgress(
        episodeId: String,
        animeId: String,
        position: Long,
        duration: Long,
        animeTitle: String = "",
        animeImageUrl: String = "",
        animeSlug: String = "",
        episodeNumber: Int = 0
    ) {
        watchHistoryDao.upsert(
            WatchHistoryEntity(
                episodeId = episodeId,
                animeId = animeId,
                episodeNumber = episodeNumber,
                watchedPositionMs = position,
                totalDurationMs = duration,
                lastWatchedTimestamp = System.currentTimeMillis(),
                animeTitle = animeTitle,
                animeImageUrl = animeImageUrl,
                animeSlug = animeSlug
            )
        )
    }

    /**
     * Fire-and-forget variant of [saveWatchProgress] backed by an application-scoped coroutine.
     * Safe to call from non-coroutine callbacks (e.g. Compose `onDispose`) without leaking work
     * onto [kotlinx.coroutines.GlobalScope].
     */
    fun saveWatchProgressAsync(
        episodeId: String,
        animeId: String,
        position: Long,
        duration: Long,
        animeTitle: String = "",
        animeImageUrl: String = "",
        animeSlug: String = "",
        episodeNumber: Int = 0
    ) {
        appScope.launch {
            saveWatchProgress(
                episodeId, animeId, position, duration, animeTitle, animeImageUrl, animeSlug,
                episodeNumber
            )
        }
    }

    suspend fun getWatchProgress(episodeId: String): WatchHistoryEntity? {
        return watchHistoryDao.getByEpisodeId(episodeId)
    }

    suspend fun getWatchHistoryForAnime(animeId: String): List<WatchHistoryEntity> {
        return watchHistoryDao.getByAnimeId(animeId)
    }

    fun getRecentlyWatched(): Flow<List<WatchHistoryEntity>> {
        return watchHistoryDao.getRecentlyWatched()
    }

    // --- Favorites ---

    fun getFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAll()

    fun isFavorite(animeId: String): Flow<Boolean> = favoriteDao.isFavorite(animeId)

    /** Toggles favorite state; returns the new state (true = now favorite). */
    suspend fun toggleFavorite(
        animeId: String,
        title: String,
        imageUrl: String,
        animeUrl: String,
        coverUrl: String? = null
    ): Boolean {
        return if (favoriteDao.isFavoriteNow(animeId)) {
            favoriteDao.delete(animeId)
            false
        } else {
            favoriteDao.upsert(
                FavoriteEntity(
                    animeId = animeId,
                    title = title,
                    imageUrl = imageUrl,
                    animeUrl = animeUrl,
                    coverUrl = coverUrl
                )
            )
            true
        }
    }

    // --- P2P sync (watch history + favorites) ---

    /** Serializes watch history + favorites to a JSON string for LAN sync. */
    suspend fun exportSyncPayload(): String {
        val root = JSONObject()
        val watchArr = JSONArray()
        watchHistoryDao.getAllNow().forEach { w ->
            watchArr.put(
                JSONObject()
                    .put("episodeId", w.episodeId)
                    .put("animeId", w.animeId)
                    .put("episodeNumber", w.episodeNumber)
                    .put("watchedPositionMs", w.watchedPositionMs)
                    .put("totalDurationMs", w.totalDurationMs)
                    .put("lastWatchedTimestamp", w.lastWatchedTimestamp)
                    .put("animeTitle", w.animeTitle)
                    .put("animeImageUrl", w.animeImageUrl)
                    .put("animeSlug", w.animeSlug)
            )
        }
        val favArr = JSONArray()
        favoriteDao.getAllNow().forEach { f ->
            favArr.put(
                JSONObject()
                    .put("animeId", f.animeId)
                    .put("title", f.title)
                    .put("imageUrl", f.imageUrl)
                    .put("animeUrl", f.animeUrl)
                    .put("coverUrl", f.coverUrl ?: JSONObject.NULL)
                    .put("addedAt", f.addedAt)
            )
        }
        return root.put("watch", watchArr).put("favorites", favArr).toString()
    }

    /**
     * Merges a peer's payload into the local DB. Watch history uses last-write-wins by
     * timestamp; favorites are additive (union by animeId). Returns how many rows changed.
     */
    suspend fun importAndMerge(json: String): Int {
        val root = JSONObject(json)

        val localWatch = watchHistoryDao.getAllNow().associateBy { it.episodeId }
        val incomingWatch = root.optJSONArray("watch") ?: JSONArray()
        val watchToUpsert = mutableListOf<WatchHistoryEntity>()
        for (i in 0 until incomingWatch.length()) {
            val o = incomingWatch.getJSONObject(i)
            val entity = WatchHistoryEntity(
                episodeId = o.getString("episodeId"),
                animeId = o.optString("animeId"),
                episodeNumber = o.optInt("episodeNumber"),
                watchedPositionMs = o.optLong("watchedPositionMs"),
                totalDurationMs = o.optLong("totalDurationMs"),
                lastWatchedTimestamp = o.optLong("lastWatchedTimestamp"),
                animeTitle = o.optString("animeTitle"),
                animeImageUrl = o.optString("animeImageUrl"),
                animeSlug = o.optString("animeSlug")
            )
            val local = localWatch[entity.episodeId]
            if (local == null || entity.lastWatchedTimestamp > local.lastWatchedTimestamp) {
                watchToUpsert.add(entity)
            }
        }
        if (watchToUpsert.isNotEmpty()) watchHistoryDao.upsertAll(watchToUpsert)

        val localFavIds = favoriteDao.getAllNow().mapTo(HashSet()) { it.animeId }
        val incomingFav = root.optJSONArray("favorites") ?: JSONArray()
        val favToUpsert = mutableListOf<FavoriteEntity>()
        for (i in 0 until incomingFav.length()) {
            val o = incomingFav.getJSONObject(i)
            val id = o.getString("animeId")
            if (id !in localFavIds) {
                favToUpsert.add(
                    FavoriteEntity(
                        animeId = id,
                        title = o.optString("title"),
                        imageUrl = o.optString("imageUrl"),
                        animeUrl = o.optString("animeUrl"),
                        coverUrl = if (o.isNull("coverUrl")) null else o.optString("coverUrl"),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
        }
        if (favToUpsert.isNotEmpty()) favoriteDao.upsertAll(favToUpsert)

        return watchToUpsert.size + favToUpsert.size
    }

    // --- Private helpers ---

    /**
     * Wraps scraping calls with automatic Cloudflare bypass retry.
     * If the initial request returns 403 (CF challenge), obtains clearance cookies
     * via WebView and retries the original request.
     */
    private suspend fun <T> executeWithCfRetry(tag: String, block: suspend () -> T): Result<T> {
        return try {
            Log.d(TAG, "[$tag] Executing scraping request...")
            val result = block()
            Log.d(TAG, "[$tag] Success")
            Result.success(result)
        } catch (e: ScrapingException.CloudflareBlockedException) {
            Log.w(TAG, "[$tag] Cloudflare 403 detected, attempting WebView bypass...")
            try {
                if (!cfCookiesObtained && !bypassAttempted) {
                    bypassAttempted = true
                    val cookies = cloudflareBypass.obtainClearanceCookies()
                    scraper.injectCookies("www.animeunity.so", cookies)
                    scraper.injectCookies("animeunity.so", cookies)
                    cfCookiesObtained = true
                    Log.i(TAG, "[$tag] Cloudflare cookies injected successfully (${cookies.size} cookies)")
                }
                Log.d(TAG, "[$tag] Retrying after bypass...")
                val result = block()
                Log.d(TAG, "[$tag] Retry successful")
                Result.success(result)
            } catch (retryEx: Exception) {
                Log.e(TAG, "[$tag] Cloudflare bypass + retry failed", retryEx)
                Result.failure(
                    ScrapingException.ParsingException(
                        "Protezione Cloudflare attiva. Il bypass automatico ha fallito. " +
                        "Errore: ${retryEx.message}"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$tag] Scraping error: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }
}
