package xyz.block.gosling.features.agent.ondevice

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import xyz.block.gosling.features.agent.AiModel
import xyz.block.gosling.features.agent.ModelProvider
import java.io.File

/**
 * The inference engine used by an on-device model.
 */
enum class OnDeviceEngine {
    /** Google LiteRT-LM - runs .litertlm models, supports NPU/GPU acceleration */
    LITERT
}

data class OnDeviceModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contextLength: Int,
    val engine: OnDeviceEngine
)

sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadStatus()
    data class Failed(val reason: String) : DownloadStatus()
    data object Completed : DownloadStatus()
}

object OnDeviceModelManager {
    private const val TAG = "OnDeviceModelManager"
    private const val MODELS_DIR = "models"
    private const val LITERT_CONFIG = "models_litert.json"

    // Maps DownloadManager download IDs to model IDs for tracking
    private val activeDownloads = mutableMapOf<Long, String>()

    // Cached model list, loaded lazily from config files
    private var cachedModels: List<OnDeviceModelInfo>? = null

    /**
     * Model registry loaded from JSON config files.
     *
     * Resolution order per engine:
     *   1. User override: <externalFilesDir>/models/models_<engine>.json
     *   2. Bundled default: assets/models_<engine>.json
     *
     * The user-override file lets models be added/updated without rebuilding the APK.
     * Drop a modified JSON file into the models directory and restart the app.
     */
    fun getKnownModels(context: Context): List<OnDeviceModelInfo> {
        cachedModels?.let { return it }

        val models = loadModelsConfig(context, LITERT_CONFIG, OnDeviceEngine.LITERT)

        cachedModels = models
        Log.i(TAG, "Loaded ${models.size} on-device model definitions")
        return models
    }

    /**
     * Force reload model config from disk. Call after editing config files.
     */
    fun reloadModelsConfig(context: Context): List<OnDeviceModelInfo> {
        cachedModels = null
        return getKnownModels(context)
    }

    /**
     * Load models from a config file. Checks user override first, then bundled asset.
     */
    private fun loadModelsConfig(context: Context, fileName: String, engine: OnDeviceEngine): List<OnDeviceModelInfo> {
        val json = readUserOverride(context, fileName)
            ?: readBundledAsset(context, fileName)
            ?: return emptyList()

        return try {
            parseModelsJson(json, engine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $fileName: ${e.message}", e)
            emptyList()
        }
    }

    private fun readUserOverride(context: Context, fileName: String): String? {
        val file = File(getModelsDir(context), fileName)
        return if (file.exists()) {
            Log.i(TAG, "Using user override: ${file.absolutePath}")
            file.readText()
        } else null
    }

    private fun readBundledAsset(context: Context, fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().readText()
        } catch (e: Exception) {
            Log.w(TAG, "Bundled asset $fileName not found: ${e.message}")
            null
        }
    }

