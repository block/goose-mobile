package xyz.block.gosling.shared.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import xyz.block.gosling.features.app.ConversationScreen
import xyz.block.gosling.features.app.MainScreen
import xyz.block.gosling.features.onboarding.Onboarding
import xyz.block.gosling.features.settings.SettingsScreen
import xyz.block.gosling.features.settings.SettingsStore

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Settings : Screen("settings")
    data object Conversation : Screen("conversation/{conversationId}") {
        fun createRoute(conversationId: String) = "conversation/$conversationId"
    }

    data object Onboarding : Screen("onboarding")
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
                onNavigateToConversation = { conversationId ->
                    navController.navigate(Screen.Conversation.createRoute(conversationId))
                },
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

        composable(
            route = Screen.Conversation.route,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val conversationId =
                backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ConversationScreen(
                conversationId = conversationId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
} 
