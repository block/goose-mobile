package xyz.block.gosling

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.block.gosling.features.agent.AiModel
import xyz.block.gosling.features.agent.ModelProvider
import xyz.block.gosling.features.agent.ondevice.OnDeviceModelManager
import xyz.block.gosling.features.agent.ondevice.LiteRTInference

@RunWith(AndroidJUnit4::class)
class OnDeviceModelTest {

    private val context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    companion object {
        private const val TAG = "OnDeviceModelTest"
    }

    @Before
    fun setup() {
        // Force reload model config so we pick up the latest JSON
        OnDeviceModelManager.reloadModelsConfig(context)
    }

    @Test
    fun knownModels_containsGemma4Only() {
        val models = OnDeviceModelManager.getKnownModels(context)

        Log.i(TAG, "Known models: ${models.map { it.id }}")
        assertEquals("Should have exactly 2 models", 2, models.size)
        assertTrue(
            "Should contain gemma4-e2b",
            models.any { it.id == "on-device/gemma4-e2b" }
        )
        assertTrue(
            "Should contain gemma4-e4b",
            models.any { it.id == "on-device/gemma4-e4b" }
        )
    }

    @Test
    fun downloadedModels_containsGemma4E2B() {
        val downloaded = OnDeviceModelManager.getDownloadedModels(context)

        Log.i(TAG, "Downloaded models: ${downloaded.map { it.id }}")
        assertTrue(
            "Gemma 4 E2B should be downloaded",
            downloaded.any { it.id == "on-device/gemma4-e2b" }
        )
    }

    @Test
    fun modelRegistration_registersDownloadedModels() {
        OnDeviceModelManager.registerDownloadedModels(context)

        val onDeviceModels = AiModel.getModelsForProvider(ModelProvider.ON_DEVICE_LITERT)
        Log.i(TAG, "Registered on-device models: ${onDeviceModels.map { it.identifier }}")
        assertTrue(
            "Should have at least one registered on-device model",
            onDeviceModels.isNotEmpty()
        )
        assertTrue(
            "Gemma 4 E2B should be registered",
            onDeviceModels.any { it.identifier == "on-device/gemma4-e2b" }
        )
    }

    @Test
    fun modelPath_resolvesForDownloadedModel() {
        val path = OnDeviceModelManager.getModelPath(context, "on-device/gemma4-e2b")

        Log.i(TAG, "Model path: $path")
        assertNotNull("Model path should not be null", path)
        assertTrue("Model path should end with .litertlm", path!!.endsWith(".litertlm"))
    }

    @Test
    fun contextLength_correctForGemma4() {
        val ctxE2B = OnDeviceModelManager.getContextLength(context, "on-device/gemma4-e2b")
        val ctxE4B = OnDeviceModelManager.getContextLength(context, "on-device/gemma4-e4b")

        assertEquals("Gemma 4 E2B context should be 32000", 32000, ctxE2B)
        assertEquals("Gemma 4 E4B context should be 32000", 32000, ctxE4B)
    }

    @Test
    fun liteRTInference_isAvailable() {
        assertTrue("LiteRT-LM should be available", LiteRTInference.isAvailable())
    }

    @Test
    fun liteRTInference_initializeAndChat() {
        val modelPath = OnDeviceModelManager.getModelPath(context, "on-device/gemma4-e2b")
        assertNotNull("Model must be downloaded to run this test", modelPath)

        Log.i(TAG, "Initializing LiteRT engine with model: $modelPath")
        LiteRTInference.initialize(modelPath!!, context.cacheDir.path)

        Log.i(TAG, "Creating conversation...")
        val conversation = LiteRTInference.createConversation(
            systemInstruction = "You are a helpful assistant. Reply briefly.",
            tools = emptyList()
        )

        Log.i(TAG, "Sending test message...")
        val response = conversation.sendMessage("Say hello in one sentence.")
        val responseText = response.toString()

        Log.i(TAG, "Response: $responseText")
        assertTrue(
            "Response should not be empty",
            responseText.isNotBlank()
        )

        conversation.close()
        LiteRTInference.close()
        Log.i(TAG, "Test complete")
    }
}
