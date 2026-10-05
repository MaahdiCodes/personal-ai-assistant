package dev.maahdi.mavick.ui.inbox

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
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
fun chatName(message: MessageEntity): String = chatName(message.conversationTitle, message.app)

@Composable
fun chatName(title: String, app: SourceApp): String = title.ifBlank { appName(app) }

/** The first line of a task's notes: where the message came from. */
@Composable
fun originLine(message: MessageEntity): String =
    originLine(message.app, message.conversationTitle, message.sender, message.isFromMe, message.isGroup)

@Composable
fun originLine(app: SourceApp, chatTitle: String, sender: String?, isFromMe: Boolean, isGroup: Boolean): String {
    val appName = appName(app)
    return when {
        isFromMe -> stringResource(R.string.origin_you, chatName(chatTitle, app), appName)
        sender == null -> stringResource(R.string.origin_app, appName)
        isGroup -> stringResource(R.string.origin_group, sender, chatName(chatTitle, app), appName)
        else -> stringResource(R.string.origin_person, sender, appName)
    }
}

/**
 * Asks before "Never read this chat": new messages from it are skipped, and its [messageCount]
 * saved messages (and their suggestions) deleted.
 */
@Composable
fun NeverReadChatDialog(chatName: String, messageCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.never_read_confirm_title, chatName)) },
        text = { Text(pluralStringResource(R.plurals.never_read_confirm_text, messageCount, messageCount)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.never_read_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

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
