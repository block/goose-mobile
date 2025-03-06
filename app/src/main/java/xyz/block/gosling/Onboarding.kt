package xyz.block.gosling

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

enum class OnboardingStep {
    WELCOME,
    LLM_CONFIG
}

@Composable
fun Onboarding(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    settingsManager: SettingsManager,
    openAccessibilitySettings: () -> Unit,
    isAccessibilityEnabled: Boolean
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var llmModel by remember { mutableStateOf(settingsManager.llmModel) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LinearProgressIndicator(
            progress = {
                when (currentStep) {
                    OnboardingStep.WELCOME -> 0.5f
                    OnboardingStep.LLM_CONFIG -> 1f
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Step content
        when (currentStep) {
            OnboardingStep.WELCOME -> {
                WelcomeStep(
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onEnableAccessibility = openAccessibilitySettings,
                    onNext = { currentStep = OnboardingStep.LLM_CONFIG }
                )
            }

            OnboardingStep.LLM_CONFIG -> {
                LLMConfigStep(
                    llmModel = llmModel,
                    onLLMModelChange = { llmModel = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    onComplete = {
                        settingsManager.llmModel = llmModel
                        settingsManager.apiKey = apiKey
                        settingsManager.isFirstTime = false
                        onSetupComplete()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    isAccessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var isAssistantEnabled by remember { mutableStateOf(false) }

    // Function to check assistant status
    fun checkAssistantStatus() {
        val settingSecure = Settings.Secure.getString(
            context.contentResolver,
            "assistant"
        )
        isAssistantEnabled = settingSecure?.contains(context.packageName) == true
    }

    // Check on initial launch
    LaunchedEffect(Unit) {
        checkAssistantStatus()
    }

    // Check when app regains focus
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val lifecycleObserver = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                if (activity == context) {
                    checkAssistantStatus()
                }
            }

            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(
                activity: android.app.Activity,
                outState: android.os.Bundle
            ) {
            }

            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityCreated(
                activity: android.app.Activity,
                savedInstanceState: android.os.Bundle?
            ) {
            }
        }

        activity?.application?.registerActivityLifecycleCallbacks(lifecycleObserver)

        onDispose {
            activity?.application?.unregisterActivityLifecycleCallbacks(lifecycleObserver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Welcome Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Welcome to Gosling!",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = "Let's get you set up with your personal AI assistant.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // Settings Sections
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Accessibility Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Accessibility Permissions",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Gosling needs accessibility permissions to interact with other apps and help you with tasks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = onEnableAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAccessibilityEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAccessibilityEnabled)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isAccessibilityEnabled) "Accessibility Enabled" else "Enable Accessibility")
                }
            }

            // Assistant Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Set Default Assistant",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Make Gosling your default assistant app to get the most out of its features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAssistantEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAssistantEnabled)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isAssistantEnabled) "Assistant Enabled" else "Set as Default Assistant")
                }
            }
        }

        // Next Button
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Next")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LLMConfigStep(
    llmModel: String,
    onLLMModelChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    val models = AiModel.AVAILABLE_MODELS.map {
        it.identifier to it.displayName
    }

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "LLM Configuration",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = "Select your LLM model and enter your API key to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // Form Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Model Selection
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "LLM Model")
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = models.find { it.first == llmModel }?.second ?: llmModel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        models.forEach { (modelId, displayName) ->
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    onLLMModelChange(modelId)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // API Key
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "API Key")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        }

        // Complete Button
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            enabled = llmModel.isNotEmpty() && apiKey.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Complete Setup")
        }
    }
} 
