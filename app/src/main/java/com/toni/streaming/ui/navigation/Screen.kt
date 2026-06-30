package com.toni.streaming.ui.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Detail : Screen("detail/{animeId}/{animeUrl}") {
        fun createRoute(animeId: String, animeUrl: String): String {
            return "detail/${animeId}/${java.net.URLEncoder.encode(animeUrl, "UTF-8")}"
        }
    }
    data object Player : Screen("player/{animeId}/{episodeId}/{episodeUrl}") {
        fun createRoute(animeId: String, episodeId: String, episodeUrl: String): String {
            return "player/${animeId}/${episodeId}/${java.net.URLEncoder.encode(episodeUrl, "UTF-8")}"
        }
    }
}
