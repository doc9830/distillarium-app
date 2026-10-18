package ru.distillarium.knowledge

/** Колонка справочной таблицы. */
data class ReferenceColumn(
    val key: String,
    val title: String,
    val unit: String? = null,
    val numeric: Boolean = false,
)

/** Строка справочной таблицы: значения по ключам колонок. */
data class ReferenceRow(val values: Map<String, String>) {
    operator fun get(key: String): String = values[key].orEmpty()
}

/**
 * Справочная таблица из content/reference. Данные приходят пакетом,
 * поэтому таблицу можно обновить без выпуска APK.
 */
data class ReferenceTable(
    val id: String,
    val title: String,
    val short: String,
    val columns: List<ReferenceColumn>,
    val rows: List<ReferenceRow>,
    val source: String? = null,
    val sourceLabel: String? = null,
    val updated: String? = null,
    val note: String? = null,
    val calcId: String? = null,
) {
    val rowCount: Int get() = rows.size

    private val normalizedText: String by lazy {
        buildString {
            append(title).append(' ')
            append(short).append(' ')
            note?.let { append(it).append(' ') }
            columns.forEach { column ->
                append(column.title).append(' ')
                column.unit?.let { append(it).append(' ') }
            }
            rows.forEach { row ->
                columns.forEach { column -> append(row[column.key]).append(' ') }
            }
        }.let(SearchText::normalize)
    }

    fun cell(row: ReferenceRow, column: ReferenceColumn): String = row[column.key]

    fun matches(query: String): Boolean = matchesNormalized(normalizedText, query)

    fun rowMatches(row: ReferenceRow, query: String): Boolean {
        if (query.isBlank()) return true
        val text = SearchText.normalize(columns.joinToString(" ") { row[it.key] })
        return matchesNormalized(text, query)
    }

    /**
     * Сортировка по колонке. Числовые диапазоны вроде «10–14» сравниваются
     * по первому числу; пустые значения уходят в конец.
     */
    fun sortedRows(columnKey: String, ascending: Boolean): List<ReferenceRow> {
        val column = columns.firstOrNull { it.key == columnKey } ?: return rows
        val comparator = Comparator<ReferenceRow> { left, right ->
            val a = left[columnKey]
            val b = right[columnKey]
            val result = when {
                column.numeric -> compareNumbers(a, b)
                else -> a.compareTo(b, ignoreCase = true)
            }
            if (ascending) result else -result
        }
        return rows.sortedWith(comparator)
    }

    private fun matchesNormalized(text: String, query: String): Boolean {
        val tokens = SearchText.tokens(query)
        if (tokens.isEmpty()) return true
        return tokens.all { token ->
            SearchText.variants(token).any(text::contains)
        }
    }

    private fun compareNumbers(a: String, b: String): Int {
        val first = firstNumber(a)
        val second = firstNumber(b)
        return when {
            first != null && second != null -> first.compareTo(second)
            first != null -> -1
            second != null -> 1
            else -> a.compareTo(b, ignoreCase = true)
        }
    }

    companion object {
        private val numberRegex = Regex("""-?\d+(?:[.,]\d+)?""")

        fun firstNumber(value: String): Double? =
            numberRegex.find(value)?.value?.replace(',', '.')?.toDoubleOrNull()
    }
}
