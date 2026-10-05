package dev.maahdi.mavick.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.TEST_NOW
import java.time.Duration
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiStatusStoreTest {
    private val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ai-status-test", Context.MODE_PRIVATE)
    private val store = AiStatusStore(preferences)

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `nothing has happened at first`() {
        assertThat(store.snapshot()).isEqualTo(AiStatus())
        assertThat(store.snapshot().averageAnswerMillis).isNull()
    }

    @Test
    fun `checks, skips, suggestions and failures are counted`() {
        store.recordChecked(skipped = false)
        store.recordChecked(skipped = true)
        store.recordChecked(skipped = true)
        store.recordSuggested(2)
        store.recordSuggested(0)
        store.recordFailure()

        with(store.snapshot()) {
            assertThat(checked).isEqualTo(3)
            assertThat(skipped).isEqualTo(2)
            assertThat(suggested).isEqualTo(2)
            assertThat(failed).isEqualTo(1)
        }
    }

    @Test
    fun `answer times give an average`() {
        store.recordAnswer(4_000)
        store.recordAnswer(6_000)

        assertThat(store.snapshot().averageAnswerMillis).isEqualTo(5_000)
    }

    @Test
    fun `a pause is kept until the next full run`() {
        store.markPaused(AiPause.HOT, TEST_NOW)
        assertThat(store.snapshot().pause).isEqualTo(AiPause.HOT)
        assertThat(store.snapshot().pausedAt).isEqualTo(TEST_NOW)

        store.markRun(TEST_NOW.plusSeconds(60))

        assertThat(store.snapshot().pause).isNull()
        assertThat(store.snapshot().lastRunAt).isEqualTo(TEST_NOW.plusSeconds(60))
    }

    @Test
    fun `a problem leaves the model alone for an hour`() {
        store.markProblem(TEST_NOW, IllegalStateException("never stored"))

        val status = store.snapshot()
        assertThat(status.problem).isEqualTo("IllegalStateException")
        assertThat(status.inProblemPause(TEST_NOW.plus(Duration.ofMinutes(59)))).isTrue()
        assertThat(status.inProblemPause(TEST_NOW.plus(AiStatus.RETRY_AFTER))).isFalse()
        assertThat(status.retryAt()).isEqualTo(TEST_NOW.plus(AiStatus.RETRY_AFTER))
        // The error's message could hold content; only its type is kept.
        assertThat(preferences.all.values.map { it.toString() }).doesNotContain("never stored")
    }

    @Test
    fun `model work that never ended counts as an interruption, and two in a row switch the model off`() {
        store.beginModelWork(TEST_NOW)
        // Mavick stopped here; the next work finds the mark.
        store.beginModelWork(TEST_NOW.plusSeconds(10))
        assertThat(store.snapshot().interruptions).isEqualTo(1)
        assertThat(store.snapshot().modelSwitchedOff).isFalse()

        store.beginModelWork(TEST_NOW.plusSeconds(20))

        assertThat(store.snapshot().interruptions).isEqualTo(2)
        assertThat(store.snapshot().modelSwitchedOff).isTrue()
    }

    @Test
    fun `finished work clears the mark, and success ends a run of interruptions`() {
        store.beginModelWork(TEST_NOW)
        store.beginModelWork(TEST_NOW)
        store.endModelWork(completed = false)
        assertThat(store.snapshot().interruptions).isEqualTo(1)

        store.beginModelWork(TEST_NOW)
        store.endModelWork(completed = true)

        assertThat(store.snapshot().interruptions).isEqualTo(0)
        store.beginModelWork(TEST_NOW)
        assertThat(store.snapshot().interruptions).isEqualTo(0)
    }

    @Test
    fun `a new model or Turn on again forgets problems and interruptions, not the counts`() {
        store.recordChecked(skipped = false)
        store.markProblem(TEST_NOW, IllegalStateException())
        store.markLoaded(TEST_NOW, 4_200)
        store.beginModelWork(TEST_NOW)
        store.beginModelWork(TEST_NOW)
        store.beginModelWork(TEST_NOW)

        store.resetModel()

        with(store.snapshot()) {
            assertThat(problem).isNull()
            assertThat(problemAt).isNull()
            assertThat(interruptions).isEqualTo(0)
            assertThat(loadedAt).isNull()
            assertThat(checked).isEqualTo(1)
        }
        store.beginModelWork(TEST_NOW)
        assertThat(store.snapshot().interruptions).isEqualTo(0)
    }

    @Test
    fun `only counts, times and error types are stored`() {
        store.recordChecked(skipped = false)
        store.markLoaded(TEST_NOW, 4_200)
        store.markPaused(AiPause.BATTERY_LOW, TEST_NOW)
        store.markProblem(TEST_NOW, IllegalArgumentException())

        val strings = preferences.all.values.filterIsInstance<String>()
        assertThat(strings).containsExactly("BATTERY_LOW", "IllegalArgumentException")
    }
}
