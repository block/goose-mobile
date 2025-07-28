package xyz.block.gosling.features.agent

enum class ModelProvider {
    OPENAI,
    GEMINI,
    OPENROUTER
}

data class AiModel(
    val displayName: String,
    val identifier: String,
    val provider: ModelProvider
) {
    companion object {
        val AVAILABLE_MODELS = listOf(
            AiModel("GPT-4.1", "gpt-4.1", ModelProvider.OPENAI),
            AiModel("GPT-4o", "gpt-4o", ModelProvider.OPENAI),
            AiModel("GPT-4o mini", "gpt-4o-mini", ModelProvider.OPENAI),
            AiModel("O3 Mini", "o3-mini", ModelProvider.OPENAI),
            AiModel("O3 Small", "o3-small", ModelProvider.OPENAI),
            AiModel("O3 Medium", "o3-medium", ModelProvider.OPENAI),
            AiModel("O3 Large", "o3-large", ModelProvider.OPENAI),

            AiModel("Gemini Flash", "gemini-2.0-flash", ModelProvider.GEMINI),
            AiModel("Gemini Flash light", "gemini-2.0-flash-lite", ModelProvider.GEMINI),

            // OpenRouter models (from various underlying providers)
            AiModel("Claude 4 Sonnet", "anthropic/claude-sonnet-4", ModelProvider.OPENROUTER),
            AiModel("Claude 4 Opus", "anthropic/claude-opus-4", ModelProvider.OPENROUTER),
            AiModel("Claude 3.5 Sonnet", "anthropic/claude-3.5-sonnet", ModelProvider.OPENROUTER),
            AiModel("Claude 3 Haiku", "anthropic/claude-3-haiku", ModelProvider.OPENROUTER),
            AiModel("Claude 3 Opus", "anthropic/claude-3-opus", ModelProvider.OPENROUTER),
            AiModel("Llama 3.1 70B", "meta-llama/llama-3.1-70b-instruct", ModelProvider.OPENROUTER),
            AiModel("Llama 3.1 8B", "meta-llama/llama-3.1-8b-instruct", ModelProvider.OPENROUTER),
            AiModel("Mistral Large", "mistralai/mistral-large", ModelProvider.OPENROUTER),
            AiModel("Cohere Command R+", "cohere/command-r-plus", ModelProvider.OPENROUTER)
        )

        fun fromIdentifier(identifier: String): AiModel {
            return AVAILABLE_MODELS.find { it.identifier == identifier }
                ?: AVAILABLE_MODELS.first()
        }

        fun getProviders(): List<ModelProvider> = 
            AVAILABLE_MODELS.map { it.provider }.distinct()
        
        fun getModelsForProvider(provider: ModelProvider): List<AiModel> = 
            AVAILABLE_MODELS.filter { it.provider == provider }
    }
}
