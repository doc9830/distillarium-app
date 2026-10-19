package ru.distillarium.knowledge

import java.util.Locale

/** Ветка навигационного дерева: корень, подгруппа или статья-лист. */
data class WikiNode(
    val key: String,
    val title: String,
    val articleId: String? = null,
    val children: List<WikiNode> = emptyList(),
    val articleIds: List<String> = emptyList(),
) {
    val count: Int get() = articleIds.size
    val isArticle: Boolean get() = articleId != null
}

/** Тег для быстрого входа в дерево. */
data class WikiTag(
    val key: String,
    val label: String,
    val count: Int,
)

data class SearchResult(
    val article: Article,
    val score: Int,
    val snippet: String,
    val breadcrumb: String,
    val titleMatch: Boolean,
)

class WikiLibrary(
    val articles: List<Article>,
    val redirects: Map<String, String>,
    val roots: List<WikiNode>,
    val tags: List<WikiTag>,
    val referenceTables: List<ReferenceTable> = emptyList(),
    val contentVersion: String? = null,
    val released: String? = null,
) {
    private val byId = articles.associateBy { it.id }
    private val referenceById = referenceTables.associateBy { it.id }
    private val tagKeysByArticle: Map<String, Set<String>> by lazy {
        val rootTitles = HashMap<String, String>()
        fun collect(nodes: List<WikiNode>, root: String?) {
            for (node in nodes) {
                val title = root ?: node.title
                node.articleId?.let { rootTitles[it] = title }
                collect(node.children, title)
            }
        }
        collect(roots, null)
        articles.associate { article ->
            article.id to WikiTags.tagsFor(article, rootTitles[article.id])
                .map { WikiTags.key(it) }
                .toSet()
        }
    }
    private val searchEngine by lazy { SearchEngine(articles, this) }

    fun article(id: String): Article? = byId[id]

    /** Резолвит id, `/articles/slug` из старого контента и редиректы. */
    fun resolve(target: String): Article? {
        val clean = target.trim()
            .removePrefix("wiki/")
            .removeSuffix(".md")
            .trim('/')
        byId[clean]?.let { return it }
        val oldAddress = if (clean.startsWith("articles/")) {
            "/$clean"
        } else {
            "/articles/$clean"
        }
        val redirected = redirects["/$clean"]
            ?: redirects[oldAddress]
            ?: redirects[clean]
        return redirected?.trim('/')?.let(byId::get)
    }

    fun referenceTable(id: String): ReferenceTable? =
        referenceById[WikiLinks.referenceTarget(id)]

    /** Резолвит `ref:id`, `[[ref:id|…]]` и просто id таблицы. */
    fun resolveReference(target: String): ReferenceTable? =
        referenceTable(target)

    /** Таблицы, на которые ссылается статья. */
    fun tablesForArticle(articleId: String): List<ReferenceTable> {
        val article = byId[articleId] ?: return emptyList()
        return WikiLinks.targets(article.body)
            .mapNotNull(::resolveReference)
            .distinctBy { it.id }
    }

    /** Статьи, которые ссылаются на таблицу через `[[ref:id]]`. */
    fun articlesReferencingTable(tableId: String): List<Article> {
        val id = WikiLinks.referenceTarget(tableId)
        return articles.asSequence()
            .filter { article ->
                WikiLinks.targets(article.body).any { WikiLinks.referenceTarget(it) == id }
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
            .toList()
    }

    /** Поиск по названиям, описаниям и содержимому справочников. */
    fun searchTables(query: String): List<ReferenceTable> {
        if (SearchText.tokens(query).isEmpty()) return emptyList()
        return referenceTables
            .filter { it.matches(query) }
            .sortedWith(
                compareByDescending<ReferenceTable> { table ->
                    if (table.title.contains(query.trim(), ignoreCase = true)) 1 else 0
                }.thenBy { it.title.lowercase(Locale.ROOT) },
            )
            .take(20)
    }

    fun backlinks(id: String): List<Article> = articles
        .asSequence()
        .filter { it.id != id }
        .filter { source -> WikiLinks.targets(source.body).any { resolve(it)?.id == id } }
        .sortedBy { it.title.lowercase(Locale.ROOT) }
        .toList()

    fun search(query: String, tagKey: String? = null): List<SearchResult> {
        val allowed = tagKey?.let { key ->
            tagKeysByArticle.filterValues { it.contains(key) }.keys
        }
        return searchEngine.search(query, allowed)
    }

    /** Оставляет в дереве только статьи выбранного тега. */
    fun rootsFor(tagKey: String?): List<WikiNode> {
        if (tagKey == null) return roots
        return roots.mapNotNull { prune(it, tagKey) }
    }

    fun breadcrumb(articleId: String): String {
        breadcrumbIn(roots, articleId)?.let { return it.joinToString(" / ") }
        val parts = articleId.split('/')
        val labels = parts.mapIndexed { index, part ->
            when {
                index == 0 -> WikiLabels.root(part)
                parts.size > 2 && index == 1 -> WikiLabels.processGroup(part)
                else -> null
            }
        }
        val last = byId[articleId]?.title ?: parts.last()
        return (labels.filterNotNull() + last).joinToString(" / ")
    }

    private fun breadcrumbIn(nodes: List<WikiNode>, articleId: String): List<String>? {
        for (node in nodes) {
            val trail = listOf(node.title)
            if (node.articleId == articleId) return trail
            breadcrumbIn(node.children, articleId)?.let { child ->
                return trail + child
            }
        }
        return null
    }

    private fun prune(node: WikiNode, tagKey: String): WikiNode? {
        if (node.articleId != null) {
            return node.takeIf { tagKeysByArticle[it.articleId].orEmpty().contains(tagKey) }
        }
        val children = node.children.mapNotNull { prune(it, tagKey) }
        if (children.isEmpty()) return null
        return node.copy(
            children = children,
            articleIds = children.flatMap { it.articleIds },
        )
    }
}

object WikiLinks {
    private val WIKI_LINK = Regex("""\[\[([^\]|]+)(?:\|([^\]]*))?]]""")
    private val MARKDOWN_LINK = Regex("""(?<!!)\[[^\]]*]\(([^)]+)\)""")

    /** Приводит `ref:id`, `[[ref:id]]` и `id.json` к id справочника. */
    fun referenceTarget(target: String): String = target
        .trim()
        .removePrefix("[[")
        .removeSuffix("]]")
        .removePrefix("ref:")
        .removeSuffix(".json")
        .trim('/')

    fun targets(markdown: String): List<String> =
        WIKI_LINK.findAll(markdown).map { it.groupValues[1].trim() }.toList()

    fun externalLinks(markdown: String): List<String> =
        MARKDOWN_LINK.findAll(markdown)
            .map { it.groupValues[1].trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .toList()
}

object WikiTags {
    fun tagsFor(article: Article, rootLabel: String? = null): List<String> {
        val result = LinkedHashSet<String>()
        article.tags.forEach { if (it.isNotBlank()) result += it.trim() }
        WikiLabels.category(article.category)
            ?.takeIf { it.isNotBlank() }
            ?.let(result::add)
        val root = rootLabel ?: WikiLabels.root(article.id.substringBefore('/'))
        if (root.isNotBlank()) result += root
        return result.toList()
    }

    fun key(label: String): String = label
        .trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
}
