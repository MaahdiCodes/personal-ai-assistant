package dev.maahdi.mavick.capture

import android.service.notification.StatusBarNotification

/**
 * Saves the raw notifications of the supported apps, so parsers can be checked against what the
 * apps really post (scripts/record-notifications.ps1). Only debug builds have a working recorder;
 * in release builds createNotificationRecorder returns [NONE] and no recorder code is shipped.
 */
fun interface NotificationRecorder {
    fun record(sbn: StatusBarNotification)

    companion object {
        val NONE = NotificationRecorder { }
    }
}
