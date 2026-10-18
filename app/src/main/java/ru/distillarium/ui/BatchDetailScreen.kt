package ru.distillarium.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.temporal.ChronoUnit
import kotlin.math.max
import ru.distillarium.R
import ru.distillarium.batches.BatchDetails
import ru.distillarium.batches.BatchRules
import ru.distillarium.batches.BatchStatus
import ru.distillarium.batches.BatchStep
import ru.distillarium.batches.DateText
import ru.distillarium.batches.JournalKind
import ru.distillarium.batches.Measurement

@Composable
fun BatchDetailScreen(
    state: BatchesUiState,
    batchId: Long,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onCompleteStep: (BatchStep, Boolean) -> Unit,
    onAddStep: (String, Int) -> Unit,
    onAddMeasurement: (Double, Double?, String?) -> Unit,
    onAddJournal: (JournalKind, String) -> Unit,
    onSaveNotes: (String?) -> Unit,
    onSetStatus: (BatchStatus) -> Unit,
    onRepeat: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val details = state.selected?.takeIf { it.batch.id == batchId }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var addStep by rememberSaveable { mutableStateOf(false) }
    var addMeasurement by rememberSaveable { mutableStateOf(false) }
    var addJournal by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = details?.batch?.title ?: stringResource(R.string.batches_title),
                subtitle = details?.let {
                    statusLabel(it.batch.status) + " · " + kindLabel(it.batch.kind)
                },
                onBack = onBack,
                backLabel = stringResource(R.string.nav_back),
                actions = {
                    if (details != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.batch_detail_more),
                                )
                            }
                            BatchMenu(
                                expanded = showMenu,
                                status = details.batch.status,
                                onDismiss = { showMenu = false },
                                onStatus = { status ->
                                    showMenu = false
                                    onSetStatus(status)
                                },
                                onRepeat = {
                                    showMenu = false
                                    onRepeat()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            details == null && (state.detailLoading || state.loading) -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.batch_detail_loading))
                    }
                }
            }
            details == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EmptyBlock(
                        title = stringResource(R.string.batch_detail_missing),
                        text = stringResource(R.string.batch_detail_missing_text),
                        actionLabel = stringResource(R.string.nav_back),
                        onAction = onBack,
                    )
                }
            }
            else -> {
                BatchContent(
                    details = details,
                    now = now,
                    error = state.error,
                    onOpenArticle = onOpenArticle,
                    onCompleteStep = onCompleteStep,
                    onAddStep = { addStep = true },
                    onAddMeasurement = { addMeasurement = true },
                    onAddJournal = { addJournal = true },
                    onSaveNotes = onSaveNotes,
                    onRepeat = onRepeat,
                    padding = padding,
                )
            }
        }
    }
    if (addStep) {
        AddStepDialog(
            onDismiss = { addStep = false },
            onConfirm = { title, days ->
                addStep = false
                onAddStep(title, days)
            },
        )
    }
    if (addMeasurement) {
        MeasurementDialog(
            onDismiss = { addMeasurement = false },
            onConfirm = { gravity, temp, note ->
                addMeasurement = false
                onAddMeasurement(gravity, temp, note)
            },
        )
    }
    if (addJournal) {
        JournalDialog(
            onDismiss = { addJournal = false },
            onConfirm = { kind, text ->
                addJournal = false
                onAddJournal(kind, text)
            },
        )
    }
}


