package xyz.block.gosling.features.agent.providers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import xyz.block.gosling.features.agent.*
import java.lang.reflect.Method
import java.util.*

/**
 * OpenRouter provider handler for accessing multiple model providers through a single API.
 * Handles models from Anthropic, Meta, Mistral, Cohere, and many others via OpenRouter.
 */
class OpenRouterProviderHandler : LLMProviderHandler {
    
    private val jsonFormat = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    override fun createToolDefinitions(toolMethods: List<Method>): SerializableToolDefinitions {
        val toolDefinitions = toolMethods.mapNotNull { method ->
            val tool = method.getAnnotation(Tool::class.java) ?: return@mapNotNull null

            // Create tool parameters for OpenRouter (OpenAI-compatible format)
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

        // OpenRouter uses OpenAI-compatible format
        // Note: Not all models support tool calling - check OpenRouter docs for model-specific capabilities
        
        // Convert messages to OpenAI format, handling tool messages specially
        val openAIMessages = messages.map { message ->
            when (message.role) {
                "tool" -> {
                    // Tool messages need special handling for OpenRouter
                    val content = message.content?.firstOrNull()
                    val textContent = if (content is Content.Text) content.text else content.toString()
                    
                    // Create a tool message using JSON directly
                    org.json.JSONObject().apply {
                        put("role", "tool")
                        put("content", textContent)
                        put("tool_call_id", message.toolCallId ?: "")
                        put("name", message.name ?: "")
                    }
                }
                else -> {
                    // Use the original message structure for non-tool messages
                    message
                }
            }
        }
        
        // Create the request using JSONObject to avoid serialization issues
        val requestJson = org.json.JSONObject().apply {
            put("model", modelIdentifier)
            put("temperature", 0.1)
            
            // Handle messages array
            val messagesArray = org.json.JSONArray()
            openAIMessages.forEach { msg ->
                when (msg) {
                    is org.json.JSONObject -> messagesArray.put(msg)
                    is Message -> {
                        // Serialize the Message object manually
                        val messageJson = org.json.JSONObject().apply {
                            put("role", msg.role)
                            
                            // Handle content - simplify for OpenRouter compatibility
                            if (msg.content != null && msg.content.isNotEmpty()) {
                                // Check if we have mixed content (text + images)
                                val textContent = msg.content.filterIsInstance<Content.Text>()
                                val imageContent = msg.content.filterIsInstance<Content.ImageUrl>()
                                
                                if (imageContent.isNotEmpty()) {
                                    // Mixed content - use array format
                                    val contentArray = org.json.JSONArray()
                                    msg.content.forEach { content ->
                                        when (content) {
                                            is Content.Text -> {
                                                contentArray.put(org.json.JSONObject().apply {
                                                    put("type", "text")
                                                    put("text", content.text)
                                                })
                                            }
                                            is Content.ImageUrl -> {
                                                contentArray.put(org.json.JSONObject().apply {
                                                    put("type", "image_url")
                                                    put("image_url", org.json.JSONObject().apply {
                                                        put("url", content.imageUrl.url)
                                                    })
                                                })
                                            }
                                        }
                                    }
                                    put("content", contentArray)
                                } else if (textContent.isNotEmpty()) {
                                    // Text-only content - use simple string format
                                    val combinedText = textContent.joinToString(" ") { it.text }
                                    put("content", combinedText)
                                }
                            }
                            
                            // Handle tool calls
                            if (msg.toolCalls != null) {
                                val toolCallsArray = org.json.JSONArray()
                                msg.toolCalls.forEach { toolCall ->
                                    toolCallsArray.put(org.json.JSONObject().apply {
                                        put("id", toolCall.id)
                                        put("type", toolCall.type)
                                        put("function", org.json.JSONObject().apply {
                                            put("name", toolCall.function.name)
                                            put("arguments", toolCall.function.arguments)
                                        })
                                    })
                                }
                                put("tool_calls", toolCallsArray)
                            }
                            
                            // Handle other fields
                            msg.toolCallId?.let { put("tool_call_id", it) }
                            msg.name?.let { put("name", it) }
                        }
                        messagesArray.put(messageJson)
                    }
                }
            }
            put("messages", messagesArray)
            
            // Handle tools
            if (supportsToolCalling(modelIdentifier) && toolDefinitions.isNotEmpty()) {
                val toolsArray = org.json.JSONArray()
                toolDefinitions.forEach { toolDef ->
                    val toolJson = org.json.JSONObject().apply {
                        put("type", toolDef.type)
                        put("function", org.json.JSONObject().apply {
                            put("name", toolDef.function.name)
                            put("description", toolDef.function.description)
                            put("parameters", org.json.JSONObject().apply {
                                put("type", toolDef.function.parameters.type)
                                
                                // Handle properties
                                val propertiesJson = org.json.JSONObject()
                                toolDef.function.parameters.properties.forEach { (key, param) ->
                                    propertiesJson.put(key, org.json.JSONObject().apply {
                                        put("type", param.type)
                                        put("description", param.description)
                                    })
                                }
                                put("properties", propertiesJson)
                                
                                // Handle required fields
                                if (toolDef.function.parameters.required.isNotEmpty()) {
                                    val requiredArray = org.json.JSONArray()
                                    toolDef.function.parameters.required.forEach { requiredArray.put(it) }
                                    put("required", requiredArray)
                                }
                            })
                        })
                    }
                    toolsArray.put(toolJson)
                }
                put("tools", toolsArray)
            }
        }

        return requestJson.toString()
    }
    
    /**
     * Check if a model supports tool calling.
     * TODO: Update this list based on current OpenRouter documentation
     */
    private fun supportsToolCalling(modelIdentifier: String): Boolean {
        // Based on general knowledge - this should be updated with current OpenRouter docs
        return when {
            modelIdentifier.startsWith("anthropic/claude-") -> true // Supports Claude 3, 4, and future versions
            modelIdentifier.startsWith("openai/gpt-4") -> true
            modelIdentifier.startsWith("openai/gpt-3.5") -> true
            modelIdentifier.startsWith("meta-llama/llama-3.1") -> true // Some Llama models support it
            modelIdentifier.startsWith("mistralai/") -> true // Most Mistral models support it
            modelIdentifier.startsWith("cohere/command-r") -> true
            else -> false // Conservative default - assume no tool calling support
        }
    }
    
    override fun getApiUrl(modelIdentifier: String, apiKey: String?): String {
        return "https://openrouter.ai/api/v1/chat/completions"
    }
    
    override fun getHeaders(apiKey: String?): Map<String, String> {
        return if (apiKey != null) {
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "HTTP-Referer" to "https://goose-mobile.app", // Optional: for OpenRouter analytics
                "X-Title" to "Goose Mobile" // Optional: for OpenRouter analytics
            )
        } else {
            emptyMap()
        }
    }
    
