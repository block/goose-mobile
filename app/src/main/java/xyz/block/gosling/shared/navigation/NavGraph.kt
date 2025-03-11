package xyz.block.gosling.shared.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.block.gosling.features.app.MainScreen
import xyz.block.gosling.features.onboarding.Onboarding
import xyz.block.gosling.features.settings.SettingsScreen
import xyz.block.gosling.features.settings.SettingsStore

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
    object Onboarding : Screen("onboarding")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    settingsStore: SettingsStore,
    openAccessibilitySettings: () -> Unit,
    isAccessibilityEnabled: Boolean,
    startDestination: String = if (settingsStore.isFirstTime) Screen.Onboarding.route else Screen.Main.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                isAccessibilityEnabled = isAccessibilityEnabled
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                settingsStore = settingsStore,
                onBack = { navController.popBackStack() },
                openAccessibilitySettings = openAccessibilitySettings,
                isAccessibilityEnabled = isAccessibilityEnabled
            )
        }

        composable(Screen.Onboarding.route) {
            val onboardingNavController = rememberNavController()
            Onboarding(
                navController = onboardingNavController,
                settingsStore = settingsStore,
                openAccessibilitySettings = openAccessibilitySettings,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onComplete = {
                    settingsStore.isFirstTime = false
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
    }
} 
