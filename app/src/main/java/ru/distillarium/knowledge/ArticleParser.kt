package ru.distillarium.knowledge

/**
 * Разбирает подмножество YAML из frontmatter, которое использует конвейер
 * контента. Неизвестные поля сохраняются как строки, но приложению не мешают.
 */
object ArticleParser {
    private val KEY_RE = Regex("^[A-Za-z0-9_-]+$")
    private val ITEM_RE = Regex("^\\s*-\\s+(.*)$")

    fun parse(text: String, expectedId: String): Article {
        val lines = text.removePrefix("\uFEFF").lines()
        require(lines.firstOrNull()?.trim() == "---") {
            "Статья $expectedId: нет frontmatter"
        }
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            .let { if (it < 0) -1 else it + 1 }
        require(end > 0) { "Статья $expectedId: frontmatter не закрыт" }

        val fields = parseFields(lines.subList(1, end))
        val body = lines.subList(end + 1, lines.size).joinToString("\n")

        return Article(
            id = fields["id"]?.takeIf { it.isNotBlank() } ?: expectedId,
            title = fields["title"]?.takeIf { it.isNotBlank() } ?: expectedId,
            short = fields["short"].orEmpty(),
            category = fields["category"].orEmpty(),
            tags = fields.list("tags"),
            tech = fields.list("tech"),
            equipment = fields.list("equipment"),
            updated = fields["updated"],
            sources = fields.list("sources"),
            body = body,
            path = "wiki/$expectedId.md",
        )
    }

    private fun parseFields(lines: List<String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                index++
                continue
            }
            val colon = line.indexOf(':')
            if (colon <= 0) {
                index++
                continue
            }
            val key = line.substring(0, colon).trim()
            if (!KEY_RE.matches(key)) {
                index++
                continue
            }
            val raw = line.substring(colon + 1).trim()
            if (raw.isNotEmpty()) {
                result[key] = unquote(raw)
                index++
                continue
            }

            val items = mutableListOf<String>()
            var next = index + 1
            while (next < lines.size) {
                val item = ITEM_RE.find(lines[next]) ?: break
                items += unquote(item.groupValues[1].trim())
                next++
            }
            result[key] = items.joinToString(LIST_SEPARATOR)
            index = if (items.isEmpty()) index + 1 else next
        }
        return result
    }

    private fun unquote(raw: String): String {
        val text = raw.trim()
        if (text.length < 2) return text
        if (text.startsWith('"') && text.endsWith('"')) {
            return text
                .substring(1, text.length - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
        }
        if (text.startsWith('\'') && text.endsWith('\'')) {
            return text.substring(1, text.length - 1).replace("''", "'")
        }
        return text
    }

    private fun Map<String, String>.list(key: String): List<String> {
        val value = this[key]?.trim().orEmpty()
        if (value.isEmpty() || value == "[]") return emptyList()
        if (value.startsWith('[') && value.endsWith(']')) {
            return value.substring(1, value.length - 1)
                .split(',')
                .map { unquote(it.trim()) }
                .filter { it.isNotEmpty() }
        }
        return value.split(LIST_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private const val LIST_SEPARATOR = "\u001F"
}
