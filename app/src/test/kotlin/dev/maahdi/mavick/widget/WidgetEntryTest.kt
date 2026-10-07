package dev.maahdi.mavick.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.MainActivity
import dev.maahdi.mavick.R
import dev.maahdi.mavick.reminders.ReminderIntents
import dev.maahdi.mavick.ui.Destination
import dev.maahdi.mavick.ui.NavigationViewModel
import org.junit.Test
import org.junit.runner.RunWith

/** How the widget and the tile reach Mavick: the intents, the activity that handles them, and the manifest. */
@RunWith(AndroidJUnit4::class)
class WidgetEntryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun destinationAfter(intent: Intent): Destination {
        var destination: Destination? = null
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> destination = ViewModelProvider(activity)[NavigationViewModel::class.java].current }
        }
        return destination!!
    }

    // --- The intents ---

    @Test
    fun `a new task intent has its own action and starts Mavick in a task of its own`() {
        val intent = WidgetIntents.newTask(context)

        assertThat(intent.action).isEqualTo(ReminderIntents.ACTION_NEW_TASK)
        assertThat(intent.component!!.className).isEqualTo(MainActivity::class.java.name)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `an open task intent carries the task's address`() {
        val intent = WidgetIntents.openTask(context, "abc-123")

        assertThat(intent.action).isEqualTo(ReminderIntents.ACTION_OPEN_TASK)
        assertThat(ReminderIntents.taskIdOf(intent)).isEqualTo("abc-123")
    }

    // --- The activity ---

    @Test
    fun `a new task intent opens an empty editor that goes back to Mavick when closed`() {
        val destination = destinationAfter(WidgetIntents.newTask(context))

        assertThat(destination).isInstanceOf(Destination.Editor::class.java)
        val editor = destination as Destination.Editor
        assertThat(editor.taskId).isNull()
        assertThat(editor.draft).isNull()
    }

    @Test
    fun `an open task intent opens that task`() {
        val destination = destinationAfter(WidgetIntents.openTask(context, "abc-123"))

        assertThat((destination as Destination.Editor).taskId).isEqualTo("abc-123")
    }

    // --- The manifest ---

    @Test
    fun `the widget's receiver is not exported and takes the widget update broadcast`() {
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, TasksWidgetProvider::class.java), PackageManager.GET_META_DATA)

        assertThat(receiver.exported).isFalse()
        assertThat(receiver.metaData.getInt("android.appwidget.provider")).isEqualTo(R.xml.tasks_widget_info)
    }

    @Test
    fun `the widget never updates on a timer`() {
        val parser = context.resources.getXml(R.xml.tasks_widget_info)
        while (parser.name != "appwidget-provider") parser.next()

        val android = "http://schemas.android.com/apk/res/android"
        assertThat(parser.getAttributeIntValue(android, "updatePeriodMillis", -1)).isEqualTo(0)
        assertThat(parser.getAttributeIntValue(android, "widgetCategory", -1)).isEqualTo(1) // home_screen only, not the lock screen
    }

    @Test
    fun `the tile is protected so that only Android can bind it`() {
        val tile = context.packageManager.getServiceInfo(ComponentName(context, NewTaskTileService::class.java), 0)

        assertThat(tile.permission).isEqualTo("android.permission.BIND_QUICK_SETTINGS_TILE")
        assertThat(context.getString(tile.labelRes)).isEqualTo("New task")
    }

    @Test
    fun `the tile is the only service besides the notification listener`() {
        val services = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong()))
            .services.orEmpty().map { it.name }.filter { it.startsWith("dev.maahdi.mavick") }

        assertThat(services).containsExactly(
            "dev.maahdi.mavick.capture.MavickNotificationListener",
            "dev.maahdi.mavick.widget.NewTaskTileService",
        )
    }
}
