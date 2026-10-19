package ru.distillarium.knowledge

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.distillarium.core.content.ManifestParser

/** Загружает вики из распакованного установленного пакета. */
object WikiLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(contentDir: File): WikiLibrary {
        require(contentDir.isDirectory) { "Каталог контента не найден: $contentDir" }
        val wikiDir = File(contentDir, "wiki")
        require(wikiDir.isDirectory) { "В пакете нет каталога wiki" }

        val articles = wikiDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .sortedBy { it.path }
            .map { file -> parseArticle(wikiDir, file) }
            .toList()
        require(articles.isNotEmpty()) { "В пакете нет статей" }
        val duplicates = articles.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Дубли id статей: ${duplicates.joinToString()}" }

        val manifest = runCatching {
            ManifestParser.parseFile(File(contentDir, "manifest.json"))
        }.getOrNull()
        val redirects = readRedirects(File(contentDir, "redirects.json"))
        val referenceTables = ReferenceLoader.load(contentDir)
        val tree = WikiTreeParser.parse(File(contentDir, "tree.json"))
        val roots = WikiTree.build(articles, tree)

        return WikiLibrary(
            articles = articles,
            redirects = redirects,
            roots = roots,
            tags = WikiTree.tags(articles, roots),
            referenceTables = referenceTables,
            contentVersion = manifest?.contentVersion,
            released = manifest?.released,
        )
    }

    private fun parseArticle(wikiDir: File, file: File): Article {
        val id = file.relativeTo(wikiDir).invariantSeparatorsPath.removeSuffix(".md")
        return try {
            ArticleParser.parse(file.readText(), id)
        } catch (error: Exception) {
            throw IllegalArgumentException("Не удалось разобрать ${file.name}: ${error.message}", error)
        }
    }

    private fun readRedirects(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            json.parseToJsonElement(file.readText()).jsonObject
                .mapNotNull { (key, value) ->
                    value.jsonPrimitive.contentOrNull?.let { key to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }
}
