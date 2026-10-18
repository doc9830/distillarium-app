package ru.distillarium.knowledge

/** Выделяет из тела блоки `::: tabs` с секциями `== Название ==`. */
object ArticleBodyParser {
    private const val TABS_START = "::: tabs"
    private const val TABS_END = ":::"

    fun parse(body: String): List<ArticleSegment> {
        val lines = body.lines()
        val segments = mutableListOf<ArticleSegment>()
        val markdown = mutableListOf<String>()
        var index = 0

        fun flushMarkdown() {
            val text = markdown.joinToString("\n").trim('\n')
            if (text.isNotBlank()) segments += ArticleSegment.Markdown(text)
            markdown.clear()
        }

        while (index < lines.size) {
            val line = lines[index]
            if (line.trim() != TABS_START) {
                markdown += line
                index++
                continue
            }

            var end = index + 1
            while (end < lines.size && lines[end].trim() != TABS_END) end++
            if (end >= lines.size) {
                // Незакрытый блок не должен съедать остаток статьи.
                markdown += line
                index++
                continue
            }

            val tabs = parseTabs(lines.subList(index + 1, end))
            if (tabs.isEmpty()) {
                markdown += line
                index++
                continue
            }
            flushMarkdown()
            segments += ArticleSegment.Tabs(tabs)
            index = end + 1
        }
        flushMarkdown()
        return segments.ifEmpty { listOf(ArticleSegment.Markdown(body)) }
    }

    private fun parseTabs(lines: List<String>): List<ArticleTab> {
        val tabs = mutableListOf<ArticleTab>()
        var title: String? = null
        val content = mutableListOf<String>()

        fun flush() {
            val current = title ?: return
            tabs += ArticleTab(current, content.joinToString("\n").trim())
            content.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("==") && trimmed.endsWith("==") && trimmed.length > 4) {
                flush()
                title = trimmed.removePrefix("==").removeSuffix("==").trim()
            } else if (title != null) {
                content += line
            }
        }
        flush()
        return tabs.filter { it.markdown.isNotBlank() }
    }
}
