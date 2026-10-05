package dev.maahdi.mavick.capture

import android.content.Context

/** Release builds never record notifications; the debug build's version records on request. */
@Suppress("UNUSED_PARAMETER")
fun createNotificationRecorder(context: Context): NotificationRecorder = NotificationRecorder.NONE
