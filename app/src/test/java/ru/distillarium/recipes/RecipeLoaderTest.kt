package ru.distillarium.recipes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.distillarium.batches.BatchKind

class RecipeLoaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun parserReadsTemplateFieldsAndDefaults() {
        val file = temp.newFile("test.json").apply {
            writeText(
                """
                {
                  "id": "test-recipe",
                  "title": "Тестовый рецепт",
                  "short": "Короткое описание",
                  "type": "wine",
                  "volume": 20,
                  "expected": { "volume": 18, "abv": 12.5, "note": "Примерно" },
                  "ingredients": [{ "name": "Сок", "amount": "20 л" }],
                  "steps": [
                    { "title": "Подготовить", "afterDays": 0 },
                    { "title": "Проверить", "afterDays": 2, "wikiId": "processes/ferment/primary" }
                  ]
                }
                """.trimIndent(),
            )
        }

        val recipe = RecipeLoader.parse(file)

        assertEquals("test-recipe", recipe.id)
        assertEquals("Тестовый рецепт", recipe.title)
        assertEquals("Короткое описание", recipe.short)
        assertEquals(BatchKind.Wine, recipe.kind)
        assertEquals(20.0, recipe.volume, 1e-9)
        assertEquals(18.0, recipe.expected.volume!!, 1e-9)
        assertEquals(12.5, recipe.expected.abv!!, 1e-9)
        assertEquals("Примерно", recipe.expected.note)
        assertEquals(1, recipe.ingredients.size)
        assertEquals(2, recipe.steps.size)
        assertEquals(2, recipe.steps[1].afterDays)
        assertEquals("processes/ferment/primary", recipe.steps[1].wikiId)
    }

    @Test
    fun missingTypeFallsBackToOtherKind() {
        val file = temp.newFile("blank.json").apply {
            writeText(
                """
                {
                  "title": "Без типа",
                  "short": "Описание",
                  "volume": 5,
                  "steps": [{ "title": "Первый" }]
                }
                """.trimIndent(),
            )
        }

        val recipe = RecipeLoader.parse(file)

        assertEquals(BatchKind.Other, recipe.kind)
        assertEquals(0, recipe.steps.single().afterDays)
        assertTrue(recipe.ingredients.isEmpty())
    }

    @Test
    fun loaderRejectsDuplicateRecipeIds() {
        val root = temp.newFolder("content")
        val directory = File(root, "recipes").apply { mkdirs() }
        File(directory, "first.json").writeText(
            """{ "id": "same", "title": "Первый", "short": "A", "volume": 1, "steps": [{ "title": "Шаг" }] }""",
        )
        File(directory, "second.json").writeText(
            """{ "id": "same", "title": "Второй", "short": "B", "volume": 1, "steps": [{ "title": "Шаг" }] }""",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            RecipeLoader.load(root)
        }

        assertTrue(error.message.orEmpty().contains("same"))
    }
}
