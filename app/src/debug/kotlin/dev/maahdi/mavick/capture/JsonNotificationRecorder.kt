package dev.maahdi.mavick.capture

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.io.File
import java.time.Clock
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug builds only. While switched on, saves each supported app's notification, with all of its
 * data except images, as a JSON file in the app's external files folder, where
 * scripts/record-notifications.ps1 collects it. Use it only with fake test messages.
 */
class JsonNotificationRecorder(
    private val context: Context,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) : NotificationRecorder {
    override fun record(sbn: StatusBarNotification) {
        if (SourceApp.fromPackage(sbn.packageName) == null) return
        if (!RecorderSwitch.isOn(context, Instant.now(clock()))) return
        val folder = context.getExternalFilesDir(FOLDER) ?: return
        if (folder.listFiles().orEmpty().size >= MAX_FILES) return
        val name = "${sbn.postTime}-${sbn.packageName}-${Integer.toHexString(sbn.key.hashCode())}.json"
        File(folder, name).writeText(toJson(sbn).toString(2))
    }

    companion object {
        const val FOLDER = "recordings"
        const val MAX_FILES = 2_000

        fun toJson(sbn: StatusBarNotification): JSONObject {
            val notification = sbn.notification
            return JSONObject()
                .put("package", sbn.packageName)
                .put("userId", userNumber(sbn))
                .put("key", sbn.key)
                .put("id", sbn.id)
                .put("tag", sbn.tag ?: JSONObject.NULL)
                .put("postTime", sbn.postTime)
                .put("when", notification.`when`)
                .put("flags", notification.flags)
                .put("isGroupSummary", (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0)
                .put("category", notification.category ?: JSONObject.NULL)
                .put("channelId", notification.channelId ?: JSONObject.NULL)
                .put("group", notification.group ?: JSONObject.NULL)
                .put("groupKey", sbn.groupKey ?: JSONObject.NULL)
                .put("shortcutId", notification.shortcutId ?: JSONObject.NULL)
                .put("actionTitles", JSONArray(notification.actions.orEmpty().map { it.title?.toString() }))
                .put("extras", bundleToJson(notification.extras))
        }

        @Suppress("DEPRECATION") // Bundle.get: the recorder must see every value, whatever its type.
        private fun bundleToJson(bundle: Bundle): JSONObject {
            val json = JSONObject()
            bundle.keySet().sorted().forEach { key ->
                val value = try {
                    valueToJson(bundle.get(key))
                } catch (e: RuntimeException) {
                    // An app's own data type that Mavick can't unpack: note it and record the rest.
                    "<unreadable: ${e.javaClass.simpleName}>"
                }
                json.put(key, value)
            }
            return json
        }

        private fun valueToJson(value: Any?): Any = when (value) {
            null -> JSONObject.NULL
            is CharSequence -> JSONObject()
                .put("text", value.toString())
                .put("length", value.length)
                .put("class", value.javaClass.simpleName)
            is Bundle -> bundleToJson(value)
            is Person -> JSONObject()
                .put("person", value.name?.toString() ?: JSONObject.NULL)
                .put("key", value.key ?: JSONObject.NULL)
                .put("uri", value.uri ?: JSONObject.NULL)
                .put("isBot", value.isBot)
                .put("isImportant", value.isImportant)
            is Icon, is Bitmap -> "<image omitted>"
            is Boolean, is Number -> value
            is Array<*> -> JSONArray(value.map(::valueToJson))
            is Iterable<*> -> JSONArray(value.map(::valueToJson))
            is BooleanArray -> JSONArray(value.toList())
            is IntArray -> JSONArray(value.toList())
            is LongArray -> JSONArray(value.toList())
            else -> "<${value.javaClass.name}>"
        }

        @Suppress("DEPRECATION")
        private fun userNumber(sbn: StatusBarNotification): Int = sbn.userId
    }
}
