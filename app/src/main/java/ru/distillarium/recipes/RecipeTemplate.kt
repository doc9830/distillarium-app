package ru.distillarium.recipes

import ru.distillarium.batches.BatchKind

data class RecipeIngredient(
    val name: String,
    val amount: String,
)

data class RecipeExpected(
    val volume: Double? = null,
    val abv: Double? = null,
    val note: String? = null,
)

data class RecipeStep(
    val title: String,
    val afterDays: Int = 0,
    val wikiId: String? = null,
)

/**
 * Шаблон рецепта из контент-пакета. При создании партии значения копируются:
 * последующее обновление шаблона не меняет историю пользователя.
 */
data class RecipeTemplate(
    val id: String,
    val title: String,
    val short: String,
    val kind: BatchKind,
    val volume: Double,
    val expected: RecipeExpected = RecipeExpected(),
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
)
