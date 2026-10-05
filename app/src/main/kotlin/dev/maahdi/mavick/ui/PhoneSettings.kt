package dev.maahdi.mavick.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * The phone's own settings screens that Mavick's "Fix" and "Turn on" buttons open.
 *
 * Android warns that some phones leave some of these screens out, and a few have them but don't
 * let other apps open them. [open] then tries the next screen, and finally Mavick's App info page,
 * instead of crashing.
 */
object PhoneSettings {
    /** Mavick's notification settings. */
    fun notifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** The list of apps' battery settings; picking "Unrestricted" needs no extra permission. */
    fun battery(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** "Notification access" for Mavick's listener, then the list of all apps with that access. */
    fun notificationAccess(listener: ComponentName): Array<Intent> = arrayOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString()),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    /** Xiaomi's Autostart list (HyperOS / MIUI), which Android itself can't open or check. */
    fun xiaomiAutostart(): Intent = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )

    /** Mavick's App info page. Every phone has one, and it leads to notifications and battery. */
    fun appInfo(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Xiaomi, Redmi and Poco phones, which need extra steps to keep Mavick running (docs/PLAN.md §6). */
    fun isXiaomi(manufacturer: String = Build.MANUFACTURER, brand: String = Build.BRAND): Boolean =
        listOf(manufacturer, brand).any { it.lowercase() in XIAOMI_BRANDS }

    /**
     * Opens the first of [screens] this phone can show, or else Mavick's App info page.
     *
     * @param context the visible screen's context (an Activity): Android opens screens only from one.
     * @param start opens a screen; tests replace it with a pretend phone.
     */
    fun open(context: Context, vararg screens: Intent, start: (Intent) -> Unit = context::startActivity) {
        for (screen in screens) {
            if (tryToOpen(screen, start)) return
        }
        tryToOpen(appInfo(context), start)
    }

    /** Returns false if this phone has no such screen or won't let Mavick open it. */
    private fun tryToOpen(screen: Intent, start: (Intent) -> Unit): Boolean {
        try {
            start(screen)
            return true
        } catch (e: ActivityNotFoundException) {
            // The phone left this screen out.
        } catch (e: SecurityException) {
            // The phone has this screen but keeps it private to its own Settings app.
        }
        Log.w(TAG, "Can't open the settings screen ${screen.action ?: screen.component?.className}")
        return false
    }

    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")
    private const val TAG = "Mavick"
}
