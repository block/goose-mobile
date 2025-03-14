package xyz.block.gosling.features.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.ref.WeakReference

class ConversationManager(context: Context) {
    private val contextRef = WeakReference(context)
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    init {
        loadConversations()
    }

    private fun loadConversations() {
        contextRef.get()?.getExternalFilesDir(null)?.let { filesDir ->
            val conversationsDir = File(filesDir, "session_dumps")
            if (conversationsDir.exists()) {
                val conversations = conversationsDir.listFiles()
                    ?.filter { it.extension == "json" }
                    ?.mapNotNull { file ->
                        try {
                            json.decodeFromString<Conversation>(file.readText())
                        } catch (e: Exception) {
                            null
                        }
                    }
                    ?.sortedByDescending { it.startTime } ?: emptyList()
                
                _conversations.value = conversations
            }
        }
    }

    fun updateCurrentConversation(conversation: Conversation) {
        _currentConversation.update { conversation }

        // Update the conversation in the list if it exists, add it if it doesn't
        _conversations.update { conversations ->
            val updatedList = if (conversations.any { it.id == conversation.id }) {
                conversations.map { if (it.id == conversation.id) conversation else it }
            } else {
                conversations + conversation
            }
            updatedList.sortedByDescending { it.startTime }
        }

        saveConversation(conversation)
    }

    private fun saveConversation(conversation: Conversation) {
        contextRef.get()?.getExternalFilesDir(null)?.let { filesDir ->
            val conversationsDir = File(filesDir, "session_dumps")
            conversationsDir.mkdirs()

            File(conversationsDir, "${conversation.id}.json").writeText(
                json.encodeToString(conversation)
            )
        }
    }

    fun clearConversations() {
        contextRef.get()?.getExternalFilesDir(null)?.let { filesDir ->
            val conversationsDir = File(filesDir, "session_dumps")
            if (conversationsDir.exists()) {
                conversationsDir.deleteRecursively()
                conversationsDir.mkdirs()
            }
        }
        _conversations.value = emptyList()
        _currentConversation.value = null
    }

    fun deleteConversation(conversationId: String) {
        contextRef.get()?.getExternalFilesDir(null)?.let { filesDir ->
            val conversationsDir = File(filesDir, "session_dumps")
            val conversationFile = File(conversationsDir, "$conversationId.json")
            if (conversationFile.exists()) {
                conversationFile.delete()
            }
        }

        _conversations.update { conversations ->
            conversations.filterNot { it.id == conversationId }
        }
        
        if (_currentConversation.value?.id == conversationId) {
            _currentConversation.value = null
        }
    }
} 
