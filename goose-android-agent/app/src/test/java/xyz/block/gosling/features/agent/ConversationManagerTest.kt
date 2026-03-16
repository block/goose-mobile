package xyz.block.gosling.features.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversationManagerTest {

    private lateinit var context: Context
    private lateinit var conversationManager: ConversationManager
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        conversationManager = ConversationManager(context)
        testDir = File(context.getExternalFilesDir(null), "session_dumps")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        // Clean up test files
        testDir.deleteRecursively()
    }

    @Test
    fun conversations_initialState_isEmptyList() = runBlocking {
        val conversations = conversationManager.conversations.first()
        
        assertTrue(conversations.isEmpty())
    }

    @Test
    fun updateCurrentConversation_newConversation_updatesStateFlow() = runBlocking {
        val conversation = Conversation(
            id = "test-123",
            fileName = "test.json",
            messages = listOf(
                Message(role = "user", content = contentWithText("Hello"))
            )
        )
        
        conversationManager.updateCurrentConversation(conversation)
        
        val current = conversationManager.currentConversation.first()
        assertNotNull(current)
        assertEquals("test-123", current.id)
        assertEquals(1, current.messages.size)
    }

    @Test
    fun updateCurrentConversation_savesToDisk() = runBlocking {
        val conversation = Conversation(
            id = "save-test",
            fileName = "save-test.json",
            messages = listOf(
                Message(role = "user", content = contentWithText("Test message"))
            )
        )
        
        conversationManager.updateCurrentConversation(conversation)
        
        val file = File(testDir, "save-test.json")
        assertTrue(file.exists())
        assertTrue(file.readText().contains("save-test"))
    }

    @Test
    fun updateCurrentConversation_existingConversation_updatesInList() = runBlocking {
        val conversation1 = Conversation(
            id = "conv-1",
            fileName = "conv-1.json",
            messages = listOf(Message(role = "user", content = contentWithText("First")))
        )
        
        conversationManager.updateCurrentConversation(conversation1)
        
        val updatedConversation = conversation1.copy(
            messages = listOf(
                Message(role = "user", content = contentWithText("First")),
                Message(role = "assistant", content = contentWithText("Response"))
            )
        )
        
        conversationManager.updateCurrentConversation(updatedConversation)
        
        val conversations = conversationManager.conversations.first()
        assertEquals(1, conversations.size)
        assertEquals(2, conversations[0].messages.size)
    }

    @Test
    fun setCurrentConversation_existingId_setsCurrentConversation() = runBlocking {
        val conversation = Conversation(
            id = "set-test",
            fileName = "set-test.json",
            messages = listOf(Message(role = "user", content = contentWithText("Test")))
        )
        
        conversationManager.updateCurrentConversation(conversation)
        conversationManager.setCurrentConversation("set-test")
        
        val current = conversationManager.currentConversation.first()
        assertNotNull(current)
        assertEquals("set-test", current.id)
    }

    @Test
    fun setCurrentConversation_nonExistingId_doesNothing() = runBlocking {
        val current = conversationManager.currentConversation.first()
        
        conversationManager.setCurrentConversation("non-existing-id")
        
        val stillCurrent = conversationManager.currentConversation.first()
        assertEquals(current, stillCurrent)
    }

    @Test
    fun clearConversations_removesAllConversations() = runBlocking {
        val conv1 = Conversation(id = "1", fileName = "1.json", messages = emptyList())
        val conv2 = Conversation(id = "2", fileName = "2.json", messages = emptyList())
        
        conversationManager.updateCurrentConversation(conv1)
        conversationManager.updateCurrentConversation(conv2)
        
        conversationManager.clearConversations()
        
        val conversations = conversationManager.conversations.first()
        assertTrue(conversations.isEmpty())
        
        val current = conversationManager.currentConversation.first()
        assertNull(current)
    }

    @Test
    fun deleteConversation_removesSpecificConversation() = runBlocking {
        val conv1 = Conversation(id = "delete-1", fileName = "delete-1.json", messages = emptyList())
        val conv2 = Conversation(id = "delete-2", fileName = "delete-2.json", messages = emptyList())
        
        conversationManager.updateCurrentConversation(conv1)
        conversationManager.updateCurrentConversation(conv2)
        
        conversationManager.deleteConversation("delete-1")
        
        val conversations = conversationManager.conversations.first()
        assertEquals(1, conversations.size)
        assertEquals("delete-2", conversations[0].id)
    }

    @Test
    fun deleteConversation_currentConversation_clearsCurrentConversation() = runBlocking {
        val conversation = Conversation(
            id = "current-delete",
            fileName = "current-delete.json",
            messages = emptyList()
        )
        
        conversationManager.updateCurrentConversation(conversation)
        conversationManager.deleteConversation("current-delete")
        
        val current = conversationManager.currentConversation.first()
        assertNull(current)
    }

    @Test
    fun fileNameFor_sanitizesInput() {
        val fileName = conversationManager.fileNameFor("Hello World! Test @#$%")
        
        assertTrue(fileName.contains("hello_world__test_____"))
        assertTrue(fileName.endsWith(".json"))
    }

    @Test
    fun fileNameFor_truncatesLongInput() {
        val longInput = "a".repeat(100)
        
        val fileName = conversationManager.fileNameFor(longInput)
        
        val sanitized = fileName.substringAfter("-").substringBefore(".json")
        assertTrue(sanitized.length <= 50)
    }

    @Test
    fun fileNameFor_generatesUniqueFileNames() {
        // Create first file to establish counter
        val fileName1 = conversationManager.fileNameFor("test")
        File(testDir, fileName1).createNewFile()
        
        val fileName2 = conversationManager.fileNameFor("test")
        
        assertTrue(fileName1 != fileName2)
        assertTrue(fileName1.matches(Regex("\\d{4}-test\\.json")))
        assertTrue(fileName2.matches(Regex("\\d{4}-test\\.json")))
    }

    @Test
    fun recentConversations_filtersOldConversations() = runBlocking {
        val now = System.currentTimeMillis()
        val recent = Conversation(
            id = "recent",
            fileName = "recent.json",
            startTime = now - (30 * 60 * 1000), // 30 minutes ago
            messages = emptyList()
        )
        val old = Conversation(
            id = "old",
            fileName = "old.json",
            startTime = now - (2 * 60 * 60 * 1000), // 2 hours ago
            messages = emptyList()
        )
        
        conversationManager.updateCurrentConversation(recent)
        conversationManager.updateCurrentConversation(old)
        
        val recentConversations = conversationManager.recentConversations()
        
        assertEquals(1, recentConversations.size)
        assertEquals("recent", recentConversations[0].id)
    }

    @Test
    fun recentConversations_sortedByStartTime() = runBlocking {
        val now = System.currentTimeMillis()
        val conv1 = Conversation(
            id = "1",
            fileName = "1.json",
            startTime = now - (10 * 60 * 1000),
            messages = emptyList()
        )
        val conv2 = Conversation(
            id = "2",
            fileName = "2.json",
            startTime = now - (5 * 60 * 1000),
            messages = emptyList()
        )
        
        conversationManager.updateCurrentConversation(conv1)
        conversationManager.updateCurrentConversation(conv2)
        
        val recent = conversationManager.recentConversations()
        
        assertEquals(2, recent.size)
        assertEquals("2", recent[0].id) // Most recent first
        assertEquals("1", recent[1].id)
    }
}
