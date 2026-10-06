package com.daytoday.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.daytoday.ui.navigation.Screen

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

@Composable
fun BottomNavHost(navController: NavController) {
    val items = listOf(
        BottomNavItem(Screen.Home, Icons.Default.Home, "Home"),
        BottomNavItem(Screen.NbaScoreboard, Icons.Default.SportsBasketball, "NBA"),
        BottomNavItem(Screen.WorkoutActive, Icons.Default.FitnessCenter, "Workout"),
        BottomNavItem(Screen.PdfMerge, Icons.Default.PictureAsPdf, "PDF"),
        BottomNavItem(Screen.Settings, Icons.Default.Settings, "Settings")
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ""

    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            val route = item.screen.route
            val selected = currentRoute == route || (
                item.screen == Screen.NbaScoreboard && (
                    currentRoute == Screen.NbaNews.route ||
                    currentRoute == Screen.NbaInjuries.route ||
                    currentRoute.startsWith(Screen.NbaGameDetail.route)
                )
            )
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 12.sp) }
            )
        }
    }
}