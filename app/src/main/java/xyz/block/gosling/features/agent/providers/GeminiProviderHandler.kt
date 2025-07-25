package xyz.block.gosling.features.agent.providers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import xyz.block.gosling.features.agent.*
import java.lang.reflect.Method
import java.util.*

/**
 * Gemini provider handler for Google's Gemini models.
 * Handles Gemini-specific API format and tool calling conventions.
 */
class GeminiProviderHandler : LLMProviderHandler {
    
    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    override fun createToolDefinitions(toolMethods: List<Method>): SerializableToolDefinitions {
        val toolDefinitions = toolMethods.mapNotNull { method ->
            val tool = method.getAnnotation(Tool::class.java) ?: return@mapNotNull null
            val toolParameters = ToolParametersObject(
                properties = tool.parameters.associate { param ->
                    param.name to ToolParameter(
                        type = when (param.type.lowercase(Locale.getDefault())) {
                            "integer" -> "string" // Use string for integers
                            "boolean" -> "boolean"
                            "string" -> "string"
                            "double", "float" -> "number"
                            else -> "string"
                        },
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
        
        // For Gemini, we need to convert the tool definitions to Gemini format
        val functionDeclarations = toolDefinitions.map { toolDef ->
            GeminiFunctionDeclaration(
                name = toolDef.function.name,
                description = toolDef.function.description,
                parameters = if (toolDef.function.parameters.properties.isEmpty()) null else toolDef.function.parameters
            )
        }

        return SerializableToolDefinitions.GeminiTools(
            listOf(
                GeminiTool(
                    functionDeclarations = functionDeclarations
                )
            )
        )
    }
    
    override fun createRequest(
        modelIdentifier: String,
        messages: List<Message>,
        tools: SerializableToolDefinitions,
        apiKey: String?
    ): String {
        val combinedText = messages.joinToString("\n") {
            "${it.role}: ${it.content}"
        }

        val geminiTools = when (tools) {
            is SerializableToolDefinitions.GeminiTools -> tools.tools
            else -> emptyList()
        }

        val geminiRequest = GeminiRequest(
            contents = GeminiContent(
                parts = listOf(GeminiPart(text = combinedText))
            ),
            tools = geminiTools
        )

        return jsonFormat.encodeToString(geminiRequest)
    }
    
    override fun getApiUrl(modelIdentifier: String, apiKey: String?): String {
        return "https://generativelanguage.googleapis.com/v1beta/models/${modelIdentifier}:generateContent?key=$apiKey"
    }
    
    override fun getHeaders(apiKey: String?): Map<String, String> {
        // Gemini includes the API key in the URL, so no additional headers needed
        return emptyMap()
    }
    
    override fun parseResponse(
        response: JSONObject,
        requestDurationMs: Double
    ): Triple<String, List<InternalToolCall>?, Map<String, Double>> {
        val candidate = response.getJSONArray("candidates").getJSONObject(0)
        val content = candidate.getJSONObject("content")
        val text = content.getJSONArray("parts").getJSONObject(0)
            .optString("text", "Ok")

        val tools = content.optJSONArray("parts")?.let { parts ->
            List(parts.length()) { i ->
                val part = parts.getJSONObject(i)
                if (part.has("functionCall")) {
                    ToolHandler.fromJson(part)
                } else null
            }.filterNotNull()
        }
        
        val annotation = mapOf(
            "duration" to requestDurationMs
        )
        
        return Triple(text, tools, annotation)
    }
}
