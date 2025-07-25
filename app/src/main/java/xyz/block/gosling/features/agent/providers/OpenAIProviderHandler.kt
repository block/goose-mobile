package xyz.block.gosling.features.agent.providers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import xyz.block.gosling.features.agent.*
import java.lang.reflect.Method
import java.util.*

/**
 * OpenAI provider handler for GPT models.
 * Handles OpenAI-specific API format and tool calling conventions.
 */
class OpenAIProviderHandler : LLMProviderHandler {
    
    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    override fun createToolDefinitions(toolMethods: List<Method>): SerializableToolDefinitions {
        val toolDefinitions = toolMethods.mapNotNull { method ->
            val tool = method.getAnnotation(Tool::class.java) ?: return@mapNotNull null

            // Always create a ToolParametersObject, even for tools with no parameters
            val toolParameters = ToolParametersObject(
                properties = tool.parameters.associate { param ->
                    param.name to ToolParameter(
                        type = param.type,
                        description = param.description
                    )
                },
                required = tool.parameters
                    .filter { it.required }
                    .map { it.name }
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
        messages: List<Message>,
        tools: SerializableToolDefinitions,
        apiKey: String?
    ): String {
        val toolDefinitions = when (tools) {
            is SerializableToolDefinitions.OpenAITools -> tools.definitions
            else -> emptyList()
        }

        val openAIRequest = OpenAIRequest(
            model = modelIdentifier,
            messages = messages,
            temperature = if (modelIdentifier != "o3-mini") 0.1 else null,
            tools = toolDefinitions
        )

        return jsonFormat.encodeToString(openAIRequest)
    }
    
    override fun getApiUrl(modelIdentifier: String, apiKey: String?): String {
        return "https://api.openai.com/v1/chat/completions"
    }
    
    override fun getHeaders(apiKey: String?): Map<String, String> {
        return if (apiKey != null) {
            mapOf("Authorization" to "Bearer $apiKey")
        } else {
            emptyMap()
        }
    }
    
    override fun parseResponse(
        response: JSONObject,
        requestDurationMs: Double
    ): Triple<String, List<InternalToolCall>?, Map<String, Double>> {
        val assistantMessage = response.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message")
        val content = assistantMessage.optString("content", "Ok")
        val tools = assistantMessage.optJSONArray("tool_calls")?.let {
            List(it.length()) { i -> ToolHandler.fromJson(it.getJSONObject(i)) }
        }
        val usage = response.getJSONObject("usage")
        val annotation = mapOf(
            "duration" to requestDurationMs,
            "input_tokens" to usage.getInt("prompt_tokens").toDouble(),
            "output_tokens" to usage.getInt("completion_tokens").toDouble()
        )
        
        return Triple(content, tools, annotation)
    }
}
