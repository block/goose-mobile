package xyz.block.gosling.features.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import xyz.block.gosling.features.agent.AiModel
import xyz.block.gosling.features.agent.ModelProvider

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val PREFS_NAME = "gosling_prefs"
        private const val SECURE_PREFS_NAME = "gosling_secure_prefs"
        private const val KEY_FIRST_TIME = "first_time"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_API_KEY_PREFIX = "api_key_"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val KEY_PROCESS_NOTIFICATIONS = "process_notifications"
        private const val KEY_MESSAGE_HANDLING_PREFERENCES = "message_handling_preferences"
        private const val KEY_ENABLE_APP_EXTENSIONS = "enable_app_extensions"
        private const val KEY_HANDLE_SCREENSHOTS = "handle_screenshots"
        private const val KEY_SCREENSHOT_HANDLING_PREFERENCES = "screenshot_handling_preferences"
        private val DEFAULT_LLM_MODEL = AiModel.AVAILABLE_MODELS.first().identifier
    }

    var isFirstTime: Boolean
        get() = prefs.getBoolean(KEY_FIRST_TIME, true)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_TIME, value) }

    var llmModel: String
        get() = prefs.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(value) = prefs.edit { putString(KEY_LLM_MODEL, value) }

    fun getApiKey(provider: ModelProvider): String {
        val key = "$KEY_API_KEY_PREFIX${provider.name}"
        return securePrefs.getString(key, "") ?: ""
    }

    fun setApiKey(provider: ModelProvider, value: String) {
        val key = "$KEY_API_KEY_PREFIX${provider.name}"
        securePrefs.edit { putString(key, value) }
    }

    var isAccessibilityEnabled: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ACCESSIBILITY_ENABLED, value) }

    var shouldProcessNotifications: Boolean
        get() = prefs.getBoolean(KEY_PROCESS_NOTIFICATIONS, false)
        set(value) = prefs.edit { putBoolean(KEY_PROCESS_NOTIFICATIONS, value) }
        
    var messageHandlingPreferences: String
        get() = prefs.getString(KEY_MESSAGE_HANDLING_PREFERENCES, "") ?: ""
        set(value) = prefs.edit { putString(KEY_MESSAGE_HANDLING_PREFERENCES, value) }

    var handleScreenshots: Boolean
        get() = prefs.getBoolean(KEY_HANDLE_SCREENSHOTS, false)
        set(value) = prefs.edit { putBoolean(KEY_HANDLE_SCREENSHOTS, value) }

    var screenshotHandlingPreferences: String
        get() = prefs.getString(KEY_SCREENSHOT_HANDLING_PREFERENCES, "") ?: ""
        set(value) = prefs.edit { putString(KEY_SCREENSHOT_HANDLING_PREFERENCES, value) }
        
    var enableAppExtensions: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_APP_EXTENSIONS, true) // Enabled by default
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_APP_EXTENSIONS, value) }
} 