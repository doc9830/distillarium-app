package ru.distillarium.knowledge

/** Одна статья вики в том виде, в каком она лежит в установленном пакете. */
data class Article(
    val id: String,
    val title: String,
    val short: String,
    val category: String,
    val tags: List<String>,
    val tech: List<String>,
    val equipment: List<String>,
    val updated: String?,
    val sources: List<String>,
    val body: String,
    val path: String,
) {
    val segments: List<ArticleSegment> by lazy { ArticleBodyParser.parse(body) }
    val headings: List<ArticleHeading> by lazy { MarkdownParser.headings(body) }
}

data class ArticleHeading(
    val title: String,
    val level: Int,
)

/** Тело статьи делится на обычный Markdown и блоки вкладок `::: tabs`. */
sealed interface ArticleSegment {
    data class Markdown(val text: String) : ArticleSegment
    data class Tabs(val tabs: List<ArticleTab>) : ArticleSegment
}

data class ArticleTab(
    val title: String,
    val markdown: String,
)
