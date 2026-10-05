package dev.maahdi.mavick.capture

/**
 * Which account of an app received a message, as a short key: "0", or "0/you@gmail.com" when the
 * app names the account.
 *
 * - The number is the Android user: 0 is the phone's main user; app clones such as Xiaomi's
 *   "Dual apps" run as another user (often 999).
 * - Gmail names the receiving address in its notifications.
 * - WhatsApp's own account switcher (several accounts inside one app) is not told apart yet; the
 *   notification recorder shows which field names the account (docs/PLAN.md §5.1). WhatsApp
 *   Business is a separate app, so it is always told apart.
 */
object Accounts {
    const val MAIN_USER = 0

    fun key(userId: Int, label: String? = null): String = if (label.isNullOrBlank()) "$userId" else "$userId/$label"

    fun userIdOf(key: String): Int? = key.substringBefore('/').toIntOrNull()

    fun labelOf(key: String): String? = key.substringAfter('/', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
}
