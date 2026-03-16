package xyz.block.gosling.features.agent

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiModelTest {

    @Test
    fun fromIdentifier_validOpenAIIdentifier_returnsCorrectModel() {
        val model = AiModel.fromIdentifier("gpt-4o")
        
        assertEquals("GPT-4o", model.displayName)
        assertEquals("gpt-4o", model.identifier)
        assertEquals(ModelProvider.OPENAI, model.provider)
    }

    @Test
    fun fromIdentifier_validGeminiIdentifier_returnsCorrectModel() {
        val model = AiModel.fromIdentifier("gemini-2.0-flash")
        
        assertEquals("Gemini Flash", model.displayName)
        assertEquals("gemini-2.0-flash", model.identifier)
        assertEquals(ModelProvider.GEMINI, model.provider)
    }

    @Test
    fun fromIdentifier_validOpenRouterIdentifier_returnsCorrectModel() {
        val model = AiModel.fromIdentifier("anthropic/claude-3.5-sonnet")
        
        assertEquals("Claude 3.5 Sonnet", model.displayName)
        assertEquals("anthropic/claude-3.5-sonnet", model.identifier)
        assertEquals(ModelProvider.OPENROUTER, model.provider)
    }

    @Test
    fun fromIdentifier_invalidIdentifier_returnsFirstModel() {
        val model = AiModel.fromIdentifier("non-existent-model")
        
        assertNotNull(model)
        assertEquals(AiModel.AVAILABLE_MODELS.first(), model)
    }

    @Test
    fun fromIdentifier_emptyString_returnsFirstModel() {
        val model = AiModel.fromIdentifier("")
        
        assertNotNull(model)
        assertEquals(AiModel.AVAILABLE_MODELS.first(), model)
    }

    @Test
    fun getProviders_returnsAllThreeProviders() {
        val providers = AiModel.getProviders()
        
        assertEquals(3, providers.size)
        assertTrue(providers.contains(ModelProvider.OPENAI))
        assertTrue(providers.contains(ModelProvider.GEMINI))
        assertTrue(providers.contains(ModelProvider.OPENROUTER))
    }

    @Test
    fun getProviders_returnsDistinctProviders() {
        val providers = AiModel.getProviders()
        
        assertEquals(providers.size, providers.distinct().size)
    }

    @Test
    fun getModelsForProvider_openAI_returnsOnlyOpenAIModels() {
        val models = AiModel.getModelsForProvider(ModelProvider.OPENAI)
        
        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it.provider == ModelProvider.OPENAI })
        assertTrue(models.any { it.identifier == "gpt-4o" })
        assertTrue(models.any { it.identifier == "gpt-4o-mini" })
    }

    @Test
    fun getModelsForProvider_gemini_returnsOnlyGeminiModels() {
        val models = AiModel.getModelsForProvider(ModelProvider.GEMINI)
        
        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it.provider == ModelProvider.GEMINI })
        assertTrue(models.any { it.identifier == "gemini-2.0-flash" })
    }

    @Test
    fun getModelsForProvider_openRouter_returnsOnlyOpenRouterModels() {
        val models = AiModel.getModelsForProvider(ModelProvider.OPENROUTER)
        
        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it.provider == ModelProvider.OPENROUTER })
        assertTrue(models.any { it.identifier.startsWith("anthropic/") })
        assertTrue(models.any { it.identifier.startsWith("meta-llama/") })
    }

    @Test
    fun availableModels_containsExpectedCount() {
        val models = AiModel.AVAILABLE_MODELS
        
        assertTrue(models.size >= 16, "Expected at least 16 models, found ${models.size}")
    }

    @Test
    fun availableModels_allHaveValidData() {
        AiModel.AVAILABLE_MODELS.forEach { model ->
            assertTrue(model.displayName.isNotBlank(), "Display name should not be blank for ${model.identifier}")
            assertTrue(model.identifier.isNotBlank(), "Identifier should not be blank")
            assertNotNull(model.provider)
        }
    }

    @Test
    fun availableModels_noDuplicateIdentifiers() {
        val identifiers = AiModel.AVAILABLE_MODELS.map { it.identifier }
        val uniqueIdentifiers = identifiers.distinct()
        
        assertEquals(identifiers.size, uniqueIdentifiers.size, "Found duplicate identifiers")
    }

    @Test
    fun dataClass_equality_worksCorrectly() {
        val model1 = AiModel("Test Model", "test-model", ModelProvider.OPENAI)
        val model2 = AiModel("Test Model", "test-model", ModelProvider.OPENAI)
        val model3 = AiModel("Different Model", "test-model", ModelProvider.OPENAI)
        
        assertEquals(model1, model2)
        assertTrue(model1 != model3)
    }
}
