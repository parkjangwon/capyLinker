package org.parkjw.capylinker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.parkjw.capylinker.ui.screens.AddLinkScreen
import org.parkjw.capylinker.ui.screens.DetailScreen
import org.parkjw.capylinker.ui.screens.MainScreen
import org.parkjw.capylinker.ui.screens.SettingsScreen
import org.parkjw.capylinker.viewmodel.LinkReceiverViewModel
import org.parkjw.capylinker.viewmodel.MainViewModel
import org.parkjw.capylinker.viewmodel.SettingsViewModel

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ADD_LINK = "add_link"
    const val DETAIL = "detail/{itemId}"

    fun detail(itemId: Long) = "detail/$itemId"
}

@Composable
fun OldAppNavHost() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToAddLink = { navController.navigate(Routes.ADD_LINK) },
                onNavigateToDetail = { navController.navigate(Routes.detail(it.id)) }
            )
        }
        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = settingsViewModel
            )
        }
        composable(Routes.ADD_LINK) {
            val linkReceiverViewModel: LinkReceiverViewModel = hiltViewModel()
            AddLinkScreen(viewModel = linkReceiverViewModel, onLinkAdded = { navController.popBackStack() })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) {
            val itemId = it.arguments?.getLong("itemId")
            val items by mainViewModel.items.collectAsState()
            val item = items.find { i -> i.id == itemId }
            if (item != null) {
                DetailScreen(item = item, viewModel = mainViewModel, onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
