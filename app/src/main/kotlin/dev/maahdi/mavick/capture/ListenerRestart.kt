package dev.maahdi.mavick.capture

import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService

/**
 * Gets Android to connect Mavick's notification listener again after it stopped (HyperOS
 * especially). Switching the listener off and on makes Android notice it afresh; Mavick's
 * process and data are untouched (DONT_KILL_APP).
 */
object ListenerRestart {
    fun restart(packageManager: PackageManager, listener: ComponentName) {
        packageManager.setComponentEnabledSetting(listener, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(listener, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        NotificationListenerService.requestRebind(listener)
    }
}
