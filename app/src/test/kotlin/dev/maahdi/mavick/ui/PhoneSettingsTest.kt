package dev.maahdi.mavick.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PhoneSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A pretend phone: which settings screens it has, and which of those it won't let apps open. */
    private class FakePhone(private val screens: Set<String>, private val privateScreens: Set<String> = emptySet()) {
        val tried = mutableListOf<String>()
        val opened = mutableListOf<Intent>()

        fun start(intent: Intent) {
            val action = intent.action.orEmpty()
            tried += action
            if (action in privateScreens) throw SecurityException("Permission Denial: $action is not exported")
            if (action !in screens) throw ActivityNotFoundException("No Activity found to handle $action")
            opened += intent
        }
    }

    @Test
    fun `the notification screen is Mavick's own`() {
        val intent = PhoneSettings.notifications(context)

        assertThat(intent.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(context.packageName)
    }

    @Test
    fun `the App info page is Mavick's own`() {
        val intent = PhoneSettings.appInfo(context)

        assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(intent.data).isEqualTo(Uri.parse("package:${context.packageName}"))
    }

    @Test
    fun `opens the screen when the phone has it`() {
        val phone = FakePhone(setOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS))

        PhoneSettings.open(context, PhoneSettings.battery(), phone::start)

        assertThat(phone.opened.map { it.action }).containsExactly(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    @Test
    fun `a phone without the screen shows Mavick's App info instead of crashing`() {
        val phone = FakePhone(setOf(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))

        PhoneSettings.open(context, PhoneSettings.battery(), phone::start)

        val opened = phone.opened.single()
        assertThat(opened.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(opened.data).isEqualTo(Uri.parse("package:${context.packageName}"))
    }

    @Test
    fun `a phone that keeps the screen private shows Mavick's App info instead of crashing`() {
        val phone = FakePhone(
            screens = setOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
            privateScreens = setOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

        PhoneSettings.open(context, PhoneSettings.battery(), phone::start)

        assertThat(phone.opened.map { it.action }).containsExactly(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    @Test
    fun `a phone with neither screen opens nothing and does not crash`() {
        val phone = FakePhone(emptySet())

        PhoneSettings.open(context, PhoneSettings.notifications(context), phone::start)

        assertThat(phone.opened).isEmpty()
        assertThat(phone.tried).containsExactly(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS).inOrder()
    }

    @Test
    fun `by default it opens the screen through Android, from the app's screen`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        PhoneSettings.open(activity, PhoneSettings.notifications(activity))

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(started.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(activity.packageName)
    }
}
