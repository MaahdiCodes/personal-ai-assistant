package dev.maahdi.mavick.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.testing.extractionInput
import dev.maahdi.mavick.time.WhenParser
import org.junit.Test

class PrefilterTest {
    private val parser = WhenParser()

    private fun passes(text: String, app: SourceApp = SourceApp.WHATSAPP) = Prefilter.passes(extractionInput(text, app = app), parser)

    @Test
    fun `small talk is skipped without reaching the AI`() {
        listOf(
            "ok", "👍👍", "Hi", "haha", "lol 😂", "how are you?", "Good morning!", "Nice photo", "thanks a lot",
            "I'm at home", "see you", "yes", "Okay sure", "কেমন আছো?",
        ).forEach { text -> assertWithMessage(text).that(passes(text)).isFalse() }
    }

    @Test
    fun `a date or time lets a message through, read by the quick-add parser`() {
        listOf("Dinner on Friday?", "see you at 7", "meet me 12/10", "every Monday", "tonight works", "The 3rd of November").forEach { text ->
            assertWithMessage(text).that(passes(text)).isTrue()
        }
    }

    @Test
    fun `to-do, request and promise words let a message through`() {
        listOf(
            "Can you send me the report?", "Pay the electricity bill", "Please call me", "Don't forget the cake",
            "dont forget the cake", "I'll bring it", "I’ll bring it", "lmk", "the deadline moved", "Need you to sign this",
        ).forEach { text -> assertWithMessage(text).that(passes(text)).isTrue() }
    }

    @Test
    fun `an amount of money lets a message through`() {
        listOf("Rent is 15,000 tk", "৳500 for the tickets", "it was $20", "Tk. 1,200 left", "1500 taka", "20 dollars").forEach { text ->
            assertWithMessage(text).that(Prefilter.hasMoney(text)).isTrue()
        }
        assertThat(Prefilter.hasMoney("I have 2 cats")).isFalse()
    }

    @Test
    fun `words match whole, so longer words that contain them don't count`() {
        listOf("The payload looks fine", "recalling the old days", "Sender unknown", "the callback works").forEach { text ->
            assertWithMessage(text).that(Prefilter.hasTaskWord(text)).isFalse()
        }
    }

    @Test
    fun `a Keep reminder always passes, however short`() {
        assertThat(passes("Milk", app = SourceApp.KEEP)).isTrue()
    }

    @Test
    fun `fewer than three letters or digits never pass, even a to-do word`() {
        assertThat(passes("Pa")).isFalse()
        assertThat(passes("Pay")).isTrue()
    }
}
