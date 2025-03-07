package xyz.block.gosling.features.agent

enum class ModelProvider {
    OPENAI,
    GEMINI
}

data class AiModel(
    val displayName: String,
    val identifier: String,
    val provider: ModelProvider
) {
    companion object {
        val AVAILABLE_MODELS = listOf(
            AiModel("GPT-4o", "gpt-4o", ModelProvider.OPENAI),
            AiModel("GPT-4o mini", "gpt-4o-mini", ModelProvider.OPENAI),
            AiModel("O3 Mini", "o3-mini", ModelProvider.OPENAI),
            AiModel("O3 Small", "o3-small", ModelProvider.OPENAI),
            AiModel("O3 Medium", "o3-medium", ModelProvider.OPENAI),
            AiModel("O3 Large", "o3-large", ModelProvider.OPENAI),

            AiModel("Gemini Flash", "gemini-2.0-flash", ModelProvider.GEMINI),
            AiModel("Gemini Flash light", "gemini-2.0-flash-lite", ModelProvider.GEMINI)
        )

        fun fromIdentifier(identifier: String): AiModel {
            return AVAILABLE_MODELS.find { it.identifier == identifier }
                ?: AVAILABLE_MODELS.first()
        }
    }
} 