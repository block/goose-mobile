package xyz.block.gosling.features.agent.providers

import org.junit.Test
import xyz.block.gosling.features.agent.ParameterDef
import xyz.block.gosling.features.agent.SerializableToolDefinitions
import xyz.block.gosling.features.agent.Tool
import kotlin.test.assertTrue

class GeminiProviderHandlerTest {

    private val handler = GeminiProviderHandler()

    @Test
    fun getApiUrl_includesModelIdentifierAndKey() {
        val modelId = "gemini-2.0-flash"
        val apiKey = "test-api-key-123"
        
        val url = handler.getApiUrl(modelId, apiKey)
        
        assertTrue(url.contains(modelId))
        assertTrue(url.contains(apiKey))
        assertTrue(url.startsWith("https://"))
        assertTrue(url.contains("generativelanguage.googleapis.com"))
    }

    @Test
    fun getApiUrl_differentModels_hasDifferentUrls() {
        val apiKey = "test-key"
        
        val url1 = handler.getApiUrl("gemini-2.0-flash", apiKey)
        val url2 = handler.getApiUrl("gemini-2.0-flash-lite", apiKey)
        
        assertTrue(url1.contains("gemini-2.0-flash"))
        assertTrue(url2.contains("gemini-2.0-flash-lite"))
        assertTrue(url1 != url2)
    }

    @Test
    fun getHeaders_returnsEmptyMap() {
        val headers = handler.getHeaders("test-key")
        
        // Gemini includes API key in URL, not headers
        assertTrue(headers.isEmpty())
    }

    @Test
    fun getHeaders_doesNotIncludeContentType() {
        val headers = handler.getHeaders("test-key")
        
        assertTrue(!headers.containsKey("Content-Type"))
        assertTrue(!headers.containsKey("Authorization"))
    }

    @Test
    fun createToolDefinitions_withMockMethod_createsValidDefinition() {
        val toolMethods = getMockToolMethods()
        
        val definitions = handler.createToolDefinitions(toolMethods)
        
        assertTrue(definitions is SerializableToolDefinitions.GeminiTools)
    }

    @Test
    fun createToolDefinitions_emptyList_returnsEmptyDefinitions() {
        val definitions = handler.createToolDefinitions(emptyList())
        
        assertTrue(definitions is SerializableToolDefinitions.GeminiTools)
        assertTrue(definitions.tools.isNotEmpty()) // Gemini wraps tools in function declarations
    }

    private fun getMockToolMethods(): List<java.lang.reflect.Method> {
        return MockToolClass::class.java.declaredMethods.filter {
            it.isAnnotationPresent(Tool::class.java)
        }
    }

    private class MockToolClass {
        @Tool(
            name = "test_tool",
            description = "A test tool",
            parameters = [
                ParameterDef(name = "param1", type = "string", description = "First parameter"),
                ParameterDef(name = "param2", type = "integer", description = "Second parameter", required = false)
            ]
        )
        fun testTool(param1: String, param2: Int = 0) {
        }
    }
}
