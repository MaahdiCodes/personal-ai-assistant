package dev.maahdi.mavick.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.maahdi.mavick.R
import dev.maahdi.mavick.capture.Accounts
import dev.maahdi.mavick.capture.CapturePause
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.time.DueFormatter
import java.time.Instant
import java.time.ZoneId

/** Names shown for apps, accounts and chats, shared by the message screens. */
@Composable
fun appName(app: SourceApp): String = stringResource(app.label)

/** "you@gmail.com" or "Clone (user 999)"; "Main" for the phone's own copy of an app. */
@Composable
fun accountName(accountKey: String): String {
    val label = Accounts.labelOf(accountKey)
    val user = Accounts.userIdOf(accountKey)
    return when {
        label != null -> label
        user == null || user == Accounts.MAIN_USER -> stringResource(R.string.account_main)
        else -> stringResource(R.string.account_clone, user)
    }
}

/** "WhatsApp", or "WhatsApp · Clone (user 999)" when the account isn't the main one. */
@Composable
fun appAndAccount(app: SourceApp, accountKey: String): String {
    val isMain = Accounts.labelOf(accountKey) == null && Accounts.userIdOf(accountKey) == Accounts.MAIN_USER
    return if (isMain) appName(app) else "${appName(app)} · ${accountName(accountKey)}"
}

/** The chat's name, or the app's when it names none (Keep reminders). */
@Composable
fun chatName(message: MessageEntity): String = message.conversationTitle.ifBlank { appName(message.app) }

/** "Reading is paused until Today · 15:30", or null when not paused. */
@Composable
fun pauseText(pause: CapturePause, now: Instant, zone: ZoneId, use24Hour: Boolean): String? = when (pause) {
    CapturePause.Off -> null
    CapturePause.UntilResumed -> stringResource(R.string.paused_until_resumed)
    is CapturePause.Until -> if (pause.isActive(now)) {
        stringResource(R.string.paused_until, DueFormatter.moment(pause.until, zone, now.atZone(zone).toLocalDate(), use24Hour))
    } else {
        null
    }
}
