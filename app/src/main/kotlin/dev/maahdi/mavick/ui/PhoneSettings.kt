package dev.maahdi.mavick.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * The phone's own settings screens that Mavick's "Fix" and "Turn on" buttons open.
 *
 * Android warns that some phones leave some of these screens out, and a few have them but don't
 * let other apps open them. [open] then shows Mavick's App info page instead of crashing.
 */
object PhoneSettings {
    /** Mavick's notification settings. */
    fun notifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** The list of apps' battery settings; picking "Unrestricted" needs no extra permission. */
    fun battery(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Mavick's App info page. Every phone has one, and it leads to notifications and battery. */
    fun appInfo(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /**
     * Opens [screen], or Mavick's App info page if this phone can't show it.
     *
     * @param context the visible screen's context (an Activity): Android opens screens only from one.
     * @param start opens a screen; tests replace it with a pretend phone.
     */
    fun open(context: Context, screen: Intent, start: (Intent) -> Unit = context::startActivity) {
        if (tryToOpen(screen, start)) return
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
        Log.w(TAG, "Can't open the settings screen ${screen.action}")
        return false
    }

    private const val TAG = "Mavick"
}
