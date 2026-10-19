package ru.distillarium.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import ru.distillarium.R
import ru.distillarium.batches.BatchDetails
import ru.distillarium.batches.BatchKind
import ru.distillarium.batches.BatchRules
import ru.distillarium.batches.BatchStatus
import ru.distillarium.batches.DateText
import ru.distillarium.batches.JournalKind

@Composable
fun kindLabel(kind: BatchKind): String = stringResource(
    when (kind) {
        BatchKind.Distillate -> R.string.batch_kind_distillate
        BatchKind.Wine -> R.string.batch_kind_wine
        BatchKind.Beer -> R.string.batch_kind_beer
        BatchKind.Other -> R.string.batch_kind_other
    },
)

@Composable
fun statusLabel(status: BatchStatus): String = stringResource(
    when (status) {
        BatchStatus.Active -> R.string.batch_status_active
        BatchStatus.Completed -> R.string.batch_status_completed
        BatchStatus.Archived -> R.string.batch_status_archived
    },
)

@Composable
fun journalKindLabel(kind: JournalKind): String = stringResource(
    when (kind) {
        JournalKind.Note -> R.string.journal_kind_note
        JournalKind.Addition -> R.string.journal_kind_addition
        JournalKind.Racking -> R.string.journal_kind_racking
        JournalKind.Tasting -> R.string.journal_kind_tasting
        JournalKind.Calculation -> R.string.journal_kind_calculation
    },
)

/** «Сегодня», «Завтра» или короткая дата. Просроченность показываем отдельно. */
@Composable
fun dueLabel(dueAt: Long, now: Long = System.currentTimeMillis()): String {
    val today = BatchRules.localDate(now)
    val due = BatchRules.localDate(dueAt)
    return when {
        due == today -> stringResource(R.string.batch_due_today)
        due == today.plusDays(1) -> stringResource(R.string.batch_due_tomorrow)
        else -> stringResource(R.string.batch_due_on, DateText.shortDate(dueAt))
    }
}

fun formatDecimal(value: Double, digits: Int): String =
    String.format(Locale.forLanguageTag("ru"), "%.${digits}f", value)

fun formatVolume(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        formatDecimal(value, 1)
    }

@Composable
fun BatchCard(
    details: BatchDetails,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val batch = details.batch
    val next = details.nextStep()
    var menuOpen by remember(batch.id) { mutableStateOf(false) }
    val fraction = if (details.totalSteps == 0) {
        0f
    } else {
        details.doneSteps.toFloat() / details.totalSteps
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = batch.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(
                            kindLabel(batch.kind),
                            stringResource(R.string.batch_volume, formatVolume(batch.volume)),
                            stringResource(
                                R.string.batch_started,
                                DateText.shortDate(batch.startedAt),
                            ),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(batch.status)
                    if (onDelete != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(
                                        R.string.batch_detail_more,
                                    ),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.batch_delete),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (details.totalSteps > 0) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (details.totalSteps == 0) {
                        stringResource(R.string.batch_no_steps)
                    } else {
                        stringResource(
                            R.string.batch_progress,
                            details.doneSteps,
                            details.totalSteps,
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (next != null) {
                    Text(
                        text = dueLabel(next.dueAt, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (BatchRules.isOverdue(next, now)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            Text(
                text = next?.let {
                    stringResource(R.string.batch_next_step, it.title)
                } ?: stringResource(R.string.batch_no_next_step),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
fun StatusPill(status: BatchStatus) {
    val container = when (status) {
        BatchStatus.Active -> MaterialTheme.colorScheme.primaryContainer
        BatchStatus.Completed -> MaterialTheme.colorScheme.secondaryContainer
        BatchStatus.Archived -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (status) {
        BatchStatus.Active -> MaterialTheme.colorScheme.onPrimaryContainer
        BatchStatus.Completed -> MaterialTheme.colorScheme.onSecondaryContainer
        BatchStatus.Archived -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container,
    ) {
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun EmptyBlock(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun ErrorBlock(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.now_retry),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteBatchDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_delete_title)) },
        text = { Text(stringResource(R.string.batch_delete_text, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.batch_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.batch_delete_cancel))
            }
        },
    )
}

@Composable
fun StepDueText(step: ru.distillarium.batches.BatchStep, now: Long) {
    val overdue = BatchRules.isOverdue(step, now)
    val days = java.time.temporal.ChronoUnit.DAYS.between(
        BatchRules.localDate(step.dueAt),
        BatchRules.localDate(now),
    ).toInt()
    Text(
        text = if (overdue && days > 0) {
            stringResource(R.string.batch_due_overdue_days, days)
        } else {
            dueLabel(step.dueAt, now)
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (overdue) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
