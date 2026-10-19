package ru.distillarium

import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.distillarium.calculators.Calculators
import ru.distillarium.core.content.ContentInstaller

/**
 * Проверяет именно тот пакет, который лежит в APK: при первом запуске он
 * устанавливается без сети. Если сборщик контента и приложение разойдутся,
 * этот тест упадёт до релиза.
 */
class EmbeddedContentTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun embeddedPackIsValidAndInstallsOffline() {
        val assets = File("src/main/assets")
        val pack = File(assets, "content-pack.zip")
        val manifest = File(assets, "manifest.json")
        assertTrue("нет встроенного пакета: ${pack.absolutePath}", pack.isFile)
        assertTrue("нет манифеста пакета: ${manifest.absolutePath}", manifest.isFile)

        val installer = ContentInstaller(temp.newFolder("files"), BuildConfig.VERSION_NAME)
        val content = installer.ensureInstalled(pack, manifest)

        assertEquals("2026.10.16", content.state.contentVersion)
        assertEquals(1, content.state.schemaVersion)
        assertEquals(13, content.validation?.articles)
        assertEquals(6, content.validation?.links)
        assertEquals(0, content.validation?.redirects)
        assertEquals(30, content.validation?.files)
        assertTrue(File(temp.root, "files/content/current/search/index.json").isFile)
    }

    @Test
    fun embeddedCalculatorsCatalogCoversEveryAppCalculator() {
        val pack = File("src/main/assets/content-pack.zip")
        assertTrue("нет встроенного пакета: ${pack.absolutePath}", pack.isFile)

        val items = ZipFile(pack).use { zip ->
            val entry = requireNotNull(zip.getEntry("calculators.json")) {
                "во встроенном пакете нет calculators.json"
            }
            val root = zip.getInputStream(entry).bufferedReader().use { reader ->
                Json.parseToJsonElement(reader.readText()).jsonObject
            }
            root.getValue("items").jsonArray.map { element ->
                element.jsonObject.getValue("id").jsonPrimitive.content
            }
        }

        assertEquals(
            "каталог контента должен содержать все калькуляторы приложения",
            Calculators.all.map { it.id }.toSet(),
            items.toSet(),
        )
        assertEquals("в каталоге не должно быть повторов id", items.size, items.toSet().size)
    }
}
