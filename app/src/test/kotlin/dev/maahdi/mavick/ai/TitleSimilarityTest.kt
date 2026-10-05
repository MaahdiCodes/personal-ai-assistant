package dev.maahdi.mavick.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TitleSimilarityTest {
    private fun similar(first: String, second: String) = TitleSimilarity.similar(first, second) to TitleSimilarity.similar(second, first)

    @Test
    fun `the same words in another case or with punctuation are similar`() {
        assertThat(similar("Send the form to Sam", "send form to sam!")).isEqualTo(true to true)
    }

    @Test
    fun `a title whose words all appear in the other is similar`() {
        assertThat(similar("Pay rent", "Pay the rent today")).isEqualTo(true to true)
        assertThat(similar("Bring the cake at 5", "Bring cake")).isEqualTo(true to true)
    }

    @Test
    fun `different things to do are not similar`() {
        assertThat(similar("Buy milk", "Buy eggs")).isEqualTo(false to false)
        assertThat(similar("Call mom", "Call dad")).isEqualTo(false to false)
        assertThat(similar("Send the report to Sam", "Call Rina about dinner")).isEqualTo(false to false)
    }

    @Test
    fun `one shared word alone is not enough`() {
        assertThat(similar("Meeting", "Meeting with the bank manager")).isEqualTo(false to false)
        assertThat(similar("Meeting", "meeting")).isEqualTo(true to true)
    }

    @Test
    fun `titles of only small words are compared as they are`() {
        assertThat(similar("To the", "to the")).isEqualTo(true to true)
        assertThat(similar("to", "the")).isEqualTo(false to false)
    }

    @Test
    fun `other alphabets work too`() {
        assertThat(similar("দুধ কিনো", "দুধ কিনো")).isEqualTo(true to true)
        assertThat(similar("দুধ কিনো", "ডিম কিনো")).isEqualTo(false to false)
    }
}
