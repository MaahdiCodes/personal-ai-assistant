package dev.maahdi.mavick.ai

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maahdi.mavick.ai.eval.EvalOutcome
import dev.maahdi.mavick.ai.eval.EvalRunSettings
import dev.maahdi.mavick.ai.eval.EvalRunner
import dev.maahdi.mavick.ai.eval.EvalScorer
import dev.maahdi.mavick.ai.eval.EvalSet
import dev.maahdi.mavick.ai.eval.EvalTimings
import dev.maahdi.mavick.time.WhenParser
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The accuracy check (docs/PLAN.md §8, AI eval), run on the phone by scripts/eval.ps1 with a model
 * and a labelled file in /data/local/tmp/mavick. Skipped in normal on-phone test runs.
 *
 * Writes report.txt (numbers only) and details.csv (per message, private) to the app's files, where
 * the script collects them. Messages run as real suggestions do (2 threads, background priority)
 * unless the script asks for other threads or priority, to measure the difference.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionEvalRun {
    @Test
    fun runAccuracyCheck() {
        val arguments = InstrumentationRegistry.getArguments()
        val setFile = arguments.getString(ARG_SET)?.let(::File)
        val modelFile = arguments.getString(ARG_MODEL)?.let(::File)
        assumeTrue("Run through scripts/eval.ps1", setFile != null && modelFile != null)
        check(setFile!!.canRead()) { "Can't read the labelled file at ${setFile.path}" }
        check(modelFile!!.canRead()) { "Can't read the model at ${modelFile.path}" }
        val settings = EvalRunSettings.parse(arguments.getString(ARG_THREADS), arguments.getString(ARG_PRIORITY))

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val set = EvalSet.read(setFile.readText(), ZoneId.systemDefault())
        val outDir = File(context.getExternalFilesDir(null), "eval").apply {
            deleteRecursively()
            mkdirs()
        }
        val parser = WhenParser()

        val rules = EvalRunner(RuleExtractor { parser }, parser, SystemClock::elapsedRealtime).run(set.rows)
        // Only the AI thread adds to it, and the report is written on that thread too.
        val timings = mutableListOf<ModelTimings>()
        SuggestionWorker.aiThread(settings.priority.androidPriority).use { aiThread ->
            runBlocking(aiThread) {
                val loadStarted = SystemClock.elapsedRealtime()
                LiteRtLmModel.load(modelFile, File(context.cacheDir, "litertlm-eval"), settings.threads, onTimings = { timings += it }).use { model ->
                    val loadMillis = SystemClock.elapsedRealtime() - loadStarted
                    val withModel = EvalRunner(GemmaExtractor(model), parser, SystemClock::elapsedRealtime).run(set.rows) { done, of ->
                        instrumentation.sendStatus(0, Bundle().apply { putString("progress", "$done of $of") })
                    }
                    File(outDir, "report.txt").writeText(report(withModel, rules, modelFile.name, loadMillis, settings, timings, set.problems))
                    File(outDir, "details.csv").writeText(EvalScorer.details(withModel))
                }
            }
        }
    }

    private fun report(
        withModel: List<EvalOutcome>,
        rules: List<EvalOutcome>,
        modelName: String,
        loadMillis: Long,
        settings: EvalRunSettings,
        timings: List<ModelTimings>,
        problems: List<String>,
    ) = buildString {
        append(EvalScorer.summary(EvalScorer.score(withModel), modelName, loadMillis))
        appendLine("Run: ${settings.describe()}")
        append(EvalTimings.summary(timings))
        appendLine()
        append(EvalScorer.summary(EvalScorer.score(rules), "simple rules, for comparison", loadMillis = null))
        if (problems.isNotEmpty()) {
            appendLine()
            appendLine("Lines left out of the labelled file:")
            problems.forEach { appendLine("  $it") }
        }
    }

    private companion object {
        const val ARG_SET = "evalSet"
        const val ARG_MODEL = "model"
        const val ARG_THREADS = "threads"
        const val ARG_PRIORITY = "priority"
    }
}
