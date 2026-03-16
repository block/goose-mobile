package xyz.block.gosling.features.agent

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConversationUtilsTest {

    @Test
    fun firstText_withTextContent_returnsText() {
        val message = Message(
            role = "user",
            content = listOf(Content.Text(text = "Hello, world!"))
        )
        
        val result = firstText(message)
        
        assertEquals("Hello, world!", result)
    }

    @Test
    fun firstText_withMultipleTextContent_returnsFirstText() {
        val message = Message(
            role = "user",
            content = listOf(
                Content.Text(text = "First text"),
                Content.Text(text = "Second text")
            )
        )
        
        val result = firstText(message)
        
        assertEquals("First text", result)
    }

    @Test
    fun firstText_withNullContent_returnsEmpty() {
        val message = Message(
            role = "user",
            content = null
        )
        
        val result = firstText(message)
        
        assertEquals("<empty>", result)
    }

    @Test
    fun firstText_withEmptyContent_returnsEmpty() {
        val message = Message(
            role = "user",
            content = emptyList()
        )
        
        val result = firstText(message)
        
        assertEquals("<empty>", result)
    }

    @Test
    fun firstText_withOnlyImageContent_returnsImagePlaceholder() {
        val message = Message(
            role = "user",
            content = listOf(
                Content.ImageUrl(imageUrl = Image(url = "http://example.com/image.jpg"))
            )
        )
        
        val result = firstText(message)
        
        assertEquals("<image>", result)
    }

    @Test
    fun firstText_withNullString_returnsEmpty() {
        val message = Message(
            role = "user",
            content = listOf(Content.Text(text = "null"))
        )
        
        val result = firstText(message)
        
        assertEquals("", result)
    }

    @Test
    fun firstText_withBlankText_returnsEmpty() {
        val message = Message(
            role = "user",
            content = listOf(Content.Text(text = "   "))
        )
        
        val result = firstText(message)
        
        assertEquals("<empty>", result)
    }

    @Test
    fun firstText_withMixedContent_returnsFirstText() {
        val message = Message(
            role = "user",
            content = listOf(
                Content.ImageUrl(imageUrl = Image(url = "http://example.com/image.jpg")),
                Content.Text(text = "Some text"),
                Content.Text(text = "More text")
            )
        )
        
        val result = firstText(message)
        
        assertEquals("Some text", result)
    }

    @Test
    fun firstImage_withImageContent_returnsImage() {
        val imageUrl = Content.ImageUrl(imageUrl = Image(url = "http://example.com/image.jpg"))
        val message = Message(
            role = "user",
            content = listOf(imageUrl)
        )
        
        val result = firstImage(message)
        
        assertNotNull(result)
        assertEquals("http://example.com/image.jpg", result.imageUrl.url)
    }

    @Test
    fun firstImage_withMultipleImages_returnsFirstImage() {
        val image1 = Content.ImageUrl(imageUrl = Image(url = "http://example.com/image1.jpg"))
        val image2 = Content.ImageUrl(imageUrl = Image(url = "http://example.com/image2.jpg"))
        val message = Message(
            role = "user",
            content = listOf(image1, image2)
        )
        
        val result = firstImage(message)
        
        assertNotNull(result)
        assertEquals("http://example.com/image1.jpg", result.imageUrl.url)
    }

    @Test
    fun firstImage_withNoImageContent_returnsNull() {
        val message = Message(
            role = "user",
            content = listOf(Content.Text(text = "Only text"))
        )
        
        val result = firstImage(message)
        
        assertNull(result)
    }

    @Test
    fun firstImage_withNullContent_returnsNull() {
        val message = Message(
            role = "user",
            content = null
        )
        
        val result = firstImage(message)
        
        assertNull(result)
    }

    @Test
    fun contentWithText_createsTextContentList() {
        val result = contentWithText("Test message")
        
        assertEquals(1, result.size)
        assertEquals("text", result[0].type)
        assertEquals("Test message", result[0].text)
    }

    @Test
    fun contentWithText_withEmptyString_createsEmptyTextContent() {
        val result = contentWithText("")
        
        assertEquals(1, result.size)
        assertEquals("", result[0].text)
    }

    @Test
    fun getConversationTitle_withUserMessage_returnsMessageText() {
        val conversation = Conversation(
            id = "123",
            fileName = "test.json",
            messages = listOf(
                Message(role = "user", content = listOf(Content.Text(text = "Hello assistant")))
            )
        )
        
        val title = getConversationTitle(conversation)
        
        assertEquals("Hello assistant", title)
    }

    @Test
    fun getConversationTitle_withLongText_truncatesAndAddsEllipsis() {
        val longText = "This is a very long message that should be truncated because it exceeds the fifty character limit"
        val conversation = Conversation(
            id = "123",
            fileName = "test.json",
            messages = listOf(
                Message(role = "user", content = listOf(Content.Text(text = longText)))
            )
        )
        
        val title = getConversationTitle(conversation)
        
        assertEquals(53, title.length) // 50 characters + "..."
        assertEquals("...", title.takeLast(3))
        assertEquals(longText.take(50) + "...", title)
    }

    @Test
    fun getConversationTitle_withNoUserMessage_returnsDefaultTitle() {
        val conversation = Conversation(
            id = "123",
            fileName = "test.json",
            messages = listOf(
                Message(role = "assistant", content = listOf(Content.Text(text = "Assistant response")))
            )
        )
        
        val title = getConversationTitle(conversation)
        
        assertEquals("Conversation 123", title)
    }

    @Test
    fun getConversationTitle_withEmptyMessages_returnsDefaultTitle() {
        val conversation = Conversation(
            id = "456",
            fileName = "test.json",
            messages = emptyList()
        )
        
        val title = getConversationTitle(conversation)
        
        assertEquals("Conversation 456", title)
    }

    @Test
    fun getConversationTitle_withBlankUserMessage_returnsDefaultTitle() {
        val conversation = Conversation(
            id = "789",
            fileName = "test.json",
            messages = listOf(
                Message(role = "user", content = listOf(Content.Text(text = "   ")))
            )
        )
        
        val title = getConversationTitle(conversation)
        
        assertEquals("Conversation 789", title)
    }

    @Test
    fun getConversationTitle_withImageOnlyMessage_returnsDefaultTitle() {
        val conversation = Conversation(
            id = "999",
            fileName = "test.json",
            messages = listOf(
                Message(role = "user", content = listOf(
                    Content.ImageUrl(imageUrl = Image(url = "http://example.com/image.jpg"))
                ))
            )
        )
        
        val title = getConversationTitle(conversation)
        
        assertEquals("Conversation 999", title)
    }

    @Test
    fun getCurrentAssistantMessage_withAssistantMessages_returnsLastOne() {
        val conversation = Conversation(
            id = "123",
            fileName = "test.json",
            messages = listOf(
                Message(role = "user", content = listOf(Content.Text(text = "Question"))),
                Message(role = "assistant", content = listOf(Content.Text(text = "First response"))),
                Message(role = "user", content = listOf(Content.Text(text = "Follow-up"))),
                Message(role = "assistant", content = listOf(Content.Text(text = "Second response")))
            )
        )
        
        val result = getCurrentAssistantMessage(conversation)
        
        assertNotNull(result)
        assertEquals("assistant", result.role)
        assertEquals("Second response", firstText(result))
    }

    @Test
    fun getCurrentAssistantMessage_withNoAssistantMessages_returnsNull() {
        val conversation = Conversation(
            id = "123",
            fileName = "test.json",
            messages = listOf(
                Message(role = "user", content = listOf(Content.Text(text = "Question")))
            )
        )
        
        val result = getCurrentAssistantMessage(conversation)
        
        assertNull(result)
    }

    @Test
    fun getCurrentAssistantMessage_withEmptyMessages_returnsNull() {
        val conversation = Conversation(
            id = "123",
            fileName = "test.json",
            messages = emptyList()
        )
        
        val result = getCurrentAssistantMessage(conversation)
        
        assertNull(result)
    }
}
