package com.toni.streaming.data.scraper

import android.util.Log
import com.toni.streaming.data.model.Anime
import com.toni.streaming.data.model.AnimeDetails
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.model.RelatedAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

enum class TopAnimeType(val queryParam: String) {
    MOST_VIEWED("order=most_viewed"),
    POPULAR("popular=true"),
    IN_PROGRESS("status=In%20Corso")
}

class AnimeUnityScraper {

    companion object {
        private const val TAG = "AnimeUnityScraper"
        const val BASE_URL = "https://www.animeunity.so"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }

    // Cookie jar that holds Cloudflare clearance cookies
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Injects Cloudflare clearance cookies obtained from the WebView bypass.
     */
    fun injectCookies(host: String, cookies: List<Cookie>) {
        cookieStore[host] = cookies
    }

    /**
     * Fetches and parses the main anime listing page.
     * AnimeUnity embeds anime data in a Vue.js <archivio> component
     * via the "records" attribute as HTML-encoded JSON.
     */
    suspend fun getAnimeList(page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/archivio?page=$page"
        val doc = fetchDocument(url)
        parseAnimeFromArchivio(doc)
    }


    /**
     * Fetches top anime lists based on type (most viewed, popular, in progress).
     */
    suspend fun getTopAnimeList(type: TopAnimeType, page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/top-anime?${type.queryParam}&page=$page"
        val doc = fetchDocument(url)
        
        val topAnimeElement = doc.selectFirst("top-anime")
        if (topAnimeElement != null) {
            val animesJson = topAnimeElement.attr("animes")
            if (animesJson.isNotBlank()) {
                val dataStart = animesJson.indexOf("\"data\":[")
                if (dataStart != -1) {
                    val start = dataStart + 7 // position of the '[' of "data"
                    var depth = 0
                    var end = -1
                    for (i in start until animesJson.length) {
                        val c = animesJson[i]
                        if (c == '[') depth++
                        else if (c == ']') {
                            depth--
                            if (depth == 0) {
                                end = i
                                break
                            }
                        }
                    }
                    if (end != -1) {
                        val arrayJson = animesJson.substring(start, end + 1)
                        return@withContext parseJsonAnimeArray(arrayJson)
                    }
                }
            }
        }
        
        emptyList()
    }

    suspend fun getPopularAnimeList(page: Int = 1): List<Anime> {
        return getTopAnimeList(TopAnimeType.MOST_VIEWED, page)
    }

    private fun extractRecordsFromJsonResponse(json: String): String {
        val start = json.indexOf("[")
        val end = json.lastIndexOf("]")
        if (start in 0 until end) {
            return json.substring(start, end + 1)
        }
        return "[]"
    }

    /**
     * Pre-loads the episode page document and extracts iframeUrl and anime metadata (title, imageUrl, slug).
     * This avoids double GET requests on the episode page.
     */
    suspend fun getEpisodePageDetails(episodeUrl: String): EpisodePageDetails = withContext(Dispatchers.IO) {
        val doc = fetchDocument(episodeUrl)
        val iframeUrl = extractIframeUrlFromDoc(doc)

        var animeTitle = ""
        var animeImageUrl = ""
        var animeSlug = ""

        val videoPlayer = doc.selectFirst("video-player[episodes_count]")
            ?: doc.selectFirst("video-player")
        if (videoPlayer != null) {
            val animeJson = videoPlayer.attr("anime")
            if (animeJson.isNotBlank()) {
                animeTitle = cleanText(
                    extractTopLevelJsonString(animeJson, "title_it")
                        ?: extractTopLevelJsonString(animeJson, "title_eng")
                        ?: extractTopLevelJsonString(animeJson, "title")
                        ?: ""
                )
                animeImageUrl = extractTopLevelJsonString(animeJson, "imageurl") ?: ""
                animeSlug = extractTopLevelJsonString(animeJson, "slug") ?: ""
            }
        }

        EpisodePageDetails(
            iframeUrl = iframeUrl,
            animeTitle = animeTitle,
            animeImageUrl = animeImageUrl,
            animeSlug = animeSlug
        )
    }

    /**
     * Searches for anime by title.
     * AnimeUnity handles search on the same /archivio endpoint with a title param.
     */
    suspend fun searchAnime(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/archivio?title=$query"
        val doc = fetchDocument(url)
        parseAnimeFromArchivio(doc)
    }

