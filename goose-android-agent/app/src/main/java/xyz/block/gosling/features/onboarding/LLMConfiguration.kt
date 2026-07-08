package xyz.block.gosling.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import xyz.block.gosling.features.agent.AiModel
import xyz.block.gosling.features.agent.ModelProvider
import xyz.block.gosling.features.agent.ondevice.OnDeviceModelManager
import xyz.block.gosling.features.settings.OnDeviceModelDropdown
import xyz.block.gosling.features.settings.QRCodeScannerDialog
import xyz.block.gosling.features.settings.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLMConfigStep(
    settingsStore: SettingsStore,
    onComplete: () -> Unit,
    onNavigateToModelManagement: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var llmModel by remember { mutableStateOf(settingsStore.llmModel) }
    var currentModel by remember { mutableStateOf(AiModel.fromIdentifier(llmModel)) }
    var selectedProvider by remember { mutableStateOf(currentModel.provider) }
    var selectedModelId by remember { mutableStateOf(llmModel) }
    var apiKey by remember { mutableStateOf(settingsStore.getApiKey(currentModel.provider)) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }

    val isOnDevice = selectedProvider.isOnDevice
    val downloadedModels = remember(selectedProvider) {
        if (isOnDevice) OnDeviceModelManager.getDownloadedModels(context) else emptyList()
    }
    val modelsForProvider = AiModel.getModelsForProvider(selectedProvider)
    val hasModels = modelsForProvider.isNotEmpty()

    // When provider changes, reset to first model of that provider
    LaunchedEffect(selectedProvider) {
        if (modelsForProvider.isNotEmpty()) {
            selectedModelId = modelsForProvider.first().identifier
            llmModel = selectedModelId
            currentModel = AiModel.fromIdentifier(selectedModelId)
            apiKey = settingsStore.getApiKey(currentModel.provider)
        } else {
            selectedModelId = ""
            llmModel = ""
        }
    }

    // Determine if setup can complete
    val canComplete = if (isOnDevice) {
        hasModels && selectedModelId.isNotEmpty()
    } else {
        llmModel.isNotEmpty() && apiKey.isNotEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Provider")

                    // Provider Dropdown
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProvider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) }
                        )

                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false }
                        ) {
                            AiModel.getProviders().forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        selectedProvider = provider
                                        providerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Model Dropdown
                    Text(text = "Model")
                    if (isOnDevice) {
                        OnDeviceModelDropdown(
                            knownModels = OnDeviceModelManager.getKnownModels(context),
                            downloadedModels = downloadedModels,
                            selectedModelId = selectedModelId,
                            onModelSelected = { modelInfo ->
                                val onDeviceModel = AiModel(
                                    displayName = modelInfo.displayName,
                                    identifier = modelInfo.id,
                                    provider = ModelProvider.ON_DEVICE_LITERT
                                )
                                selectedModelId = modelInfo.id
                                llmModel = modelInfo.id
                                currentModel = onDeviceModel
                                apiKey = settingsStore.getApiKey(onDeviceModel.provider)
                            },
                            onUndownloadedClicked = { onNavigateToModelManagement?.invoke() }
                        )
                    } else if (hasModels) {
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = modelsForProvider
                                    .find { it.identifier == selectedModelId }?.displayName
                                    ?: selectedModelId,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) }
                            )

                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                modelsForProvider.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.displayName) },
                                        onClick = {
                                            selectedModelId = model.identifier
                                            llmModel = model.identifier
                                            currentModel = model
                                            apiKey = settingsStore.getApiKey(model.provider)
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // API Key section (cloud providers only)
                if (!isOnDevice) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "API Key")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                modifier = Modifier.weight(1f),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )

                            // QR Code scanner button
                            Button(
                                onClick = { showQRScanner = true },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR Code"
                                )
                                Text(
                                    text = "Scan",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            // QR Code scanner dialog
                            if (showQRScanner) {
                                QRCodeScannerDialog(
                                    onDismiss = { showQRScanner = false },
                                    onQRCodeScanned = { scannedApiKey ->
                                        apiKey = scannedApiKey
                                    }
                                )
                            }
                        }
                    }
                }

                // On-device section
                if (isOnDevice) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "On-device models run locally without internet or API keys. " +
                                    "Download at least one model to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (downloadedModels.isEmpty()) {
                            Text(
                                text = "No models downloaded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "${downloadedModels.size} model(s) ready to use.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (onNavigateToModelManagement != null) {
                            Button(
                                onClick = onNavigateToModelManagement,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Download Models")
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                settingsStore.llmModel = llmModel
                if (!isOnDevice) {
                    settingsStore.setApiKey(currentModel.provider, apiKey)
                }
                onComplete()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            enabled = canComplete,
        ) {
            Text("Complete Setup")
        }
    }
}

