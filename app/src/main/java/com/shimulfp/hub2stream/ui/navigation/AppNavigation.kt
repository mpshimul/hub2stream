package com.shimulfp.hub2stream.ui.navigation

import android.net.Uri
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shimulfp.hub2stream.ui.screens.*
import kotlinx.coroutines.delay

sealed class Screen(val route: String) {
    object Home : Screen("home")

    object MovieDetail : Screen("movie/{slug}/{resumePosition}") {
        fun pass(slug: String, resumePosition: Long = 0L) = "movie/$slug/$resumePosition"
    }

    object SeriesDetail : Screen("series/{slug}/{season}/{episode}/{resumePosition}?skipAutoPlay={skipAutoPlay}") {
        fun pass(slug: String, season: Int = 0, episode: Int = 0, resumePosition: Long = 0L, skipAutoPlay: Boolean = false) =
            "series/$slug/$season/$episode/$resumePosition?skipAutoPlay=${if (skipAutoPlay) "1" else "0"}"
    }

    object Player : Screen("player?url={url}&title={title}&episodesJson={episodesJson}&startIndex={startIndex}&isLive={isLive}&slug={slug}&poster={poster}&type={type}&season={season}&episode={episode}&resumePosition={resumePosition}") {
        fun pass(
            url: String,
            title: String,
            episodesJson: String = "",
            startIndex: Int = 0,
            isLive: Boolean = false,
            slug: String = "",
            poster: String = "",
            type: String = "",
            season: Int = 0,
            episode: Int = 0,
            resumePosition: Long = 0L
        ): String {
            android.util.Log.d("Screen.Player.pass", "Building Player route - url: '$url', title: '$title', slug: '$slug', type: '$type', resumePosition: $resumePosition")
            android.util.Log.d("Screen.Player.pass", "URL isBlank: ${url.isBlank()}, url.length: ${url.length}")

            // Log stack trace to see where this is being called from
            if (url.isBlank()) {
                android.util.Log.e("Screen.Player.pass", "❌ CRITICAL: Called with EMPTY URL!")
                android.util.Log.e("Screen.Player.pass", "❌ Stack trace:")
                Thread.currentThread().stackTrace.take(15).forEach {
                    android.util.Log.e("Screen.Player.pass", "   at $it")
                }
            }

            val result = buildString {
                append("player?url=${Uri.encode(url)}&title=${Uri.encode(title)}")
                if (episodesJson.isNotBlank()) append("&episodesJson=${Uri.encode(episodesJson)}")
                append("&startIndex=$startIndex&isLive=${if (isLive) "1" else "0"}")
                if (slug.isNotBlank()) append("&slug=${Uri.encode(slug)}")
                if (poster.isNotBlank()) append("&poster=${Uri.encode(poster)}")
                if (type.isNotBlank()) append("&type=${Uri.encode(type)}")
                if (season > 0) append("&season=$season")
                if (episode > 0) append("&episode=$episode")
                if (resumePosition > 0) append("&resumePosition=$resumePosition")
            }
            android.util.Log.d("Screen.Player.pass", "Result route: $result")
            return result
        }
    }

    object LivePlayer : Screen("liveplayer?url={url}&title={title}&channelsJson={channelsJson}&startIndex={startIndex}") {
        fun pass(url: String, title: String, channelsJson: String = "", startIndex: Int = 0): String {
            return "liveplayer?url=${Uri.encode(url)}&title=${Uri.encode(title)}&channelsJson=${Uri.encode(channelsJson)}&startIndex=$startIndex"
        }
    }

    object LiveTV : Screen("live_tv")
    object LiveSports : Screen("live_sports")
    object FifaWorldCup : Screen("fifa_world_cup")

    object Search : Screen("search")

    object Category : Screen("category/{title}/{isSeries}/{category}") {
        fun pass(title: String, isSeries: Boolean, category: String? = null): String {
            val categoryParam = category?.let { Uri.encode(it) } ?: "null"
            return "category/${Uri.encode(title)}/${if (isSeries) "1" else "0"}/$categoryParam"
        }
    }
}

