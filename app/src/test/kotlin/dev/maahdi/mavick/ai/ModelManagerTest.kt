package dev.maahdi.mavick.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.FakeElapsed
import dev.maahdi.mavick.testing.FakeLanguageModel
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.fakeModelBytes
import dev.maahdi.mavick.testing.testModelStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelManagerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("model-manager-test", Context.MODE_PRIVATE)
    private val status = AiStatusStore(preferences)
    private val clock = MutableClock(MONDAY_10AM)
    private val elapsed = FakeElapsed()
    private val cacheDir: File get() = File(folder.root, "cache")
    private var wakeUps = 0

    /** What the next model load gives. */
    private var model: LanguageModel = FakeLanguageModel()

    private fun TestScope.manager(withModel: Boolean = true): Pair<ModelManager, ModelStore> {
        val store = testModelStore(File(folder.root, "models"), withModel)
        val host = ModelHost(store, status, { model }, { clock }, elapsed)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return ModelManager(store, host, status, cacheDir, dispatcher, { wakeUps++ }, { clock }, elapsed, dispatcher) to store
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `an imported model replaces the old one, forgets old problems, clears the runtime's cache and wakes the queue`() = runTest {
        val (manager, store) = manager()
        File(cacheDir, "weights.cache").apply { parentFile!!.mkdirs() }.writeText("old")
        status.markProblem(clock.instant(), IllegalStateException())
        val bytes = fakeModelBytes(8_000)

        val result = manager.import("gemma.litertlm", bytes.size.toLong(), { bytes.inputStream() }) {}

        assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
        assertThat(store.info()!!.name).isEqualTo("gemma.litertlm")
        assertThat(manager.info()).isEqualTo(store.info())
        assertThat(status.snapshot().problem).isNull()
        assertThat(cacheDir.exists()).isFalse()
        assertThat(wakeUps).isEqualTo(1)
    }

    @Test
    fun `a refused file leaves the old model, and nobody is woken`() = runTest {
        val (manager, store) = manager()
        val before = store.info()

        val result = manager.import("photo.jpg", 5_000, { ByteArray(5_000).inputStream() }) {}

        assertThat(result).isEqualTo(ImportResult.Rejected(ImportProblem.NOT_A_MODEL))
        assertThat(store.info()).isEqualTo(before)
        assertThat(wakeUps).isEqualTo(0)
    }

    @Test
    fun `a picked file that can't be opened is refused`() = runTest {
        val (manager, _) = manager(withModel = false)
        val unreadable: () -> InputStream = { throw IOException("no access") }

        assertThat(manager.import("gemma.litertlm", null, unreadable) {}).isEqualTo(ImportResult.Rejected(ImportProblem.READ_FAILED))
        assertThat(manager.import("gemma.litertlm", null, { throw SecurityException("revoked") }) {})
            .isEqualTo(ImportResult.Rejected(ImportProblem.READ_FAILED))
    }

    @Test
    fun `importing closes a loaded model first`() = runTest {
        val loaded = FakeLanguageModel(NOT_ACTIONABLE)
        model = loaded
        val (manager, _) = manager()
        manager.check()

        manager.import("gemma.litertlm", null, { fakeModelBytes().inputStream() }) {}

        assertThat(loaded.closed).isTrue()
    }

    @Test
    fun `removing the model deletes it and the runtime's cache`() = runTest {
        val (manager, store) = manager()
        cacheDir.mkdirs()

        manager.remove()

        assertThat(store.hasModel()).isFalse()
        assertThat(manager.isUsable()).isFalse()
        assertThat(cacheDir.exists()).isFalse()
    }

    @Test
    fun `a check asks the model about a sample message and times the answer`() = runTest {
        model = object : LanguageModel {
            override fun generate(request: GenerationRequest): String {
                assertThat(request.prompt).contains(ModelManager.SAMPLE)
                elapsed.millis += 5_200
                return """{"actionable": true, "items": [{"kind": "task", "title": "Send Sam the signed form", "when_text": "Thursday 5pm", "person": "Sam", "confidence": 0.9}]}"""
            }

            override fun close() = Unit
        }
        val (manager, _) = manager()

        assertThat(manager.check()).isEqualTo(ModelCheck.Worked(millis = 5_200, itemsFound = 1))
    }

    @Test
    fun `a check without a usable model says so`() = runTest {
        val (manager, _) = manager(withModel = false)

        assertThat(manager.check()).isEqualTo(ModelCheck.NotUsable)
    }

    @Test
    fun `a model that can't give the right form fails the check`() = runTest {
        model = FakeLanguageModel("Sure! Send the form.", "Sure! Send the form.")
        val (manager, _) = manager()

        assertThat(manager.check()).isEqualTo(ModelCheck.BadAnswer)
    }

    @Test
    fun `a model that breaks while answering fails the check with the error's type`() = runTest {
        model = FakeLanguageModel().apply { failure = IllegalStateException("engine broke") }
        val (manager, _) = manager()

        assertThat(manager.check()).isEqualTo(ModelCheck.Failed("IllegalStateException"))
    }

    @Test
    fun `turning the model on again forgets the interruptions and wakes the queue`() = runTest {
        val (manager, _) = manager()
        repeat(3) { status.beginModelWork(clock.instant()) }
        assertThat(manager.isUsable()).isFalse()

        manager.turnOnAgain()

        assertThat(manager.isUsable()).isTrue()
        assertThat(wakeUps).isEqualTo(1)
    }

    private companion object {
        const val NOT_ACTIONABLE = """{"actionable": false, "items": []}"""
    }
}
