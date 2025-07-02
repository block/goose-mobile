package xyz.block.goosemobile.features.agent

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Image(
    val url: String
)

@Serializable(with = ContentSerializer::class)
sealed class Content {
    abstract val type: String

    @Serializable
    data class Text(
        override val type: String = "text",
        val text: String
    ) : Content()

    @Serializable
    data class ImageUrl(
        override val type: String = "image_url",
        @SerialName("image_url")
        val imageUrl: Image
    ) : Content()
}

class ContentSerializer : JsonContentPolymorphicSerializer<Content>(Content::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out Content> {
        return when (element.jsonObject["type"]?.jsonPrimitive?.content) {
            "text" -> Content.Text.serializer()
            "image_url" -> Content.ImageUrl.serializer()
            else -> throw IllegalArgumentException("Unknown content type")
        }
    }
}

@Serializable
data class Message(
    val role: String,
    val content: List<Content>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("annotations")
    val annotations: JsonElement? = null,
    val time: Long = System.currentTimeMillis(),
    @SerialName("stats")
    val stats: Map<String, Double>? = null
)

@Serializable
data class Conversation(
    val id: String = System.currentTimeMillis().toString(),
    val fileName: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val messages: List<Message> = emptyList(),
    val isComplete: Boolean = false
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val arguments: String
)

@Serializable
data class ToolParameter(
    val type: String,
    val description: String
)

@Serializable
data class ToolParametersObject(
    val type: String = "object",
    val properties: Map<String, ToolParameter>,
    val required: List<String> = emptyList()
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunctionDefinition
)

@Serializable
data class ToolFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParametersObject
)

// OpenAI specific models
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = 0.1,
    val tools: List<ToolDefinition>? = null
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>
)

@Serializable
data class OpenAIChoice(
    val message: Message
)

// Gemini specific models
@Serializable
data class GeminiRequest(
    val contents: GeminiContent,
    val tools: List<GeminiTool>? = null
)

@Serializable
data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("functionCall")
    val functionCall: GeminiFunctionCall? = null
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject
)

@Serializable
data class GeminiTool(
    @SerialName("function_declarations")
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: ToolParametersObject? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent
)

// UI Hierarchy models
@Serializable
data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class NodeInfo(
    val className: String? = null,
    val packageName: String? = null,
    val text: String? = null,
    @SerialName("content-desc")
    val contentDesc: String? = null,
    @SerialName("resource-id")
    val resourceId: String? = null,
    val bounds: NodeBounds,
    val clickable: Boolean? = null,
    val focusable: Boolean? = null,
    val enabled: Boolean? = null,
    val scrollable: Boolean? = null,
    val editable: Boolean? = null,
    val children: List<NodeInfo>? = null,
    val error: String? = null
)
