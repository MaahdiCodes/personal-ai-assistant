package dev.maahdi.mavick.ai.eval

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.ModelTimings
import java.util.Locale
import org.junit.Test

class EvalTimingsTest {
    private fun timings(promptTokens: Int, promptSpeed: Double, answerTokens: Int, answerSpeed: Double, firstToken: Double = 1.0) =
        ModelTimings(promptTokens, promptSpeed, answerTokens, answerSpeed, firstToken)

    @Test
    fun `no timings says so`() {
        assertThat(EvalTimings.summary(emptyList())).isEqualTo("Where the time goes: the runtime gave no timings\n")
    }

    @Test
    fun `one call shows its reading and writing time`() {
        val summary = EvalTimings.summary(listOf(timings(400, 20.0, 60, 6.0, firstToken = 20.5)))

        assertThat(summary).contains("medians of 1 model calls, retries included")
        assertThat(summary).contains("Reading the prompt: 400 tokens at 20.0 tokens/s, 20.0 s")
        assertThat(summary).contains("Writing the answer: 60 tokens at 6.0 tokens/s, 10.0 s")
        assertThat(summary).contains("First word of the answer after: 20.5 s")
    }

    @Test
    fun `each figure is its own median across calls`() {
        val summary = EvalTimings.summary(
            listOf(
                timings(300, 10.0, 40, 4.0, firstToken = 30.0),
                timings(500, 50.0, 80, 8.0, firstToken = 10.0),
                timings(400, 20.0, 60, 5.0, firstToken = 20.0),
            ),
        )

        // Seconds per call: 30, 10 and 20 to read; 10, 10 and 12 to write.
        assertThat(summary).contains("Reading the prompt: 400 tokens at 20.0 tokens/s, 20.0 s")
        assertThat(summary).contains("Writing the answer: 60 tokens at 5.0 tokens/s, 10.0 s")
        assertThat(summary).contains("First word of the answer after: 20.0 s")
    }

    @Test
    fun `an even number of calls takes the middle two`() {
        assertThat(EvalTimings.median(listOf(4.0, 1.0, 3.0, 2.0))).isEqualTo(2.5)
        assertThat(EvalTimings.median(listOf(7.0))).isEqualTo(7.0)
        assertThat(EvalTimings.median(emptyList())).isNull()
    }

    @Test
    fun `calls the runtime didn't time are left out of speeds and seconds, not counted as zero`() {
        val summary = EvalTimings.summary(
            listOf(
                timings(400, 20.0, 60, 6.0, firstToken = 0.0),
                timings(400, 0.0, 60, 0.0, firstToken = 0.0),
            ),
        )

        assertThat(summary).contains("Reading the prompt: 400 tokens at 20.0 tokens/s, 20.0 s")
        assertThat(summary).contains("Writing the answer: 60 tokens at 6.0 tokens/s, 10.0 s")
        assertThat(summary).contains("First word of the answer after: not measured")
    }

    @Test
    fun `speeds never measured say so instead of dividing by zero`() {
        val summary = EvalTimings.summary(listOf(timings(400, 0.0, 60, 0.0)))

        assertThat(summary).contains("Reading the prompt: 400 tokens, speed not measured")
        assertThat(summary).contains("Writing the answer: 60 tokens, speed not measured")
    }

    @Test
    fun `numbers use a dot whatever the phone's language`() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertThat(EvalTimings.summary(listOf(timings(400, 20.5, 60, 6.0)))).contains("20.5 tokens/s")
        } finally {
            Locale.setDefault(before)
        }
    }
}
