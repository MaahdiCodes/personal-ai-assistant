package dev.maahdi.mavick.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.AiStatusStore
import dev.maahdi.mavick.ai.ImportProblem
import dev.maahdi.mavick.ai.LanguageModel
import dev.maahdi.mavick.ai.ModelCheck
import dev.maahdi.mavick.ai.ModelHost
import dev.maahdi.mavick.ai.ModelManager
import dev.maahdi.mavick.testing.FakeElapsed
import dev.maahdi.mavick.testing.FakeLanguageModel
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MainDispatcherRule
import dev.maahdi.mavick.testing.MutableClock
import dev.maahdi.mavick.testing.fakeModelBytes
import dev.maahdi.mavick.testing.testModelStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("ai-settings-test", Context.MODE_PRIVATE)
    private val status = AiStatusStore(preferences)
    private val clock = MutableClock(MONDAY_10AM)
    private var model: LanguageModel = FakeLanguageModel(WORKS)

    /** [io]: where file work runs; a real thread when a test must hold an import half-way. */
    private fun viewModel(withModel: Boolean = false, io: CoroutineDispatcher = Dispatchers.Unconfined): AiSettingsViewModel {
        val store = testModelStore(File(folder.root, "models"), withModel)
        val host = ModelHost(store, status, { model }, { clock }, FakeElapsed())
        val manager = ModelManager(store, host, status, File(folder.root, "cache"), Dispatchers.Unconfined, {}, { clock }, FakeElapsed(), io)
        return AiSettingsViewModel(manager, status)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `it starts by showing whether a model is imported`() = runTest {
        val viewModel = viewModel(withModel = true)
        viewModel.refresh().join()

        assertThat(viewModel.state.value.model!!.name).isEqualTo("test.litertlm")
        assertThat(viewModel.state.value.usable).isTrue()
    }

    @Test
    fun `an import is followed by a check of the new model`() = runTest {
        val viewModel = viewModel()
        val bytes = fakeModelBytes(6_000)

        viewModel.import("gemma.litertlm", bytes.size.toLong(), { bytes.inputStream() })!!.join()

        val state = viewModel.state.value
        val outcome = state.outcome as ModelOutcome.Imported
        assertThat(outcome.info.name).isEqualTo("gemma.litertlm")
        assertThat(outcome.check).isInstanceOf(ModelCheck.Worked::class.java)
        assertThat(state.model).isEqualTo(outcome.info)
        assertThat(state.importing).isNull()
        assertThat(state.checking).isFalse()
    }

    @Test
    fun `a refused import says why and leaves things as they were`() = runTest {
        val viewModel = viewModel()

        viewModel.import("notes.txt", 5_000, { ByteArray(5_000).inputStream() })!!.join()

        assertThat(viewModel.state.value.outcome).isEqualTo(ModelOutcome.Rejected(ImportProblem.NOT_A_MODEL))
        assertThat(viewModel.state.value.model).isNull()
    }

    @Test
    fun `while one import runs, a second is ignored`() = runTest {
        val viewModel = viewModel(io = Dispatchers.IO)
        val release = CountDownLatch(1)
        val bytes = fakeModelBytes(6_000)
        val slow = viewModel.import("first.litertlm", bytes.size.toLong(), {
            release.await(10, TimeUnit.SECONDS)
            bytes.inputStream()
        })

        val second = viewModel.import("second.litertlm", bytes.size.toLong(), { bytes.inputStream() })

        assertThat(second).isNull()
        assertThat(viewModel.state.value.busy).isTrue()
        release.countDown()
        slow!!.join()
        assertThat(viewModel.state.value.model!!.name).isEqualTo("first.litertlm")
    }

    @Test
    fun `remove and check report what happened`() = runTest {
        val viewModel = viewModel(withModel = true)

        viewModel.check()!!.join()
        assertThat((viewModel.state.value.outcome as ModelOutcome.Checked).check).isInstanceOf(ModelCheck.Worked::class.java)

        viewModel.remove()!!.join()
        assertThat(viewModel.state.value.outcome).isEqualTo(ModelOutcome.Removed)
        assertThat(viewModel.state.value.model).isNull()
    }

    @Test
    fun `a model switched off can be turned on again`() = runTest {
        val viewModel = viewModel(withModel = true)
        repeat(3) { status.beginModelWork(clock.instant()) }
        viewModel.refresh().join()
        assertThat(viewModel.state.value.status.modelSwitchedOff).isTrue()

        viewModel.turnOnAgain().join()

        assertThat(viewModel.state.value.status.modelSwitchedOff).isFalse()
        assertThat(viewModel.state.value.usable).isTrue()
    }

    private companion object {
        const val WORKS = """{"actionable": true, "items": [{"kind": "task", "title": "Send Sam the form", "when_text": "Thursday 5pm", "person": "Sam", "confidence": 0.9}]}"""
    }
}
