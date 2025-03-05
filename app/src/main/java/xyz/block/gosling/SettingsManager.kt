package xyz.block.gosling

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gosling_prefs"
        private const val KEY_FIRST_TIME = "first_time"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val KEY_PROCESS_NOTIFICATIONS = "process_notifications"
        private val DEFAULT_LLM_MODEL = AiModel.AVAILABLE_MODELS.first().identifier
    }

    var isFirstTime: Boolean
        get() = prefs.getBoolean(KEY_FIRST_TIME, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_TIME, value).apply()

    var llmModel: String
        get() = prefs.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(value) = prefs.edit().putString(KEY_LLM_MODEL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var isAccessibilityEnabled: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    var shouldProcessNotifications: Boolean
        get() = prefs.getBoolean(KEY_PROCESS_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_PROCESS_NOTIFICATIONS, value).apply()

    fun hasValidConfig(): Boolean {
        return llmModel.isNotEmpty() && apiKey.isNotEmpty()
    }
} 
