package ru.distillarium.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import ru.distillarium.R
import ru.distillarium.batches.BatchDetails
import ru.distillarium.batches.BatchStatus
import ru.distillarium.calculators.CalcOutput
import ru.distillarium.calculators.CalcResult
import ru.distillarium.calculators.Calculator
import ru.distillarium.calculators.CalculatorBatchContext
import ru.distillarium.calculators.CalculatorField
import ru.distillarium.calculators.CalculatorGroup
import ru.distillarium.calculators.CalculatorLink
import ru.distillarium.calculators.Calculators

@Composable
fun CalculatorsScreen(
    onOpenCalculator: (String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (Calculator) -> Unit,
) {
    var favoritesOnly by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showFavorites = favoritesOnly ?: favorites.isNotEmpty()
    LaunchedEffect(favorites) {
        if (favorites.isEmpty()) {
            favoritesOnly = false
        } else if (favoritesOnly == null) {
            favoritesOnly = true
        }
    }
    val visible = if (showFavorites) {
        Calculators.all.filter { it.id in favorites }
    } else {
        Calculators.all
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.calc_heading),
                subtitle = stringResource(R.string.calc_count, Calculators.all.size),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        ) {
            if (favorites.isNotEmpty()) {
                item(key = "favorites-filter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = showFavorites,
                            onClick = { favoritesOnly = true },
                            label = { Text(stringResource(R.string.calc_favorites)) },
                        )
                        FilterChip(
                            selected = !showFavorites,
                            onClick = { favoritesOnly = false },
                            label = { Text(stringResource(R.string.calc_all)) },
                        )
                    }
                }
            }
            Calculators.groups.forEach { group ->
                val items = visible.filter { it.group == group }
                if (items.isEmpty()) return@forEach
                item(key = "group:${group.name}") {
                    Text(
                        text = group.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(items, key = { it.id }) { calculator ->
                    CalculatorCard(
                        calculator = calculator,
                        favorite = calculator.id in favorites,
                        onClick = { onOpenCalculator(calculator.id) },
                        onToggleFavorite = { onToggleFavorite(calculator) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculatorCard(
    calculator: Calculator,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(calculatorIcon(calculator)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = calculator.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = calculator.short,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(
                        if (favorite) R.string.calc_favorite_remove else R.string.calc_favorite_add,
                        calculator.title,
                    ),
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@DrawableRes
private fun calculatorIcon(calculator: Calculator): Int = when (calculator.id) {
    "dilution", "dilution-volume", "dilution-mass", "blend-target", "liqueur",
    "mix-temperature", "mass-volume" -> R.drawable.ic_calc_water_drop
    "blend-strength" -> R.drawable.ic_calc_science
    "heads-tails" -> R.drawable.ic_calc_science
    "takeoff-rate", "reflux-ratio" -> R.drawable.ic_calc_speed
    "abv", "sugar-addition", "ibu" -> R.drawable.ic_calc_percent
    "hydrometer-temp", "alcoholmeter-temp", "boiling-point", "wash-boiling" ->
        R.drawable.ic_calc_thermostat
    "brix", "gravity-convert" -> R.drawable.ic_calc_bubble_chart
    "sugar-wash", "fruit-wash", "grain-wash", "malt" -> R.drawable.ic_calc_science
    "spn" -> R.drawable.ic_calc_table_chart
    "heater" -> R.drawable.ic_calc_calculate
    else -> groupIcon(calculator.group)
}

@DrawableRes
private fun groupIcon(group: CalculatorGroup): Int = when (group) {
    CalculatorGroup.Finish -> R.drawable.ic_calc_water_drop
    CalculatorGroup.Separation -> R.drawable.ic_calc_science
    CalculatorGroup.Fermentation -> R.drawable.ic_calc_bubble_chart
    CalculatorGroup.Measurement -> R.drawable.ic_calc_thermostat
    CalculatorGroup.Beer -> R.drawable.ic_calc_percent
    CalculatorGroup.Equipment -> R.drawable.ic_calc_table_chart
}


@Composable
fun CalculatorDetailScreen(
    calculator: Calculator,
    onBack: () -> Unit,
    initialValues: Map<String, String> = emptyMap(),
    batch: BatchDetails? = null,
    batches: List<BatchDetails> = emptyList(),
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onOpenCalculator: (String, Map<String, String>) -> Unit = { _, _ -> },
    onSaveToBatch: (Long?, Map<String, String>, CalcResult, String?) -> Unit = { _, _, _, _ -> },
) {
    BackHandler(onBack = onBack)
    val batchContext = remember(batch) {
        batch?.let { details ->
            val ordered = details.measurements.sortedBy { it.at }
            CalculatorBatchContext(
                volume = details.batch.volume,
                initialGravity = ordered.firstOrNull()?.gravity,
                latestGravity = ordered.lastOrNull()?.gravity,
                latestTemperature = ordered.lastOrNull()?.temp,
                calculatedAbv = details.calculatedAbv,
            )
        }
    }
    val initial = remember(calculator.id, batchContext, initialValues) {
        encodeValues(
            calculator.defaults() +
                (batchContext?.let(calculator::prefillFrom) ?: emptyMap()) +
                initialValues,
        )
    }
    var encoded by rememberSaveable(calculator.id, initial) {
        mutableStateOf(initial)
    }
    var showSave by rememberSaveable(calculator.id) { mutableStateOf(false) }
    var saved by rememberSaveable(calculator.id, initial) { mutableStateOf(false) }
    val values = remember(encoded) { decodeValues(encoded, calculator.fields) }
    val inputErrors = calculator.inputErrors(values)
    val computed: Result<CalcResult>? = remember(calculator.id, values) {
        if (inputErrors.isNotEmpty()) {
            null
        } else {
            runCatching {
                val numbers = calculator.fields.associate { field ->
                    field.key to field.value(values[field.key].orEmpty())!!
                }
                calculator.calculate(numbers)
            }
        }
    }
    val result = computed?.getOrNull()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = calculator.title,
                subtitle = batch?.let {
                    stringResource(R.string.calc_batch_subtitle, it.batch.title)
                } ?: calculator.group.title,
                onBack = onBack,
                backLabel = stringResource(R.string.calc_back),
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(
                                if (favorite) {
                                    R.string.calc_favorite_remove
                                } else {
                                    R.string.calc_favorite_add
                                },
                                calculator.title,
                            ),
                            tint = if (favorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                    IconButton(onClick = {
                        encoded = initial
                        saved = false
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.calc_reset),
                        )
                    }
                },
            )
        },
        bottomBar = {
            ResultPanel(
                hasInputErrors = inputErrors.isNotEmpty(),
                computed = computed,
                actions = if (result != null) {
                    {
                        CalculatorActions(
                            result = result,
                            saved = saved,
                            onSave = { showSave = true },
                            links = calculator.links,
                            onOpenLink = { link ->
                                val prefill = calculator.transfer(link, values, result)
                                onOpenCalculator(link.targetId, prefill)
                            },
                        )
                    }
                } else {
                    null
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = calculator.short,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            item(key = "form") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.calc_inputs).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        calculator.fields.forEachIndexed { index, field ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            CalculatorInput(
                                field = field,
                                text = values[field.key].orEmpty(),
                                error = inputErrors[field.key],
                                imeAction = if (index == calculator.fields.lastIndex) {
                                    ImeAction.Done
                                } else {
                                    ImeAction.Next
                                },
                                onValueChange = { text ->
                                    saved = false
                                    encoded = encodeValues(values + (field.key to text))
                                },
                            )
                        }
                    }
                }
            }
            item(key = "details") {
                CalculatorDetails(calculator)
            }
        }
    }
    if (showSave && result != null) {
        SaveCalcSheet(
            batches = batches,
            initialBatchId = batch?.batch?.id,
            onDismiss = { showSave = false },
            onSave = { batchId, note ->
                showSave = false
                saved = true
                onSaveToBatch(batchId, values, result, note)
            },
        )
    }
}

@Composable
private fun CalculatorActions(
    result: CalcResult,
    saved: Boolean,
    onSave: () -> Unit,
    links: List<CalculatorLink>,
    onOpenLink: (CalculatorLink) -> Unit,
) {
    if (result.outputs.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.calc_save_to_batch))
        }
        if (saved) {
            Text(
                text = stringResource(R.string.calc_saved_to_batch),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        links.forEach { link ->
            OutlinedButton(
                onClick = { onOpenLink(link) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(link.title)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveCalcSheet(
    batches: List<BatchDetails>,
    initialBatchId: Long?,
    onDismiss: () -> Unit,
    onSave: (Long?, String?) -> Unit,
) {
    var selectedId by rememberSaveable(initialBatchId, batches.size) {
        mutableStateOf(
            initialBatchId ?: batches
                .firstOrNull { it.batch.status == BatchStatus.Active }
                ?.batch?.id,
        )
    }
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.calc_save_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (batches.isEmpty()) {
                Text(
                    text = stringResource(R.string.calc_save_no_batches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    batches.forEach { details ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedId == details.batch.id,
                                onClick = { selectedId = details.batch.id },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = details.batch.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = listOf(
                                        kindLabel(details.batch.kind),
                                        statusLabel(details.batch.status),
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.calc_save_note)) },
                    minLines = 2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    Button(
                        onClick = { onSave(selectedId, note.takeIf { it.isNotBlank() }) },
                        enabled = selectedId != null,
                    ) {
                        Text(stringResource(R.string.dialog_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorDetails(calculator: Calculator) {
    val source = calculator.source?.takeIf { it.isNotBlank() }
    val note = calculator.note?.takeIf { it.isNotBlank() }
    if (source == null && note == null) return
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.calc_details).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (source != null) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (note != null) {
                if (source != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalculatorInput(
    field: CalculatorField,
    text: String,
    error: String?,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = field.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = field.unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.titleLarge,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = imeAction,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        val hint = error ?: field.hint
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun ResultPanel(
    hasInputErrors: Boolean,
    computed: Result<CalcResult>?,
    actions: (@Composable () -> Unit)? = null,
) {
    val failed = hasInputErrors || computed?.isFailure == true
    val container = if (failed) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (failed) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val muted = if (failed) {
        content.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                hasInputErrors -> {
                    Text(
                        text = stringResource(R.string.calc_error),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.calc_result_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                computed == null -> {
                    Text(
                        text = stringResource(R.string.calc_result),
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                    )
                }
                computed.isSuccess -> {
                    val result = computed.getOrThrow()
                    val first = result.outputs.firstOrNull()
                    if (first != null) {
                        Text(
                            text = first.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                        )
                        Text(
                            text = first.display(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (result.outputs.size > 1) {
                        HorizontalDivider(color = content.copy(alpha = 0.16f))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            result.outputs.drop(1).forEach { output ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = output.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = muted,
                                    )
                                    Text(
                                        text = output.display(),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.calc_result_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                    }
                }
                else -> {
                    Text(
                        text = stringResource(R.string.calc_error),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = computed.exceptionOrNull()?.message
                            ?: stringResource(R.string.calc_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }
            if (actions != null) {
                HorizontalDivider(color = content.copy(alpha = 0.16f))
                actions()
            }
        }
    }
}

private fun CalcOutput.display(): String {
    val formatted = String.format(Locale.ROOT, "%.${decimals}f", value)
    val number = if ('.' in formatted) {
        formatted.trimEnd('0').trimEnd('.')
    } else {
        formatted
    }.replace('.', ',').ifBlank { "0" }
    return "$number $unit"
}

private fun encodeValues(values: Map<String, String>): String =
    values.entries.joinToString("\n") { (key, value) -> "$key=$value" }

private fun decodeValues(encoded: String, fields: List<CalculatorField>): Map<String, String> {
    val saved = encoded.lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.take(separator) to line.drop(separator + 1)
        }
        .toMap()
    return fields.associate { field ->
        field.key to (saved[field.key] ?: CalculatorField.number(field.default))
    }
}

@Composable
fun MissingCalculatorScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.calc_title),
                onBack = onBack,
                backLabel = stringResource(R.string.calc_back),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.calc_not_found),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.calc_not_found_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
