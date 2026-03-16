package xyz.block.gosling.features.agent.providers

import org.junit.Test
import xyz.block.gosling.features.agent.ParameterDef
import xyz.block.gosling.features.agent.SerializableToolDefinitions
import xyz.block.gosling.features.agent.Tool
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAIProviderHandlerTest {

    private val handler = OpenAIProviderHandler()

    @Test
    fun getApiUrl_returnsOpenAIEndpoint() {
        val url = handler.getApiUrl("gpt-4o", "test-key")
        
        assertEquals("https://api.openai.com/v1/chat/completions", url)
    }

    @Test
    fun getApiUrl_sameForAllModels() {
        val key = "test-key"
        
        val url1 = handler.getApiUrl("gpt-4o", key)
        val url2 = handler.getApiUrl("gpt-4o-mini", key)
        val url3 = handler.getApiUrl("o3-mini", key)
        
        assertEquals(url1, url2)
        assertEquals(url2, url3)
    }

    @Test
    fun getHeaders_includesAuthorizationBearer() {
        val apiKey = "test-api-key-123"
        val headers = handler.getHeaders(apiKey)
        
        assertTrue(headers.containsKey("Authorization"))
        assertEquals("Bearer $apiKey", headers["Authorization"])
    }

    @Test
    fun getHeaders_withNullKey_returnsEmptyMap() {
        val headers = handler.getHeaders(null)
        
        assertTrue(headers.isEmpty())
    }

    @Test
    fun getHeaders_differentKeys_hasDifferentAuth() {
        val headers1 = handler.getHeaders("key1")
        val headers2 = handler.getHeaders("key2")
        
        assertEquals("Bearer key1", headers1["Authorization"])
        assertEquals("Bearer key2", headers2["Authorization"])
        assertTrue(headers1["Authorization"] != headers2["Authorization"])
    }

    @Test
    fun createToolDefinitions_withMockMethod_createsValidDefinition() {
        val toolMethods = getMockToolMethods()
        
        val definitions = handler.createToolDefinitions(toolMethods)
        
        assertTrue(definitions is SerializableToolDefinitions.OpenAITools)
    }

    @Test
    fun createToolDefinitions_emptyList_returnsEmptyDefinitions() {
        val definitions = handler.createToolDefinitions(emptyList())
        
        assertTrue(definitions is SerializableToolDefinitions.OpenAITools)
        assertTrue(definitions.definitions.isEmpty())
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
                ParameterDef(name = "param2", type = "boolean", description = "Second parameter", required = true)
            ]
        )
        fun testTool(param1: String, param2: Boolean) {
        }
    }
}
