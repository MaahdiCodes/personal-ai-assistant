package dev.maahdi.mavick.ai

import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.time.WhenParser
import java.time.LocalDateTime

/**
 * A cheap first look at each message (docs/PLAN.md §5.3), so only messages that might hold a task
 * reach the AI and most chat ("ok 👍", "haha", "how are you?") costs nothing. When unsure it lets a
 * message through: the AI decides.
 *
 * A message passes if it has a date or time (read by the same [WhenParser] as quick-add), a to-do,
 * request or promise word ("pay", "please", "can you", "I'll"), or an amount of money. Keep
 * reminders always pass: you set them yourself.
 */
object Prefilter {
    /** Shorter than this (letters and digits) can't hold a task: "ok", "👍", "Hi". */
    const val MIN_LETTERS = 3

    fun passes(input: ExtractionInput, parser: WhenParser): Boolean {
        if (input.app == SourceApp.KEEP) return true
        val text = input.message.text
        if (text.count { it.isLetterOrDigit() } < MIN_LETTERS) return false
        return hasWhen(text, input.sentAt, parser) || hasTaskWord(text) || hasMoney(text)
    }

    fun hasWhen(text: String, sentAt: LocalDateTime, parser: WhenParser): Boolean {
        val parsed = parser.parse(text, sentAt)
        return parsed.dueDate != null || parsed.dueTime != null || parsed.repeatRule != null
    }

    fun hasTaskWord(text: String): Boolean = TASK_WORDS.containsMatchIn(text)

    fun hasMoney(text: String): Boolean = MONEY.containsMatchIn(text)

    /** Letters (with their accent and vowel marks) and digits make up words, in any alphabet. */
    private const val WORD = "[\\p{L}\\p{M}\\p{N}]"
    private const val APOSTROPHE = "['’]"

    private val TASK_WORD_LIST = listOf(
        // To-dos
        "pay", "pays", "payment", "send", "sends", "call", "calls", "submit", "submission", "bring", "buy",
        "meet", "meeting", "book", "booking", "remind", "reminder", "deadline", "due", "deliver", "delivery",
        "collect", "pick\\s+up", "pickup", "drop\\s+off", "sign", "confirm", "reply", "respond", "review",
        "fill", "register", "renew", "cancel", "reschedule", "schedule", "transfer", "deposit", "return",
        "prepare", "finish", "complete", "attend", "join", "visit", "appointment", "interview", "exam",
        "flight", "ticket", "bill", "invoice", "rent", "fee", "fees",
        // Requests
        "please", "pls", "plz", "can\\s+you", "could\\s+you", "would\\s+you", "will\\s+you", "can\\s+u",
        "could\\s+u", "need\\s+to", "needs\\s+to", "need\\s+you", "have\\s+to", "has\\s+to", "must",
        "don${APOSTROPHE}?t\\s+forget", "remember\\s+to", "make\\s+sure", "let\\s+me\\s+know", "lmk", "asap",
        "urgent",
        // Promises you make
        "i${APOSTROPHE}ll", "i\\s+will",
    )

    private val TASK_WORDS = Regex("(?iu)(?<!$WORD)(?:${TASK_WORD_LIST.joinToString("|")})(?!$WORD)")

    /** "৳500", "$20", "500 tk", "Tk. 1,200", "1500 taka", "20 dollars". */
    private val MONEY = Regex(
        "(?iu)(?:[$€£৳₹]\\s?\\d|(?<!$WORD)(?:tk|bdt|usd|rs|inr)\\.?\\s?\\d|\\d\\s?(?:tk|taka|bdt|usd|dollars?|rs|rupees?|inr)(?!$WORD))",
    )
}
