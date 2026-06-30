# ProGuard rules for ToniStreaming

# Keep Jsoup
-keeppackagenames org.jsoup.nodes

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Room entities
-keep class com.toni.streaming.data.local.** { *; }

# Keep data models
-keep class com.toni.streaming.data.model.** { *; }
