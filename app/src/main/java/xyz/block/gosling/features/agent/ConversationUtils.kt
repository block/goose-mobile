package xyz.block.gosling.features.agent

fun firstText(message: Message): String {
    if (message.content.isNullOrEmpty()) {
        return "<empty>"
    }

    val textContent = message.content.filterIsInstance<Content.Text>().firstOrNull()
    return textContent?.text ?: "<image>"
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
    return conversation.messages
        .find { it.role == "user" }
        ?.let { firstText(it) }
        ?: "Conversation ${conversation.id}"
}

fun getCurrentAssistantMessage(conversation: Conversation): Message? {
    return conversation.messages.lastOrNull { it.role == "assistant" }
}
