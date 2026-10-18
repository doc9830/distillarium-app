package ru.distillarium.knowledge

import java.util.Locale

/**
 * Поиск по уже загруженным статьям. Индекс строится в памяти: пакет маленький,
 * а это избавляет M1 от отдельной схемы FTS и миграций.
 */
class SearchEngine(
    articles: List<Article>,
    private val library: WikiLibrary,
) {
    private val index = articles.map(::Indexed)

    fun search(query: String, allowedIds: Set<String>? = null): List<SearchResult> {
        val tokens = SearchText.tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val variants = tokens.associateWith(::variantsOf)
        val phrase = tokens.joinToString(" ")

        return index.asSequence()
            .filter { allowedIds == null || it.article.id in allowedIds }
            .mapNotNull { entry ->
                val allTokensMatch = tokens.all { token ->
                    containsAny(entry.haystack, variants.getValue(token))
                }
                val anyTokenMatch = tokens.any { token ->
                    containsAny(entry.haystack, variants.getValue(token))
                }
                if (!allTokensMatch && !anyTokenMatch) return@mapNotNull null

                SearchResult(
                    article = entry.article,
                    score = score(entry, tokens, variants, phrase, allTokensMatch),
                    snippet = snippet(entry, variants.values.flatten().toSet()),
                    breadcrumb = library.breadcrumb(entry.article.id),
                    titleMatch = containsAny(entry.title, variants.values.flatten().toSet()),
                )
            }
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.article.title })
            .take(40)
            .toList()
    }

    private fun score(
        entry: Indexed,
        tokens: List<String>,
        variants: Map<String, Set<String>>,
        phrase: String,
        allTokensMatch: Boolean,
    ): Int {
        var score = 0
        if (entry.title == phrase) score += 1000
        if (entry.title.startsWith(phrase)) score += 300
        if (entry.title.contains(phrase)) score += 180
        if (entry.headings.contains(phrase)) score += 100
        if (entry.short.contains(phrase)) score += 60
        if (entry.body.contains(phrase)) score += 40
        if (allTokensMatch) score += 80

        for (token in tokens) {
            val tokenVariants = variants.getValue(token)
            if (containsAny(entry.title, tokenVariants)) score += 100
            if (containsAny(entry.headings, tokenVariants)) score += 45
            if (containsAny(entry.short, tokenVariants)) score += 30
            if (containsAny(entry.tags, tokenVariants)) score += 25
            if (containsAny(entry.body, tokenVariants)) score += 10
        }
        return score
    }

    private fun snippet(entry: Indexed, variants: Set<String>): String {
        val source = entry.plainBody.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (source.isEmpty()) return entry.article.short
        val match = SearchText.tokenRegex.findAll(source).firstOrNull { result ->
            SearchText.stem(result.value.lowercase(Locale.ROOT).replace('ё', 'е')) in variants
        } ?: return entry.article.short.ifBlank { source.take(220).trim() + "…" }

        var start = match.range.first
        val floor = (start - 90).coerceAtLeast(0)
        for (index in start downTo floor) {
            if (source[index] == '.' || source[index] == '!' || source[index] == '?') {
                start = index + 1
                break
            }
        }
        start = start.coerceAtMost(match.range.first)
        val end = (start + 220).coerceAtMost(source.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < source.length) "…" else ""
        return prefix + source.substring(start, end).trim() + suffix
    }

    private fun containsAny(haystack: String, needles: Set<String>): Boolean =
        needles.any(haystack::contains)

    private fun variantsOf(token: String): Set<String> = SearchText.variants(token)

    private data class Indexed(val article: Article) {
        val title = SearchText.normalize(article.title)
        val short = SearchText.normalize(article.short)
        val headings = SearchText.normalize(article.headings.joinToString(" ") { it.title })
        val tags = SearchText.normalize(WikiTags.tagsFor(article).joinToString(" "))
        val plainBody = MarkdownParser.plainText(article.body)
        val body = SearchText.normalize(plainBody)
        val haystack = "$title $short $headings $tags $body"
    }
}
