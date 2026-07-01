package com.toni.streaming.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.ui.detail.DetailScreen
import com.toni.streaming.ui.home.HomeScreen
import com.toni.streaming.ui.player.PlayerScreen
import java.net.URLDecoder

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = AnimeRepository(context)

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                repository = repository,
                onAnimeClick = { anime ->
                    navController.navigate(
                        Screen.Detail.createRoute(anime.id, anime.episodeUrl)
                    )
                }
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
                animeId = animeId,
                animeUrl = animeUrl,
                repository = repository,
                onEpisodeClick = { episode ->
                    navController.navigate(
                        Screen.Player.createRoute(animeId, episode.id, episode.number, episode.url)
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
                repository = repository,
                onBack = { navController.popBackStack() },
                episodeNumber = episodeNumber
            )
        }
    }
}
