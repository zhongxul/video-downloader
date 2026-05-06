package com.example.videodownloader.ui.redesign.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.ui.redesign.detail.DetailScreen
import com.example.videodownloader.ui.redesign.detail.DetailViewModelFactory
import com.example.videodownloader.ui.redesign.download.DownloadScreen
import com.example.videodownloader.ui.redesign.download.DownloadViewModelFactory
import com.example.videodownloader.ui.redesign.library.LibraryScreen
import com.example.videodownloader.ui.redesign.library.LibraryViewModelFactory
import com.example.videodownloader.ui.redesign.parse_result.ParseResultScreen
import com.example.videodownloader.ui.redesign.parse_result.ParseResultViewModelFactory
import com.example.videodownloader.ui.redesign.profile.ProfileScreen
import com.example.videodownloader.ui.redesign.profile.ProfileViewModel
import com.example.videodownloader.ui.redesign.profile.ProfileViewModelFactory
import com.example.videodownloader.ui.redesign.theme.AppDesignTheme
import com.example.videodownloader.ui.screen.settings.DouyinSettingsScreen
import com.example.videodownloader.ui.screen.settings.DouyinSettingsViewModel
import com.example.videodownloader.ui.screen.settings.DouyinSettingsViewModelFactory
import com.example.videodownloader.ui.screen.settings.XLoginWebViewScreen
import com.example.videodownloader.ui.screen.settings.XSettingsScreen
import com.example.videodownloader.ui.screen.settings.XSettingsViewModel
import com.example.videodownloader.ui.screen.settings.XSettingsViewModelFactory

object AppRoutes {
    const val DOWNLOAD = "download"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val PARSE_RESULT = "parse_result/{parseRecordId}"
    const val DETAIL = "detail/{taskId}?successOnly={successOnly}"
    const val X_SETTINGS = "x_settings"
    const val X_LOGIN = "x_login"
    const val DOUYIN_SETTINGS = "douyin_settings"
    const val DOUYIN_LOGIN = "douyin_login"

    fun parseResult(parseRecordId: String) = "parse_result/$parseRecordId"
    fun detail(taskId: String, successOnly: Boolean = false) = "detail/$taskId?successOnly=$successOnly"
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(AppRoutes.DOWNLOAD) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun RedesignNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    AppDesignTheme {
        NavHost(
            navController = navController,
            startDestination = AppRoutes.DOWNLOAD,
        ) {
            composable(AppRoutes.DOWNLOAD) {
                val vm: com.example.videodownloader.ui.redesign.download.DownloadViewModel = viewModel(
                    factory = DownloadViewModelFactory(container),
                )
                DownloadScreen(
                    viewModel = vm,
                    onNavigateToLibrary = {
                        navController.navigateTopLevel(AppRoutes.LIBRARY)
                    },
                    onNavigateToProfile = {
                        navController.navigateTopLevel(AppRoutes.PROFILE)
                    },
                    onNavigateToParseResult = { parseRecordId ->
                        navController.navigate(AppRoutes.parseResult(parseRecordId))
                    },
                )
            }

            composable(AppRoutes.LIBRARY) {
                val vm: com.example.videodownloader.ui.redesign.library.LibraryViewModel = viewModel(
                    factory = LibraryViewModelFactory(container),
                )
                LibraryScreen(
                    viewModel = vm,
                    onNavigateToDownload = {
                        navController.navigateTopLevel(AppRoutes.DOWNLOAD)
                    },
                    onNavigateToProfile = {
                        navController.navigateTopLevel(AppRoutes.PROFILE)
                    },
                    onNavigateToDetail = { taskId, successOnly ->
                        navController.navigate(AppRoutes.detail(taskId, successOnly))
                    },
                )
            }

            composable(AppRoutes.PROFILE) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(container))
                ProfileScreen(
                    viewModel = vm,
                    onNavigateToDownload = {
                        navController.navigateTopLevel(AppRoutes.DOWNLOAD)
                    },
                    onNavigateToLibrary = {
                        navController.navigateTopLevel(AppRoutes.LIBRARY)
                    },
                    onNavigateToXCookieSettings = {
                        navController.navigate(AppRoutes.X_SETTINGS)
                    },
                    onNavigateToDouyinCookieSettings = {
                        navController.navigate(AppRoutes.DOUYIN_SETTINGS)
                    },
                )
            }

            composable(
                route = AppRoutes.PARSE_RESULT,
                arguments = listOf(navArgument("parseRecordId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val parseRecordId = backStackEntry.arguments?.getString("parseRecordId").orEmpty()
                val vm: com.example.videodownloader.ui.redesign.parse_result.ParseResultViewModel = viewModel(
                    factory = ParseResultViewModelFactory(container, parseRecordId),
                )
                ParseResultScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = AppRoutes.DETAIL,
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType },
                    navArgument("successOnly") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId").orEmpty()
                val successOnly = backStackEntry.arguments?.getBoolean("successOnly") ?: false
                val vm: com.example.videodownloader.ui.redesign.detail.DetailViewModel = viewModel(
                    factory = DetailViewModelFactory(container, taskId, successOnly),
                )
                DetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AppRoutes.X_SETTINGS) {
                val vm: XSettingsViewModel = viewModel(factory = XSettingsViewModelFactory(container))
                XSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                    onOpenLoginWebView = { navController.navigate(AppRoutes.X_LOGIN) },
                )
            }

            composable(AppRoutes.X_LOGIN) {
                val vm: XSettingsViewModel = viewModel(factory = XSettingsViewModelFactory(container))
                XLoginWebViewScreen(
                    onBack = { navController.navigateUp() },
                    onCookieCaptured = { rawCookie ->
                        vm.importCookieFromWeb(rawCookie)
                        navController.navigateUp()
                    },
                )
            }

            composable(AppRoutes.DOUYIN_SETTINGS) {
                val vm: DouyinSettingsViewModel = viewModel(factory = DouyinSettingsViewModelFactory(container))
                DouyinSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                    onOpenLoginWebView = { navController.navigate(AppRoutes.DOUYIN_LOGIN) },
                )
            }

            composable(AppRoutes.DOUYIN_LOGIN) {
                val vm: DouyinSettingsViewModel = viewModel(factory = DouyinSettingsViewModelFactory(container))
                XLoginWebViewScreen(
                    onBack = { navController.navigateUp() },
                    onCookieCaptured = { rawCookie ->
                        vm.importCookieFromWeb(rawCookie)
                        navController.navigateUp()
                    },
                    title = "抖音登录",
                    subtitle = "登录抖音后点右上角保存 Cookie，用于图文详情解析。",
                    startUrl = "https://www.douyin.com/",
                    cookieUrl = "https://www.douyin.com",
                )
            }
        }
    }
}
