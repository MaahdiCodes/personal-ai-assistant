@file:OptIn(ExperimentalMaterial3Api::class)

package dev.maahdi.mavick.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.maahdi.mavick.R
import java.time.LocalTime

/** A clock-face time picker in a dialog, shared by the task editor and Settings. */
@Composable
fun TimePickerDialog(initial: LocalTime, use24Hour: Boolean, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = use24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime.of(pickerState.hour, pickerState.minute))
                onDismiss()
            }) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        text = { TimePicker(state = pickerState) },
    )
}
