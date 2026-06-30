package com.toni.streaming.data.scraper

import android.util.Log
import com.toni.streaming.data.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class VixcloudExtractor(private val httpClient: OkHttpClient) {

    companion object {
        const val VIXCLOUD_ORIGIN = "https://vixcloud.co"
        const val VIXCLOUD_REFERER = "https://vixcloud.co/"
    }

    /**
     * Step 2 of the streaming pipeline:
     * Fetches the Vixcloud embed page and extracts the .m3u8 playlist URL.
     *
     * The embed page contains JavaScript that initializes the video player
     * with the HLS manifest URL. We parse this from the response body.
     */
    suspend fun extractM3u8Url(embedUrl: String): StreamInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", AnimeUnityScraper.USER_AGENT)
            .header("Referer", "${AnimeUnityScraper.BASE_URL}/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Origin", AnimeUnityScraper.BASE_URL)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw ScrapingException.NetworkException(
            RuntimeException("Empty response from Vixcloud embed")
        )
        response.close()

        // Search for the playlist URL using the new strategy
        val m3u8Url = findPlaylistUrl(responseBody)
            ?: throw ScrapingException.M3u8NotFoundException()

        StreamInfo(
            m3u8Url = m3u8Url,
            referer = embedUrl,
            userAgent = AnimeUnityScraper.USER_AGENT,
            headers = mapOf(
                "Referer" to embedUrl,
                "Origin" to VIXCLOUD_ORIGIN,
                "User-Agent" to AnimeUnityScraper.USER_AGENT
            )
        )
    }

    /**
     * Tries multiple strategies to find the playlist or video URL in the Vixcloud response.
     */
    private fun findPlaylistUrl(body: String): String? {
        // Strategy 1: Parse direct download URL (.mp4 - highly stable, bypasses Cloudflare)
        parseDownloadUrl(body)?.let { return it }

        // Strategy 2: Parse from window.masterPlaylist (HLS)
        parseMasterPlaylist(body)?.let { return it }

        // Strategy 3: Direct .m3u8 URL or patterns containing .m3u8
        findM3u8Url(body)?.let { return it }

        // Strategy 4: Parse from window.streams list (Fallback)
        parseStreamsFallback(body)?.let { return it }

        return null
    }

    /**
     * Parses the direct MP4 download URL if available.
     */
    private fun parseDownloadUrl(body: String): String? {
        try {
            val downloadRegex = """window\.downloadUrl\s*=\s*['"](https?://[^'"]+)['"]""".toRegex()
            downloadRegex.find(body)?.let { match ->
                val url = match.groupValues[1]
                Log.d("VixcloudExtractor", "Found direct download MP4 URL: $url")
                return url
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    /**
     * Parses the master playlist URL and token/expires query params.
     */
    private fun parseMasterPlaylist(body: String): String? {
        try {
            val urlRegex = """url\s*:\s*['"](https?://[^'"]+)['"]""".toRegex()
            val tokenRegex = """['"]token['"]\s*:\s*['"]([^'"]+)['"]""".toRegex()
            val expiresRegex = """['"]expires['"]\s*:\s*['"]([^'"]+)['"]""".toRegex()

            val url = urlRegex.find(body)?.groupValues?.get(1) ?: return null
            val token = tokenRegex.find(body)?.groupValues?.get(1) ?: return null
            val expires = expiresRegex.find(body)?.groupValues?.get(1) ?: return null

            return "$url?token=$token&expires=$expires"
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parses the stream URL from window.streams list.
     */
    private fun parseStreamsFallback(body: String): String? {
        try {
            val streamsRegex = """"url"\s*:\s*"([^"]+)"""".toRegex()
            streamsRegex.find(body)?.let { match ->
                val rawUrl = match.groupValues[1]
                return rawUrl.replace("\\/", "/")
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    /**
     * Legacy search for direct .m3u8 references.
     */
    private fun findM3u8Url(body: String): String? {
        // Strategy 1: Direct .m3u8 URL
        val m3u8Regex = """(https?://[^"'\s]+\.m3u8[^"'\s]*)""".toRegex()
        m3u8Regex.find(body)?.let { return it.groupValues[1] }

        // Strategy 2: window.video or window.masterPlaylist pattern
        val windowVideoRegex = """(?:window\.video|masterPlaylist|file)\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex()
        windowVideoRegex.find(body)?.let { return it.groupValues[1] }

        // Strategy 3: JSON-like object with url field containing m3u8
        val jsonUrlRegex = """["']?(?:url|src|file|source)["']?\s*[:=]\s*["'](https?://[^"']+\.m3u8[^"']*)["']""".toRegex()
        jsonUrlRegex.find(body)?.let { return it.groupValues[1] }

        // Strategy 4: Look in a params/config block
        val paramsRegex = """params\s*=\s*\{[^}]*["']?url["']?\s*:\s*["']([^"']+)["']""".toRegex(RegexOption.DOT_MATCHES_ALL)
        paramsRegex.find(body)?.let {
            val url = it.groupValues[1]
            if (url.contains(".m3u8")) return url
        }

        return null
    }
}
