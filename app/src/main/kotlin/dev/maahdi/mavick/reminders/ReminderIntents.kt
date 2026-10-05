package dev.maahdi.mavick.reminders

import android.content.Intent
import android.net.Uri

/** Intent actions and task addresses shared by alarms, notifications and the app screen. */
object ReminderIntents {
    const val ACTION_REMINDER = "dev.maahdi.mavick.action.REMINDER"

    /**
     * The daily alarm (briefing and chores). Keeps the old "BRIEFING" text so that, after an
     * update, the new alarm replaces the one already set instead of running beside it.
     */
    const val ACTION_DAILY = "dev.maahdi.mavick.action.BRIEFING"
    const val ACTION_OPEN_TASK = "dev.maahdi.mavick.action.OPEN_TASK"

    /** Opens Settings, where the Health section is (from a reading warning). */
    const val ACTION_OPEN_SETTINGS = "dev.maahdi.mavick.action.OPEN_SETTINGS"

    private const val SCHEME = "mavick"
    private const val TASK_HOST = "task"

    /**
     * The task's address, put in each intent's data. It makes every task's alarm and buttons
     * distinct to Android, so one task's alarm can never replace another's.
     */
    fun taskUri(taskId: String): Uri = Uri.Builder().scheme(SCHEME).authority(TASK_HOST).appendPath(taskId).build()

    fun taskIdOf(intent: Intent): String? =
        intent.data?.takeIf { it.scheme == SCHEME && it.authority == TASK_HOST }?.lastPathSegment
}

/** The buttons on a reminder notification. */
enum class ReminderAction(val intentAction: String) {
    DONE("dev.maahdi.mavick.action.DONE"),
    SNOOZE("dev.maahdi.mavick.action.SNOOZE"),
    TOMORROW("dev.maahdi.mavick.action.TOMORROW"),
    ;

    companion object {
        fun fromIntentAction(action: String?): ReminderAction? = entries.firstOrNull { it.intentAction == action }
    }
}
