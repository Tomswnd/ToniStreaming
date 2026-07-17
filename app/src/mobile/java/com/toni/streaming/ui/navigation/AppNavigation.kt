package com.toni.streaming.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.toni.streaming.ui.detail.DetailScreen
import com.toni.streaming.ui.home.HomeScreen
import com.toni.streaming.ui.player.PlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(
                onAnimeClick = { anime ->
                    val encodedUrl = URLEncoder.encode(anime.episodeUrl, "UTF-8")
                    navController.navigate(
                        Screen.Detail.createRoute(anime.id, encodedUrl)
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.StringType },
                navArgument("animeUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
            val animeUrl = URLDecoder.decode(
                backStackEntry.arguments?.getString("animeUrl") ?: "", "UTF-8"
            )
            DetailScreen(
                animeUrl = animeUrl,
                animeId = animeId,
                onEpisodeClick = { episode ->
                    val encodedUrl = URLEncoder.encode(episode.url, "UTF-8")
                    navController.navigate(
                        Screen.Player.createRoute(episode.animeId, episode.id, episode.number, encodedUrl)
                    )
                },
                onBack = { navController.popBackStack() },
                onRelatedClick = { related ->
                    navController.navigate(
                        Screen.Detail.createRoute(related.id, related.url)
                    )
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.StringType },
                navArgument("episodeId") { type = NavType.StringType },
                navArgument("episodeNumber") { type = NavType.IntType },
                navArgument("episodeUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
            val episodeNumber = backStackEntry.arguments?.getInt("episodeNumber") ?: 0
            val episodeUrl = URLDecoder.decode(
                backStackEntry.arguments?.getString("episodeUrl") ?: "", "UTF-8"
            )
            PlayerScreen(
                animeId = animeId,
                episodeId = episodeId,
                episodeUrl = episodeUrl,
                onBack = { navController.popBackStack() },
                episodeNumber = episodeNumber
            )
        }
    }
}
