package xyz.block.gosling.features.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.compose.rememberNavController
import xyz.block.gosling.GoslingApplication
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.overlay.OverlayService
import xyz.block.gosling.features.settings.SettingsStore
import xyz.block.gosling.shared.navigation.NavGraph
import xyz.block.gosling.shared.theme.GoslingTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1234
        private const val PREFS_NAME = "GoslingPrefs"
        private const val MESSAGES_KEY = "chat_messages"
    }

    private lateinit var settingsStore: SettingsStore
    private lateinit var accessibilitySettingsLauncher: ActivityResultLauncher<Intent>
    private var isAccessibilityEnabled by mutableStateOf(false)
    private val sharedPreferences by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var agentServiceManager: AgentServiceManager

    internal fun saveMessages(messages: List<ChatMessage>) {
        val json = messages.joinToString(",", "[", "]") { message ->
            """{"text":"${message.text}","isUser":${message.isUser},"timestamp":${message.timestamp}}"""
        }
        sharedPreferences.edit { putString(MESSAGES_KEY, json) }
    }

    internal fun loadMessages(): List<ChatMessage> {
        val json = sharedPreferences.getString(MESSAGES_KEY, "[]") ?: "[]"
        return try {
            json.removeSurrounding("[", "]")
                .split("},{")
                .filter { it.isNotEmpty() }
                .map { messageStr ->
                    val cleanStr = messageStr.removeSurrounding("{", "}")
                    val parts = cleanStr.split(",")
                    val text = parts[0].split(":")[1].trim('"')
                    val isUser = parts[1].split(":")[1].toBoolean()
                    val timestamp = parts[2].split(":")[1].toLong()
                    ChatMessage(text, isUser, timestamp)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("UseKtx")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)
        isAccessibilityEnabled = settingsStore.isAccessibilityEnabled
        agentServiceManager = AgentServiceManager(this)

        // Check for overlay permission
        if (!Settings.canDrawOverlays(this)) {
            // If not granted, request it
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        } else {
            // Start services only if we have overlay permission
            agentServiceManager.bindAndStartAgent { agent ->
                // Agent is now started as a foreground service and has a status listener set up
                Log.d("MainActivity", "Agent service started successfully")
            }

            startService(Intent(this, OverlayService::class.java))
        }

        // Register the launcher for accessibility settings
        accessibilitySettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            // Check accessibility permission when returning from settings
            val isEnabled = checkAccessibilityPermission(this)
            settingsStore.isAccessibilityEnabled = isEnabled
            isAccessibilityEnabled = isEnabled
            Log.d("Gosling", "MainActivity: Updated accessibility state after settings: $isEnabled")
        }

        enableEdgeToEdge()
        setContent {
            GoslingTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    settingsStore = settingsStore,
                    openAccessibilitySettings = { openAccessibilitySettings() },
                    isAccessibilityEnabled = isAccessibilityEnabled
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GoslingApplication.isMainActivityRunning = true
        OverlayService.getInstance()?.updateOverlayVisibility()

        // Check and save accessibility permission state
        val isEnabled = checkAccessibilityPermission(this)
        settingsStore.isAccessibilityEnabled = isEnabled
        isAccessibilityEnabled = isEnabled
        Log.d("Gosling", "MainActivity: Updated accessibility state on resume: $isEnabled")

        // Start services if overlay permission is granted
        if (Settings.canDrawOverlays(this)) {
            // Start the overlay service if it's not running
            if (OverlayService.getInstance() == null) {
                startService(Intent(this, OverlayService::class.java))
            }

            // Bind to the agent service
            agentServiceManager.bindAndStartAgent { agent ->
                // Agent is now started as a foreground service and has a status listener set up
                Log.d("MainActivity", "Agent service started successfully")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        GoslingApplication.isMainActivityRunning = false
        OverlayService.getInstance()?.updateOverlayVisibility()

        // Unbind the agent service to prevent leaks
        agentServiceManager.unbindAgent()
    }

    override fun onDestroy() {
        super.onDestroy()
        GoslingApplication.isMainActivityRunning = false
        OverlayService.getInstance()?.updateOverlayVisibility()

        // Ensure the agent service is unbound
        agentServiceManager.unbindAgent()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilitySettingsLauncher.launch(intent)
    }
}

fun checkAccessibilityPermission(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    val isEnabled = enabledServices?.contains(context.packageName) == true
    Log.d("Gosling", "Accessibility check: $enabledServices, enabled: $isEnabled")
    return isEnabled
}
