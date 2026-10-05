package dev.maahdi.mavick.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SuggestionWorkerTest {
    private var runs = 0
    private var unloads = 0
    private val saved = mutableListOf<Int>()

    private fun TestScope.worker(process: suspend () -> Int = { 0 }) = SuggestionWorker(
        process = {
            runs++
            process()
        },
        unload = { unloads++ },
        onSaved = { saved += it },
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `a wake-up runs the queue and reports what it saved`() = runTest {
        val worker = worker { 2 }

        worker.wake()
        runCurrent()

        assertThat(runs).isEqualTo(1)
        assertThat(saved).containsExactly(2)
    }

    @Test
    fun `a run that saves nothing tells no one`() = runTest {
        val worker = worker { 0 }

        worker.wake()
        runCurrent()

        assertThat(runs).isEqualTo(1)
        assertThat(saved).isEmpty()
    }

    @Test
    fun `wake-ups during a run merge into one more run`() = runTest {
        val firstRunMayEnd = CompletableDeferred<Unit>()
        val worker = worker {
            if (runs == 1) firstRunMayEnd.await()
            0
        }
        worker.wake()
        runCurrent()

        worker.wake()
        worker.wake()
        worker.wake()
        firstRunMayEnd.complete(Unit)
        runCurrent()

        assertThat(runs).isEqualTo(2)
    }

    @Test
    fun `the model is closed a minute after the last run, and a new run puts that off`() = runTest {
        val worker = worker()
        worker.wake()
        runCurrent()

        advanceTimeBy(SuggestionWorker.UNLOAD_AFTER_MS - 1_000)
        runCurrent()
        assertThat(unloads).isEqualTo(0)

        worker.wake()
        runCurrent()
        advanceTimeBy(SuggestionWorker.UNLOAD_AFTER_MS - 1_000)
        runCurrent()
        assertThat(unloads).isEqualTo(0)

        advanceTimeBy(2_000)
        runCurrent()
        assertThat(unloads).isEqualTo(1)
    }

    @Test
    fun `a failing run doesn't stop the next one`() = runTest {
        val worker = worker {
            if (runs == 1) throw IllegalStateException("database closed")
            1
        }

        worker.wake()
        runCurrent()
        worker.wake()
        runCurrent()

        assertThat(runs).isEqualTo(2)
        assertThat(saved).containsExactly(1)
    }
}
