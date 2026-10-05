package dev.maahdi.mavick.capture

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListenerRestartTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val listener = ComponentName(context, MavickNotificationListener::class.java)

    @Test
    fun `restarting leaves the listener switched on`() {
        ListenerRestart.restart(context.packageManager, listener)

        assertThat(context.packageManager.getComponentEnabledSetting(listener)).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    @Test
    fun `the listener is declared so that only Android can connect to it`() {
        val info = context.packageManager.getServiceInfo(listener, PackageManager.ComponentInfoFlags.of(0))

        assertThat(info.permission).isEqualTo("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        assertThat(NotificationListenerService::class.java.isAssignableFrom(MavickNotificationListener::class.java)).isTrue()
    }
}
