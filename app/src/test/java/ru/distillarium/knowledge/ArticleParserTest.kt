package ru.distillarium.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleParserTest {
    @Test
    fun parsesFrontmatterListsAndQuotedTitle() {
        val article = ArticleParser.parse(
            """
            ---
            id: processes/test/stuck
            title: "Брожение \"зависло\""
            category: problemy-i-resheniya
            tags: [проблема, новичку]
            tech:
              - wine
              - beer
            updated: 2026-10-18
            sources:
              - https://example.org/a
              - https://example.org/b
            ---

            ## Коротко
            Ответ.
            """.trimIndent(),
            expectedId = "processes/test/stuck",
        )

        assertEquals("processes/test/stuck", article.id)
        assertEquals("Брожение \"зависло\"", article.title)
        assertEquals(listOf("проблема", "новичку"), article.tags)
        assertEquals(listOf("wine", "beer"), article.tech)
        assertEquals(2, article.sources.size)
        assertEquals("2026-10-18", article.updated)
        assertTrue(article.body.contains("Ответ."))
    }

    @Test
    fun parsesInlineListsAndUnknownFields() {
        val article = ArticleParser.parse(
            """
            ---
            id: raw/sugar
            title: Сахар
            tags: []
            custom: value
            ---
            Тело.
            """.trimIndent(),
            expectedId = "raw/sugar",
        )

        assertEquals(emptyList<String>(), article.tags)
        assertEquals("Сахар", article.title)
    }

    @Test
    fun splitsTabsAndKeepsMarkdownAroundThem() {
        val segments = ArticleBodyParser.parse(
            """
            ## До

            Текст до.

            ::: tabs
            == Прямоток ==
            Прямой текст.

            == Колонна ==
            Колонный текст.
            :::

            ## После

            Текст после.
            """.trimIndent(),
        )

        assertEquals(3, segments.size)
        assertTrue((segments[0] as ArticleSegment.Markdown).text.contains("Текст до."))
        val tabs = (segments[1] as ArticleSegment.Tabs).tabs
        assertEquals(listOf("Прямоток", "Колонна"), tabs.map { it.title })
        assertTrue(tabs[1].markdown.contains("Колонный текст."))
        assertTrue((segments[2] as ArticleSegment.Markdown).text.contains("Текст после."))
    }

    @Test
    fun unclosedTabsStayInMarkdown() {
        val segments = ArticleBodyParser.parse(
            """
            Текст.

            ::: tabs
            == Прямоток ==
            Без закрытия.
            """.trimIndent(),
        )

        assertEquals(1, segments.size)
        assertTrue((segments.single() as ArticleSegment.Markdown).text.contains("::: tabs"))
    }
}