@Composable
private fun BatchContent(
    details: BatchDetails,
    now: Long,
    error: String?,
    padding: PaddingValues,
    onOpenArticle: (String) -> Unit,
    onCompleteStep: (BatchStep, Boolean) -> Unit,
    onAddStep: () -> Unit,
    onAddMeasurement: () -> Unit,
    onAddJournal: () -> Unit,
    onSaveNotes: (String?) -> Unit,
    onRepeat: () -> Unit,
) {
    val editable = details.batch.status == BatchStatus.Active
    var notes by rememberSaveable(details.batch.id) {
        mutableStateOf(details.batch.notes.orEmpty())
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { message ->
            item(key = "error") { ErrorBlock(message = message) }
        }
        item(key = "header") { BatchHeader(details, now) }
        item(key = "steps-title") {
            SectionHeader(
                title = stringResource(R.string.batch_detail_steps),
                actionLabel = if (editable) {
                    stringResource(R.string.batch_detail_add_step)
                } else {
                    null
                },
                onAction = onAddStep,
            )
        }
        if (details.steps.isEmpty()) {
            item(key = "steps-empty") {
                EmptyBlock(
                    title = stringResource(R.string.batch_detail_steps),
                    text = stringResource(R.string.batch_detail_steps_empty),
                    actionLabel = if (editable) {
                        stringResource(R.string.batch_detail_add_step)
                    } else {
                        null
                    },
                    onAction = if (editable) onAddStep else null,
                )
            }
        } else {
            items(details.steps, key = { "step-${it.id}" }) { step ->
                StepRow(
                    step = step,
                    now = now,
                    enabled = editable,
                    onDoneChange = { done -> onCompleteStep(step, done) },
                    onOpenArticle = onOpenArticle,
                )
            }
        }

        item(key = "measurements-title") {
            SectionHeader(
                title = stringResource(R.string.batch_detail_measurements),
                actionLabel = if (editable) {
                    stringResource(R.string.batch_detail_add_measurement)
                } else {
                    null
                },
                onAction = onAddMeasurement,
            )
        }
        if (details.measurements.isEmpty()) {
            item(key = "measurements-empty") {
                EmptyBlock(
                    title = stringResource(R.string.batch_detail_measurements),
                    text = stringResource(R.string.batch_detail_measurements_empty),
                    actionLabel = if (editable) {
                        stringResource(R.string.batch_detail_add_measurement)
                    } else {
                        null
                    },
                    onAction = if (editable) onAddMeasurement else null,
                )
            }
        } else {
            if (details.measurements.size >= 2) {
                item(key = "chart") { MeasurementChart(details.measurements) }
            }
            items(details.measurements.reversed(), key = { "measurement-${it.id}" }) { measurement ->
                MeasurementRow(measurement)
            }
        }
        item(key = "journal-title") {
            SectionHeader(
                title = stringResource(R.string.batch_detail_journal),
                actionLabel = if (editable) {
                    stringResource(R.string.batch_detail_add_journal)
                } else {
                    null
                },
                onAction = onAddJournal,
            )
        }
        if (details.journal.isEmpty()) {
            item(key = "journal-empty") {
                EmptyBlock(
                    title = stringResource(R.string.batch_detail_journal),
                    text = stringResource(R.string.batch_detail_journal_empty),
                    actionLabel = if (editable) {
                        stringResource(R.string.batch_detail_add_journal)
                    } else {
                        null
                    },
                    onAction = if (editable) onAddJournal else null,
                )
            }
        } else {
            items(details.journal, key = { "journal-${it.id}" }) { entry ->
                JournalRow(entry)
            }
        }

        item(key = "notes") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.batch_detail_notes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(stringResource(R.string.batch_detail_notes_hint))
                        },
                        minLines = 3,
                    )
                    TextButton(
                        onClick = { onSaveNotes(notes.takeIf { it.isNotBlank() }) },
                    ) {
                        Text(stringResource(R.string.batch_detail_save_notes))
                    }
                }
            }
        }
        item(key = "repeat") {
            OutlinedButton(
                onClick = onRepeat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.batch_detail_repeat))
            }
        }
    }
}


