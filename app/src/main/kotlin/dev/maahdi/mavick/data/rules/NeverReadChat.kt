package dev.maahdi.mavick.data.rules

import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageRepository

/**
 * "Never read this chat", from a message or a suggestion: a "Never read" rule for the chat's key (so
 * a rename doesn't undo it), then the chat's saved messages are deleted, and their suggestions with
 * them (docs/PLAN.md §5.2).
 */
object NeverReadChat {
    suspend fun apply(
        exclusions: ExclusionRepository,
        messages: MessageRepository,
        app: SourceApp,
        accountKey: String,
        conversationKey: String,
        chatName: String,
    ) {
        exclusions.add(RuleType.CHAT, RuleEffect.EXCLUDE, conversationKey, chatName, app, accountKey)
        messages.deleteConversation(app, accountKey, conversationKey)
    }
}
