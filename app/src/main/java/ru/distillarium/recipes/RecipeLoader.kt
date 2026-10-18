package ru.distillarium.recipes

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import ru.distillarium.batches.BatchKind

/** Загружает шаблоны рецептов из content/recipes установленного пакета. */
object RecipeLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(contentDir: File): List<RecipeTemplate> {
        val directory = File(contentDir, "recipes")
        if (!directory.isDirectory) return emptyList()
        val recipes = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .sortedBy { it.path }
            .map(::parse)
            .toList()
        val duplicates = recipes.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Дубли id рецептов: ${duplicates.joinToString()}" }
        return recipes.sortedBy { it.title.lowercase() }
    }

    fun parse(file: File): RecipeTemplate {
        val root = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Не удалось разобрать рецепт ${file.name}: ${error.message}",
                error,
            )
        }
        val id = root.string("id") ?: file.nameWithoutExtension
        val title = root.string("title")
            ?: throw IllegalArgumentException("В рецепте ${file.name} нет title")
        val short = root.string("short")
            ?: throw IllegalArgumentException("В рецепте ${file.name} нет short")
        val volume = root.number("volume")
            ?: throw IllegalArgumentException("В рецепте ${file.name} нет volume")
        require(volume > 0) { "В рецепте ${file.name} volume должен быть больше нуля" }
        val steps = root.array("steps").mapIndexed { index, element ->
            val step = element as? JsonObject
                ?: throw IllegalArgumentException(
                    "В рецепте ${file.name} шаг ${index + 1} не объект",
                )
            val stepTitle = step.string("title")
                ?: throw IllegalArgumentException(
                    "В рецепте ${file.name} у шага ${index + 1} нет title",
                )
            val afterDays = step.int("afterDays") ?: 0
            require(afterDays >= 0) {
                "В рецепте ${file.name} afterDays шага ${index + 1} отрицательный"
            }
            RecipeStep(
                title = stepTitle,
                afterDays = afterDays,
                wikiId = step.string("wikiId"),
            )
        }
        require(steps.isNotEmpty()) { "В рецепте ${file.name} нет шагов" }
        return RecipeTemplate(
            id = id,
            title = title,
            short = short,
            kind = BatchKind.fromStorage(root.string("type")),
            volume = volume,
            expected = root.objectOrNull("expected")?.let { expected ->
                RecipeExpected(
                    volume = expected.number("volume"),
                    abv = expected.number("abv"),
                    note = expected.string("note"),
                )
            } ?: RecipeExpected(),
            ingredients = root.array("ingredients").mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val name = item.string("name") ?: return@mapNotNull null
                val amount = item.string("amount") ?: return@mapNotNull null
                RecipeIngredient(name, amount)
            },
            steps = steps,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())
}
