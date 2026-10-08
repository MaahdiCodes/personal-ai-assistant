package dev.maahdi.mavick.ai.eval

import android.os.Process
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.LiteRtLmModel
import org.junit.Assert.assertThrows
import org.junit.Test

class EvalRunSettingsTest {
    @Test
    fun `nothing asked means as the app runs`() {
        val settings = EvalRunSettings.parse(null, null)

        assertThat(settings).isEqualTo(EvalRunSettings.APP)
        assertThat(settings.threads).isEqualTo(LiteRtLmModel.THREADS)
        assertThat(settings.priority).isEqualTo(EvalPriority.BACKGROUND)
    }

    @Test
    fun `blank arguments also mean as the app runs`() {
        assertThat(EvalRunSettings.parse("", "  ")).isEqualTo(EvalRunSettings.APP)
    }

    @Test
    fun `threads and priority can be chosen, with spaces and any case`() {
        val settings = EvalRunSettings.parse(" 4 ", "NORMAL ")

        assertThat(settings).isEqualTo(EvalRunSettings(4, EvalPriority.NORMAL))
    }

    @Test
    fun `either one alone keeps the other as the app runs`() {
        assertThat(EvalRunSettings.parse("6", null)).isEqualTo(EvalRunSettings(6, EvalPriority.BACKGROUND))
        assertThat(EvalRunSettings.parse(null, "normal")).isEqualTo(EvalRunSettings(LiteRtLmModel.THREADS, EvalPriority.NORMAL))
    }

    @Test
    fun `the thread count is limited to the phone's cores`() {
        assertThat(EvalRunSettings.parse("1", null).threads).isEqualTo(1)
        assertThat(EvalRunSettings.parse("${LiteRtLmModel.MAX_THREADS}", null).threads).isEqualTo(LiteRtLmModel.MAX_THREADS)
        listOf("0", "-2", "${LiteRtLmModel.MAX_THREADS + 1}").forEach { threads ->
            assertThrows(IllegalArgumentException::class.java) { EvalRunSettings.parse(threads, null) }
        }
    }

    @Test
    fun `a typo is refused rather than measuring the wrong thing`() {
        assertThrows(IllegalArgumentException::class.java) { EvalRunSettings.parse("four", null) }
        assertThrows(IllegalArgumentException::class.java) { EvalRunSettings.parse("2.5", null) }
        assertThrows(IllegalArgumentException::class.java) { EvalRunSettings.parse(null, "high") }
        assertThrows(IllegalArgumentException::class.java) { EvalRunSettings.parse(null, "backgrund") }
    }

    @Test
    fun `each priority maps to Android's own value`() {
        assertThat(EvalPriority.BACKGROUND.androidPriority).isEqualTo(Process.THREAD_PRIORITY_BACKGROUND)
        assertThat(EvalPriority.NORMAL.androidPriority).isEqualTo(Process.THREAD_PRIORITY_DEFAULT)
        assertThat(EvalRunSettings.parse(null, "low").priority).isEqualTo(EvalPriority.LOW)
    }

    @Test
    fun `low sits between normal and background, just short of background`() {
        // Android uses larger numbers for lower priority.
        assertThat(EvalPriority.LOW.androidPriority).isGreaterThan(EvalPriority.NORMAL.androidPriority)
        assertThat(EvalPriority.LOW.androidPriority).isEqualTo(Process.THREAD_PRIORITY_BACKGROUND - 1)
    }

    @Test
    fun `the report says whether the run matched the app`() {
        assertThat(EvalRunSettings.APP.describe()).isEqualTo("2 CPU threads, background priority (as the app runs)")
        assertThat(EvalRunSettings(4, EvalPriority.NORMAL).describe())
            .isEqualTo("4 CPU threads, normal priority (the app runs 2 CPU threads, background priority)")
    }
}
