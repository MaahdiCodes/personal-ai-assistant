package dev.maahdi.mavick.ai.eval

import android.os.Process
import dev.maahdi.mavick.ai.LiteRtLmModel

/** The AI thread's priority during the accuracy check. */
enum class EvalPriority(val argument: String, val androidPriority: Int) {
    /** As real suggestions run. Android may keep such threads on the phone's small cores. */
    BACKGROUND("background", Process.THREAD_PRIORITY_BACKGROUND),

    /**
     * One step above background: still yields to anything the user is doing, but below the level
     * at which Android moves a thread to the small cores.
     */
    LOW("low", Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE),

    /** An ordinary app thread, to measure what background priority costs. */
    NORMAL("normal", Process.THREAD_PRIORITY_DEFAULT),
}

/** How the accuracy check runs the model: as the app does by default, or otherwise, to compare speeds. */
data class EvalRunSettings(val threads: Int, val priority: EvalPriority) {
    /** For the report: numbers and words only. */
    fun describe(): String = if (this == APP) "${plain()} (as the app runs)" else "${plain()} (the app runs ${APP.plain()})"

    private fun plain() = "$threads CPU threads, ${priority.argument} priority"

    companion object {
        val APP = EvalRunSettings(LiteRtLmModel.THREADS, EvalPriority.BACKGROUND)

        /**
         * From scripts/eval.ps1's arguments; a missing one means as the app runs. Anything else is
         * refused, so a typo never measures something other than what was asked.
         */
        fun parse(threads: String?, priority: String?): EvalRunSettings {
            val count = if (threads.isNullOrBlank()) {
                APP.threads
            } else {
                threads.trim().toIntOrNull() ?: throw IllegalArgumentException("threads must be a number, not \"$threads\"")
            }
            require(count in 1..LiteRtLmModel.MAX_THREADS) { "threads must be 1 to ${LiteRtLmModel.MAX_THREADS}, not $count" }
            val level = if (priority.isNullOrBlank()) {
                APP.priority
            } else {
                EvalPriority.entries.firstOrNull { it.argument == priority.trim().lowercase() }
                    ?: throw IllegalArgumentException("priority must be ${EvalPriority.entries.joinToString(" or ") { it.argument }}, not \"$priority\"")
            }
            return EvalRunSettings(count, level)
        }
    }
}
