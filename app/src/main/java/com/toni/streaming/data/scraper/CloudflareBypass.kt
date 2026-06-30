package com.toni.streaming.data.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume

/**
 * Uses an invisible WebView to solve the Cloudflare JavaScript challenge.
 * Extracts the cf_clearance cookie and injects it into the OkHttpClient.
 */
class CloudflareBypass(private val context: Context) {

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val CF_CLEARANCE = "cf_clearance"
    }

    /**
     * Launches an invisible WebView to navigate to the target URL.
     * Waits for Cloudflare to resolve (or page to load for other domains), then extracts the cookies.
     * Must be called from a coroutine — will suspend until cookies are obtained or timeout.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun obtainClearanceCookies(targetUrl: String = AnimeUnityScraper.BASE_URL): List<Cookie> = withTimeout(TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = AnimeUnityScraper.USER_AGENT
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val currentUrl = url ?: targetUrl
                        val isVixcloud = currentUrl.contains("vixcloud.co")
                        
                        // Use base domain for vixcloud.co cookies to make sure they are resolved correctly
                        val cookieUrl = if (isVixcloud) "https://vixcloud.co" else currentUrl
                        val cookieString = CookieManager.getInstance().getCookie(cookieUrl)

                        val hasCf = cookieString != null && cookieString.contains(CF_CLEARANCE)

                        // For vixcloud, we resume as soon as the page is loaded and we have retrieved whatever cookies exist (even if empty, to avoid timeouts)
                        if (hasCf || isVixcloud) {
                            val cookies = if (cookieString != null && cookieString.isNotBlank()) {
                                parseCookies(cookieString, cookieUrl)
                            } else {
                                emptyList()
                            }
                            webView.destroy()
                            if (continuation.isActive) {
                                continuation.resume(cookies)
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = false
                }

                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post {
                        webView.stopLoading()
                        webView.destroy()
                    }
                }

                webView.loadUrl(targetUrl)
            }
        }
    }

    /**
     * Parses a raw cookie string from CookieManager into OkHttp Cookie objects.
     */
    private fun parseCookies(cookieString: String, url: String): List<Cookie> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptyList()
        return cookieString.split(";").mapNotNull { raw ->
            val trimmed = raw.trim()
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                Cookie.Builder()
                    .domain(httpUrl.host)
                    .name(parts[0].trim())
                    .value(parts[1].trim())
                    .build()
            } else null
        }
    }
}
