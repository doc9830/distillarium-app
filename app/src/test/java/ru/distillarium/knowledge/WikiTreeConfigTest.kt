package ru.distillarium.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiTreeConfigTest {
    private val articles = listOf(
        article("one/first", "Первая"),
        article("two/second", "Вторая"),
        article("three/third", "Третья"),
    )

    private val configJson = """
        {
          "schemaVersion": 1,
          "roots": [
            {
              "id": "root",
              "title": "Корень",
              "articles": ["one/first"],
              "children": [
                {
                  "id": "root/section",
                  "title": "Раздел",
                  "articles": ["two/second"],
                  "children": []
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun buildsConfiguredTreeAndKeepsUnplacedArticles() {
        val config = requireNotNull(WikiTreeParser.parse(configJson))
        val roots = WikiTree.build(articles, config)

        assertEquals(listOf("Корень", "Без раздела"), roots.map { it.title })
        assertEquals("Первая", roots[0].children[0].title)
        assertEquals("Раздел", roots[0].children[1].title)
        assertEquals("Вторая", roots[0].children[1].children[0].title)
        assertEquals("Третья", roots[1].children[0].title)
        assertEquals(1, roots[1].count)
    }

    @Test
    fun breadcrumbFollowsConfiguredTree() {
        val config = WikiTreeParser.parse(configJson)!!
        val library = WikiLibrary(
            articles = articles,
            redirects = emptyMap(),
            roots = WikiTree.build(articles, config),
            tags = WikiTree.tags(articles, WikiTree.build(articles, config)),
        )
        assertEquals("Корень / Раздел / Вторая", library.breadcrumb("two/second"))
        assertEquals("Без раздела / Третья", library.breadcrumb("three/third"))
    }

    @Test
    fun tagFilterUsesConfiguredRootTitles() {
        val config = WikiTreeParser.parse(configJson)!!
        val roots = WikiTree.build(articles, config)
        val library = WikiLibrary(
            articles = articles,
            redirects = emptyMap(),
            roots = roots,
            tags = WikiTree.tags(articles, roots),
        )

        val rootMatches = library.search("Первая", WikiTags.key("Корень"))
            .map { it.article.id }
        assertEquals(listOf("one/first"), rootMatches)
        assertTrue(library.search("Первая", WikiTags.key("Без раздела")).isEmpty())
    }

    @Test
    fun rejectsBrokenTreeAndFallsBackToPaths() {
        assertNull(WikiTreeParser.parse("""{"schemaVersion":1,"roots":[]}"""))
        assertNull(
            WikiTreeParser.parse(
                """{"schemaVersion":1,"roots":[{"id":"a","title":"A"},{"id":"a","title":"B"}]}""",
            ),
        )
        val roots = WikiTree.build(articles, null)
        assertEquals(listOf("One", "Three", "Two"), roots.map { it.title })
    }

    private fun article(id: String, title: String) = Article(
        id = id,
        title = title,
        short = "",
        category = "",
        tags = emptyList(),
        tech = emptyList(),
        equipment = emptyList(),
        updated = null,
        sources = emptyList(),
        body = "",
        path = "wiki/$id.md",
    )
}
