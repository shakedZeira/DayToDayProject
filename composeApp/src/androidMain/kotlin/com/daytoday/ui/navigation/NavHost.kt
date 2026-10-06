package com.daytoday.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.daytoday.ui.screen.HomeScreen
import com.daytoday.ui.screen.nba.NbaGameDetailScreen
import com.daytoday.ui.screen.nba.NbaInjuriesScreen
import com.daytoday.ui.screen.nba.NbaNewsScreen
import com.daytoday.ui.screen.nba.NbaScoreboardScreen
import com.daytoday.ui.screen.pdf.PdfMergeScreen
import com.daytoday.ui.screen.settings.SettingsScreen
import com.daytoday.ui.screen.workout.WorkoutActiveScreen
import com.daytoday.ui.screen.workout.WorkoutHistoryScreen
import com.daytoday.ui.screen.workout.WorkoutProgressScreen

@Composable
fun DayTodayNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.NbaScoreboard.route) {
            NbaScoreboardScreen(navController = navController, initialTab = 0)
        }
        composable(
            route = "${Screen.NbaGameDetail.route}/{gameId}",
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId").orEmpty()
            NbaGameDetailScreen(gameId = gameId, navController = navController)
        }
        composable(Screen.NbaNews.route) {
            NbaScoreboardScreen(navController = navController, initialTab = 1)
        }
        composable(Screen.NbaInjuries.route) {
            NbaScoreboardScreen(navController = navController, initialTab = 2)
        }
        composable(Screen.WorkoutActive.route) {
            WorkoutActiveScreen(navController = navController)
        }
        composable(Screen.WorkoutHistory.route) {
            WorkoutHistoryScreen(navController = navController)
        }
        composable(Screen.WorkoutProgress.route) {
            WorkoutProgressScreen(navController = navController)
        }
        composable(Screen.PdfMerge.route) {
            PdfMergeScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
