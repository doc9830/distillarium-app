package ru.distillarium

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

        assertEquals("2026.10.11", content.state.contentVersion)
        assertEquals(1, content.state.schemaVersion)
        assertEquals(2, content.validation?.articles)
        assertEquals(1, content.validation?.links)
        assertEquals(0, content.validation?.redirects)
        assertEquals(19, content.validation?.files)
        assertTrue(File(temp.root, "files/content/current/search/index.json").isFile)
    }
}