@Composable
fun AppNavigation(showExitDialog: Boolean = false, onExitDialogDismiss: () -> Unit = {}) {
    val navController = rememberNavController()
    val exitConfirmFocusRequester = remember { FocusRequester() }

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }

        composable(Screen.Search.route) {
            SearchScreen(navController)
        }

        composable(
            route = Screen.MovieDetail.route,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("resumePosition") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            val resumePosition = backStackEntry.arguments?.getLong("resumePosition") ?: 0L
            MovieDetailScreen(navController, slug, resumePosition)
        }
        composable(
            route = "series/{slug}/{season}/{episode}/{resumePosition}",
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("season") { type = NavType.IntType; defaultValue = 0 },
                navArgument("episode") { type = NavType.IntType; defaultValue = 0 },
                navArgument("resumePosition") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            val season = backStackEntry.arguments?.getInt("season") ?: 0
            val episode = backStackEntry.arguments?.getInt("episode") ?: 0
            val resumePosition = backStackEntry.arguments?.getLong("resumePosition") ?: 0L
            android.util.Log.d("AppNavigation", "SeriesDetail - slug: $slug, season: $season, episode: $episode")
            SeriesDetailScreen(navController, slug, season, episode, resumePosition)
        }
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("episodesJson") { type = NavType.StringType; nullable = true },
                navArgument("startIndex") { type = NavType.IntType; defaultValue = 0 },
                navArgument("isLive") { type = NavType.StringType; defaultValue = "0" },
                navArgument("slug") { type = NavType.StringType; nullable = true },
                navArgument("poster") { type = NavType.StringType; nullable = true },
                navArgument("type") { type = NavType.StringType; nullable = true },
                navArgument("season") { type = NavType.IntType; defaultValue = 0 },
                navArgument("episode") { type = NavType.IntType; defaultValue = 0 },
                navArgument("resumePosition") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")
            val title = backStackEntry.arguments?.getString("title")
            val slug = backStackEntry.arguments?.getString("slug")
            android.util.Log.d("AppNavigation", "=== PlayerScreen route received ===")
            android.util.Log.d("AppNavigation", "url: '$url', title: '$title', slug: '$slug'")
            android.util.Log.d("AppNavigation", "url is null: ${url == null}, url is blank: ${url.isNullOrBlank()}")

            // If URL is empty, show error message instead of crashing
            if (url.isNullOrBlank()) {
                android.util.Log.e("AppNavigation", "❌ ERROR: PlayerScreen route called with empty URL! title: $title, slug: $slug")
                android.util.Log.e("AppNavigation", "❌ This means MovieDetailScreen navigated without a valid streamUrl")
                android.util.Log.e("AppNavigation", "❌ Full backStackEntry: ${backStackEntry.destination.route}")
                Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Stream URL not available",
                            color = Color(0xFFCC4444),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This movie's streaming information could not be loaded. The content may have been removed or is temporarily unavailable.",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8C200))
                        ) { Text("Go Back", color = Color.Black) }
                    }
                }
                return@composable
            }
            
            val episodesJson = backStackEntry.arguments?.getString("episodesJson") ?: ""
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            val isLive = backStackEntry.arguments?.getString("isLive")?.toBoolean() ?: false
            val poster = backStackEntry.arguments?.getString("poster") ?: ""
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val season = backStackEntry.arguments?.getInt("season") ?: 0
            val episode = backStackEntry.arguments?.getInt("episode") ?: 0
            val resumePosition = backStackEntry.arguments?.getLong("resumePosition") ?: 0L
            PlayerScreen(navController, url!!, title ?: "Video", episodesJson, startIndex, isLive, slug ?: "", poster, type, season, episode, resumePosition)
        }
        composable(
            route = Screen.LivePlayer.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("channelsJson") { type = NavType.StringType; nullable = true },
                navArgument("startIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: return@composable
            val title = backStackEntry.arguments?.getString("title") ?: "Live TV"
            val channelsJson = backStackEntry.arguments?.getString("channelsJson") ?: ""
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            LivePlayerScreen(navController, url, title, channelsJson, startIndex)
        }
        composable(Screen.LiveTV.route) {
            LiveTVScreen(navController)
        }
        composable(Screen.LiveSports.route) {
            LiveSportsScreen(navController)
        }
        composable(Screen.FifaWorldCup.route) {
            FifaWorldCupCategoryScreen(navController)
        }
        composable(
            route = Screen.Category.route,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("isSeries") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: return@composable
            val isSeries = backStackEntry.arguments?.getString("isSeries")?.toBoolean() ?: false
            val category = backStackEntry.arguments?.getString("category")
            CategoryScreen(navController, title, isSeries, category)
        }
    }

    // Exit confirmation dialog with proper focus navigation
    if (showExitDialog) {
        val cancelFocusRequester = remember { FocusRequester() }

        LaunchedEffect(showExitDialog) {
            try {
                delay(50)
                exitConfirmFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }

        AlertDialog(
            onDismissRequest = onExitDialogDismiss,
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onExitDialogDismiss()
                        // Exit the app
                        Process.killProcess(Process.myPid())
                    },
                    modifier = Modifier
                        .focusRequester(exitConfirmFocusRequester)
                        .focusProperties {
                            left = cancelFocusRequester
                            right = cancelFocusRequester
                        }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onExitDialogDismiss,
                    modifier = Modifier
                        .focusRequester(cancelFocusRequester)
                        .focusProperties {
                            left = exitConfirmFocusRequester
                            right = exitConfirmFocusRequester
                        }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}