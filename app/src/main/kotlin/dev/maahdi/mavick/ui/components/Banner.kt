package dev.maahdi.mavick.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown across the top of a screen, with an optional button: a problem the user can usually fix,
 * or, with [problem] false, news such as suggested tasks waiting.
 */
@Composable
fun Banner(message: String, action: String?, onAction: () -> Unit, detail: String? = null, problem: Boolean = true) {
    val background = if (problem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (problem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = background, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(message, color = content)
                if (detail != null) {
                    Text(detail, color = content, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}