@Composable
private fun BatchMenu(
    expanded: Boolean,
    status: BatchStatus,
    onDismiss: () -> Unit,
    onStatus: (BatchStatus) -> Unit,
    onRepeat: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (status) {
            BatchStatus.Active -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.batch_detail_complete)) },
                    onClick = { onStatus(BatchStatus.Completed) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.batch_detail_archive)) },
                    onClick = { onStatus(BatchStatus.Archived) },
                )
            }
            BatchStatus.Completed -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.batch_detail_return_active)) },
                    onClick = { onStatus(BatchStatus.Active) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.batch_detail_archive)) },
                    onClick = { onStatus(BatchStatus.Archived) },
                )
            }
            BatchStatus.Archived -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.batch_detail_return_active)) },
                    onClick = { onStatus(BatchStatus.Active) },
                )
            }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.batch_detail_repeat)) },
            onClick = onRepeat,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = actionLabel, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun StepRow(
    step: BatchStep,
    now: Long,
    enabled: Boolean,
    onDoneChange: (Boolean) -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = step.isDone,
                onCheckedChange = onDoneChange,
                enabled = enabled,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (step.isDone) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    color = if (step.isDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (step.isDone) {
                    Text(
                        text = stringResource(R.string.batch_step_done),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    StepDueText(step = step, now = now)
                }
            }
            step.wikiId?.let { wikiId ->
                TextButton(onClick = { onOpenArticle(wikiId) }) {
                    Text(stringResource(R.string.batch_detail_step_wiki))
                }
            }
        }
    }
}


@Composable
private fun BatchHeader(details: BatchDetails, now: Long) {
    val batch = details.batch
    val next = details.nextStep()
    val fraction = if (details.totalSteps == 0) {
        0f
    } else {
        details.doneSteps.toFloat() / details.totalSteps
    }
    val percent = if (details.totalSteps == 0) {
        0
    } else {
        details.doneSteps * 100 / details.totalSteps
    }
    val abv = details.calculatedAbv
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = listOf(
                    kindLabel(batch.kind),
                    stringResource(R.string.batch_volume, formatVolume(batch.volume)),
                    stringResource(R.string.batch_started, DateText.shortDate(batch.startedAt)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.batch_days,
                        BatchRules.activeDay(batch.startedAt, now),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.batch_progress_percent, percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (details.totalSteps > 0) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Text(
                text = next?.let {
                    stringResource(R.string.batch_detail_current, it.title)
                } ?: stringResource(R.string.batch_no_next_step),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (abv != null) {
                Text(
                    text = stringResource(R.string.batch_abv, formatDecimal(abv, 1)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.batch_abv_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun MeasurementChart(measurements: List<Measurement>) {
    val points = measurements.sortedBy { it.at }
    if (points.size < 2) return
    val minGravity = points.minOf { it.gravity }
    val maxGravity = points.maxOf { it.gravity }
    val spanGravity = max(0.01, maxGravity - minGravity)
    val firstAt = points.first().at
    val spanTime = max(1L, points.last().at - firstAt)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.batch_detail_chart),
                style = MaterialTheme.typography.titleSmall,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                val padX = 8.dp.toPx()
                val padY = 8.dp.toPx()
                val width = size.width - padX * 2
                val height = size.height - padY * 2
                repeat(3) { index ->
                    val y = padY + height * index / 2f
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(padX, y),
                        end = Offset(padX + width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                val low = minGravity - spanGravity * 0.15
                val high = maxGravity + spanGravity * 0.15
                val spanY = high - low
                val offsets = points.map { point ->
                    val x = padX + width * (point.at - firstAt).toFloat() / spanTime.toFloat()
                    val y = padY + height * ((high - point.gravity) / spanY).toFloat()
                    Offset(x, y)
                }
                offsets.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = lineColor,
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                offsets.forEach { offset ->
                    drawCircle(
                        color = lineColor,
                        radius = 3.5.dp.toPx(),
                        center = offset,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.batch_detail_chart_start,
                        formatDecimal(points.first().gravity, 3),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.batch_detail_chart_finish,
                        formatDecimal(points.last().gravity, 3),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun MeasurementRow(measurement: Measurement) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = DateText.dateTime(measurement.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.batch_gravity,
                        formatDecimal(measurement.gravity, 3),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                measurement.temp?.let { temp ->
                    Text(
                        text = stringResource(R.string.batch_temp, formatDecimal(temp, 1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                measurement.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (measurement.abvCalc > 0) {
                Text(
                    text = stringResource(
                        R.string.batch_abv,
                        formatDecimal(measurement.abvCalc, 1),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun JournalRow(entry: ru.distillarium.batches.JournalEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = journalKindLabel(entry.kind),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = DateText.dateTime(entry.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = entry.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
