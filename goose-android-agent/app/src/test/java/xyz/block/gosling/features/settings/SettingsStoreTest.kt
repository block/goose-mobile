package xyz.block.gosling.features.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.block.gosling.FakeAndroidKeyStore
import xyz.block.gosling.features.agent.AiModel
import xyz.block.gosling.features.agent.ModelProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {

    private lateinit var context: Context
    private lateinit var settingsStore: SettingsStore

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setUp()
        context = ApplicationProvider.getApplicationContext()
        settingsStore = SettingsStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("gosling_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        
        context.getSharedPreferences("gosling_secure_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        
        FakeAndroidKeyStore.tearDown()
    }

    @Test
    fun isFirstTime_defaultValue_isTrue() {
        val result = settingsStore.isFirstTime
        
        assertTrue(result)
    }

    @Test
    fun isFirstTime_afterSettingToFalse_returnsFalse() {
        settingsStore.isFirstTime = false
        
        assertFalse(settingsStore.isFirstTime)
    }

    @Test
    fun llmModel_defaultValue_isFirstAvailableModel() {
        val result = settingsStore.llmModel
        
        assertEquals(AiModel.AVAILABLE_MODELS.first().identifier, result)
    }

    @Test
    fun llmModel_afterSetting_returnsSetValue() {
        settingsStore.llmModel = "gpt-4o"
        
        assertEquals("gpt-4o", settingsStore.llmModel)
    }

    @Test
    fun apiKey_openAI_defaultValue_isEmpty() {
        val result = settingsStore.getApiKey(ModelProvider.OPENAI)
        
        assertEquals("", result)
    }

    @Test
    fun apiKey_afterSetting_returnsSetValue() {
        settingsStore.setApiKey(ModelProvider.OPENAI, "test-api-key-123")
        
        assertEquals("test-api-key-123", settingsStore.getApiKey(ModelProvider.OPENAI))
    }

    @Test
    fun apiKey_differentProviders_storesSeparately() {
        settingsStore.setApiKey(ModelProvider.OPENAI, "openai-key")
        settingsStore.setApiKey(ModelProvider.GEMINI, "gemini-key")
        settingsStore.setApiKey(ModelProvider.OPENROUTER, "openrouter-key")
        
        assertEquals("openai-key", settingsStore.getApiKey(ModelProvider.OPENAI))
        assertEquals("gemini-key", settingsStore.getApiKey(ModelProvider.GEMINI))
        assertEquals("openrouter-key", settingsStore.getApiKey(ModelProvider.OPENROUTER))
    }

    @Test
    fun isAccessibilityEnabled_defaultValue_isFalse() {
        val result = settingsStore.isAccessibilityEnabled
        
        assertFalse(result)
    }

    @Test
    fun isAccessibilityEnabled_afterSettingToTrue_returnsTrue() {
        settingsStore.isAccessibilityEnabled = true
        
        assertTrue(settingsStore.isAccessibilityEnabled)
    }

    @Test
    fun shouldProcessNotifications_defaultValue_isFalse() {
        val result = settingsStore.shouldProcessNotifications
        
        assertFalse(result)
    }

    @Test
    fun shouldProcessNotifications_afterToggling_persists() {
        settingsStore.shouldProcessNotifications = true
        
        assertTrue(settingsStore.shouldProcessNotifications)
        
        settingsStore.shouldProcessNotifications = false
        
        assertFalse(settingsStore.shouldProcessNotifications)
    }

    @Test
    fun messageHandlingPreferences_defaultValue_isEmpty() {
        val result = settingsStore.messageHandlingPreferences
        
        assertEquals("", result)
    }

    @Test
    fun messageHandlingPreferences_afterSetting_returnsSetValue() {
        val preferences = "Only respond to messages from John"
        settingsStore.messageHandlingPreferences = preferences
        
        assertEquals(preferences, settingsStore.messageHandlingPreferences)
    }

    @Test
    fun handleScreenshots_defaultValue_isFalse() {
        val result = settingsStore.handleScreenshots
        
        assertFalse(result)
    }

    @Test
    fun handleScreenshots_afterSettingToTrue_returnsTrue() {
        settingsStore.handleScreenshots = true
        
        assertTrue(settingsStore.handleScreenshots)
    }

    @Test
    fun screenshotHandlingPreferences_defaultValue_isEmpty() {
        val result = settingsStore.screenshotHandlingPreferences
        
        assertEquals("", result)
    }

    @Test
    fun screenshotHandlingPreferences_afterSetting_returnsSetValue() {
        val preferences = "Extract text from screenshots"
        settingsStore.screenshotHandlingPreferences = preferences
        
        assertEquals(preferences, settingsStore.screenshotHandlingPreferences)
    }

    @Test
    fun enableAppExtensions_defaultValue_isTrue() {
        val result = settingsStore.enableAppExtensions
        
        assertTrue(result)
    }

    @Test
    fun enableAppExtensions_afterSettingToFalse_returnsFalse() {
        settingsStore.enableAppExtensions = false
        
        assertFalse(settingsStore.enableAppExtensions)
    }

    @Test
    fun userMemories_defaultValue_isEmpty() {
        val result = settingsStore.userMemories
        
        assertEquals("", result)
    }

    @Test
    fun userMemories_afterSetting_returnsSetValue() {
        val memories = "User prefers dark mode. User lives in New York."
        settingsStore.userMemories = memories
        
        assertEquals(memories, settingsStore.userMemories)
    }

    @Test
    fun multipleSettings_canBeSetAndRetrieved() {
        settingsStore.isFirstTime = false
        settingsStore.llmModel = "gpt-4o"
        settingsStore.setApiKey(ModelProvider.OPENAI, "test-key")
        settingsStore.isAccessibilityEnabled = true
        settingsStore.shouldProcessNotifications = true
        settingsStore.enableAppExtensions = false
        
        assertFalse(settingsStore.isFirstTime)
        assertEquals("gpt-4o", settingsStore.llmModel)
        assertEquals("test-key", settingsStore.getApiKey(ModelProvider.OPENAI))
        assertTrue(settingsStore.isAccessibilityEnabled)
        assertTrue(settingsStore.shouldProcessNotifications)
        assertFalse(settingsStore.enableAppExtensions)
    }
}
