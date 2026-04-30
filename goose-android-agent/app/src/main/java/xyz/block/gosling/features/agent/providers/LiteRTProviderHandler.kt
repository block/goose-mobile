package xyz.block.gosling.features.agent.providers

import android.os.Trace
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import org.json.JSONObject
import xyz.block.gosling.features.agent.InternalToolCall
import xyz.block.gosling.features.agent.SerializableToolDefinitions
import xyz.block.gosling.features.agent.Tool
import xyz.block.gosling.features.agent.ToolDefinition
import xyz.block.gosling.features.agent.ToolFunctionDefinition
import xyz.block.gosling.features.agent.ToolParameter
import xyz.block.gosling.features.agent.ToolParametersObject
import xyz.block.gosling.features.agent.ondevice.LiteRTInference
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong
import xyz.block.gosling.features.agent.Message as AgentMessage

/**
 * LLMProviderHandler for on-device inference via Google LiteRT-LM.
 *
 * Uses LiteRT-LM's Conversation API with automaticToolCalling=false,
 * bridging our tool definitions to OpenApiTool format. The LiteRT-LM
 * engine handles prompt formatting, tokenization, and model-specific
 * chat templates internally.
 *
 * Advantages over llama.cpp path:
 * - NPU/GPU acceleration via Android hardware delegates
 * - Native function calling support (no GBNF grammar needed)
 * - Purpose-built models like FunctionGemma
 * - Pure Kotlin, no JNI/NDK
 */
