package dev.maahdi.mavick.capture

import androidx.annotation.StringRes
import dev.maahdi.mavick.R

/**
 * The apps whose notifications Mavick reads. Every other app's notifications are ignored the
 * moment they arrive, so banking, OTP and other apps are never read.
 *
 * Stored by name: never rename a constant, or saved messages and rules become unreadable.
 */
enum class SourceApp(val packageName: String, @param:StringRes val label: Int) {
    WHATSAPP("com.whatsapp", R.string.app_whatsapp),
    WHATSAPP_BUSINESS("com.whatsapp.w4b", R.string.app_whatsapp_business),
    MESSENGER("com.facebook.orca", R.string.app_messenger),
    GMAIL("com.google.android.gm", R.string.app_gmail),
    KEEP("com.google.android.keep", R.string.app_keep),
    ;

    companion object {
        private val byPackage = entries.associateBy { it.packageName }

        /** The supported app with this package name, or null for every other app. */
        fun fromPackage(packageName: String?): SourceApp? = byPackage[packageName]
    }
}
