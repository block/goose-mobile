package xyz.block.gosling.features.agent

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiModelsTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun message_withTextContent_serializesCorrectly() {
        val message = Message(
            role = "user",
            content = listOf(Content.Text(text = "Hello"))
        )
        
        val jsonString = json.encodeToString(message)
        
        assertTrue(jsonString.contains("user"))
        assertTrue(jsonString.contains("Hello"))
    }

    @Test
    fun message_withImageContent_serializesCorrectly() {
        val message = Message(
            role = "user",
            content = listOf(
                Content.ImageUrl(imageUrl = Image(url = "http://example.com/image.jpg"))
            )
        )
        
        val jsonString = json.encodeToString(message)
        
        assertTrue(jsonString.contains("image_url"))
        assertTrue(jsonString.contains("http://example.com/image.jpg"))
    }

    @Test
    fun message_roundTripSerialization_preservesData() {
        val original = Message(
            role = "assistant",
            content = listOf(Content.Text(text = "Response")),
            time = 1234567890
        )
        
        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<Message>(jsonString)
        
        assertEquals(original.role, deserialized.role)
        assertEquals(original.time, deserialized.time)
        assertEquals(1, deserialized.content?.size)
    }

    @Test
    fun conversation_serializesWithAllFields() {
        val conversation = Conversation(
            id = "test-id",
            fileName = "test.json",
            startTime = 1000000,
            endTime = 2000000,
            messages = listOf(
                Message(role = "user", content = listOf(Content.Text(text = "Test")))
            ),
            isComplete = true
        )
        
        val jsonString = json.encodeToString(conversation)
        
        assertTrue(jsonString.contains("test-id"))
        assertTrue(jsonString.contains("test.json"))
        assertTrue(jsonString.contains("true"))
    }

    @Test
    fun conversation_roundTripSerialization_preservesMessages() {
        val original = Conversation(
            id = "conv-123",
            fileName = "conv.json",
            messages = listOf(
                Message(role = "user", content = listOf(Content.Text(text = "Question"))),
                Message(role = "assistant", content = listOf(Content.Text(text = "Answer")))
            )
        )
        
        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<Conversation>(jsonString)
        
        assertEquals(original.id, deserialized.id)
        assertEquals(2, deserialized.messages.size)
        assertEquals("user", deserialized.messages[0].role)
        assertEquals("assistant", deserialized.messages[1].role)
    }

    @Test
    fun toolCall_serializesCorrectly() {
        val toolCall = ToolCall(
            id = "call_123",
            type = "function",
            function = ToolFunction(
                name = "test_function",
                arguments = "{\"param\":\"value\"}"
            )
        )
        
        val jsonString = json.encodeToString(toolCall)
        
        assertTrue(jsonString.contains("call_123"))
        assertTrue(jsonString.contains("test_function"))
        assertTrue(jsonString.contains("function"))
    }

    @Test
    fun toolDefinition_serializesWithParameters() {
        val toolDef = ToolDefinition(
            type = "function",
            function = ToolFunctionDefinition(
                name = "my_tool",
                description = "A test tool",
                parameters = ToolParametersObject(
                    type = "object",
                    properties = mapOf(
                        "input" to ToolParameter(type = "string", description = "Input parameter")
                    ),
                    required = listOf("input")
                )
            )
        )
        
        val jsonString = json.encodeToString(toolDef)
        
        assertTrue(jsonString.contains("my_tool"))
        assertTrue(jsonString.contains("A test tool"))
        assertTrue(jsonString.contains("required"))
    }

    @Test
    fun content_polymorphicDeserialization_text() {
        val jsonString = """{"type":"text","text":"Hello world"}"""
        
        val content = json.decodeFromString<Content>(jsonString)
        
        assertTrue(content is Content.Text)
        assertEquals("Hello world", content.text)
    }

    @Test
    fun content_polymorphicDeserialization_imageUrl() {
        val jsonString = """{"type":"image_url","image_url":{"url":"http://test.com/img.jpg"}}"""
        
        val content = json.decodeFromString<Content>(jsonString)
        
        assertTrue(content is Content.ImageUrl)
        assertEquals("http://test.com/img.jpg", content.imageUrl.url)
    }

    @Test
    fun message_withToolCalls_serializesCorrectly() {
        val message = Message(
            role = "assistant",
            content = null,
            toolCalls = listOf(
                ToolCall(
                    id = "call_1",
                    function = ToolFunction(name = "tool1", arguments = "{}")
                )
            )
        )
        
        val jsonString = json.encodeToString(message)
        
        assertTrue(jsonString.contains("\"tool_calls\""))
        assertTrue(jsonString.contains("\"call_1\""))
    }

    @Test
    fun message_withStats_serializesCorrectly() {
        val message = Message(
            role = "assistant",
            content = listOf(Content.Text(text = "Response")),
            stats = mapOf(
                "duration" to 1.5,
                "tokens" to 100.0
            )
        )
        
        val jsonString = json.encodeToString(message)
        
        assertTrue(jsonString.contains("\"stats\""))
        assertTrue(jsonString.contains("\"duration\""))
    }

    @Test
    fun conversation_withDefaultValues_hasCorrectDefaults() {
        val conversation = Conversation(
            id = "default-test",
            fileName = "default.json"
        )
        
        assertNotNull(conversation.startTime)
        assertEquals(emptyList(), conversation.messages)
        assertEquals(false, conversation.isComplete)
        assertEquals(null, conversation.endTime)
    }
}
