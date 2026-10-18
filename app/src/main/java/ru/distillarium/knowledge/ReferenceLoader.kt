package ru.distillarium.knowledge

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Загружает справочные таблицы из content/reference. */
object ReferenceLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(contentDir: File): List<ReferenceTable> {
        val directory = File(contentDir, "reference")
        if (!directory.isDirectory) return emptyList()
        val tables = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .sortedBy { it.path }
            .map(::parse)
            .toList()
        val duplicates = tables.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Дубли id справочников: ${duplicates.joinToString()}" }
        return tables.sortedBy { it.title }
    }

    fun parse(file: File): ReferenceTable {
        val root = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Не удалось разобрать справочник ${file.name}: ${error.message}",
                error,
            )
        }
        val id = root.string("id") ?: file.nameWithoutExtension
        val title = root.string("title")
            ?: throw IllegalArgumentException("В справочнике ${file.name} нет title")
        val columns = root.array("columns").mapIndexed { index, element ->
            val column = element as? JsonObject
                ?: throw IllegalArgumentException(
                    "В справочнике ${file.name} колонка ${index + 1} не объект",
                )
            val key = column.string("key")
                ?: throw IllegalArgumentException(
                    "В справочнике ${file.name} у колонки ${index + 1} нет key",
                )
            ReferenceColumn(
                key = key,
                title = column.string("title") ?: key,
                unit = column.string("unit"),
                numeric = column.boolean("numeric") ?: false,
            )
        }
        require(columns.isNotEmpty()) { "В справочнике ${file.name} нет колонок" }
        val rows = root.array("rows").mapIndexed { index, element ->
            val row = element as? JsonObject
                ?: throw IllegalArgumentException(
                    "В справочнике ${file.name} строка ${index + 1} не объект",
                )
            ReferenceRow(
                columns.associate { column ->
                    column.key to (row[column.key]?.let(::valueText).orEmpty())
                },
            )
        }
        require(rows.isNotEmpty()) { "В справочнике ${file.name} нет строк" }
        return ReferenceTable(
            id = id,
            title = title,
            short = root.string("short").orEmpty(),
            columns = columns,
            rows = rows,
            source = root.string("source"),
            sourceLabel = root.string("sourceLabel"),
            updated = root.string("updated"),
            note = root.string("note"),
            calcId = root.string("calc"),
        )
    }

    private fun valueText(element: kotlinx.serialization.json.JsonElement): String =
        (element as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())
}
