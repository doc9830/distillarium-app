package ru.distillarium.knowledge

import java.io.File
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

        assertEquals("2026.10.8", library.contentVersion)
        assertEquals(33, library.articles.size)
        assertEquals(7, library.roots.size)
        assertEquals(
            listOf(
                "Самогоноварение",
                "Виноделие",
                "Пивоварение",
                "Оборудование",
                "Рецепты",
                "Проблемы",
                "Справочник",
            ),
            library.roots.map { it.title },
        )
        assertEquals(33, library.redirects.size)
        assertEquals(6, library.referenceTables.size)
        assertTrue(library.tags.isEmpty())
        assertTrue(library.tags.none { it.label == "Оборудование" })
        assertEquals(
            "Самогоноварение / Инструкции / Колонна с дефлегматором / " +
                "Отбор голов, тела и хвостов",
            library.breadcrumb("processes/separate/heads-hearts-tails"),
        )

        library.redirects.values.forEach { target ->
            assertTrue("редирект ведёт в пустоту: $target", library.resolve(target) != null)
        }
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

        val measurements = requireNotNull(library.article("equipment/measurements")) {
            "нет статьи про измерения"
        }
        assertEquals(measurements, library.resolve("/articles/areometr"))

        val synonymResults = library.search("сивушные")
        assertTrue(synonymResults.any { it.article.id == "problems/off-flavour" })
        val sugarMeterResults = library.search("сахаромер")
        assertTrue(sugarMeterResults.any { it.article.id == "equipment/measurements" })

        val heads = requireNotNull(library.article("processes/separate/heads-hearts-tails")) {
            "нет статьи про фракции"
        }
        assertTrue(heads.headings.any { it.title == "Коротко" })
        assertEquals(3, heads.segments.filterIsInstance<ArticleSegment.Tabs>().single().tabs.size)
        assertTrue(
            library.backlinks("processes/separate/heads-hearts-tails")
                .any { it.id == "problems/off-flavour" },
        )

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
}
