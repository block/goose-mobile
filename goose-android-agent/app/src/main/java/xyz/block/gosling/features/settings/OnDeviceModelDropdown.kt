package xyz.block.gosling.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import xyz.block.gosling.features.agent.ondevice.OnDeviceModelInfo

/**
 * Model picker for the on-device LLM provider.
 *
 * Lists every known on-device model (downloaded or not). Tapping a downloaded
 * model invokes [onModelSelected]; tapping an un-downloaded model invokes
 * [onUndownloadedClicked] (typically to navigate to a download screen) without
 * changing the selection — so the saved llmModel always points at a real file.
 *
 * If [knownModels] is empty, a disabled "No models available" field is shown
 * instead of a dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnDeviceModelDropdown(
    knownModels: List<OnDeviceModelInfo>,
    downloadedModels: List<OnDeviceModelInfo>,
    selectedModelId: String,
    onModelSelected: (OnDeviceModelInfo) -> Unit,
    onUndownloadedClicked: (OnDeviceModelInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (knownModels.isEmpty()) {
        OutlinedTextField(
            value = "No models available",
            onValueChange = {},
            readOnly = true,
            modifier = modifier.fillMaxWidth(),
            enabled = false
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val downloadedIds = downloadedModels.map { it.id }.toSet()
    val displayValue = knownModels.find { it.id == selectedModelId }?.displayName
        ?: "Select a model"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            knownModels.forEach { modelInfo ->
                val isDownloaded = modelInfo.id in downloadedIds
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(modelInfo.displayName)
                            if (!isDownloaded) {
                                Text(
                                    text = "Not downloaded · Tap to download",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (isDownloaded) {
                            onModelSelected(modelInfo)
                        } else {
                            onUndownloadedClicked(modelInfo)
                        }
                    }
                )
            }
        }
    }
}
