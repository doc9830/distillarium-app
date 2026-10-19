package ru.distillarium.core.content

import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Собирает маленький валидный пакет для тестов установщика.
 * Формат повторяет dist/content и dist/manifest.json из distillarium-content.
 */
object TestPackFactory {
    private val json = Json { prettyPrint = true }

    data class TestPack(
        val sourceDir: File,
        val zipFile: File,
        val manifest: ContentManifest,
        val manifestJson: ByteArray,
    )

    fun create(
        root: File,
        version: String = "2026.10.1",
        schemaVersion: Int = 1,
        minAppVersion: String = "0.1.0",
        secondArticleTitle: String = "Две",
        brokenLink: Boolean = false,
        includeManifest: Boolean = true,
        treeJson: String? = null,
    ): TestPack {
        val source = File(root, "source-$version")
        source.deleteRecursively()
        source.mkdirs()
        write(
            File(source, "wiki/start/one.md"),
            """
            ---
            id: start/one
            title: "Одна"
            short: "Первая статья"
            ---
            Смотрите [[start/two|вторую]].

            ![схема](media/diagram.png)
            ${if (brokenLink) "\nБитая [[start/missing]]." else ""}
            """.trimIndent(),
        )
        write(
            File(source, "wiki/start/two.md"),
            """
            ---
            id: start/two
            title: "$secondArticleTitle"
            short: "Вторая статья"
            ---
            Назад: [[start/one]].
            """.trimIndent(),
        )
        write(
            File(source, "redirects.json"),
            "{\n  \"/articles/old\": \"start/one\"\n}\n",
        )
        write(File(source, "media/diagram.png"), "not-a-real-png")
        write(
            File(source, "search/index.json"),
            """
            {
              "schemaVersion": $schemaVersion,
              "count": 2,
              "entries": [
                {"id": "start/one", "title": "Одна"},
                {"id": "start/two", "title": "$secondArticleTitle"}
              ]
            }
            """.trimIndent(),
        )

        if (treeJson != null) {
            write(File(source, "tree.json"), treeJson)
        }

        val files = source.walkTopDown()
            .filter { it.isFile && it.name != "manifest.json" }
            .map { file ->
                val bytes = file.readBytes()
                ContentFile(
                    path = file.relativeTo(source).invariantSeparatorsPath,
                    sha256 = Sha256.ofBytes(bytes),
                    size = bytes.size.toLong(),
                )
            }
            .sortedBy { it.path }
            .toList()
        val searchSha = files.first { it.path == "search/index.json" }.sha256
        val inner = ContentManifest(
            contentVersion = version,
            schemaVersion = schemaVersion,
            minAppVersion = minAppVersion,
            released = "2026-10-18",
            files = files,
            searchIndex = "search/index.json",
            searchIndexSha256 = searchSha,
            redirects = "redirects.json",
        )
        if (includeManifest) {
            write(File(source, "manifest.json"), json.encodeToString(inner) + "\n")
        }

        val zipFile = File(root, "content-$version.zip")
        zipFile.delete()
        zipDirectory(source, zipFile)
        val packBytes = zipFile.readBytes()
        val release = inner.copy(
            packSize = packBytes.size.toLong(),
            packSha256 = Sha256.ofBytes(packBytes),
        )
        val releaseJson = (json.encodeToString(release) + "\n").toByteArray(Charsets.UTF_8)
        return TestPack(source, zipFile, release, releaseJson)
    }

    fun zipDirectory(source: File, target: File) {
        val entries = source.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(source).invariantSeparatorsPath }
            .sorted()
            .toList()
        ZipOutputStream(BufferedOutputStream(target.outputStream())).use { zip ->
            for (path in entries) {
                val entry = ZipEntry(path)
                entry.time = 315532800000L // 1980-01-01, как в детерминированном сборщике
                zip.putNextEntry(entry)
                File(source, path).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }
}
