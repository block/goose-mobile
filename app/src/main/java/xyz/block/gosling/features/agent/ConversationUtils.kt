package xyz.block.gosling.features.agent


fun firstText(message: Message): String {
    if (message.content.isNullOrEmpty()) {
        return "<empty>"
    }

    val textContent = message.content.filterIsInstance<Content.Text>().firstOrNull()
    return if (textContent != null) {
        textContent.text
    } else {
        "<image>"
    }
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
        ?.let { it }
        ?: "Conversation ${conversation.id}"
}

fun getCurrentAssistantMessage(conversation: Conversation): Message? {
    return conversation.messages
        .filter { it.role == "assistant" }
        .lastOrNull()
}
