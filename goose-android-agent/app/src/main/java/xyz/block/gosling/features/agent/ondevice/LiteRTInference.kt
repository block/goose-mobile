package xyz.block.gosling.features.agent.ondevice

import android.content.Context
import android.os.Build
import android.os.Trace
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
import java.io.File

/**
 * Wrapper around LiteRT-LM Engine for on-device LLM inference.
 *
 * LiteRT-LM provides native Android support with NPU/GPU acceleration
 * and built-in function calling via the Conversation API.
 *
 * Usage:
 * 1. Call initialize() with a model path
 * 2. Call createConversation() to start a chat session with tools
 * 3. Call sendMessage() on the conversation
 * 4. Call close() when done
 */
object LiteRTInference {
    private const val TAG = "LiteRTInference"
    private var engine: Engine? = null
    private var currentModelPath: String? = null

    /**
     * Whether the device is likely to support LiteRT-LM's GPU backend.
     * Computed once and cached: the answer doesn't change at runtime, and
     * each probe shells out to `Build` + a few file existence checks.
     */
    private val gpuSupported: Boolean by lazy { detectGpuSupport() }

    /**
     * Check if LiteRT-LM is available at runtime.
     */
    fun isAvailable(): Boolean {
        return try {
            Class.forName("com.google.ai.edge.litertlm.Engine")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun detectGpuSupport(): Boolean {
        if (isAndroidEmulator()) {
            Log.i(TAG, "Android emulator detected (hw=${Build.HARDWARE}); using CPU backend")
            return false
        }
        val openCl = OPENCL_LIB_PATHS.firstOrNull { File(it).exists() }
        return if (openCl != null) {
            Log.i(TAG, "OpenCL runtime present at $openCl; will use GPU backend")
            true
        } else {
            Log.i(TAG, "No OpenCL runtime found; using CPU backend")
            false
        }
    }

    private fun isAndroidEmulator(): Boolean {
        val hw = Build.HARDWARE
        if (hw == "ranchu" || hw == "goldfish") return true
        val fp = Build.FINGERPRINT
        if (fp.startsWith("generic") || fp.startsWith("unknown")) return true
        if (Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for")) return true
        if (Build.PRODUCT.startsWith("sdk_") || Build.PRODUCT == "sdk") return true
        return false
    }

    private val OPENCL_LIB_PATHS = listOf(
        "/system/lib64/libOpenCL.so",
        "/vendor/lib64/libOpenCL.so",
        "/system/vendor/lib64/libOpenCL.so",
        "/system/lib/libOpenCL.so",
        "/vendor/lib/libOpenCL.so",
        "/system/vendor/lib/libOpenCL.so",
    )

    /**
     * Initialize the engine with a model file.
     * Safe to call multiple times - only reloads if the model path changed.
     *
     * @param modelPath Absolute path to a .litertlm model file
     * @param cacheDir Cache directory for faster subsequent loads
     */
    @Synchronized
    fun initialize(modelPath: String, cacheDir: String) {
        if (engine != null && currentModelPath == modelPath) {
            Log.d(TAG, "Engine already initialized with same model")
            return
        }

        close()

        // Pick GPU when the device looks capable, otherwise CPU.
        // Backend.GPU() SIGSEGVs inside nativeCreateEngine when the OpenCL
        // runtime is missing (e.g. the Android emulator), and a JNI SIGSEGV
        // can't be caught from Kotlin — so the choice has to be made before
        // we hand the backend to LiteRT-LM.
        val backend = if (gpuSupported) {
            Log.i(TAG, "Initializing LiteRT-LM engine on GPU: $modelPath")
            Backend.GPU()
        } else {
            Log.i(TAG, "Initializing LiteRT-LM engine on CPU: $modelPath")
            Backend.CPU()
        }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir
        )

        val initStart = System.currentTimeMillis()
        Trace.beginSection("LiteRT.engineInit")
        try {
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            currentModelPath = modelPath
        } finally {
            Trace.endSection()
        }
        Log.i(TAG, "LiteRT-LM engine initialized in ${System.currentTimeMillis() - initStart} ms")
    }

    /**
     * Create a conversation with optional tool support.
     *
     * @param systemInstruction System prompt text
     * @param tools List of OpenApiTool implementations (empty for no tool calling)
     * @return A Conversation that can send/receive messages
     */
    fun createConversation(
        systemInstruction: String,
        tools: List<OpenApiTool> = emptyList()
    ): Conversation {
        val eng = engine ?: throw RuntimeException("LiteRT-LM engine not initialized. Call initialize() first.")

        val config = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            tools = tools.map { tool(it) },
            automaticToolCalling = false,
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.1)
        )

        Trace.beginSection("LiteRT.createConversation")
        try {
            return eng.createConversation(config)
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Close the engine and free resources.
     */
    @Synchronized
    fun close() {
        engine?.let {
            Log.i(TAG, "Closing LiteRT-LM engine")
            it.close()
        }
        engine = null
        currentModelPath = null
    }
}
