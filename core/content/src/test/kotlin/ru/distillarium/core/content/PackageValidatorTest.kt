package ru.distillarium.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackageValidatorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun allowsArticleInMultipleSections() {
        val pack = TestPackFactory.create(
            temp.newFolder("pack"),
            treeJson = """
                {
                  "schemaVersion": 1,
                  "roots": [
                    {"id": "one", "title": "Первый", "articles": ["start/one"]},
                    {"id": "two", "title": "Второй", "articles": ["start/one", "start/two"]}
                  ]
                }
            """.trimIndent(),
        )

        val validation = PackageValidator.validate(pack.sourceDir, pack.manifest)

        assertEquals(2, validation.articles)
    }

    @Test
    fun rejectsSameArticleTwiceInOneSection() {
        val pack = TestPackFactory.create(
            temp.newFolder("pack"),
            treeJson = """
                {
                  "schemaVersion": 1,
                  "roots": [
                    {"id": "one", "title": "Первый", "articles": ["start/one", "start/one"]}
                  ]
                }
            """.trimIndent(),
        )

        val error = assertThrows(ContentException::class.java) {
            PackageValidator.validate(pack.sourceDir, pack.manifest)
        }

        assertTrue(error.message.orEmpty().contains("повторяется в одном разделе"))
    }
}
