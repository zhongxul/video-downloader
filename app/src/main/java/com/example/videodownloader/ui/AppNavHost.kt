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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.ui.screen.detail.DownloadDetailScreen
import com.example.videodownloader.ui.screen.detail.DownloadDetailViewModel
import com.example.videodownloader.ui.screen.detail.DownloadDetailViewModelFactory
import com.example.videodownloader.ui.screen.history.CompletedScreen
import com.example.videodownloader.ui.screen.history.CompletedViewModel
import com.example.videodownloader.ui.screen.history.CompletedViewModelFactory
import com.example.videodownloader.ui.screen.history.HistoryScreen
import com.example.videodownloader.ui.screen.history.HistoryViewModel
import com.example.videodownloader.ui.screen.history.HistoryViewModelFactory
import com.example.videodownloader.ui.screen.home.HomeScreen
import com.example.videodownloader.ui.screen.home.HomeViewModel
import com.example.videodownloader.ui.screen.home.HomeViewModelFactory
import com.example.videodownloader.ui.screen.settings.XLoginWebViewScreen
import com.example.videodownloader.ui.screen.settings.XSettingsScreen
import com.example.videodownloader.ui.screen.settings.XSettingsViewModel
import com.example.videodownloader.ui.screen.settings.XSettingsViewModelFactory

private enum class AppRoute(
    val route: String,
    val label: String,
) {
    HOME("home", "下载"),
    HISTORY("history", "历史"),
    SETTINGS("settings", "X 设置"),
    X_LOGIN("x_login", "X 登录"),
    COMPLETED("completed", "已完成"),
    DETAIL("detail/{taskId}", "详情"),
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
                                    Icon(Icons.Outlined.Download, contentDescription = item.label)
                                } else {
                                    Icon(Icons.Outlined.History, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label) },
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
                )
            }

            composable(AppRoute.HISTORY.route) {
                val vm: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(container))
                HistoryScreen(
                    viewModel = vm,
                    onOpenDetail = { taskId -> navController.navigate("detail/$taskId") },
                    onOpenCompleted = { navController.navigate(AppRoute.COMPLETED.route) },
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

            composable(AppRoute.COMPLETED.route) {
                val vm: CompletedViewModel = viewModel(factory = CompletedViewModelFactory(container))
                CompletedScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
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
