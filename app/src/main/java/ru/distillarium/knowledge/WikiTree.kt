package ru.distillarium.knowledge

/** Человеческие названия для путей пакета. */
object WikiLabels {
    val rootOrder = listOf("raw", "processes", "equipment", "problems", "recipes", "reference")
    val processGroupOrder = listOf("prepare", "ferment", "separate", "finish", "beer")

    private val roots = mapOf(
        "raw" to "Сырьё",
        "processes" to "Процессы",
        "equipment" to "Оборудование",
        "problems" to "Проблемы",
        "recipes" to "Рецепты",
        "reference" to "Справочник",
    )

    private val processGroups = mapOf(
        "prepare" to "Подготовка сырья",
        "ferment" to "Брожение",
        "separate" to "Разделение",
        "finish" to "Обработка и финиш",
        "beer" to "Пиво",
    )

    private val categories = mapOf(
        "ingredienty" to "Ингредиенты",
        "oborudovanie" to "Оборудование",
        "pivo" to "Пиво",
        "problemy-i-resheniya" to "Проблемы",
        "retsepty" to "Рецепты",
        "samogon" to "Самогон",
        "spravochnik" to "Справочник",
        "tehnologii" to "Технологии",
        "vino" to "Вино",
    )

    fun root(slug: String): String = roots[slug] ?: humanize(slug)
    fun processGroup(slug: String): String = processGroups[slug] ?: humanize(slug)
    fun category(slug: String): String? = categories[slug]?.takeIf { it.isNotBlank() }

    fun humanize(slug: String): String = slug
        .replace('-', ' ')
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase() }
}

object WikiTree {
    fun build(articles: List<Article>): List<WikiNode> {
        val grouped = articles.groupBy { it.id.substringBefore('/') }
        val rootSlugs = WikiLabels.rootOrder + grouped.keys
            .filterNot { it in WikiLabels.rootOrder }
            .sorted()
        return rootSlugs.mapNotNull { rootSlug ->
            val inRoot = grouped[rootSlug].orEmpty()
            if (inRoot.isEmpty()) return@mapNotNull null
            val children = if (rootSlug == "processes") {
                buildProcessGroups(inRoot)
            } else {
                inRoot.sortedBy { it.title.lowercase() }.map(::leaf)
            }
            WikiNode(
                key = "root:$rootSlug",
                title = WikiLabels.root(rootSlug),
                children = children,
                articleIds = inRoot.map { it.id },
            )
        }
    }

    private fun buildProcessGroups(articles: List<Article>): List<WikiNode> {
        val groups = articles.groupBy { it.id.split('/').getOrNull(1).orEmpty() }
        val slugs = WikiLabels.processGroupOrder +
            groups.keys.filterNot { it in WikiLabels.processGroupOrder }.sorted()
        return slugs.mapNotNull { slug ->
            val inGroup = groups[slug].orEmpty()
            if (inGroup.isEmpty()) return@mapNotNull null
            val leaves = inGroup.sortedBy { it.title.lowercase() }.map(::leaf)
            WikiNode(
                key = "group:processes/$slug",
                title = WikiLabels.processGroup(slug),
                children = leaves,
                articleIds = inGroup.map { it.id },
            )
        }
    }

    private fun leaf(article: Article) = WikiNode(
        key = "article:${article.id}",
        title = article.title,
        articleId = article.id,
        articleIds = listOf(article.id),
    )

    fun tags(articles: List<Article>): List<WikiTag> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        for (article in articles) {
            for (label in WikiTags.tagsFor(article)) {
                val key = WikiTags.key(label)
                val current = counts[key]
                counts[key] = if (current == null) label to 1 else current.first to current.second + 1
            }
        }
        return counts.map { (key, value) ->
            WikiTag(key = key, label = value.first, count = value.second)
        }.sortedWith(compareByDescending<WikiTag> { it.count }.thenBy { it.label })
    }
}
