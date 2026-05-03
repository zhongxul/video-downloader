package com.example.videodownloader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.videodownloader.R
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.ui.screen.detail.DownloadDetailScreen
import com.example.videodownloader.ui.screen.detail.DownloadDetailViewModel
import com.example.videodownloader.ui.screen.detail.DownloadDetailViewModelFactory
import com.example.videodownloader.ui.screen.history.HistoryScreen
import com.example.videodownloader.ui.screen.history.HistoryViewModel
import com.example.videodownloader.ui.screen.history.HistoryViewModelFactory
import com.example.videodownloader.ui.screen.home.HomeScreen
import com.example.videodownloader.ui.screen.home.HomeViewModel
import com.example.videodownloader.ui.screen.home.HomeViewModelFactory
import com.example.videodownloader.ui.screen.result.ParseResultScreen
import com.example.videodownloader.ui.screen.result.ParseResultViewModel
import com.example.videodownloader.ui.screen.result.ParseResultViewModelFactory
import com.example.videodownloader.ui.screen.settings.XLoginWebViewScreen
import com.example.videodownloader.ui.screen.settings.XSettingsScreen
import com.example.videodownloader.ui.screen.settings.XSettingsViewModel
import com.example.videodownloader.ui.screen.settings.XSettingsViewModelFactory

private enum class AppRoute(
    val route: String,
    val labelRes: Int,
) {
    HOME("home", R.string.nav_home),
    PARSE_RESULT("parse_result", R.string.nav_parse_result),
    HISTORY("history", R.string.nav_history),
    SETTINGS("settings", R.string.nav_settings),
    X_LOGIN("x_login", R.string.nav_x_login),
    DETAIL("detail/{taskId}", R.string.nav_detail),
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val bottomItems = listOf(AppRoute.HOME, AppRoute.HISTORY)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute == AppRoute.HOME.route || currentRoute == AppRoute.HISTORY.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Transparent) {
                    bottomItems.forEach { item ->
                        val label = stringResource(item.labelRes)
                        NavigationBarItem(
                            selected = item.route == currentRoute,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (item == AppRoute.HOME) {
                                    Icon(Icons.Outlined.Download, contentDescription = label)
                                } else {
                                    Icon(Icons.Outlined.History, contentDescription = label)
                                }
                            },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppRoute.HOME.route) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(container))
                HomeScreen(
                    viewModel = vm,
                    onOpenXSettings = { navController.navigate(AppRoute.SETTINGS.route) },
                    onOpenParseResult = { navController.navigate(AppRoute.PARSE_RESULT.route) },
                )
            }

            composable(AppRoute.PARSE_RESULT.route) {
                val vm: ParseResultViewModel = viewModel(factory = ParseResultViewModelFactory(container))
                ParseResultScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                )
            }

            composable(AppRoute.HISTORY.route) {
                val vm: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(container))
                HistoryScreen(
                    viewModel = vm,
                    onOpenDetail = { taskId -> navController.navigate("detail/$taskId") },
                )
            }

            composable(AppRoute.SETTINGS.route) {
                val vm: XSettingsViewModel = viewModel(factory = XSettingsViewModelFactory(container))
                XSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                    onOpenLoginWebView = { navController.navigate(AppRoute.X_LOGIN.route) },
                )
            }

            composable(AppRoute.X_LOGIN.route) {
                val vm: XSettingsViewModel = viewModel(factory = XSettingsViewModelFactory(container))
                XLoginWebViewScreen(
                    onBack = { navController.navigateUp() },
                    onCookieCaptured = { rawCookie ->
                        vm.importCookieFromWeb(rawCookie)
                        navController.navigateUp()
                    },
                )
            }

            composable(
                route = AppRoute.DETAIL.route,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) { entry ->
                val taskId = entry.arguments?.getString("taskId").orEmpty()
                val vm: DownloadDetailViewModel = viewModel(
                    factory = DownloadDetailViewModelFactory(container, taskId),
                )
                DownloadDetailScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                )
            }
        }
    }
}
