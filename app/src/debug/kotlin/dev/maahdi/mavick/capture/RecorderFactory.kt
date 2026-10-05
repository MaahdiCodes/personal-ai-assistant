package dev.maahdi.mavick.capture

import android.content.Context

/** Debug builds: a recorder that saves raw notifications while switched on (RecorderControlReceiver). */
fun createNotificationRecorder(context: Context): NotificationRecorder = JsonNotificationRecorder(context.applicationContext)
