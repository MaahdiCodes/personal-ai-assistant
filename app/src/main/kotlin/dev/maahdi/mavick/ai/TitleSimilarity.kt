package dev.maahdi.mavick.ai

/**
 * Whether two suggestion titles say the same thing, for merging near-duplicates (docs/PLAN.md §5.3,
 * step 6): a chat often repeats a plan ("Bring the cake at 5" … "ok so you bring cake").
 *
 * Titles are compared by their words, ignoring case, punctuation and small words like "the" and
 * "to". Similar means most words are shared (at least [MIN_SHARED] of all words), or one title's
 * words, two or more, all appear in the other.
 */
object TitleSimilarity {
    const val MIN_SHARED = 0.6

    fun similar(first: String, second: String): Boolean {
        val firstWords = words(first)
        val secondWords = words(second)
        if (firstWords.isEmpty() || secondWords.isEmpty()) return first.trim().equals(second.trim(), ignoreCase = true)
        val shared = (firstWords intersect secondWords).size
        val all = (firstWords union secondWords).size
        val smaller = minOf(firstWords.size, secondWords.size)
        return shared.toDouble() / all >= MIN_SHARED || (shared == smaller && smaller >= 2)
    }

    private fun words(text: String): Set<String> =
        text.lowercase().split(NOT_WORD).filter { it.isNotEmpty() && it !in SMALL_WORDS }.toSet()

    private val NOT_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private val SMALL_WORDS = setOf(
        "a", "an", "the", "to", "for", "of", "at", "on", "in", "by", "with", "and", "or", "my", "your", "me", "you",
        "is", "are", "be", "it", "this", "that",
    )
}
