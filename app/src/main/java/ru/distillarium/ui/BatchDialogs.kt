package ru.distillarium.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.distillarium.R
import ru.distillarium.batches.JournalKind

fun parseLocalizedDouble(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()

@Composable
fun AddStepDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var daysText by remember { mutableStateOf("1") }
    val days = daysText.toIntOrNull()
    val valid = title.isNotBlank() && days != null && days in 0..365
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_step_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_step_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) daysText = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_step_after_days)) },
                    placeholder = {
                        Text(stringResource(R.string.dialog_step_after_days_hint))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onConfirm(title.trim(), days) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.dialog_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}


@Composable
fun MeasurementDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, Double?, String?) -> Unit,
) {
    var gravityText by remember { mutableStateOf("") }
    var tempText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_measurement_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = gravityText,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() || it == ',' || it == '.' }) {
                            gravityText = value
                            invalid = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_measurement_gravity)) },
                    placeholder = {
                        Text(stringResource(R.string.dialog_measurement_gravity_hint))
                    },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) {
                            tempText = value
                            invalid = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_measurement_temp)) },
                    placeholder = {
                        Text(stringResource(R.string.dialog_measurement_temp_hint))
                    },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_measurement_note)) },
                    minLines = 2,
                )
                if (invalid) {
                    Text(
                        text = stringResource(R.string.dialog_measurement_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val gravity = parseLocalizedDouble(gravityText)
                    val temp = tempText.takeIf { it.isNotBlank() }?.let(::parseLocalizedDouble)
                    val tempValid = tempText.isBlank() ||
                        (temp != null && temp in -10.0..60.0)
                    if (gravity == null || gravity !in 0.8..1.3 || !tempValid) {
                        invalid = true
                    } else {
                        onConfirm(gravity, temp, note.takeIf { it.isNotBlank() })
                    }
                },
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
fun JournalDialog(
    onDismiss: () -> Unit,
    onConfirm: (JournalKind, String) -> Unit,
) {
    var kind by remember { mutableStateOf(JournalKind.Note) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_journal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.dialog_journal_kind),
                    style = MaterialTheme.typography.labelMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JournalKind.entries.take(2).forEach { option ->
                            FilterChip(
                                selected = kind == option,
                                onClick = { kind = option },
                                label = { Text(journalKindLabel(option)) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JournalKind.entries.drop(2).forEach { option ->
                            FilterChip(
                                selected = kind == option,
                                onClick = { kind = option },
                                label = { Text(journalKindLabel(option)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dialog_journal_text)) },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(kind, text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
