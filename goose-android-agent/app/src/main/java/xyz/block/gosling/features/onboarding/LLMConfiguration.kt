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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import xyz.block.gosling.features.agent.AiModel
import xyz.block.gosling.features.settings.QRCodeScannerDialog
import xyz.block.gosling.features.settings.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLMConfigStep(
    settingsStore: SettingsStore,
    onComplete: () -> Unit
) {
    var llmModel by remember { mutableStateOf(settingsStore.llmModel) }
    var currentModel by remember { mutableStateOf(AiModel.fromIdentifier(llmModel)) }
    var selectedProvider by remember { mutableStateOf(currentModel.provider) }
    var selectedModelId by remember { mutableStateOf(llmModel) }
    var apiKey by remember { mutableStateOf(settingsStore.getApiKey(currentModel.provider)) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }

    // When provider changes, reset to first model of that provider
    androidx.compose.runtime.LaunchedEffect(selectedProvider) {
        val modelsForProvider = AiModel.getModelsForProvider(selectedProvider)
        if (modelsForProvider.isNotEmpty()) {
            selectedModelId = modelsForProvider.first().identifier
            llmModel = selectedModelId
            currentModel = AiModel.fromIdentifier(selectedModelId)
            apiKey = settingsStore.getApiKey(currentModel.provider)
        }
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
                            value = selectedProvider.name,
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
                                    text = { Text(provider.name) },
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
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = AiModel.getModelsForProvider(selectedProvider)
                                .find { it.identifier == selectedModelId }?.displayName ?: selectedModelId,
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
                            AiModel.getModelsForProvider(selectedProvider).forEach { model ->
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
        }

        Button(
            onClick = {
                settingsStore.llmModel = llmModel
                settingsStore.setApiKey(currentModel.provider, apiKey)
                onComplete()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            enabled = llmModel.isNotEmpty() && apiKey.isNotEmpty(),
        ) {
            Text("Complete Setup")
        }
    }
}
