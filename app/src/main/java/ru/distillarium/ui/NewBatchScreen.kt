package ru.distillarium.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.distillarium.R
import ru.distillarium.batches.BatchKind
import ru.distillarium.recipes.RecipeTemplate

@Composable
fun NewBatchScreen(
    state: BatchesUiState,
    onBack: () -> Unit,
    onCreate: (RecipeTemplate?, String, BatchKind, Double) -> Unit,
    onRetryRecipes: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        onRetryRecipes()
    }
    var selectedRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(BatchKind.Distillate.name) }
    var volumeText by rememberSaveable { mutableStateOf("20") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRecipe = state.recipes.firstOrNull { it.id == selectedRecipeId }
    val kind = BatchKind.entries.firstOrNull { it.name == kindName } ?: BatchKind.Distillate

    fun selectRecipe(recipe: RecipeTemplate?) {
        selectedRecipeId = recipe?.id
        if (recipe == null) {
            title = ""
            kindName = BatchKind.Distillate.name
            volumeText = "20"
        } else {
            title = recipe.title
            kindName = recipe.kind.name
            volumeText = formatVolume(recipe.volume)
        }
        validation = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.new_batch_title),
                onBack = onBack,
                backLabel = stringResource(R.string.nav_back),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = {
                        val volume = volumeText.replace(',', '.').toDoubleOrNull()
                        if (title.isBlank() || volume == null || volume <= 0) {
                            validation = "invalid"
                        } else {
                            validation = null
                            onCreate(selectedRecipe, title.trim(), kind, volume)
                        }
                    },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = if (state.busy) {
                            stringResource(R.string.new_batch_creating)
                        } else {
                            stringResource(R.string.new_batch_create)
                        },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.new_batch_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "params") {
                BatchParameters(
                    title = title,
                    onTitleChange = { title = it; validation = null },
                    volumeText = volumeText,
                    onVolumeChange = { volumeText = it; validation = null },
                    kind = kind,
                    onKindChange = { kindName = it.name },
                )
            }
            validation?.let {
                item(key = "validation") {
                    ErrorBlock(message = stringResource(R.string.new_batch_validation))
                }
            }
            state.error?.let { error ->
                item(key = "error") {
                    ErrorBlock(message = error)
                }
            }
            item(key = "templates-title") {
                SectionTitle(stringResource(R.string.new_batch_template))
            }
            item(key = "blank") {
                TemplateCard(
                    selected = selectedRecipeId == null,
                    title = stringResource(R.string.new_batch_blank),
                    short = stringResource(R.string.new_batch_blank_short),
                    meta = "",
                    onClick = { selectRecipe(null) },
                )
            }
            state.recipesError?.let {
                item(key = "recipes-error") {
                    ErrorBlock(
                        message = stringResource(R.string.new_batch_recipes_error),
                        onRetry = onRetryRecipes,
                    )
                }
            }
            items(state.recipes, key = { "recipe-${it.id}" }) { recipe ->
                TemplateCard(
                    selected = selectedRecipeId == recipe.id,
                    title = recipe.title,
                    short = recipe.short,
                    meta = templateMeta(recipe),
                    onClick = { selectRecipe(recipe) },
                )
            }
        }
    }
}

@Composable
private fun BatchParameters(
    title: String,
    onTitleChange: (String) -> Unit,
    volumeText: String,
    onVolumeChange: (String) -> Unit,
    kind: BatchKind,
    onKindChange: (BatchKind) -> Unit,
) {
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.new_batch_params),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.new_batch_name)) },
                placeholder = { Text(stringResource(R.string.new_batch_name_hint)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = volumeText,
                onValueChange = { value ->
                    if (value.all { it.isDigit() || it == ',' || it == '.' }) {
                        onVolumeChange(value)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.new_batch_volume)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Text(
                text = stringResource(R.string.new_batch_type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BatchKind.entries, key = { it.name }) { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { onKindChange(option) },
                        label = { Text(kindLabel(option)) },
                    )
                }
            }
        }
    }
}


@Composable
private fun TemplateCard(
    selected: Boolean,
    title: String,
    short: String,
    meta: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (short.isNotBlank()) {
                Text(
                    text = short,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun templateMeta(recipe: RecipeTemplate): String {
    val parts = mutableListOf(
        kindLabel(recipe.kind),
        stringResource(R.string.new_batch_steps_count, recipe.steps.size),
    )
    if (recipe.ingredients.isNotEmpty()) {
        parts += stringResource(R.string.new_batch_ingredients_count, recipe.ingredients.size)
    }
    val volume = recipe.expected.volume ?: recipe.volume
    val abv = recipe.expected.abv
    parts += if (abv != null && abv > 0) {
        stringResource(
            R.string.new_batch_expected,
            formatVolume(volume),
            formatDecimal(abv, 1),
        )
    } else {
        stringResource(R.string.new_batch_expected_volume, formatVolume(volume))
    }
    return parts.joinToString(" · ")
}
