package dev.maahdi.mavick.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.FakeElapsed
import dev.maahdi.mavick.testing.FakeLanguageModel
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.testModelStore
import java.io.File
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelHostTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("model-host-test", Context.MODE_PRIVATE)
    private val status = AiStatusStore(preferences)
    private val clock = MutableClock(MONDAY_10AM)
    private val elapsed = FakeElapsed()
    private val loaded = mutableListOf<FakeLanguageModel>()

    /** What the next load does: give a fresh model (default) or throw. */
    private var load: (File) -> LanguageModel = { FakeLanguageModel().also { loaded += it } }

    private fun host(withModel: Boolean = true) = ModelHost(
        store = testModelStore(File(folder.root, "models"), withModel),
        status = status,
        loader = { file ->
            elapsed.millis += LOAD_MILLIS
            load(file)
        },
        clock = { clock },
        elapsedMillis = elapsed,
    )

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `without a model nothing is loaded and the work doesn't run`() = runTest {
        val host = host(withModel = false)

        assertThat(host.withModel { "ran" }).isNull()
        assertThat(host.isUsable()).isFalse()
        assertThat(loaded).isEmpty()
    }

    @Test
    fun `the model loads on first use, is kept for the next, and its load and answers are timed`() = runTest {
        val host = host()

        val first = host.withModel { model ->
            elapsed.millis += 3_000
            model
        }
        val second = host.withModel { it }

        assertThat(loaded).hasSize(1)
        assertThat(first).isSameInstanceAs(second)
        assertThat(host.isLoaded).isTrue()
        with(status.snapshot()) {
            assertThat(loadMillis).isEqualTo(LOAD_MILLIS)
            assertThat(loadedAt).isEqualTo(clock.instant())
            assertThat(modelAnswers).isEqualTo(2)
            assertThat(averageAnswerMillis).isEqualTo(1_500)
        }
    }

    @Test
    fun `unloading frees the model, and the next use loads it again`() = runTest {
        val host = host()
        host.withModel { it }

        host.unload()

        assertThat(loaded.single().closed).isTrue()
        assertThat(host.isLoaded).isFalse()
        host.withModel { it }
        assertThat(loaded).hasSize(2)
    }

    @Test
    fun `a model that fails to load is left alone for an hour, then tried again`() = runTest {
        load = { throw IllegalStateException("bad file") }
        val host = host()

        assertThat(host.withModel { "ran" }).isNull()
        assertThat(status.snapshot().problem).isEqualTo("IllegalStateException")

        load = { FakeLanguageModel().also { loaded += it } }
        clock.advance(Duration.ofMinutes(59))
        assertThat(host.isUsable()).isFalse()
        assertThat(host.withModel { "ran" }).isNull()
        assertThat(loaded).isEmpty()

        clock.advance(Duration.ofMinutes(1))
        assertThat(host.withModel { "ran" }).isEqualTo("ran")
    }

    @Test
    fun `a missing native library counts as a failed load, not a crash`() = runTest {
        load = { throw UnsatisfiedLinkError("no liblitertlm_jni") }
        val host = host()

        assertThat(host.withModel { "ran" }).isNull()
        assertThat(status.snapshot().problem).isEqualTo("UnsatisfiedLinkError")
        assertThat(status.snapshot().interruptions).isEqualTo(0)
    }

    @Test
    fun `work that fails closes the model, passes the error on, and pauses the model for an hour`() = runTest {
        val host = host()

        val error = runCatching { host.withModel<Unit> { throw IllegalStateException("engine broke") } }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(loaded.single().closed).isTrue()
        assertThat(status.snapshot().problem).isEqualTo("IllegalStateException")
        assertThat(host.withModel { "ran" }).isNull()
        assertThat(loaded).hasSize(1)
    }

    @Test
    fun `after Mavick stops twice during model work, the model is switched off until turned on again`() = runTest {
        val host = host()
        // As if Mavick stopped while the model worked, twice: the marks are left behind.
        status.beginModelWork(clock.instant())
        status.beginModelWork(clock.instant())
        status.beginModelWork(clock.instant())

        assertThat(host.isUsable()).isFalse()
        assertThat(host.withModel { "ran" }).isNull()
        assertThat(loaded).isEmpty()

        status.resetModel()

        assertThat(host.withModel { "ran" }).isEqualTo("ran")
    }

    @Test
    fun `work that finishes clears its mark, so a later start isn't counted as an interruption`() = runTest {
        val host = host()

        host.withModel { "ran" }
        host.withModel { "ran" }

        assertThat(status.snapshot().interruptions).isEqualTo(0)
        assertThat(preferences.contains("model_work_started_at")).isFalse()
    }

    @Test
    fun `a change to the model file waits with the model closed`() = runTest {
        val host = host()
        host.withModel { it }

        val sawClosed = host.whileClosed { loaded.single().closed }

        assertThat(sawClosed).isTrue()
        assertThat(host.isLoaded).isFalse()
    }

    private companion object {
        const val LOAD_MILLIS = 4_200L
    }
}
