package dev.maahdi.mavick.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant

/**
 * Debug builds only: scripts/record-notifications.ps1 switches the recorder on for a number of
 * minutes, or off (0). Only adb can send this (the receiver requires android.permission.DUMP).
 */
class RecorderControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECORD) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0).coerceIn(0, MAX_MINUTES)
        RecorderSwitch.set(context, if (minutes == 0) null else Instant.now().plus(Duration.ofMinutes(minutes.toLong())))
    }

    companion object {
        const val ACTION_RECORD = "dev.maahdi.mavick.debug.RECORD"
        const val EXTRA_MINUTES = "minutes"

        /** A recording switches itself off after at most a day. */
        const val MAX_MINUTES = 24 * 60
    }
}

/** Whether the recorder is on, kept as an end time so that it always switches itself off. */
object RecorderSwitch {
    private const val FILE_NAME = "recorder"
    private const val KEY_UNTIL = "record_until"

    fun isOn(context: Context, now: Instant): Boolean {
        val until = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0)
        return now.toEpochMilli() < until
    }

    fun set(context: Context, until: Instant?) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit {
            if (until == null) remove(KEY_UNTIL) else putLong(KEY_UNTIL, until.toEpochMilli())
        }
    }
}
