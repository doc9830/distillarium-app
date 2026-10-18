package ru.distillarium.core.content

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContentUpdaterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private class MemoryEtagStore(var value: String? = null) : EtagStore {
        override fun read(): String? = value
        override fun write(etag: String?) {
            value = etag
        }
    }

    private fun installerWithFirst(version: String = "2026.10.1"): Pair<ContentInstaller, TestPackFactory.TestPack> {
        val root = temp.newFolder("files-${System.nanoTime()}")
        val pack = TestPackFactory.create(
            temp.newFolder("pack-${System.nanoTime()}"),
            version = version,
        )
        val installer = ContentInstaller(root, "0.1.0")
        installer.ensureInstalled(
            pack.zipFile,
            File(root, "embedded-manifest.json").apply { writeBytes(pack.manifestJson) },
        )
        return installer to pack
    }

    private fun updater(
        installer: ContentInstaller,
        manifest: ByteArray,
        etag: String? = "\"v1\"",
        pack: File? = null,
        status: Int = 200,
        etagStore: EtagStore = MemoryEtagStore(),
    ): ContentUpdater {
        val fetcher = ManifestFetcher { _, _ ->
            if (status == 304) {
                ManifestResponse.NotModified
            } else {
                ManifestResponse.Fresh(manifest, etag)
            }
        }
        val downloader = PackDownloader { _, destination, maxBytes, progress ->
            val source = pack ?: throw AssertionError("download не должен вызываться")
            val bytes = source.readBytes()
            if (bytes.size > maxBytes) throw AssertionError("превышен maxBytes")
            destination.writeBytes(bytes)
            progress(bytes.size.toLong(), maxBytes)
        }
        return ContentUpdater(installer, fetcher, downloader, etagStore)
    }

    @Test
    fun upToDateDoesNotDownload() {
        val (installer, pack) = installerWithFirst()
        val etag = MemoryEtagStore()

        val result = updater(installer, pack.manifestJson, etagStore = etag)
            .update("https://example.test/manifest.json", "https://example.test/pack.zip")

        assertTrue(result is UpdateResult.UpToDate)
        assertEquals("\"v1\"", etag.value)
    }

    @Test
    fun notModifiedLeavesEverythingAsIs() {
        val (installer, _) = installerWithFirst()

        val result = updater(installer, ByteArray(0), status = 304)
            .update("https://example.test/manifest.json", "https://example.test/pack.zip")

        assertTrue(result is UpdateResult.NotModified)
        assertEquals("2026.10.1", installer.installedContent()?.state?.contentVersion)
    }

    @Test
    fun newerPackIsDownloadedAndInstalled() {
        val (installer, _) = installerWithFirst()
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            secondArticleTitle = "Две (новое)",
        )
        val stages = mutableListOf<UpdateStage>()
        val etag = MemoryEtagStore()

        val result = updater(installer, second.manifestJson, pack = second.zipFile, etagStore = etag)
            .update("https://example.test/manifest.json", "https://example.test/pack.zip") { stage, _ ->
                stages += stage
            }

        assertTrue(result is UpdateResult.Updated)
        assertEquals("2026.10.2", installer.installedContent()?.state?.contentVersion)
        assertEquals("\"v1\"", etag.value)
        assertTrue(stages.contains(UpdateStage.DOWNLOADING))
        assertTrue(stages.contains(UpdateStage.INSTALLING))
    }

    @Test
    fun newerAppRequirementIsReportedWithoutDownload() {
        val (installer, _) = installerWithFirst()
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            minAppVersion = "9.0.0",
        )

        val result = updater(installer, second.manifestJson)
            .update("https://example.test/manifest.json", "https://example.test/pack.zip")

        assertTrue(result is UpdateResult.RequiresNewerApp)
        assertEquals("2026.10.1", installer.installedContent()?.state?.contentVersion)
    }

    @Test
    fun newerSchemaIsReportedWithoutDownload() {
        val (installer, _) = installerWithFirst()
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
            schemaVersion = 2,
        )

        val result = updater(installer, second.manifestJson)
            .update("https://example.test/manifest.json", "https://example.test/pack.zip")

        assertTrue(result is UpdateResult.IncompatibleSchema)
    }

    @Test
    fun truncatedDownloadDoesNotReplaceCurrent() {
        val (installer, _) = installerWithFirst()
        val second = TestPackFactory.create(
            temp.newFolder("pack2"),
            version = "2026.10.2",
        )
        val truncated = File(temp.newFolder("bad"), "truncated.zip")
        truncated.writeBytes(second.zipFile.readBytes().copyOf(100))

        assertThrows(ContentException::class.java) {
            updater(installer, second.manifestJson, pack = truncated)
                .update("https://example.test/manifest.json", "https://example.test/pack.zip")
        }

        assertEquals("2026.10.1", installer.installedContent()?.state?.contentVersion)
        assertTrue(installer.previousContent() == null)
    }
}
