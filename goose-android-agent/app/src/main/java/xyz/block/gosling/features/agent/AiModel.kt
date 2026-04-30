package xyz.block.gosling.features.agent

enum class ModelProvider(val displayName: String) {
    OPENAI("OpenAI"),
    GEMINI("Gemini"),
    OPENROUTER("OpenRouter"),
    ON_DEVICE_LITERT("On-Device");

    val isOnDevice: Boolean
        get() = this == ON_DEVICE_LITERT
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

        private val onDeviceModels = mutableListOf<AiModel>()

        fun registerOnDeviceModel(model: AiModel) {
            if (onDeviceModels.none { it.identifier == model.identifier }) {
                onDeviceModels.add(model)
            }
        }

        fun unregisterOnDeviceModel(identifier: String) {
            onDeviceModels.removeAll { it.identifier == identifier }
        }

        fun getAllModels(): List<AiModel> = AVAILABLE_MODELS + onDeviceModels

        fun fromIdentifier(identifier: String): AiModel {
            return getAllModels().find { it.identifier == identifier }
                ?: AVAILABLE_MODELS.first()
        }

        fun getProviders(): List<ModelProvider> =
            ModelProvider.entries.toList()

        fun getModelsForProvider(provider: ModelProvider): List<AiModel> =
            getAllModels().filter { it.provider == provider }
    }
}
