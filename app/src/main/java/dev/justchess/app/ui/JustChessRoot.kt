package dev.justchess.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
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
import dev.justchess.app.ui.report.AnalyzeGameScreen
import dev.justchess.app.ui.report.ReportScreen

@Composable
fun JustChessRoot(vm: GameViewModel = viewModel()) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route ?: "home"
    val hideBar = route == "play" || route.startsWith("replay") || route.startsWith("report") || route.startsWith("analyze") || route == "about"
    Scaffold(
        bottomBar = {
            if (!hideBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == "home",
                        onClick = { nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = route == "history",
                        onClick = { nav.navigate("history") { popUpTo("home") { inclusive = false }; launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
                        label = { Text("History") },
                    )
                    NavigationBarItem(
                        selected = route == "profile",
                        onClick = { nav.navigate("profile") { popUpTo("home") { inclusive = false }; launchSingleTop = true } },
                        icon = { Icon(Icons.Outlined.Person, contentDescription = "You") },
                        label = { Text("You") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onNewGame = {
                        nav.navigate("play") { launchSingleTop = true }
                        vm.openNewGameSheet()
                    },
                    onHistory = { nav.navigate("history") { launchSingleTop = true } },
                    onProfile = { nav.navigate("profile") { launchSingleTop = true } },
                    onAbout = { nav.navigate("about") { launchSingleTop = true } },
                )
            }
            composable("play") {
                PlayScreen(
                    vm,
                    onReport = { id -> nav.navigate("report/$id") },
                    onHome = { nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true } },
                )
            }
            composable("history") { HistoryScreen(vm, onOpen = { id -> nav.navigate("replay/$id") }) }
            composable("report/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ReportScreen(vm, id, onBack = { nav.popBackStack() }, onAnalyzeGame = { nav.navigate("analyze/$id") }, onHome = { nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true } })
            }
            composable("analyze/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                AnalyzeGameScreen(vm, id, onBack = { nav.popBackStack() }, onHome = { nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true } })
            }
            composable("replay/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ReplayScreen(vm, id, onBack = { nav.popBackStack() }, onReport = { nav.navigate("report/$id") })
            }
            composable("profile") { ProfileScreen(vm, onAbout = { nav.navigate("about") }) }
            composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
