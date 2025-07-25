package xyz.block.gosling.features.agent

import org.junit.Test
import org.junit.Assert.*
import xyz.block.gosling.features.agent.providers.OpenRouterProviderHandler

class OpenRouterProviderTest {
    
    @Test
    fun testOpenRouterModelsAreAvailable() {
        val openRouterModels = AiModel.getModelsForProvider(ModelProvider.OPENROUTER)
        
        assertTrue("Should have at least one OpenRouter model", openRouterModels.isNotEmpty())
        
        val expectedModels = listOf(
            "anthropic/claude-3.5-sonnet",
            "anthropic/claude-3-haiku", 
            "anthropic/claude-3-opus",
            "meta-llama/llama-3.1-70b-instruct",
            "mistralai/mistral-large",
            "cohere/command-r-plus",
            "deepseek/deepseek-chat"
        )
        
        expectedModels.forEach { expectedIdentifier ->
            val model = openRouterModels.find { it.identifier == expectedIdentifier }
            assertNotNull("Model $expectedIdentifier should exist", model)
            assertEquals("Model should have OPENROUTER provider", ModelProvider.OPENROUTER, model?.provider)
        }
    }
    
    @Test
    fun testOpenRouterProviderHandler() {
        val handler = OpenRouterProviderHandler()
        
        // Test API URL
        val apiUrl = handler.getApiUrl("anthropic/claude-3.5-sonnet", "test-key")
        assertEquals("Should use OpenRouter API endpoint", 
            "https://openrouter.ai/api/v1/chat/completions", apiUrl)
        
        // Test headers
        val headers = handler.getHeaders("test-key")
        assertEquals("Should have authorization header", "Bearer test-key", headers["Authorization"])
        assertTrue("Should have referer header", headers.containsKey("HTTP-Referer"))
        assertTrue("Should have title header", headers.containsKey("X-Title"))
    }
    
    @Test
    fun testProviderSelection() {
        val providers = AiModel.getProviders()
        
        assertTrue("Should include OPENAI", providers.contains(ModelProvider.OPENAI))
        assertTrue("Should include GEMINI", providers.contains(ModelProvider.GEMINI))
        assertTrue("Should include OPENROUTER", providers.contains(ModelProvider.OPENROUTER))
        
        assertEquals("Should have exactly 3 providers", 3, providers.size)
    }
    
    @Test
    fun testModelFromIdentifier() {
        val claudeModel = AiModel.fromIdentifier("anthropic/claude-3.5-sonnet")
        
        assertEquals("Should find Claude 3.5 Sonnet", "Claude 3.5 Sonnet", claudeModel.displayName)
        assertEquals("Should have correct identifier", "anthropic/claude-3.5-sonnet", claudeModel.identifier)
        assertEquals("Should have OPENROUTER provider", ModelProvider.OPENROUTER, claudeModel.provider)
        
        val llamaModel = AiModel.fromIdentifier("meta-llama/llama-3.1-70b-instruct")
        assertEquals("Should find Llama model", "Llama 3.1 70B", llamaModel.displayName)
        assertEquals("Should have OPENROUTER provider", ModelProvider.OPENROUTER, llamaModel.provider)
    }
    
    @Test
    fun testMultipleProviderModels() {
        val openRouterModels = AiModel.getModelsForProvider(ModelProvider.OPENROUTER)
        
        // Should have models from different underlying providers
        val hasAnthropicModel = openRouterModels.any { it.identifier.startsWith("anthropic/") }
        val hasLlamaModel = openRouterModels.any { it.identifier.startsWith("meta-llama/") }
        val hasMistralModel = openRouterModels.any { it.identifier.startsWith("mistralai/") }
        val hasCohereModel = openRouterModels.any { it.identifier.startsWith("cohere/") }
        
        assertTrue("Should have Anthropic models", hasAnthropicModel)
        assertTrue("Should have Llama models", hasLlamaModel)
        assertTrue("Should have Mistral models", hasMistralModel)
        assertTrue("Should have Cohere models", hasCohereModel)
    }
}
