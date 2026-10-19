package ru.distillarium.knowledge

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Дерево навигации из `tree.json`. Оно не зависит от id статей, поэтому
 * разделы можно переставлять и переименовывать без ломки ссылок.
 */
data class WikiTreeConfig(
    val roots: List<WikiTreeSection>,
)

data class WikiTreeSection(
    val id: String,
    val title: String,
    val articles: List<String> = emptyList(),
    val children: List<WikiTreeSection> = emptyList(),
)

object WikiTreeParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(file: File): WikiTreeConfig? {
        if (!file.isFile) return null
        return parse(file.readText())
    }

    fun parse(text: String): WikiTreeConfig? {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            if (root["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != 1) {
                return null
            }
            val nodes = root["roots"] as? JsonArray ?: return null
            val ids = HashSet<String>()
            val sections = ArrayList<WikiTreeSection>(nodes.size)
            for (node in nodes) {
                sections += parseSection(node, ids, depth = 1) ?: return null
            }
            if (sections.isEmpty()) null else WikiTreeConfig(sections)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSection(
        element: JsonElement,
        ids: MutableSet<String>,
        depth: Int,
    ): WikiTreeSection? {
        if (depth > 4) return null
        val node = element as? JsonObject ?: return null
        val id = node["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val title = node["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (id.isEmpty() || title.isEmpty() || !ids.add(id)) return null

        val articles = (node["articles"] as? JsonArray)
            ?.mapNotNull { item ->
                (item as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val children = (node["children"] as? JsonArray)
            ?.mapNotNull { parseSection(it, ids, depth + 1) }
            .orEmpty()
        return WikiTreeSection(
            id = id,
            title = title,
            articles = articles,
            children = children,
        )
    }
}