    private fun parseModelsJson(json: String, engine: OnDeviceEngine): List<OnDeviceModelInfo> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            OnDeviceModelInfo(
                id = obj.getString("id"),
                displayName = obj.getString("displayName"),
                fileName = obj.getString("fileName"),
                downloadUrl = obj.getString("downloadUrl"),
                sizeBytes = obj.getLong("sizeBytes"),
                contextLength = obj.getInt("contextLength"),
                engine = engine
            )
        }
    }

    private fun getModelsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), MODELS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Scan for downloaded .litertlm model files and return matching model info.
     */
    fun getDownloadedModels(context: Context): List<OnDeviceModelInfo> {
        val modelsDir = getModelsDir(context)
        if (!modelsDir.exists()) return emptyList()

        val downloadedFiles = modelsDir.listFiles()
            ?.filter { !it.name.endsWith(".part") && it.extension == "litertlm" }
            ?.map { it.name }
            ?.toSet() ?: emptySet()

        return getKnownModels(context).filter { it.fileName in downloadedFiles }
    }

    /**
     * Register all downloaded models with the AiModel system.
     */
    fun registerDownloadedModels(context: Context) {
        val downloaded = getDownloadedModels(context)
        for (modelInfo in downloaded) {
            AiModel.registerOnDeviceModel(
                AiModel(
                    displayName = modelInfo.displayName,
                    identifier = modelInfo.id,
                    provider = ModelProvider.ON_DEVICE_LITERT
                )
            )
        }
        Log.i(TAG, "Registered ${downloaded.size} on-device models")
    }

    /**
     * Get the absolute file path for a model by its identifier.
     *
     * @return File path if model exists, null otherwise
     */
    fun getModelPath(context: Context, identifier: String): String? {
        val modelInfo = getKnownModels(context).find { it.id == identifier } ?: return null
        val file = File(getModelsDir(context), modelInfo.fileName)
        return if (file.exists() && !file.name.endsWith(".part")) file.absolutePath else null
    }

    /**
     * Get the context length for a model by its identifier.
     */
    fun getContextLength(context: Context, identifier: String): Int {
        return getKnownModels(context).find { it.id == identifier }?.contextLength ?: 2048
    }

    /**
     * Check if the device is on an unmetered (WiFi) connection.
     */
    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Pre-flight check before starting a download.
     *
     * @return null if OK, or an error message string if the download should not proceed.
     */
    fun preDownloadCheck(context: Context, modelInfo: OnDeviceModelInfo, allowMetered: Boolean = false): String? {
        // Storage check -- require model size + 10% headroom
        val available = getAvailableStorageBytes(context)
        val required = (modelInfo.sizeBytes * 1.1).toLong()
        if (available < required) {
            return "Not enough storage. Need ${formatSize(required)} but only ${formatSize(available)} available."
        }

        // Network check
        if (!allowMetered && !isOnWifi(context)) {
            return "WiFi required. This model is ${formatSize(modelInfo.sizeBytes)}. Connect to WiFi or allow cellular download."
        }

        // Already downloaded check
        val finalFile = File(getModelsDir(context), modelInfo.fileName)
        if (finalFile.exists()) {
            return "Model already downloaded."
        }

        return null
    }

    /**
     * Download a model using Android DownloadManager.
     *
     * The download runs as a system-level background task that:
     * - Survives the app being closed or the screen navigated away
     * - Shows progress in the system notification tray
     * - Handles network interruptions automatically
     * - Respects battery saver settings
     *
     * Returns a Flow that emits DownloadStatus updates.
     * The flow completes after the download finishes (success or failure).
     */
    fun downloadModel(
        context: Context,
        modelInfo: OnDeviceModelInfo,
        allowMetered: Boolean = false
    ): Flow<DownloadStatus> = callbackFlow {
        // Pre-flight checks
        val error = preDownloadCheck(context, modelInfo, allowMetered)
        if (error != null) {
            trySend(DownloadStatus.Failed(error))
            close()
            return@callbackFlow
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val modelsDir = getModelsDir(context)
        val destinationFile = File(modelsDir, modelInfo.fileName)

        // Build DownloadManager request
        val request = DownloadManager.Request(Uri.parse(modelInfo.downloadUrl)).apply {
            setTitle("Downloading ${modelInfo.displayName}")
            setDescription("${formatSize(modelInfo.sizeBytes)} model for Goose Mobile")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(destinationFile))

            if (!allowMetered) {
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            } else {
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
            }

            // Allow download to continue when device enters low-power idle
            setAllowedOverMetered(allowMetered)
            setAllowedOverRoaming(false)
        }

        val downloadId = dm.enqueue(request)
        activeDownloads[downloadId] = modelInfo.id
        Log.i(TAG, "Enqueued download #$downloadId for ${modelInfo.id}")

        // Register a BroadcastReceiver for download completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (completedId != downloadId) return

                // Query final status
                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        val reasonIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)

                        when (cursor.getInt(statusIdx)) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                launch(Dispatchers.IO) {
                                    // Register model
                                    AiModel.registerOnDeviceModel(
                                        AiModel(
                                            displayName = modelInfo.displayName,
                                            identifier = modelInfo.id,
                                            provider = ModelProvider.ON_DEVICE_LITERT
                                        )
                                    )
                                    Log.i(TAG, "Download complete: ${modelInfo.id}")
                                    activeDownloads.remove(downloadId)
                                    trySend(DownloadStatus.Completed)
                                    close()
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(reasonIdx)
                                val message = downloadFailureReason(reason)
                                Log.e(TAG, "Download failed: $message (reason=$reason)")
                                destinationFile.delete()
                                activeDownloads.remove(downloadId)
                                trySend(DownloadStatus.Failed(message))
                                close()
                            }
                        }
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        // Poll for progress updates while download is active
        val progressJob = launch(Dispatchers.IO) {
            while (isActive) {
                val progress = queryDownloadProgress(dm, downloadId)
                if (progress != null) {
                    trySend(progress)
                    if (progress is DownloadStatus.Failed) {
                        break
                    }
                }
                delay(500)
            }
        }

        awaitClose {
            progressJob.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
    }

    /**
     * Cancel an in-progress download.
     */
    fun cancelDownload(context: Context, modelId: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val entry = activeDownloads.entries.find { it.value == modelId } ?: return
        dm.remove(entry.key)
        activeDownloads.remove(entry.key)

        // Clean up partial file
        val modelInfo = getKnownModels(context).find { it.id == modelId } ?: return
        File(getModelsDir(context), modelInfo.fileName).delete()
        Log.i(TAG, "Cancelled download for $modelId")
    }

    /**
     * Check if a model is currently being downloaded.
     */
    fun isDownloading(modelId: String): Boolean {
        return activeDownloads.containsValue(modelId)
    }

    private fun queryDownloadProgress(dm: DownloadManager, downloadId: Long): DownloadStatus? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return dm.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null

            val statusIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val downloadedIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            when (cursor.getInt(statusIdx)) {
                DownloadManager.STATUS_RUNNING -> {
                    val downloaded = cursor.getLong(downloadedIdx)
                    val total = cursor.getLong(totalIdx)
                    val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else -1f
                    DownloadStatus.Downloading(progress, downloaded, total)
                }
                DownloadManager.STATUS_PENDING -> {
                    DownloadStatus.Downloading(0f, 0, 0)
                }
                DownloadManager.STATUS_PAUSED -> {
                    val downloaded = cursor.getLong(downloadedIdx)
                    val total = cursor.getLong(totalIdx)
                    val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                    DownloadStatus.Downloading(progress, downloaded, total)
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    DownloadStatus.Failed(downloadFailureReason(cursor.getInt(reasonIdx)))
                }
                else -> null
            }
        }
    }

    private fun downloadFailureReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot be resumed"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "Storage error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unexpected server response"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Download failed (error $reason)"
        }
    }

    /**
     * Delete a downloaded model.
     */
    fun deleteModel(context: Context, modelId: String): Boolean {
        val modelInfo = getKnownModels(context).find { it.id == modelId } ?: return false
        val file = File(getModelsDir(context), modelInfo.fileName)

        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            AiModel.unregisterOnDeviceModel(modelId)
            Log.i(TAG, "Deleted model: $modelId")
        }
        return deleted
    }

    /**
     * Get available storage in bytes on the models directory.
     */
    fun getAvailableStorageBytes(context: Context): Long {
        val modelsDir = getModelsDir(context)
        return modelsDir.usableSpace
    }

    /**
     * Format byte size as human-readable string.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            else -> "%.1f KB".format(bytes / 1_000.0)
        }
    }
}
