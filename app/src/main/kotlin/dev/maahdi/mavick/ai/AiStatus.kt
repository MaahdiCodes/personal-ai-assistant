package dev.maahdi.mavick.ai

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant

/** Why the AI is waiting (docs/PLAN.md §5.3, Runtime). Stored by name: never rename a constant. */
enum class AiPause { BATTERY_LOW, BATTERY_SAVER, HOT }

/** What Settings shows about suggestions. Counts and times only, never content. */
data class AiStatus(
    /** Messages looked at. */
    val checked: Long = 0,
    /** Of those, skipped by the prefilter or by your rules. */
    val skipped: Long = 0,
    val suggested: Long = 0,
    /** Unusable answers (after the retry) and model errors. */
    val failed: Long = 0,
    /** Messages the AI model answered, and how long they took in total. */
    val modelAnswers: Long = 0,
    val modelMillis: Long = 0,
    val lastRunAt: Instant? = null,
    val loadedAt: Instant? = null,
    val loadMillis: Long? = null,
    /** The last time the model failed to load or answer, and the error's type (never content). */
    val problemAt: Instant? = null,
    val problem: String? = null,
    /** Model work in a row that never finished: Mavick stopped while the model ran. */
    val interruptions: Int = 0,
    val pause: AiPause? = null,
    val pausedAt: Instant? = null,
) {
    val averageAnswerMillis: Long? get() = if (modelAnswers > 0) modelMillis / modelAnswers else null

    /** After [MAX_INTERRUPTIONS] in a row the model is switched off, so it can't keep stopping Mavick. */
    val modelSwitchedOff: Boolean get() = interruptions >= MAX_INTERRUPTIONS

    /** A problem less than [RETRY_AFTER] ago: the model is left alone until then (rules are used). */
    fun inProblemPause(now: Instant): Boolean = problemAt?.let { now.isBefore(it.plus(RETRY_AFTER)) } == true

    fun retryAt(): Instant? = problemAt?.plus(RETRY_AFTER)

    companion object {
        const val MAX_INTERRUPTIONS = 2
        val RETRY_AFTER: Duration = Duration.ofHours(1)
    }
}

/** Keeps [AiStatus] in a small private preferences file. */
class AiStatusStore(private val preferences: SharedPreferences) {
    @Synchronized
    fun recordChecked(skipped: Boolean) {
        val current = snapshot()
        preferences.edit {
            putLong(KEY_CHECKED, current.checked + 1)
            if (skipped) putLong(KEY_SKIPPED, current.skipped + 1)
        }
    }

    @Synchronized
    fun recordSuggested(count: Int) {
        if (count > 0) preferences.edit { putLong(KEY_SUGGESTED, snapshot().suggested + count) }
    }

    @Synchronized
    fun recordFailure() = preferences.edit { putLong(KEY_FAILED, snapshot().failed + 1) }

    @Synchronized
    fun recordAnswer(millis: Long) {
        val current = snapshot()
        preferences.edit {
            putLong(KEY_MODEL_ANSWERS, current.modelAnswers + 1)
            putLong(KEY_MODEL_MILLIS, current.modelMillis + millis)
        }
    }

    @Synchronized
    fun markRun(at: Instant) = preferences.edit {
        putLong(KEY_LAST_RUN, at.toEpochMilli())
        remove(KEY_PAUSE)
        remove(KEY_PAUSED_AT)
    }

    @Synchronized
    fun markPaused(reason: AiPause, at: Instant) = preferences.edit {
        putString(KEY_PAUSE, reason.name)
        putLong(KEY_PAUSED_AT, at.toEpochMilli())
    }

    @Synchronized
    fun markLoaded(at: Instant, millis: Long) = preferences.edit {
        putLong(KEY_LOADED_AT, at.toEpochMilli())
        putLong(KEY_LOAD_MILLIS, millis)
    }

    /** The model failed to load or answer; it is left alone for [AiStatus.RETRY_AFTER]. */
    @Synchronized
    fun markProblem(at: Instant, error: Throwable) = preferences.edit {
        putLong(KEY_PROBLEM_AT, at.toEpochMilli())
        putString(KEY_PROBLEM, error.javaClass.simpleName)
    }

    /**
     * Called just before the model loads or answers. A mark still there from before means Mavick
     * stopped during that work (a crash, or Android ending it), which counts as an interruption.
     */
    @Synchronized
    fun beginModelWork(at: Instant) {
        val interrupted = preferences.contains(KEY_WORK_STARTED)
        preferences.edit(commit = true) {
            if (interrupted) putInt(KEY_INTERRUPTIONS, snapshot().interruptions + 1)
            putLong(KEY_WORK_STARTED, at.toEpochMilli())
        }
    }

    /** The work returned (well or with an error). [completed]: it worked, so the run of interruptions ends. */
    @Synchronized
    fun endModelWork(completed: Boolean) = preferences.edit(commit = true) {
        remove(KEY_WORK_STARTED)
        if (completed) remove(KEY_INTERRUPTIONS)
    }

    /** A new model, or "Try again" in Settings: forget past problems and interruptions. */
    @Synchronized
    fun resetModel() = preferences.edit {
        remove(KEY_PROBLEM_AT)
        remove(KEY_PROBLEM)
        remove(KEY_INTERRUPTIONS)
        remove(KEY_WORK_STARTED)
        remove(KEY_LOADED_AT)
        remove(KEY_LOAD_MILLIS)
    }

    fun snapshot(): AiStatus = AiStatus(
        checked = preferences.getLong(KEY_CHECKED, 0),
        skipped = preferences.getLong(KEY_SKIPPED, 0),
        suggested = preferences.getLong(KEY_SUGGESTED, 0),
        failed = preferences.getLong(KEY_FAILED, 0),
        modelAnswers = preferences.getLong(KEY_MODEL_ANSWERS, 0),
        modelMillis = preferences.getLong(KEY_MODEL_MILLIS, 0),
        lastRunAt = instant(KEY_LAST_RUN),
        loadedAt = instant(KEY_LOADED_AT),
        loadMillis = preferences.getLong(KEY_LOAD_MILLIS, -1).takeIf { it >= 0 },
        problemAt = instant(KEY_PROBLEM_AT),
        problem = preferences.getString(KEY_PROBLEM, null),
        interruptions = preferences.getInt(KEY_INTERRUPTIONS, 0),
        pause = preferences.getString(KEY_PAUSE, null)?.let { name -> AiPause.entries.firstOrNull { it.name == name } },
        pausedAt = instant(KEY_PAUSED_AT),
    )

    private fun instant(key: String): Instant? = preferences.getLong(key, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    companion object {
        const val FILE_NAME = "ai_status"
        private const val KEY_CHECKED = "checked"
        private const val KEY_SKIPPED = "skipped"
        private const val KEY_SUGGESTED = "suggested"
        private const val KEY_FAILED = "failed"
        private const val KEY_MODEL_ANSWERS = "model_answers"
        private const val KEY_MODEL_MILLIS = "model_millis"
        private const val KEY_LAST_RUN = "last_run_at"
        private const val KEY_LOADED_AT = "loaded_at"
        private const val KEY_LOAD_MILLIS = "load_millis"
        private const val KEY_PROBLEM_AT = "problem_at"
        private const val KEY_PROBLEM = "problem"
        private const val KEY_WORK_STARTED = "model_work_started_at"
        private const val KEY_INTERRUPTIONS = "interruptions"
        private const val KEY_PAUSE = "pause"
        private const val KEY_PAUSED_AT = "paused_at"
    }
}
