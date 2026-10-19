package ru.distillarium.knowledge

import java.io.File
import org.commonmark.node.BulletList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.distillarium.BuildConfig
import ru.distillarium.calculators.Calculators
import ru.distillarium.core.content.ContentInstaller
import ru.distillarium.recipes.RecipeLoader

/** Проверяет вики на том же пакете, который попадёт в APK. */
class EmbeddedWikiTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun embeddedPackLoadsTreeRedirectsSearchAndBacklinks() {
        val assets = File("src/main/assets")
        val installer = ContentInstaller(temp.newFolder("files"), BuildConfig.VERSION_NAME)
        installer.ensureInstalled(
            File(assets, "content-pack.zip"),
            File(assets, "manifest.json"),
        )
        val library = WikiLoader.load(File(temp.root, "files/content/current"))

        assertEquals("2026.10.12", library.contentVersion)
        assertEquals(2, library.articles.size)
        assertEquals(4, library.roots.size)
        assertEquals(
            listOf("Самогоноварение", "Виноделие", "Пивоварение", "Оборудование"),
            library.roots.map { it.title },
        )
        assertEquals(0, library.redirects.size)
        assertEquals(6, library.referenceTables.size)
        assertEquals(7, library.tags.size)
        assertTrue(library.tags.any { it.label == "ареометр" })
        assertTrue(library.tags.none { it.label == "Оборудование" })

        assertEquals(
            "Самогоноварение / Оборудование / Ареометр-сахаромер",
            library.breadcrumb("articles/areometr-saharomer"),
        )
        assertEquals(
            "Самогоноварение / Оборудование / Ареометр-спиртомер",
            library.breadcrumb("articles/areometr-spirtomer"),
        )

        val leaves = collectLeaves(library.roots)
        assertEquals(2, leaves.count { it.articleId == "articles/areometr-saharomer" })
        assertEquals(2, leaves.count { it.articleId == "articles/areometr-spirtomer" })
        assertEquals(leaves.size, leaves.map { it.key }.toSet().size)

        library.articles.flatMap { article -> WikiLinks.targets(article.body) }
            .forEach { target ->
                when {
                    target.startsWith("ref:") ->
                        assertTrue("битая ссылка: $target", library.resolveReference(target) != null)
                    target.startsWith("calc:") ->
                        assertTrue(
                            "нет калькулятора: $target",
                            Calculators.byId(target.removePrefix("calc:")) != null,
                        )
                    target.startsWith("recipe:") -> Unit
                    else -> assertTrue("битая ссылка: $target", library.resolve(target) != null)
                }
            }

        val sugarMeter = requireNotNull(library.article("articles/areometr-saharomer")) {
            "нет статьи про сахаромер"
        }
        assertTrue(sugarMeter.headings.any { it.title.startsWith("Ареометр-сахаромер") })
        assertTrue(
            sugarMeter.headings.any { it.title == "Что смотреть при выборе" && it.level == 3 },
        )
        val spiritMeter = requireNotNull(library.article("articles/areometr-spirtomer")) {
            "нет статьи про спиртомер"
        }
        assertTrue(spiritMeter.headings.any { it.title == "Диапазоны" && it.level == 3 })
        assertTrue(MarkdownParser.parse(spiritMeter.body).any { it is BulletList })
        assertTrue(library.search("сахаромер").any { it.article.id == sugarMeter.id })
        assertTrue(
            library.search("спиртомер")
                .any { it.article.id == "articles/areometr-spirtomer" },
        )
        assertTrue(library.backlinks(sugarMeter.id).isEmpty())

        val recipes = RecipeLoader.load(File(temp.root, "files/content/current"))
        assertEquals(7, recipes.size)
        recipes.forEach { recipe ->
            assertTrue("у рецепта ${recipe.id} нет шагов", recipe.steps.isNotEmpty())
            recipe.steps.mapNotNull { it.wikiId }.forEach { wikiId ->
                assertTrue(
                    "рецепт ${recipe.id} ведёт на отсутствующую статью: $wikiId",
                    library.resolve(wikiId) != null,
                )
            }
        }
    }

    /** Заодно проверяет, что повтор статьи в разных разделах даёт разные ключи. */
    private fun collectLeaves(nodes: List<WikiNode>): List<WikiNode> = nodes.flatMap { node ->
        listOfNotNull(node.takeIf { it.articleId != null }) + collectLeaves(node.children)
    }
}
