package dev.maahdi.mavick.ai

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDateTime
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the phone: the AI runtime itself (Phase 3). The first test needs nothing; the second
 * needs a model pushed with `scripts/push-model.ps1 -ForTests` and is skipped without one.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtLmDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cacheDir = File(context.cacheDir, "litertlm-test")

    /** Loading reaches the runtime's native code: a missing library would be a LinkageError here. */
    @Test
    fun theRuntimesNativeCodeLoadsAndRefusesAFileThatIsNotAModel() {
        val notAModel = File(context.cacheDir, "not-a-model.litertlm").apply { writeText("LITERTLM but nothing else") }

        val error = runCatching { LiteRtLmModel.load(notAModel, cacheDir) }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error).isNotInstanceOf(LinkageError::class.java)
        notAModel.delete()
    }

    @Test
    fun aPushedModelAnswersASampleMessageInTheRightForm() {
        val modelFile = File(TEST_MODEL_PATH)
        assumeTrue("No test model: run scripts/push-model.ps1 -ForTests", modelFile.canRead())

        val loadStarted = SystemClock.elapsedRealtime()
        LiteRtLmModel.load(modelFile, cacheDir).use { model ->
            val loadMillis = SystemClock.elapsedRealtime() - loadStarted
            val started = SystemClock.elapsedRealtime()
            val result = GemmaExtractor(model).extract(ModelManager.sample(LocalDateTime.now()))
            val answerMillis = SystemClock.elapsedRealtime() - started

            assertThat(result).isInstanceOf(ExtractionResult.Found::class.java)
            assertThat((result as ExtractionResult.Found).items).isNotEmpty()
            assertThat(loadMillis).isLessThan(MAX_LOAD_MILLIS)
            assertThat(answerMillis).isLessThan(MAX_ANSWER_MILLIS)
        }
    }

    /** The accuracy check's timing breakdown depends on the runtime measuring each answer. */
    @Test
    fun aMeasuredModelReportsWhereEachAnswersTimeWent() {
        val modelFile = File(TEST_MODEL_PATH)
        assumeTrue("No test model: run scripts/push-model.ps1 -ForTests", modelFile.canRead())
        val timings = mutableListOf<ModelTimings>()

        LiteRtLmModel.load(modelFile, cacheDir, onTimings = { timings += it }).use { model ->
            GemmaExtractor(model).extract(ModelManager.sample(LocalDateTime.now()))
        }

        assertThat(timings).isNotEmpty()
        assertThat(timings.first().promptTokens).isGreaterThan(0)
        assertThat(timings.first().answerTokens).isGreaterThan(0)
        assertThat(timings.first().promptTokensPerSecond).isGreaterThan(0.0)
        assertThat(timings.first().answerTokensPerSecond).isGreaterThan(0.0)
    }

    private companion object {
        /** Where scripts/push-model.ps1 -ForTests puts the model; readable by test apps. */
        const val TEST_MODEL_PATH = "/data/local/tmp/mavick/model.litertlm"

        /** Generous: the phone check measures real numbers; this catches only something badly wrong. */
        const val MAX_LOAD_MILLIS = 60_000L
        const val MAX_ANSWER_MILLIS = 60_000L
    }
}
