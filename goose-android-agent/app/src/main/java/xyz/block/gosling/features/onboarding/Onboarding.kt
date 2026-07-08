package xyz.block.gosling.features.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import xyz.block.gosling.features.settings.ModelManagementScreen
import xyz.block.gosling.features.settings.SettingsStore

sealed class OnboardingScreen(val route: String) {
    data object Welcome : OnboardingScreen("onboarding/welcome")
    data object LLMConfig : OnboardingScreen("onboarding/llm_config")
    data object ModelManagement : OnboardingScreen("onboarding/model_management")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Onboarding(
    navController: NavHostController,
    settingsStore: SettingsStore,
    openAccessibilitySettings: () -> Unit,
    isAccessibilityEnabled: Boolean,
    onComplete: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentRoute != OnboardingScreen.Welcome.route) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        when (currentRoute) {
                            OnboardingScreen.LLMConfig.route -> "LLM Configuration"
                            OnboardingScreen.ModelManagement.route -> "Download Models"
                            else -> "Welcome"
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LinearProgressIndicator(
                progress = {
                    when (currentRoute) {
                        OnboardingScreen.Welcome.route -> 0.5f
                        OnboardingScreen.LLMConfig.route -> 1.0f
                        OnboardingScreen.ModelManagement.route -> 1.0f
                        else -> 0f
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            NavHost(
                navController = navController,
                startDestination = OnboardingScreen.Welcome.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                composable(OnboardingScreen.Welcome.route) {
                    WelcomeStep(
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        onEnableAccessibility = openAccessibilitySettings,
                        onNext = { navController.navigate(OnboardingScreen.LLMConfig.route) }
                    )
                }

                composable(OnboardingScreen.LLMConfig.route) {
                    LLMConfigStep(
                        settingsStore = settingsStore,
                        onComplete = onComplete,
                        onNavigateToModelManagement = {
                            navController.navigate(OnboardingScreen.ModelManagement.route)
                        }
                    )
                }

                composable(OnboardingScreen.ModelManagement.route) {
                    ModelManagementScreen(
                        onBack = { navController.popBackStack() },
                        showTopBar = false
                    )
                }
            }
        }
    }
}
