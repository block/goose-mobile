package xyz.block.gosling.features.agent

fun getConversationTitle(conversation: Conversation): String {
    return conversation.messages
        .find { it.role == "user" }
        ?.content
        ?.let { if (it.length > 28) "${it.take(28)}..." else it }
        ?: "Conversation ${conversation.id}"
} 
