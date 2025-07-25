package xyz.block.gosling.features.agent.providers

import org.json.JSONObject
import xyz.block.gosling.features.agent.InternalToolCall
import xyz.block.gosling.features.agent.Message
import xyz.block.gosling.features.agent.SerializableToolDefinitions
import java.lang.reflect.Method

/**
 * Interface for handling provider-specific LLM operations.
 * Each provider (OpenAI, Gemini, Anthropic, etc.) implements this interface
 * to handle their specific API formats and tool calling conventions.
 */
interface LLMProviderHandler {
    
    /**
     * Creates provider-specific tool definitions from generic tool methods.
     * 
     * @param toolMethods List of annotated tool methods from ToolHandler
     * @return SerializableToolDefinitions formatted for this provider
     */
    fun createToolDefinitions(toolMethods: List<Method>): SerializableToolDefinitions
    
    /**
     * Creates a provider-specific request body for the LLM API.
     * 
     * @param modelIdentifier The specific model identifier (e.g., "gpt-4o", "gemini-2.0-flash")
     * @param messages List of conversation messages
     * @param tools Tool definitions for this provider
     * @param apiKey API key for authentication (may be null for some providers)
     * @return JSON string representing the request body
     */
    fun createRequest(
        modelIdentifier: String,
        messages: List<Message>,
        tools: SerializableToolDefinitions,
        apiKey: String?
    ): String
    
    /**
     * Gets the API endpoint URL for this provider.
     * 
     * @param modelIdentifier The specific model identifier
     * @param apiKey API key (used for providers like Gemini that include key in URL)
     * @return Complete API endpoint URL
     */
    fun getApiUrl(modelIdentifier: String, apiKey: String?): String
    
    /**
     * Gets provider-specific HTTP headers.
     * 
     * @param apiKey API key for authentication
     * @return Map of headers to include in the request
     */
    fun getHeaders(apiKey: String?): Map<String, String>
    
    /**
     * Parses the provider-specific response and extracts relevant information.
     * 
     * @param response JSON response from the provider's API
     * @param requestDurationMs Duration of the request in milliseconds
     * @return Triple of (assistant_reply, tool_calls, stats_annotation)
     */
    fun parseResponse(
        response: JSONObject,
        requestDurationMs: Double
    ): Triple<String, List<InternalToolCall>?, Map<String, Double>>
}
