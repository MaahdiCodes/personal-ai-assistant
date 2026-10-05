package dev.maahdi.mavick.ai

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.maahdi.mavick.ai.eval.EvalOutcome
import dev.maahdi.mavick.ai.eval.EvalRunner
import dev.maahdi.mavick.ai.eval.EvalScorer
import dev.maahdi.mavick.ai.eval.EvalSet
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
 * the script collects them. Messages run on the same low-priority thread as real suggestions.
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

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val set = EvalSet.read(setFile.readText(), ZoneId.systemDefault())
        val outDir = File(context.getExternalFilesDir(null), "eval").apply {
            deleteRecursively()
            mkdirs()
        }
        val parser = WhenParser()

        val rules = EvalRunner(RuleExtractor { parser }, parser, SystemClock::elapsedRealtime).run(set.rows)
        runBlocking(SuggestionWorker.lowPriorityThread()) {
            val loadStarted = SystemClock.elapsedRealtime()
            LiteRtLmModel.load(modelFile, File(context.cacheDir, "litertlm-eval")).use { model ->
                val loadMillis = SystemClock.elapsedRealtime() - loadStarted
                val withModel = EvalRunner(GemmaExtractor(model), parser, SystemClock::elapsedRealtime).run(set.rows) { done, of ->
                    instrumentation.sendStatus(0, Bundle().apply { putString("progress", "$done of $of") })
                }
                File(outDir, "report.txt").writeText(report(withModel, rules, modelFile.name, loadMillis, set.problems))
                File(outDir, "details.csv").writeText(EvalScorer.details(withModel))
            }
        }
    }

    private fun report(withModel: List<EvalOutcome>, rules: List<EvalOutcome>, modelName: String, loadMillis: Long, problems: List<String>) = buildString {
        append(EvalScorer.summary(EvalScorer.score(withModel), modelName, loadMillis))
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
    }
}