    /**
     * Fetches the episode list for a specific anime.
     * Episodes are embedded in a Vue component as JSON in the anime detail page.
     */
    /**
     * Fetches the FIRST page of episodes (the batch AnimeUnity embeds in the page, ~120 max).
     * Use [getEpisodesRange] to lazily load further pages on demand.
     */
    suspend fun getEpisodes(animeUrl: String, animeId: String): List<Episode> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animeUrl)
        val episodes = parseEpisodesFromPage(doc, animeId, animeUrl).ifEmpty {
            parseEpisodesFromScript(doc, animeId, animeUrl)
        }
        Log.d(TAG, "getEpisodes: parsed ${episodes.size} episodes (first page)")
        episodes
    }

    /**
     * Lazily fetches a window of episodes (by episode number) from AnimeUnity's
     * paginated info_api endpoint. Used to load episodes beyond the first embedded page.
     */
    suspend fun getEpisodesRange(
        animeId: String,
        animeUrl: String,
        start: Int,
        end: Int
    ): List<Episode> = withContext(Dispatchers.IO) {
        fetchEpisodesFromApi(animeId, animeUrl, start, end, csrfToken = "")
    }

    suspend fun getAnimeDetails(animeUrl: String, animeId: String): AnimeDetails = withContext(Dispatchers.IO) {
        val doc = fetchDocument(animeUrl)

        var title = ""
        var imageUrl = ""
        var coverUrl: String? = null
        var plot = ""
        var score = 0f

        // Prefer the main <video-player> (the one carrying episode data) for metadata.
        val videoPlayer = doc.selectFirst("video-player[episodes_count]")
            ?: doc.selectFirst("video-player")
        if (videoPlayer != null) {
            val animeJson = videoPlayer.attr("anime")
            if (animeJson.isNotBlank()) {
                // Use a depth-aware extractor so we read the TOP-LEVEL title and not a nested
                // related-anime title (the regex extractor skips a null "title" and would otherwise
                // grab a nested one, e.g. showing "One Piece: ...Ganzack" instead of "One Piece").
                // Prefer the Italian title, then English, then the original field.
                title = cleanText(
                    extractTopLevelJsonString(animeJson, "title_it")
                        ?: extractTopLevelJsonString(animeJson, "title_eng")
                        ?: extractTopLevelJsonString(animeJson, "title")
                        ?: ""
                )
                imageUrl = extractTopLevelJsonString(animeJson, "imageurl") ?: ""
                coverUrl = extractTopLevelJsonString(animeJson, "imageurl_cover")
                plot = cleanText(extractTopLevelJsonString(animeJson, "plot") ?: "")
                score = extractTopLevelJsonString(animeJson, "score")?.toFloatOrNull() ?: 0f
            }
        }

        val episodes = parseEpisodesFromPage(doc, animeId, animeUrl).ifEmpty {
            parseEpisodesFromScript(doc, animeId, animeUrl)
        }

        // Real total from the <video-player episodes_count> attribute (falls back to what we parsed).
        val rawCount = videoPlayer?.attr("episodes_count")?.trim().orEmpty()
        val totalEpisodes = rawCount.toIntOrNull()?.coerceAtLeast(episodes.size) ?: episodes.size

        val related = parseRelatedAnime(doc, currentAnimeId = animeId)
        Log.d(TAG, "getAnimeDetails: ${episodes.size} eps (total $totalEpisodes), ${related.size} related")

        AnimeDetails(
            title = title,
            plot = plot,
            imageUrl = imageUrl,
            coverUrl = coverUrl,
            episodes = episodes,
            score = score,
            totalEpisodes = totalEpisodes,
            related = related
        )
    }

    /**
     * Parses the "Anime Correlati" section (other seasons/movies/specials) into a
     * chronologically ordered list. Each entry lives in a <div class="related-item">.
     */
    private fun parseRelatedAnime(doc: Document, currentAnimeId: String): List<RelatedAnime> {
        val yearRegex = """(19|20)\d{2}""".toRegex()
        val items = doc.select("div.related-item").mapNotNull { item ->
            val link = item.selectFirst("a[href*=/anime/]") ?: return@mapNotNull null
            val href = link.attr("href")
            val path = href.substringAfter("/anime/", "").trim('/')
            if (path.isBlank()) return@mapNotNull null

            val id = path.substringBefore("-")
            val slug = path.substringAfter("-", "")
            if (id == currentAnimeId) return@mapNotNull null // skip self

            val title = item.selectFirst("strong.related-anime-title")?.text()?.let { cleanText(it) }
                ?: item.selectFirst(".related-anime-title")?.text()?.let { cleanText(it) }
                ?: return@mapNotNull null
            val imageUrl = item.selectFirst("img")?.attr("src").orEmpty()
            val info = item.selectFirst(".related-info")?.text()?.trim().orEmpty()
            val year = yearRegex.find(info)?.value?.toIntOrNull() ?: 0
            val url = if (href.startsWith("http")) href else "$BASE_URL$href"

            RelatedAnime(
                id = id,
                slug = slug,
                title = title,
                imageUrl = imageUrl,
                url = url,
                info = info,
                year = year
            )
        }
        // De-duplicate by id (some pages list ITA/SUB variants twice) and order chronologically.
        return items
            .distinctBy { it.id }
            .sortedBy { if (it.year == 0) Int.MAX_VALUE else it.year }
    }

    /**
     * Step 1 of the streaming pipeline:
     * Fetches the episode page and extracts the Vixcloud iframe src URL.
     */
    suspend fun extractIframeUrl(episodeUrl: String): String = withContext(Dispatchers.IO) {
        val doc = fetchDocument(episodeUrl)
        extractIframeUrlFromDoc(doc)
    }

    /**
     * Extracts the Vixcloud iframe src URL directly from a pre-loaded Document.
     */
    fun extractIframeUrlFromDoc(doc: Document): String {
        // Strategy 1: Look for embed_url attribute on <video-player> Vue component
        val videoPlayer = doc.selectFirst("video-player")
        if (videoPlayer != null) {
            val embedUrl = videoPlayer.attr("embed_url")
            if (embedUrl.isNotBlank()) {
                Log.d(TAG, "Found embed_url in <video-player>: $embedUrl")
                return embedUrl
            }
        }

        // Strategy 2: Fallback to searching for static iframe#embed in the DOM
        val iframe = doc.selectFirst("iframe#embed")
            ?: doc.selectFirst("iframe[src*='vixcloud']")
            ?: doc.selectFirst("iframe[src*='embed']")
            ?: throw ScrapingException.IframeNotFoundException()

        val src = iframe.attr("src").let { rawSrc ->
            if (rawSrc.startsWith("//")) "https:$rawSrc" else rawSrc
        }

        if (src.isBlank()) {
            throw ScrapingException.IframeNotFoundException()
        }

        Log.d(TAG, "Extracted iframe URL (fallback): $src")
        return src
    }

    // ========== PARSING METHODS ==========

    /**
     * Primary parsing strategy:
     * Extracts anime data from the <archivio records="[{...}]"> Vue component.
     * Jsoup automatically decodes HTML entities in attributes (&quot; -> "),
     * giving us clean JSON to parse.
     */
    private fun parseAnimeFromArchivio(doc: Document): List<Anime> {
        // Strategy 1: <archivio records="..."> tag
        val archivioElement = doc.selectFirst("archivio")
        if (archivioElement != null) {
            val recordsJson = archivioElement.attr("records")
            if (recordsJson.isNotBlank()) {
                Log.d(TAG, "Found <archivio records> attribute, length: ${recordsJson.length}")
                val animeList = parseJsonAnimeArray(recordsJson)
                Log.d(TAG, "Parsed ${animeList.size} anime from archivio records")
                if (animeList.isNotEmpty()) {
                    return animeList
                }
            }
        }

        // Strategy 2: look for any element with records attribute
        val elementsWithRecords = doc.select("[records]")
        for (element in elementsWithRecords) {
            val recordsJson = element.attr("records")
            if (recordsJson.startsWith("[{") && recordsJson.contains("\"title\"")) {
                Log.d(TAG, "Found element with records attribute: <${element.tagName()}>")
                val animeList = parseJsonAnimeArray(recordsJson)
                if (animeList.isNotEmpty()) {
                    return animeList
                }
            }
        }

        // Strategy 3: fallback to script tags
        Log.d(TAG, "No archivio records found, trying script tag fallback")
        return parseAnimeFromScript(doc)
    }

    /**
     * Parses a JSON array of anime objects.
     * The JSON comes from the archivio records attribute (already HTML-decoded by Jsoup).
     *
     * Expected format:
     * [{"id":4354,"title":"Mou Ippon!","imageurl":"https://...","slug":"mou-ippon",...}, ...]
     */
    private fun parseJsonAnimeArray(json: String): List<Anime> {
        val animeList = mutableListOf<Anime>()

        try {
            // Match each top-level JSON object in the array
            // We use a balanced braces approach for nested objects
            var depth = 0
            var start = -1
            var i = 0

            while (i < json.length) {
                val c = json[i]
                when {
                    c == '{' -> {
                        if (depth == 0) start = i
                        depth++
                    }
                    c == '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            val objStr = json.substring(start, i + 1)
                            parseAnimeObject(objStr)?.let { animeList.add(it) }
                            start = -1
                        }
                    }
                    c == '"' -> {
                        // Skip string content (handles escaped quotes)
                        i++
                        while (i < json.length) {
                            if (json[i] == '\\') {
                                i += 2 // skip escaped character
                                continue
                            }
                            if (json[i] == '"') break
                            i++
                        }
                    }
                }
                i++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON array: ${e.message}")
        }

        return animeList
    }

    /**
     * Parses a single anime JSON object.
     */
    private fun parseAnimeObject(json: String): Anime? {
        return try {
            val id = extractJsonValue(json, "id") ?: return null
            // Prefer the Italian title, then English, then the original field.
            val title = extractJsonString(json, "title_it")
                ?: extractJsonString(json, "title_eng")
                ?: extractJsonString(json, "title") ?: return null
            val imageUrl = extractJsonString(json, "imageurl") ?: ""
            val coverUrl = extractJsonString(json, "imageurl_cover")
            val slug = extractJsonString(json, "slug") ?: ""
            val synopsis = extractJsonString(json, "plot") ?: ""
            val status = extractJsonString(json, "status") ?: ""
            val type = extractJsonString(json, "type") ?: ""
            val episodeCount = extractJsonValue(json, "episodes_count")?.toIntOrNull() ?: 0
            val score = extractJsonString(json, "score")?.toFloatOrNull() ?: 0f
            val year = extractJsonString(json, "date")?.take(4)?.toIntOrNull() ?: 0
            val isDub = extractJsonValue(json, "dub") == "1"
            // Only append the "(ITA)" dub marker if the title doesn't already carry it,
            // otherwise some entries end up labelled "... (ITA) (ITA)".
            val cleanTitle = cleanText(title)
            val finalTitle = if (isDub && !cleanTitle.contains("(ITA)", ignoreCase = true)) {
                "$cleanTitle (ITA)"
            } else {
                cleanTitle
            }

            Anime(
                id = id,
                title = finalTitle,
                imageUrl = imageUrl,
                coverUrl = coverUrl,
                synopsis = cleanText(synopsis),
                status = status,
                type = type,
                episodeCount = episodeCount,
                rating = score,
                year = year,
                slug = slug,
                episodeUrl = "$BASE_URL/anime/$id-$slug"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse anime object: ${e.message}")
            null
        }
    }

    /**
     * Parses episodes from the anime detail page.
     * The detail page contains a Vue component (e.g., <video-player>) with episode data.
     */
    private fun parseEpisodesFromPage(doc: Document, animeId: String, animeUrl: String): List<Episode> {
        // Look for elements that contain episode data in their attributes
        val episodeElements = doc.select("[episodes], [video-player]")
        for (element in episodeElements) {
            val episodesJson = element.attr("episodes")
            if (episodesJson.isNotBlank() && episodesJson.startsWith("[")) {
                return parseJsonEpisodesArray(episodesJson, animeId, animeUrl)
            }
        }
        return emptyList()
    }

    /**
     * Fetches a single window of episodes from AnimeUnity's paginated JSON endpoint.
     * Session cookies are attached automatically by the shared cookie jar.
     */
    private fun fetchEpisodesFromApi(
        animeId: String,
        animeUrl: String,
        start: Int,
        end: Int,
        csrfToken: String
    ): List<Episode> {
        val url = "$BASE_URL/info_api/$animeId/1?start_range=$start&end_range=$end"
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", animeUrl)
            .header("X-Requested-With", "XMLHttpRequest")
        if (csrfToken.isNotBlank()) {
            builder.header("X-CSRF-TOKEN", csrfToken)
        }

        val body = httpClient.newCall(builder.build()).execute().use { response ->
            if (response.code != 200) {
                Log.w(TAG, "info_api returned ${response.code} for range $start-$end")
                return emptyList()
            }
            response.body?.string() ?: ""
        }

        Log.d(TAG, "info_api range $start-$end fetched (${body.length} bytes)")
        return parseJsonEpisodesArray(extractEpisodesArray(body), animeId, animeUrl)
    }

    /**
     * Extracts the "episodes":[...] array from the info_api JSON response using a
     * balanced-bracket scan. Falls back to the whole body if the key is absent.
     */
    private fun extractEpisodesArray(json: String): String {
        val keyIndex = json.indexOf("\"episodes\"")
        if (keyIndex == -1) return json
        val arrayStart = json.indexOf('[', keyIndex)
        if (arrayStart == -1) return json

        var depth = 0
        for (i in arrayStart until json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return json.substring(arrayStart, i + 1)
                }
            }
        }
        return json
    }

    /**
     * Fallback: parse episodes from <script> tags.
     */
    private fun parseEpisodesFromScript(doc: Document, animeId: String, animeUrl: String): List<Episode> {
        val scripts = doc.select("script")
        val episodes = mutableListOf<Episode>()
        for (script in scripts) {
            val data = script.data()
            if (data.contains("episodes") || data.contains("episode")) {
                val epRegex = """\{[^{}]*"number"\s*:\s*"?(\d+)"?[^{}]*\}""".toRegex()
                epRegex.findAll(data).forEach { match ->
                    val epNum = match.groupValues[1].toIntOrNull() ?: return@forEach
                    val epId = extractJsonString(match.value, "id") ?: "${animeId}_ep$epNum"
                    episodes.add(
                        Episode(
                            id = epId,
                            number = epNum,
                            url = "$animeUrl/$epId",
                            animeId = animeId
                        )
                    )
                }
            }
        }
        return episodes.sortedBy { it.number }
    }

    /**
     * Parses a JSON array of episode objects.
     */
    private fun parseJsonEpisodesArray(json: String, animeId: String, animeUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val digits = Regex("""\d+""")
        // Split the array into balanced top-level {...} objects so no episode is missed even if
        // an object's fields contain unusual content (the old flat regex could skip entries).
        forEachTopLevelJsonObject(json) { obj ->
            val numStr = extractJsonValue(obj, "number") ?: return@forEachTopLevelJsonObject
            val epNum = digits.find(numStr)?.value?.toIntOrNull() ?: return@forEachTopLevelJsonObject
            val epId = extractJsonValue(obj, "id") ?: "${animeId}_ep$epNum"
            episodes.add(
                Episode(
                    id = epId,
                    number = epNum,
                    url = "$animeUrl/$epId",
                    animeId = animeId
                )
            )
        }
        return episodes.distinctBy { it.number }.sortedBy { it.number }
    }

    // ========== HTTP HELPERS ==========

    private suspend fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", "$BASE_URL/")
            .build()

        val (body, code) = httpClient.newCall(request).execute().use { response ->
            if (response.code == 403) {
                throw ScrapingException.CloudflareBlockedException()
            }
            val b = response.body?.string() ?: throw ScrapingException.NetworkException(
                RuntimeException("Empty response body from $url")
            )
            b to response.code
        }

        Log.d(TAG, "Fetched $url (${body.length} bytes, status=$code)")
        return Jsoup.parse(body, url)
    }

    // ========== JSON PARSING HELPERS ==========

    /**
     * Fallback: parses anime from <script> tags.
     */
    private fun parseAnimeFromScript(doc: Document): List<Anime> {
        val scripts = doc.select("script")
        for (script in scripts) {
            val data = script.data()
            if (data.contains("anime") && (data.contains("\"title\"") || data.contains("\"imageurl\""))) {
                val jsonArrayRegex = """\[\{.*?"title".*?}]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val match = jsonArrayRegex.find(data)
                if (match != null) {
                    return parseJsonAnimeArray(match.value)
                }
            }
        }
        return emptyList()
    }
}

data class EpisodePageDetails(
    val iframeUrl: String,
    val animeTitle: String,
    val animeImageUrl: String,
    val animeSlug: String
)
