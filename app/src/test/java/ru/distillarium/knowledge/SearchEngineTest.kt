package ru.distillarium.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private val articles = listOf(
        article(
            id = "problems/off-flavour",
            title = "Неприятный запах",
            category = "problemy-i-resheniya",
            tags = listOf("проблемы"),
            body = "Неприятный запах обычно вызван сивушными маслами. Нужен отбор хвостов.",
        ),
        article(
            id = "processes/separate/heads-hearts-tails",
            title = "Отбор голов, тела и хвостов",
            category = "samogon",
            body = "Хвосты отбирают после тела. Ориентир — крепость в струе.",
        ),
        article(
            id = "equipment/measurements",
            title = "Измерения",
            category = "oborudovanie",
            body = "Ареометр показывает плотность. Сахаромер — бытовое название.",
        ),
        article(
            id = "processes/ferment/primary",
            title = "Первичное брожение",
            category = "vino",
            body = "Брожение идёт 5–14 дней. Снимите с осадка после окончания.",
        ),
        article(
            id = "problems/no-bubbles",
            title = "Затвор не бродит",
            category = "problemy-i-resheniya",
            body = "Проверьте герметичность и плотность.",
        ),
    )

    private val library = WikiLibrary(
        articles = articles,
        redirects = mapOf("/articles/zatvor-ne-brodit" to "problems/no-bubbles"),
        roots = WikiTree.build(articles),
        tags = WikiTree.tags(articles),
        contentVersion = "test",
    )

    @Test
    fun findsByHouseholdSynonym() {
        val results = library.search("сивушные")
        assertTrue(results.any { it.article.id == "problems/off-flavour" })
        assertTrue(results.any { it.article.id == "processes/separate/heads-hearts-tails" })
    }

    @Test
    fun findsHydrometerBySugarMeter() {
        val results = library.search("сахаромер")
        assertEquals("equipment/measurements", results.first().article.id)
    }

    @Test
    fun filtersByExplicitTag() {
        val tagged = library.search("запах", "проблемы")
        assertEquals(listOf("problems/off-flavour"), tagged.map { it.article.id })
        assertTrue(library.search("запах", "вино").none { it.article.id == "problems/off-flavour" })
        assertTrue(library.tags.none { it.key == "вино" })
    }

    @Test
    fun buildsTreeAndCountsArticles() {
        val processes = library.roots.first { it.title == "Процессы" }
        assertEquals(2, processes.count)
        assertEquals(listOf("Брожение", "Разделение"), processes.children.map { it.title })
    }

    @Test
    fun resolvesOldAddressThroughRedirects() {
        assertEquals("problems/no-bubbles", library.resolve("/articles/zatvor-ne-brodit")?.id)
        assertEquals("problems/no-bubbles", library.resolve("articles/zatvor-ne-brodit")?.id)
    }

    @Test
    fun findsBacklinksInBothDirections() {
        val article = articles.first { it.id == "problems/off-flavour" }
        assertTrue(library.backlinks(article.id).isEmpty())
        val linked = article.copy(
            body = article.body + "\n\nСм. [[processes/separate/heads-hearts-tails|отбор]].",
        )
        val linkedLibrary = WikiLibrary(
            articles = articles - article + linked,
            redirects = emptyMap(),
            roots = WikiTree.build(articles - article + linked),
            tags = WikiTree.tags(articles - article + linked),
        )
        assertEquals(
            listOf("problems/off-flavour"),
            linkedLibrary.backlinks("processes/separate/heads-hearts-tails").map { it.id },
        )
    }

    private fun article(
        id: String,
        title: String,
        category: String,
        tags: List<String> = emptyList(),
        body: String,
    ) = Article(
        id = id,
        title = title,
        short = "",
        category = category,
        tags = tags,
        tech = emptyList(),
        equipment = emptyList(),
        updated = null,
        sources = emptyList(),
        body = body,
        path = "wiki/$id.md",
    )
}
