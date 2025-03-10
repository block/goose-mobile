package xyz.block.gosling

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.onboarding.Onboarding
import xyz.block.gosling.features.settings.SettingsScreen
import xyz.block.gosling.features.settings.SettingsStore
import xyz.block.gosling.ui.theme.GoslingTheme

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
        val json = messages.map { message ->
            """{"text":"${message.text}","isUser":${message.isUser},"timestamp":${message.timestamp}}"""
        }.joinToString(",", "[", "]")
        sharedPreferences.edit().putString(MESSAGES_KEY, json).apply()
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
                Uri.parse("package:$packageName")
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        settingsStore = settingsStore,
                        openAccessibilitySettings = { openAccessibilitySettings() },
                        isAccessibilityEnabled = isAccessibilityEnabled
                    )
                }
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

        // Start services if overlay permission was just granted
        if (Settings.canDrawOverlays(this)) {
            agentServiceManager.bindAndStartAgent { agent ->
                // Agent is now started as a foreground service and has a status listener set up
                Log.d("MainActivity", "Agent service started successfully")
            }

            startService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        GoslingApplication.isMainActivityRunning = false
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        GoslingApplication.isMainActivityRunning = false
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilitySettingsLauncher.launch(intent)
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    settingsStore: SettingsStore,
    openAccessibilitySettings: () -> Unit,
    isAccessibilityEnabled: Boolean
) {
    var showSetup by remember { mutableStateOf(settingsStore.isFirstTime) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as MainActivity
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(activity.loadMessages())
        }
    }

    // Effect to save messages when they change
    LaunchedEffect(messages.size) {
        activity.saveMessages(messages)
    }

    if (showSetup) {
        Onboarding(
            onSetupComplete = { showSetup = false },
            modifier = modifier,
            settingsStore = settingsStore,
            openAccessibilitySettings = openAccessibilitySettings,
            isAccessibilityEnabled = isAccessibilityEnabled
        )
    } else if (showSettings) {
        SettingsScreen(
            settingsStore = settingsStore,
            onBack = { showSettings = false },
            openAccessibilitySettings = openAccessibilitySettings,
            isAccessibilityEnabled = isAccessibilityEnabled
        )
    } else {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Settings button in top-right corner with badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!isAccessibilityEnabled) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text("!")
                    }
                }
            }

            // Main UI content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoslingUI(
                    context = LocalContext.current,
                    messages = messages,
                    onMessageAdded = { messages.add(it) },
                    onMessageRemoved = { messages.remove(it) }
                )
            }
        }
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
