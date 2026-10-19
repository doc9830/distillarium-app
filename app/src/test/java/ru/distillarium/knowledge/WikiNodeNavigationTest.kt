package ru.distillarium.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiNodeNavigationTest {
    private val article = WikiNode(
        key = "processes/ferment/primary",
        title = "Первичное брожение",
        articleId = "processes/ferment/primary",
    )
    private val subsection = WikiNode(
        key = "processes/ferment",
        title = "Брожение",
        children = listOf(article),
        articleIds = listOf("processes/ferment/primary"),
    )
    private val root = WikiNode(
        key = "processes",
        title = "Процессы",
        children = listOf(subsection),
        articleIds = listOf("processes/ferment/primary"),
    )
    private val roots = listOf(
        root,
        WikiNode(
            key = "equipment",
            title = "Оборудование",
            articleIds = listOf("equipment/measurements"),
        ),
    )

    @Test
    fun findNodeLocatesNestedSectionAndArticle() {
        assertEquals(root, roots.findNode("processes"))
        assertEquals(subsection, roots.findNode("processes/ferment"))
        assertEquals(article, roots.findNode("processes/ferment/primary"))
        assertNull(roots.findNode("processes/unknown"))
    }

    @Test
    fun trailToReturnsPathFromRootToTarget() {
        assertEquals(
            listOf("Процессы", "Брожение", "Первичное брожение"),
            roots.trailTo("processes/ferment/primary").map { it.title },
        )
        assertEquals(listOf("Процессы"), roots.trailTo("processes").map { it.title })
        assertEquals(listOf("Оборудование"), roots.trailTo("equipment").map { it.title })
    }

    @Test
    fun trailToReturnsEmptyPathForUnknownKey() {
        assertTrue(roots.trailTo("unknown").isEmpty())
        assertTrue(emptyList<WikiNode>().trailTo("processes").isEmpty())
    }
}
