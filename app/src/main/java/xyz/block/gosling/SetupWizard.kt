package xyz.block.gosling

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

enum class SetupStep {
    WELCOME,
    ACCESSIBILITY_PERMISSION,
    ASSISTANT_SELECTION,
    LLM_CONFIG
}

@Composable
fun SetupWizard(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    settingsManager: SettingsManager,
    openAccessibilitySettings: () -> Unit,
    isAccessibilityEnabled: Boolean
) {
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }
    var llmModel by remember { mutableStateOf(settingsManager.llmModel) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = {
                when (currentStep) {
                    SetupStep.WELCOME -> 0.25f
                    SetupStep.ACCESSIBILITY_PERMISSION -> 0.5f
                    SetupStep.ASSISTANT_SELECTION -> 0.75f
                    SetupStep.LLM_CONFIG -> 1f
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Step content
        when (currentStep) {
            SetupStep.WELCOME -> {
                WelcomeStep(
                    onNext = { currentStep = SetupStep.ACCESSIBILITY_PERMISSION }
                )
            }

            SetupStep.ACCESSIBILITY_PERMISSION -> {
                AccessibilityPermissionStep(
                    isEnabled = isAccessibilityEnabled,
                    onEnable = openAccessibilitySettings,
                    onNext = { currentStep = SetupStep.ASSISTANT_SELECTION }
                )
            }

            SetupStep.ASSISTANT_SELECTION -> {
                AssistantSelectionStep(
                    onNext = { currentStep = SetupStep.LLM_CONFIG }
                )
            }

            SetupStep.LLM_CONFIG -> {
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
private fun WelcomeStep(onNext: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Welcome to Gosling!",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Let's get you set up with your personal AI assistant.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "Get Started",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AccessibilityPermissionStep(
    isEnabled: Boolean,
    onEnable: () -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Accessibility Permissions",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Gosling needs accessibility permissions to interact with other apps and help you with tasks.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isEnabled) "Accessibility Enabled" else "Enable Accessibility")
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = true
            ) {
                Text("Skip")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = isEnabled
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun AssistantSelectionStep(
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var isAssistantEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settingSecure = Settings.Secure.getString(
            context.contentResolver,
            "assistant"
        )
        isAssistantEnabled = settingSecure?.contains(context.packageName) == true
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Set Default Assistant",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Make Gosling your default assistant app to get the most out of its features.",
                style = MaterialTheme.typography.bodyLarge,
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

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = true
            ) {
                Text("Skip")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = true
            ) {
                Text("Next")
            }
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "LLM Configuration",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Select your LLM model and enter your API key to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = models.find { it.first == llmModel }?.second ?: llmModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("LLM Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
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

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onComplete,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            enabled = llmModel.isNotEmpty() && apiKey.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "Complete Setup",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun SettingsScreen(
//    settingsManager: SettingsManager,
//    onBack: () -> Unit,
//    openAccessibilitySettings: () -> Unit,
//    isAccessibilityEnabled: Boolean
//) {
//    var llmModel by remember { mutableStateOf(settingsManager.llmModel) }
//    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
//    var shouldProcessNotifications by remember { mutableStateOf(settingsManager.shouldProcessNotifications) }
//    var showResetDialog by remember { mutableStateOf(false) }
//
//    val models = AiModel.AVAILABLE_MODELS.map {
//        it.identifier to it.displayName
//    }
//
//    var expanded by remember { mutableStateOf(false) }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.spacedBy(16.dp)
//    ) {
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            IconButton(onClick = onBack) {
//                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
//            }
//            Text(
//                text = "Settings",
//                style = MaterialTheme.typography.titleLarge
//            )
//            Box(modifier = Modifier.width(48.dp))
//        }
//
//        // Conditional Settings Section
//        Column(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.spacedBy(16.dp)
//        ) {
//            if (isAccessibilityEnabled) {
//                Text(
//                    text = "Notification Processing",
//                    style = MaterialTheme.typography.titleLarge
//                )
//                Text(
//                    text = "Allow Gosling to analyze and respond to notifications from other apps",
//                    style = MaterialTheme.typography.bodyMedium
//                )
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text(
//                        text = "Process notifications",
//                        style = MaterialTheme.typography.bodyLarge
//                    )
//                    Switch(
//                        checked = shouldProcessNotifications,
//                        onCheckedChange = {
//                            shouldProcessNotifications = it
//                            settingsManager.shouldProcessNotifications = it
//                        }
//                    )
//                }
//            } else {
//                Text(
//                    text = "Accessibility Permissions",
//                    style = MaterialTheme.typography.titleLarge
//                )
//                Text(
//                    text = "Gosling needs accessibility permissions to interact with other apps and help you with tasks.",
//                    style = MaterialTheme.typography.bodyMedium
//                )
//                Button(
//                    onClick = openAccessibilitySettings,
//                    modifier = Modifier.fillMaxWidth(),
//                    enabled = !isAccessibilityEnabled,
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = if (isAccessibilityEnabled)
//                            MaterialTheme.colorScheme.secondary
//                        else
//                            MaterialTheme.colorScheme.primary
//                    )
//                ) {
//                    Text(if (isAccessibilityEnabled) "Accessibility Enabled" else "Enable Accessibility")
//                }
//            }
//        }
//
//        // Model Selection Dropdown
//        ExposedDropdownMenuBox(
//            expanded = expanded,
//            onExpandedChange = { expanded = it }
//        ) {
//            OutlinedTextField(
//                value = models.find { it.first == llmModel }?.second ?: llmModel,
//                onValueChange = {},
//                readOnly = true,
//                label = { Text("LLM Model") },
//                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .menuAnchor()
//            )
//
//            ExposedDropdownMenu(
//                expanded = expanded,
//                onDismissRequest = { expanded = false }
//            ) {
//                models.forEach { (modelId, displayName) ->
//                    DropdownMenuItem(
//                        text = { Text(displayName) },
//                        onClick = {
//                            llmModel = modelId
//                            settingsManager.llmModel = modelId
//                            expanded = false
//                        }
//                    )
//                }
//            }
//        }
//
//        OutlinedTextField(
//            value = apiKey,
//            onValueChange = {
//                apiKey = it
//                settingsManager.apiKey = it
//            },
//            label = { Text("API Key") },
//            visualTransformation = PasswordVisualTransformation(),
//            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//            modifier = Modifier.fillMaxWidth()
//        )
//
//        Button(
//            onClick = { showResetDialog = true },
//            modifier = Modifier.fillMaxWidth(),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = MaterialTheme.colorScheme.error
//            )
//        ) {
//            Text("Clear saved settings")
//        }
//    }
//
//    if (showResetDialog) {
//        AlertDialog(
//            onDismissRequest = { showResetDialog = false },
//            title = { Text("Reset Setup") },
//            text = { Text("This will reset all settings and show the setup wizard again. Are you sure?") },
//            confirmButton = {
//                TextButton(
//                    onClick = {
//                        settingsManager.isFirstTime = true
//                        showResetDialog = false
//                    }
//                ) {
//                    Text("Reset")
//                }
//            },
//            dismissButton = {
//                TextButton(onClick = { showResetDialog = false }) {
//                    Text("Cancel")
//                }
//            }
//        )
//    }
//}