class LiteRTProviderHandler(
    private val modelPath: String? = null,
    private val cacheDir: String? = null
) : LLMProviderHandler {

    companion object {
        private const val TAG = "LiteRTProvider"
        private val callCounter = AtomicLong(0)

        // Essential tools for on-device models
        private val ESSENTIAL_TOOLS = setOf(
            "getUiHierarchy", "click", "swipe", "enterText",
            "startApp", "home", "openUrl", "webSearch"
        )

        // Conversation is reused across agent turns so LiteRT-LM keeps its
        // KV cache and only prefills new tokens. Closed only when the logical
        // session changes (new task, new system prompt, or new tool set).
        private var activeConversation: Conversation? = null
        private var activeSessionKey: String? = null
        private var sentInputCount: Int = 0  // user+tool messages already fed

        @Synchronized
        fun closeActiveConversation() {
            activeConversation?.close()
            activeConversation = null
            activeSessionKey = null
            sentInputCount = 0
        }
    }

    override fun isLocalProvider() = true

    @Synchronized
    override fun executeLocal(
        modelIdentifier: String,
        messages: List<AgentMessage>,
        tools: SerializableToolDefinitions
    ): Triple<String, List<InternalToolCall>?, Map<String, Double>> {
        val path = modelPath
            ?: throw RuntimeException("No model path configured for LiteRT inference")

        if (!LiteRTInference.isAvailable()) {
            throw RuntimeException("LiteRT-LM not available. Check that the dependency is included.")
        }

        val startTime = System.currentTimeMillis()

        // Initialize engine (no-op if already loaded with same model)
        LiteRTInference.initialize(path, cacheDir ?: "")

        // Build OpenApiTool bridges from our tool definitions
        val openApiTools = buildOpenApiTools(tools)

        // Extract & condense the system instruction
        val systemInstruction = messages
            .filter { it.role == "system" }
            .joinToString("\n") { msg ->
                msg.content?.filterIsInstance<xyz.block.gosling.features.agent.Content.Text>()
                    ?.joinToString("") { it.text } ?: ""
            }
            .let { condenseSystemPrompt(it) }

        // Filter to the messages that actually drive new generations.
        // Assistant messages are produced by the engine itself and are stored
        // inside the Conversation, so we don't re-feed them.
        val inputMessages = messages.filter { it.role == "user" || it.role == "tool" }
        if (inputMessages.isEmpty()) {
            throw RuntimeException("No messages to send")
        }

        // Session key identifies a logical chat. Anchored on the first user
        // message text + the system instruction + the tool set. If any of
        // these changes, we treat it as a new session and reset.
        val firstUserText = textOf(inputMessages.first())
        val toolKey = openApiTools.joinToString(",") { it.getToolDescriptionJsonString() }
        val sessionKey = (firstUserText + "|" + systemInstruction + "|" + toolKey).hashCode().toString()

        val isContinuation = activeConversation != null &&
            activeSessionKey == sessionKey &&
            sentInputCount in 1..inputMessages.size

        val conversation: Conversation
        val toSend: List<AgentMessage>

        if (isContinuation) {
            conversation = activeConversation!!
            toSend = inputMessages.drop(sentInputCount)
            if (toSend.isEmpty()) {
                throw RuntimeException("LiteRT continuation called with no new messages to send")
            }
        } else {
            activeConversation?.close()
            conversation = LiteRTInference.createConversation(systemInstruction, openApiTools)
            activeConversation = conversation
            activeSessionKey = sessionKey
            sentInputCount = 0
            toSend = inputMessages
        }

        Trace.beginSection(if (isContinuation) "LiteRT.executeLocal/cont" else "LiteRT.executeLocal/new")
        try {
            var response: Message? = null
            var lastSendMs = 0L
            for (msg in toSend) {
                val sendStart = System.currentTimeMillis()
                response = sendOne(conversation, msg)
                lastSendMs = System.currentTimeMillis() - sendStart
                sentInputCount++
                Log.i(TAG, "sendMessage[role=${msg.role}, idx=${sentInputCount - 1}] took ${lastSendMs} ms")
            }
            val finalResponse = response
                ?: throw RuntimeException("LiteRT inference produced no response")

            val durationMs = (System.currentTimeMillis() - startTime).toDouble()
            val responseText = finalResponse.toString()
            val approxOutputTokens = (responseText.length / 4).coerceAtLeast(1)
            val outputToksPerSec = approxOutputTokens.toDouble() / (lastSendMs / 1000.0).coerceAtLeast(0.001)
            val toolCalls = if (finalResponse.toolCalls.isNotEmpty()) {
                finalResponse.toolCalls.map { tc ->
                    InternalToolCall(
                        toolId = "litert_call_${callCounter.incrementAndGet()}",
                        name = tc.name,
                        arguments = mapToJSONObject(tc.arguments)
                    )
                }
            } else null
            val stats = mapOf("duration" to durationMs / 1000.0)

            Log.i(
                TAG,
                "LiteRT inference complete (cont=$isContinuation, sent=${toSend.size}, total=$sentInputCount, " +
                    "lastSendMs=$lastSendMs, ~tok/s=${"%.1f".format(outputToksPerSec)}): ${responseText.take(100)}..."
            )
            return Triple(responseText, toolCalls, stats)
        } catch (e: Exception) {
            // Conversation state may be inconsistent after a failure; reset.
            closeActiveConversation()
            throw RuntimeException("LiteRT inference failed: ${e.message}", e)
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Send a single new user or tool message and return the model's reply.
     */
    private fun sendOne(conversation: Conversation, msg: AgentMessage): Message {
        Trace.beginSection("LiteRT.sendMessage/${msg.role}")
        try {
            return when (msg.role) {
                "tool" -> {
                    val toolName = msg.name ?: "unknown"
                    val raw = textOf(msg)
                    val truncated = if (raw.length > 1000) raw.take(1000) + "... (truncated)" else raw
                    conversation.sendMessage(
                        Message.tool(Contents.of(listOf(Content.ToolResponse(toolName, truncated))))
                    )
                }
                "user" -> conversation.sendMessage(textOf(msg))
                else -> error("Unexpected role for sendOne: ${msg.role}")
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun textOf(msg: AgentMessage): String =
        msg.content
            ?.filterIsInstance<xyz.block.gosling.features.agent.Content.Text>()
            ?.joinToString("") { it.text } ?: ""

    /**
     * Build OpenApiTool instances from our SerializableToolDefinitions.
     */
    private fun buildOpenApiTools(tools: SerializableToolDefinitions): List<OpenApiTool> {
        val definitions = when (tools) {
            is SerializableToolDefinitions.OpenAITools -> tools.definitions
            is SerializableToolDefinitions.GeminiTools -> return emptyList()
        }

        // Filter to essential tools
        return definitions
            .filter { it.function.name in ESSENTIAL_TOOLS }
            .map { def -> ToolDefinitionOpenApiTool(def) }
    }

    /**
     * Bridges a ToolDefinition to LiteRT-LM's OpenApiTool interface.
     */
    private class ToolDefinitionOpenApiTool(
        private val definition: ToolDefinition
    ) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String {
            val json = JSONObject()
            json.put("name", definition.function.name)
            json.put("description", definition.function.description)

            val params = JSONObject()
            params.put("type", "object")

            val properties = JSONObject()
            for ((name, param) in definition.function.parameters.properties) {
                val propObj = JSONObject()
                propObj.put("type", param.type)
                propObj.put("description", param.description)
                properties.put(name, propObj)
            }
            params.put("properties", properties)

            val required = org.json.JSONArray()
            for (req in definition.function.parameters.required) {
                required.put(req)
            }
            params.put("required", required)

            json.put("parameters", params)
            return json.toString()
        }

        override fun execute(paramsJsonString: String): String {
            // Not called when automaticToolCalling = false
            return ""
        }
    }

    /**
     * Condense the system prompt to save tokens for on-device models.
     * Keeps installed apps, screen resolution, and user memories from the original.
     * Drops verbose examples, form-filling instructions, and repetitive guidance.
     *
     * `internal` so unit tests can exercise the extraction logic without going
     * through the LiteRT engine.
     */
    internal fun condenseSystemPrompt(original: String): String {
        return buildString {
            append("You are an autonomous phone assistant. Complete tasks using the available tools. ")
            append("Do not ask the user for help - solve problems independently. ")
            append("Do NOT guess or make up URLs or information - use tools to find what you need. ")
            append("Call getUiHierarchy to see the screen. Use click, swipe, enterText to interact. ")
            append("Use startApp to open apps by name. Use home to go to the home screen. ")
            append("After each action, check the result and continue until the task is done.\n\n")

            // Extract and keep the installed apps section
            val appsMarker = "The phone has the following apps installed:"
            val appsIdx = original.indexOf(appsMarker)
            if (appsIdx != -1) {
                val afterApps = original.substring(appsIdx)
                val endIdx = afterApps.indexOf("\nBefore getting started")
                    .takeIf { it > 0 } ?: afterApps.indexOf("\n\nIf after")
                    .takeIf { it > 0 } ?: afterApps.length
                append(afterApps.substring(0, endIdx).trim())
                append("\n\n")
            }

            // Extract screen resolution
            val resPattern = Regex("""screen resolution of (\d+x\d+) pixels""")
            resPattern.find(original)?.let {
                append("Screen resolution: ${it.groupValues[1]} pixels.\n")
            }

            // Extract user memories if present
            val memoriesMarker = "USER PREFS, FACTS AND IMPORTANT MEMORIES"
            val memIdx = original.indexOf(memoriesMarker)
            if (memIdx != -1) {
                val memEnd = original.indexOf("\n\nThe phone has", memIdx)
                    .takeIf { it > 0 } ?: (memIdx + 500).coerceAtMost(original.length)
                append(original.substring(memIdx, memEnd).trim())
                append("\n")
            }
        }
    }

    private fun mapToJSONObject(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, value)
        }
        return json
    }

    // HTTP methods - no-ops for local provider

    override fun createToolDefinitions(toolMethods: List<Method>): SerializableToolDefinitions {
        val toolDefinitions = toolMethods.mapNotNull { method ->
            val tool = method.getAnnotation(Tool::class.java) ?: return@mapNotNull null
            if (tool.name !in ESSENTIAL_TOOLS) return@mapNotNull null

            val toolParameters = ToolParametersObject(
                properties = tool.parameters.associate { param ->
                    param.name to ToolParameter(
                        type = param.type,
                        description = param.description
                    )
                },
                required = tool.parameters.filter { it.required }.map { it.name }
            )

            ToolDefinition(
                function = ToolFunctionDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = toolParameters
                )
            )
        }

        return SerializableToolDefinitions.OpenAITools(toolDefinitions)
    }

    override fun createRequest(
        modelIdentifier: String,
        messages: List<AgentMessage>,
        tools: SerializableToolDefinitions,
        apiKey: String?
    ): String = ""

    override fun getApiUrl(modelIdentifier: String, apiKey: String?): String = ""

    override fun getHeaders(apiKey: String?): Map<String, String> = emptyMap()

    override fun parseResponse(
        response: JSONObject,
        requestDurationMs: Double
    ): Triple<String, List<InternalToolCall>?, Map<String, Double>> {
        val text = response.optString("text", "")
        val stats = mapOf("duration" to requestDurationMs)

        val toolCallsArray = response.optJSONArray("tool_calls")
        val toolCalls = if (toolCallsArray != null && toolCallsArray.length() > 0) {
            List(toolCallsArray.length()) { i ->
                val tc = toolCallsArray.getJSONObject(i)
                InternalToolCall(
                    toolId = tc.getString("id"),
                    name = tc.getString("name"),
                    arguments = tc.getJSONObject("arguments")
                )
            }
        } else null

        return Triple(text, toolCalls, stats)
    }
}
