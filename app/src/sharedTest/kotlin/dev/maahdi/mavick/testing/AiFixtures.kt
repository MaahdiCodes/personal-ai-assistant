package dev.maahdi.mavick.testing

import dev.maahdi.mavick.ai.ChatLine
import dev.maahdi.mavick.ai.ExtractionInput
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.suggestion.SuggestionEntity
import dev.maahdi.mavick.data.suggestion.SuggestionKind
import dev.maahdi.mavick.data.suggestion.SuggestionSource
import dev.maahdi.mavick.data.suggestion.SuggestionState
import dev.maahdi.mavick.time.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** Monday 5 October 2026, 10:00: when test messages were sent, unless a test says otherwise. */
val MESSAGE_SENT_AT: LocalDateTime = LocalDateTime.of(2026, 10, 5, 10, 0)

/** A message to extract tasks from: by default Sam writes to you in a one-to-one WhatsApp chat. */
fun extractionInput(
    text: String,
    app: SourceApp = SourceApp.WHATSAPP,
    sender: String? = "Sam",
    isFromMe: Boolean = false,
    chat: String = "Sam",
    isGroup: Boolean = false,
    earlier: List<ChatLine> = emptyList(),
    partial: Boolean = false,
    sentAt: LocalDateTime = MESSAGE_SENT_AT,
): ExtractionInput = ExtractionInput(
    app = app,
    chatTitle = chat,
    isGroup = isGroup,
    earlier = earlier,
    message = ChatLine(sender = if (isFromMe) null else sender, isFromMe = isFromMe, text = text),
    partial = partial,
    sentAt = sentAt,
)

/** A waiting suggestion from Sam in the "Family" WhatsApp group. [messageId] must exist in a real database. */
fun suggestion(
    messageId: String = "m1",
    id: String = UUID.randomUUID().toString(),
    title: String = "Send the form to Sam",
    kind: SuggestionKind = SuggestionKind.TASK,
    whenText: String? = null,
    dueDate: LocalDate? = null,
    dueTime: LocalTime? = null,
    repeatRule: RepeatRule? = null,
    needsTime: Boolean = false,
    app: SourceApp = SourceApp.WHATSAPP,
    accountKey: String = "0",
    conversationKey: String = "s:family",
    chatTitle: String = "Family",
    sender: String? = "Sam",
    isFromMe: Boolean = false,
    isGroup: Boolean = true,
    excerpt: String = "Can you send me the form?",
    messagePostedAt: Instant = TEST_NOW,
    person: String? = "Sam",
    confidence: Double = 0.9,
    source: SuggestionSource = SuggestionSource.MODEL,
    state: SuggestionState = SuggestionState.NEW,
    createdAt: Instant = TEST_NOW,
): SuggestionEntity = SuggestionEntity(
    id = id,
    messageId = messageId,
    app = app,
    accountKey = accountKey,
    conversationKey = conversationKey,
    chatTitle = chatTitle,
    sender = sender,
    isFromMe = isFromMe,
    isGroup = isGroup,
    excerpt = excerpt,
    messagePostedAt = messagePostedAt,
    kind = kind,
    title = title,
    whenText = whenText,
    dueDate = dueDate,
    dueTime = dueTime,
    repeatRule = repeatRule,
    needsTime = needsTime,
    person = person,
    confidence = confidence,
    source = source,
    state = state,
    createdAt = createdAt,
)