    override fun parseResponse(
        response: JSONObject,
        requestDurationMs: Double
    ): Triple<String, List<InternalToolCall>?, Map<String, Double>> {
        // OpenRouter returns OpenAI-compatible responses for all models
        val assistantMessage = response.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message")
        val content = assistantMessage.optString("content", "Ok")
        
        // Handle tool calls with better error handling
        val tools = assistantMessage.optJSONArray("tool_calls")?.let { toolCallsArray ->
            try {
                List(toolCallsArray.length()) { i -> 
                    val toolCallJson = toolCallsArray.getJSONObject(i)
                    // Add logging to debug the tool call format
                    android.util.Log.d("OpenRouterProvider", "Tool call JSON: $toolCallJson")
                    ToolHandler.fromJson(toolCallJson)
                }
            } catch (e: Exception) {
                android.util.Log.e("OpenRouterProvider", "Error parsing tool calls: ${e.message}", e)
                android.util.Log.e("OpenRouterProvider", "Tool calls array: $toolCallsArray")
                // Return null instead of crashing
                null
            }
        }
        
        // OpenRouter provides usage statistics
        val usage = response.optJSONObject("usage")
        val annotation = if (usage != null) {
            mapOf(
                "duration" to requestDurationMs,
                "input_tokens" to usage.optInt("prompt_tokens", 0).toDouble(),
                "output_tokens" to usage.optInt("completion_tokens", 0).toDouble()
            )
        } else {
            mapOf("duration" to requestDurationMs)
        }
        
        return Triple(content, tools, annotation)
    }
}
