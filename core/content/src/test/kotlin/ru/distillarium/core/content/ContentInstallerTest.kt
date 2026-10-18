package ru.distillarium.core.content

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContentInstallerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun newInstaller(root: File, appVersion: String = "0.1.0") =
        ContentInstaller(root, appVersion)

    private fun embeddedManifest(root: File, pack: TestPackFactory.TestPack): File =
        File(root, "embedded-manifest.json").apply { writeBytes(pack.manifestJson) }

    private fun requireVersion(installer: ContentInstaller): String =
        requireNotNull(installer.installedContent()).state.contentVersion

    @Test
    fun firstLaunchInstallsEmbeddedPack() {
        val root = temp.newFolder("files")
        val pack = TestPackFactory.create(temp.newFolder("pack1"))
        val installer = newInstaller(root)

        val content = installer.ensureInstalled(pack.zipFile, embeddedManifest(root, pack))

        assertEquals("2026.10.1", content.state.contentVersion)
        assertEquals("embedded", content.state.source)
        assertEquals(2, content.validation?.articles)
        assertTrue(File(root, "content/current/wiki/start/one.md").isFile)
        assertTrue(File(root, "content/current/manifest.json").isFile)
        assertFalse(File(root, "content/update.tmp").exists())
        assertFalse(File(root, "content/previous").exists())
    }

    @Test
    fun ensureIsIdempotentAndWorksOfflineWithoutAssets() {
        val root = temp.newFolder("files")
        val pack = TestPackFactory.create(temp.newFolder("pack1"))
        val installer = newInstaller(root)
        val first = installer.ensureInstalled(pack.zipFile, embeddedManifest(root, pack))

        val second = installer.ensureInstalled(
            File(root, "asset-pack-that-does-not-exist.zip"),
            File(root, "asset-manifest-that-does-not-exist.json"),
        )

        assertEquals(first.state.installedAt, second.state.installedAt)
        assertEquals("2026.10.1", second.state.contentVersion)
    }

    @Test
    fun updateKeepsPreviousAndRollbackReturnsToIt() {
        val root = temp.newFolder("files")
        val first = TestPackFactory.create(temp.newFolder("pack1"))
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            secondArticleTitle = "Две (новое)",
        )
        val installer = newInstaller(root)
        installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))

        val updated = installer.install(second.zipFile, second.manifest, "download")

        assertEquals("2026.10.2", updated.state.contentVersion)
        assertEquals("2026.10.1", installer.previousContent()?.state?.contentVersion)
        assertTrue(
            File(root, "content/current/wiki/start/two.md")
                .readText()
                .contains("Две (новое)"),
        )

        val rolledBack = installer.rollback()

        assertEquals("2026.10.1", rolledBack.state.contentVersion)
        assertEquals("rollback", rolledBack.state.source)
        assertEquals("2026.10.2", installer.previousContent()?.state?.contentVersion)
        assertTrue(File(root, "content/current/wiki/start/two.md").readText().contains("\"Две\""))
        assertFalse(File(root, "content/broken.tmp").exists())
    }

    @Test
    fun updateDoesNotTouchUserDatabase() {
        val root = temp.newFolder("files")
        val first = TestPackFactory.create(temp.newFolder("pack1"))
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            secondArticleTitle = "Две (новое)",
        )
        val userDb = File(root, "databases/user.db")
        userDb.parentFile.mkdirs()
        userDb.writeBytes("пользовательские партии и замеры".toByteArray())
        val before = Sha256.ofFile(userDb)
        val installer = newInstaller(root)
        installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))

        installer.install(second.zipFile, second.manifest, "download")

        assertEquals(before, Sha256.ofFile(userDb))
        assertEquals("пользовательские партии и замеры", userDb.readText())
    }

    @Test
    fun corruptArchiveIsRejectedAndCurrentStaysUntouched() {
        val root = temp.newFolder("files")
        val first = TestPackFactory.create(temp.newFolder("pack1"))
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            secondArticleTitle = "Две (новое)",
        )
        val installer = newInstaller(root)
        installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))
        val original = second.zipFile.readBytes()
        original[original.size - 1] = (original.last().toInt() xor 0xff).toByte()
        second.zipFile.writeBytes(original)

        assertThrows(ContentException::class.java) {
            installer.install(second.zipFile, second.manifest, "download")
        }

        assertEquals("2026.10.1", requireVersion(installer))
        assertFalse(File(root, "content/update.tmp").exists())
        assertNull(installer.previousContent())
    }

    @Test
    fun brokenLinkIsRejectedBeforeSwap() {
        val root = temp.newFolder("files")
        val first = TestPackFactory.create(temp.newFolder("pack1"))
        val broken = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            secondArticleTitle = "Две (новое)",
            brokenLink = true,
        )
        val installer = newInstaller(root)
        installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))

        val error = assertThrows(ContentException::class.java) {
            installer.install(broken.zipFile, broken.manifest, "download")
        }

        assertTrue(error.message.orEmpty().contains("битая ссылка"))
        assertEquals("2026.10.1", requireVersion(installer))
        assertFalse(File(root, "content/update.tmp").exists())
    }

    @Test
    fun newerSchemaAndNewerAppAreRejected() {
        val root = temp.newFolder("files")
        val first = TestPackFactory.create(temp.newFolder("pack1"))
        val future = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            schemaVersion = 2,
        )
        val installer = newInstaller(root)
        installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))

        assertThrows(ContentException::class.java) {
            installer.install(future.zipFile, future.manifest, "download")
        }
        assertEquals("2026.10.1", requireVersion(installer))

        val needsApp = TestPackFactory.create(
            temp.newFolder("pack3"),
            version = "2026.10.3",
            minAppVersion = "9.0.0",
        )
        val error = assertThrows(ContentException::class.java) {
            installer.install(needsApp.zipFile, needsApp.manifest, "download")
        }
        assertTrue(error.message.orEmpty().contains("9.0.0"))
    }

    @Test
    fun zipSlipIsRejected() {
        val root = temp.newFolder("files")
        val first = TestPackFactory.create(temp.newFolder("pack1"))
        val installer = newInstaller(root)
        installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))
        val evil = File(root, "evil.zip")
        java.util.zip.ZipOutputStream(evil.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
            zip.write("escape".toByteArray())
            zip.closeEntry()
        }
        val bytes = evil.readBytes()
        val manifest = first.manifest.copy(
            packSize = bytes.size.toLong(),
            packSha256 = Sha256.ofBytes(bytes),
        )

        val error = assertThrows(ContentException::class.java) {
            installer.install(evil, manifest, "download")
        }

        assertTrue(error.message.orEmpty().contains("недопустимый путь"))
        assertFalse(File(root.parentFile, "escape.txt").exists())
        assertEquals("2026.10.1", requireVersion(installer))
    }


    @Test
    fun interruptedUpdateRestoresPrevious() {
        val root = temp.newFolder("files")
        val pack = TestPackFactory.create(temp.newFolder("pack1"))
        val installer = newInstaller(root)
        installer.ensureInstalled(pack.zipFile, embeddedManifest(root, pack))
        val current = File(root, "content/current")
        val previous = File(root, "content/previous")
        assertTrue(current.renameTo(previous))

        val restored = installer.ensureInstalled(pack.zipFile, embeddedManifest(root, pack))

        assertEquals("2026.10.1", restored.state.contentVersion)
        assertTrue(File(root, "content/current/manifest.json").isFile)
        assertFalse(File(root, "content/previous").exists())
    }

    @Test
    fun interruptedRollbackRecoversBrokenCopy() {
        val root = temp.newFolder("files")
        val first = TestPackFactory.create(temp.newFolder("pack1"))
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            secondArticleTitle = "Две (новое)",
        )
        val installer = newInstaller(root)
        installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))
        installer.install(second.zipFile, second.manifest, "download")
        val current = File(root, "content/current")
        val broken = File(root, "content/broken.tmp")
        assertTrue(current.renameTo(broken))

        val restored = installer.ensureInstalled(first.zipFile, embeddedManifest(root, first))

        assertEquals("2026.10.2", restored.state.contentVersion)
        assertEquals("2026.10.1", installer.previousContent()?.state?.contentVersion)
        assertFalse(File(root, "content/broken.tmp").exists())
    }

    @Test
    fun installedStateContainsDocumentedFields() {
        val root = temp.newFolder("files")
        val pack = TestPackFactory.create(temp.newFolder("pack1"))
        val installer = newInstaller(root)
        installer.ensureInstalled(pack.zipFile, embeddedManifest(root, pack))

        val state = InstalledStateJson.parseFile(File(root, "installed.json"))

        assertEquals("2026.10.1", state.contentVersion)
        assertEquals(1, state.schemaVersion)
        assertEquals(pack.manifest.packSha256, state.packSha256)
        assertNotNull(state.installedAt)
    }
}
