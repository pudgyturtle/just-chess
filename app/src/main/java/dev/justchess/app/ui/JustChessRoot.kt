package dev.justchess.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.justchess.app.GameViewModel
import dev.justchess.app.ui.about.AboutScreen
import dev.justchess.app.ui.history.HistoryScreen
import dev.justchess.app.ui.history.ReplayScreen
import dev.justchess.app.ui.play.PlayScreen
import dev.justchess.app.ui.profile.ProfileScreen

@Composable
fun JustChessRoot(vm: GameViewModel = viewModel()) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route ?: "play"
    val hideBar = route.startsWith("replay") || route == "about"
    Scaffold(
        bottomBar = {
            if (!hideBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == "play",
                        onClick = { nav.navigate("play") { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.SportsEsports, contentDescription = "Play") },
                        label = { Text("Play") },
                    )
                    NavigationBarItem(
                        selected = route == "history",
                        onClick = { nav.navigate("history") { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
                        label = { Text("History") },
                    )
                    NavigationBarItem(
                        selected = route == "profile",
                        onClick = { nav.navigate("profile") { launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.Person, contentDescription = "You") },
                        label = { Text("You") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "play",
            modifier = Modifier.padding(padding),
        ) {
            composable("play") { PlayScreen(vm) }
            composable("history") { HistoryScreen(vm, onOpen = { id -> nav.navigate("replay/$id") }) }
            composable("replay/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ReplayScreen(vm, id, onBack = { nav.popBackStack() })
            }
            composable("profile") { ProfileScreen(vm, onAbout = { nav.navigate("about") }) }
            composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
