package xyz.block.gosling.features.agent

fun firstText(message: Message): String {
    if (message.content.isNullOrEmpty()) {
        return "<empty>"
    }

    val textContent = message.content.filterIsInstance<Content.Text>().firstOrNull()
    val text = textContent?.text
    
    // Handle null or "null" text content
    return when {
        text == null -> "<image>"
        text.equals("null", ignoreCase = true) -> "" // Replace "null" with empty string
        text.isBlank() -> "<empty>" // Handle blank text
        else -> text
    }
}

fun firstImage(message: Message): Content.ImageUrl? {
    if (message.content.isNullOrEmpty()) {
        return null
    }
    return message.content.filterIsInstance<Content.ImageUrl>().firstOrNull()
}

fun contentWithText(text: String): List<Content.Text> {
    return listOf(
        Content.Text(
            text = text
        )
    )
}

fun getConversationTitle(conversation: Conversation): String {
    val userMessage = conversation.messages.find { it.role == "user" }
    if (userMessage == null) {
        return "Conversation ${conversation.id}"
    }
    
    val text = firstText(userMessage)
    
    // If firstText returned empty or placeholder text, use a generic title
    return when {
        text.isBlank() || text == "<empty>" || text == "<image>" -> "Conversation ${conversation.id}"
        else -> {
            // Limit title length and add ellipsis if needed
            if (text.length > 50) text.take(50) + "..." else text
        }
    }
}

fun getCurrentAssistantMessage(conversation: Conversation): Message? {
    return conversation.messages.lastOrNull { it.role == "assistant" }
}
