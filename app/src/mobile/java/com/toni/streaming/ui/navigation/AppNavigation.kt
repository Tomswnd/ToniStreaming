package com.toni.streaming.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.toni.streaming.data.repository.AnimeRepository
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
    val context = LocalContext.current
    val repository = remember { AnimeRepository(context) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(
                repository = repository,
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
                repository = repository,
                onEpisodeClick = { episode ->
                    val encodedUrl = URLEncoder.encode(episode.url, "UTF-8")
                    navController.navigate(
                        Screen.Player.createRoute(episode.animeId, episode.id, encodedUrl)
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.StringType },
                navArgument("episodeId") { type = NavType.StringType },
                navArgument("episodeUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
            val episodeUrl = URLDecoder.decode(
                backStackEntry.arguments?.getString("episodeUrl") ?: "", "UTF-8"
            )
            PlayerScreen(
                animeId = animeId,
                episodeId = episodeId,
                episodeUrl = episodeUrl,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
