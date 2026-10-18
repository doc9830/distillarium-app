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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import ru.distillarium.calculators.CalcOutput
import ru.distillarium.calculators.CalcResult
import ru.distillarium.calculators.Calculator
import ru.distillarium.calculators.CalculatorField
import ru.distillarium.calculators.CalculatorGroup
import ru.distillarium.calculators.Calculators

@Composable
fun CalculatorsScreen(onOpenCalculator: (String) -> Unit) {
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
            Calculators.groups.forEach { group ->
                val items = Calculators.all.filter { it.group == group }
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
                        onClick = { onOpenCalculator(calculator.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculatorCard(
    calculator: Calculator,
    onClick: () -> Unit,
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
            modifier = Modifier.padding(16.dp),
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
) {
    BackHandler(onBack = onBack)
    var encoded by rememberSaveable(calculator.id) {
        mutableStateOf(encodeValues(calculator.defaults()))
    }
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = calculator.title,
                subtitle = calculator.group.title,
                onBack = onBack,
                backLabel = stringResource(R.string.calc_back),
                actions = {
                    IconButton(onClick = { encoded = encodeValues(calculator.defaults()) }) {
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
        }
    }
}

private fun CalcOutput.display(): String {
    val formatted = String.format(Locale.ROOT, "%.${decimals}f", value)
    val number = formatted
        .trimEnd('0')
        .trimEnd('.')
        .replace('.', ',')
        .ifBlank { "0" }
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
