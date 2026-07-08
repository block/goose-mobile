package xyz.block.gosling.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xyz.block.gosling.features.agent.ondevice.DownloadStatus
import xyz.block.gosling.features.agent.ondevice.OnDeviceModelInfo
import xyz.block.gosling.features.agent.ondevice.OnDeviceModelManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadedModels by remember { mutableStateOf(OnDeviceModelManager.getDownloadedModels(context)) }
    var availableStorage by remember { mutableStateOf(OnDeviceModelManager.getAvailableStorageBytes(context)) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showWifiDialog by remember { mutableStateOf<OnDeviceModelInfo?>(null) }
    val downloadStatuses = remember { mutableStateMapOf<String, DownloadStatus>() }
    val downloadJobs = remember { mutableMapOf<String, Job>() }

    fun refreshModels() {
        downloadedModels = OnDeviceModelManager.getDownloadedModels(context)
        availableStorage = OnDeviceModelManager.getAvailableStorageBytes(context)
    }

    fun startDownload(modelInfo: OnDeviceModelInfo, allowMetered: Boolean = false) {
        val job = scope.launch {
            OnDeviceModelManager.downloadModel(context, modelInfo, allowMetered).collect { status ->
                downloadStatuses[modelInfo.id] = status
                if (status is DownloadStatus.Completed || status is DownloadStatus.Failed) {
                    refreshModels()
                }
            }
        }
        downloadJobs[modelInfo.id] = job
    }

    fun cancelDownload(modelId: String) {
        OnDeviceModelManager.cancelDownload(context, modelId)
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        downloadStatuses[modelId] = DownloadStatus.Idle
    }

    fun onDownloadRequested(modelInfo: OnDeviceModelInfo) {
        if (!OnDeviceModelManager.isOnWifi(context)) {
            showWifiDialog = modelInfo
        } else {
            startDownload(modelInfo)
        }
    }

    LaunchedEffect(Unit) {
        refreshModels()
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("On-Device Models") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Available storage: ${OnDeviceModelManager.formatSize(availableStorage)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // On-device models section
            Text(
                text = "On-Device Models",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "NPU/GPU accelerated. Native function calling support.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            for (modelInfo in OnDeviceModelManager.getKnownModels(context)) {
                val isDownloaded = downloadedModels.any { it.id == modelInfo.id }
                val status = downloadStatuses[modelInfo.id] ?: DownloadStatus.Idle

                ModelCard(
                    modelInfo = modelInfo,
                    isDownloaded = isDownloaded,
                    downloadStatus = status,
                    onDownload = { onDownloadRequested(modelInfo) },
                    onCancel = { cancelDownload(modelInfo.id) },
                    onDelete = { showDeleteDialog = modelInfo.id }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { modelId ->
        val modelInfo = OnDeviceModelManager.getKnownModels(context).find { it.id == modelId }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model?") },
            text = {
                Text("Delete ${modelInfo?.displayName ?: modelId}? This will free ${OnDeviceModelManager.formatSize(modelInfo?.sizeBytes ?: 0)} of storage.")
            },
            confirmButton = {
                TextButton(onClick = {
                    OnDeviceModelManager.deleteModel(context, modelId)
                    showDeleteDialog = null
                    refreshModels()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // WiFi warning dialog
    showWifiDialog?.let { modelInfo ->
        AlertDialog(
            onDismissRequest = { showWifiDialog = null },
            title = { Text("No WiFi Connection") },
            text = {
                Text(
                    "This model is ${OnDeviceModelManager.formatSize(modelInfo.sizeBytes)}. " +
                    "Downloading over cellular data may incur charges. Continue anyway?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWifiDialog = null
                    startDownload(modelInfo, allowMetered = true)
                }) {
                    Text("Download Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWifiDialog = null }) {
                    Text("Wait for WiFi")
                }
            }
        )
    }
}

@Composable
private fun ModelCard(
    modelInfo: OnDeviceModelInfo,
    isDownloaded: Boolean,
    downloadStatus: DownloadStatus,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = downloadStatus is DownloadStatus.Downloading

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelInfo.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${OnDeviceModelManager.formatSize(modelInfo.sizeBytes)} | ${modelInfo.contextLength / 1000}K context",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    isDownloaded -> {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    isActive -> {
                        IconButton(onClick = onCancel) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel download"
                            )
                        }
                    }
                    downloadStatus !is DownloadStatus.Failed -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(
                                text = "Download",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            // Status display
            when (val status = downloadStatus) {
                is DownloadStatus.Downloading -> {
                    if (status.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading... ${(status.progress * 100).toInt()}% " +
                                    "(${OnDeviceModelManager.formatSize(status.downloadedBytes)} / ${OnDeviceModelManager.formatSize(status.totalBytes)})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Starting download...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is DownloadStatus.Failed -> {
                    Text(
                        text = status.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Retry")
                    }
                }
                is DownloadStatus.Completed, is DownloadStatus.Idle -> {
                    // No extra status to show
                }
            }

            if (isDownloaded) {
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
