package com.toni.streaming.data.scraper

sealed class ScrapingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class CloudflareBlockedException : ScrapingException("Cloudflare challenge detected. Retrying with WebView bypass.")
    class IframeNotFoundException : ScrapingException("Iframe #embed not found in the page DOM.")
    class M3u8NotFoundException : ScrapingException("Could not extract .m3u8 URL from Vixcloud response.")
    class TokenExpiredException : ScrapingException("The streaming token has expired. Please refresh.")
    class NetworkException(cause: Throwable) : ScrapingException("Network error: ${cause.message}", cause)
    class ParsingException(details: String) : ScrapingException("Parsing error: $details")
}
