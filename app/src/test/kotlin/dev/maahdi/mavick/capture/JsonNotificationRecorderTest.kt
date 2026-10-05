package dev.maahdi.mavick.capture

import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import java.io.File
import java.time.Duration
import java.time.Instant
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The debug build's notification recorder (scripts/record-notifications.ps1). */
@RunWith(AndroidJUnit4::class)
class JsonNotificationRecorderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val recorder = JsonNotificationRecorder(context, clock = { clock })
    private val folder: File get() = context.getExternalFilesDir(JsonNotificationRecorder.FOLDER)!!

    @After
    fun tearDown() {
        RecorderSwitch.set(context, null)
        folder.deleteRecursively()
    }

    private fun notification(packageName: String = SourceApp.WHATSAPP.packageName, text: String = "Call me today at 12"): StatusBarNotification {
        val sam = Person.Builder().setName("Sam").setKey("sam-key").build()
        val built = Notification.Builder(context, "chats")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setSubText("you@example.com")
            .setStyle(Notification.MessagingStyle(Person.Builder().setName("Me").build()).addMessage(text, 1_000L, sam))
            .build()
        return StatusBarNotification(packageName, packageName, 7, null, 10_123, 0, 0, built, Process.myUserHandle(), 1_000L)
    }

    private fun recordings(): List<JSONObject> = folder.listFiles().orEmpty().map { JSONObject(it.readText()) }

    private fun switchOn(minutes: Int) {
        RecorderControlReceiver().onReceive(
            context,
            Intent(RecorderControlReceiver.ACTION_RECORD).putExtra(RecorderControlReceiver.EXTRA_MINUTES, minutes),
        )
    }

    @Test
    fun `nothing is recorded until the recorder is switched on`() {
        recorder.record(notification())

        assertThat(recordings()).isEmpty()
    }

    @Test
    fun `once switched on, a supported app's notification is saved with all its data`() {
        RecorderSwitch.set(context, clock.instant().plus(Duration.ofMinutes(30)))

        recorder.record(notification())

        val recording = recordings().single()
        assertThat(recording.getString("package")).isEqualTo("com.whatsapp")
        val extras = recording.getJSONObject("extras")
        assertThat(extras.getJSONObject("android.subText").getString("text")).isEqualTo("you@example.com")
        val message = extras.getJSONArray("android.messages").getJSONObject(0)
        assertThat(message.getJSONObject("text").getString("text")).isEqualTo("Call me today at 12")
        assertThat(message.getJSONObject("text").getInt("length")).isEqualTo(19)
        assertThat(message.getJSONObject("sender_person").getString("key")).isEqualTo("sam-key")
    }

    @Test
    fun `other apps are never recorded`() {
        RecorderSwitch.set(context, clock.instant().plus(Duration.ofMinutes(30)))

        recorder.record(notification(packageName = "com.bkash.customerapp"))

        assertThat(recordings()).isEmpty()
    }

    @Test
    fun `the recorder switches itself off when its time is up`() {
        RecorderSwitch.set(context, clock.instant().plus(Duration.ofMinutes(30)))
        clock.advance(Duration.ofMinutes(31))

        recorder.record(notification())

        assertThat(recordings()).isEmpty()
    }

    @Test
    fun `the control receiver switches on for some minutes, and off with zero`() {
        switchOn(60)
        assertThat(RecorderSwitch.isOn(context, Instant.now())).isTrue()
        assertThat(RecorderSwitch.isOn(context, Instant.now().plus(Duration.ofMinutes(61)))).isFalse()

        switchOn(0)
        assertThat(RecorderSwitch.isOn(context, Instant.now())).isFalse()
    }

    @Test
    fun `a recording can never run longer than a day`() {
        switchOn(100_000)

        assertThat(RecorderSwitch.isOn(context, Instant.now().plus(Duration.ofHours(25)))).isFalse()
    }
}
