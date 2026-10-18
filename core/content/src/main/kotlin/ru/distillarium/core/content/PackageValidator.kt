package ru.distillarium.core.content

import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class PackageValidation(
    val articles: Int,
    val links: Int,
    val redirects: Int,
    val files: Int,
)

/**
 * Проверка распакованного пакета до подмены `current`.
 *
 * Повторяет правила сборщика контента: все файлы из манифеста на месте и с
 * верным sha256, id уникальны, ссылки `[[…]]` резолвятся, медиа существует,
 * индекс поиска соответствует статьям. Контент при этом не исполняется.
 */
object PackageValidator {
    private val LINK_RE = Regex("\\[\\[([^]]+)]]")
    private val OLD_LINK_RE = Regex("]\\s*\\(\\s*/articles/")
    private val MEDIA_RE = Regex("""!\[([^]]*)]\(\s*([^)\s]+)(?:\s+"[^"]*")?\s*\)""")
    private val FRONTMATTER_KEY_RE = Regex("^[A-Za-z0-9_-]+$")
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(packageDir: File, manifest: ContentManifest): PackageValidation {
        if (!packageDir.isDirectory) {
            throw ContentException("Распакованный пакет не найден")
        }
        val errors = mutableListOf<String>()
        if (manifest.schemaVersion > SUPPORTED_CONTENT_SCHEMA) {
            errors += "schemaVersion ${manifest.schemaVersion} новее поддерживаемой " +
                "$SUPPORTED_CONTENT_SCHEMA"
        }
        validateFiles(packageDir, manifest, errors)
        validateInnerManifest(packageDir, manifest, errors)
        val articles = loadArticles(packageDir, manifest, errors)
        val redirects = loadRedirects(packageDir, errors)
        validateRedirects(articles, redirects, errors)
        val objects = loadTypedObjects(packageDir, errors)
        val linkCount = validateLinks(articles, redirects, objects, errors)
        validateMedia(packageDir, articles, errors)
        validateSearchIndex(packageDir, manifest, articles, errors)
        if (errors.isNotEmpty()) fail(errors)
        return PackageValidation(
            articles = articles.size,
            links = linkCount,
            redirects = redirects.size,
            files = manifest.files.size,
        )
    }

    private fun validateFiles(
        packageDir: File,
        manifest: ContentManifest,
        errors: MutableList<String>,
    ) {
        val listed = manifest.files.associateBy { it.path }
        for (entry in manifest.files) {
            val file = File(packageDir, entry.path)
            if (!file.isFile) {
                errors += "нет файла ${entry.path}"
                continue
            }
            if (Files.isSymbolicLink(file.toPath())) {
                errors += "символическая ссылка в пакете: ${entry.path}"
                continue
            }
            if (file.length() != entry.size) {
                errors += "${entry.path}: размер ${file.length()}, в манифесте ${entry.size}"
            }
            try {
                if (!Sha256.matches(file, entry.sha256)) {
                    errors += "${entry.path}: sha256 не совпадает"
                }
            } catch (error: Exception) {
                errors += "${entry.path}: не читается (${error.message})"
            }
        }
        val actual = LinkedHashSet<String>()
        try {
            Files.walk(packageDir.toPath()).use { paths ->
                paths.forEach { path ->
                    if (Files.isSymbolicLink(path)) {
                        errors += "символическая ссылка в пакете: " +
                            packageDir.toPath().relativize(path)
                    } else if (Files.isRegularFile(path)) {
                        actual += packageDir.toPath().relativize(path).toString()
                            .replace(File.separatorChar, '/')
                    }
                }
            }
        } catch (error: Exception) {
            errors += "пакет не читается: ${error.message}"
            return
        }
        actual.remove("manifest.json")
        for (path in actual) {
            if (path !in listed) {
                errors += "файл $path есть в пакете, но не указан в манифесте"
            }
        }
    }


    private fun validateInnerManifest(
        packageDir: File,
        release: ContentManifest,
        errors: MutableList<String>,
    ) {
        val file = File(packageDir, "manifest.json")
        if (!file.isFile) {
            errors += "в пакете нет manifest.json"
            return
        }
        try {
            val inner = ManifestParser.parseFile(file)
            if (!release.sameContentAs(inner)) {
                errors += "внутренний manifest.json не совпадает с релизным"
            }
        } catch (error: ContentException) {
            errors += error.message ?: "внутренний manifest.json не разбирается"
        }
    }

    private data class Article(
        val id: String,
        val packagePath: String,
        val title: String,
        val short: String,
        val body: String,
    )

    private fun loadArticles(
        packageDir: File,
        manifest: ContentManifest,
        errors: MutableList<String>,
    ): List<Article> {
        val articles = mutableListOf<Article>()
        for (entry in manifest.files) {
            if (!entry.path.startsWith("wiki/") || !entry.path.endsWith(".md")) continue
            val id = entry.path.removePrefix("wiki/").removeSuffix(".md")
            val file = File(packageDir, entry.path)
            if (!file.isFile) continue
            val text = try {
                file.readText(Charsets.UTF_8)
            } catch (error: Exception) {
                errors += "${entry.path}: не читается (${error.message})"
                continue
            }
            val parsed = parseFrontmatter(text, entry.path, errors) ?: continue
            val declaredId = parsed.fields["id"].orEmpty()
            val title = parsed.fields["title"].orEmpty()
            val short = parsed.fields["short"].orEmpty()
            if (declaredId.isBlank()) errors += "${entry.path}: пустой id"
            if (declaredId.isNotBlank() && declaredId != id) {
                errors += "${entry.path}: id «$declaredId» не совпадает с путём «$id»"
            }
            if (title.isBlank()) errors += "${entry.path}: пустой title"
            if (short.isBlank()) errors += "${entry.path}: пустой short"
            articles += Article(id, entry.path, title, short, parsed.body)
        }
        val ids = HashSet<String>()
        for (article in articles) {
            if (!ids.add(article.id)) errors += "дубль id: ${article.id}"
        }
        return articles
    }


    private data class ParsedArticle(
        val fields: Map<String, String>,
        val body: String,
    )

    private fun parseFrontmatter(
        text: String,
        path: String,
        errors: MutableList<String>,
    ): ParsedArticle? {
        val lines = text.removePrefix("\uFEFF").lines()
        if (lines.firstOrNull()?.trim() != "---") {
            errors += "$path: нет frontmatter"
            return null
        }
        var end = -1
        for (index in 1 until lines.size) {
            if (lines[index].trim() == "---") {
                end = index
                break
            }
        }
        if (end < 0) {
            errors += "$path: frontmatter не закрыт"
            return null
        }
        val fields = LinkedHashMap<String, String>()
        for (line in lines.subList(1, end)) {
            if (line.isBlank() || line.startsWith(" ") || line.startsWith("-")) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            if (!FRONTMATTER_KEY_RE.matches(key)) continue
            fields[key] = unquote(line.substring(colon + 1).trim())
        }
        return ParsedArticle(fields, lines.subList(end + 1, lines.size).joinToString("\n"))
    }

    private fun unquote(raw: String): String {
        if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
            return try {
                (json.parseToJsonElement(raw) as JsonPrimitive).content
            } catch (_: Exception) {
                raw.substring(1, raw.length - 1)
            }
        }
        return raw
    }


    private fun loadRedirects(
        packageDir: File,
        errors: MutableList<String>,
    ): Map<String, String> {
        val file = File(packageDir, "redirects.json")
        if (!file.isFile) {
            errors += "нет redirects.json"
            return emptyMap()
        }
        val element = try {
            json.parseToJsonElement(file.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            errors += "redirects.json не разбирается (${error.message})"
            return emptyMap()
        }
        if (element !is JsonObject) {
            errors += "redirects.json должен быть объектом"
            return emptyMap()
        }
        val redirects = LinkedHashMap<String, String>()
        for ((from, value) in element) {
            val to = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (to.isNullOrBlank()) {
                errors += "redirects.json: $from ведёт в пустоту"
                continue
            }
            redirects[from] = to
        }
        return redirects
    }

    private fun validateRedirects(
        articles: List<Article>,
        redirects: Map<String, String>,
        errors: MutableList<String>,
    ) {
        val ids = articles.mapTo(HashSet()) { it.id }
        for ((from, to) in redirects) {
            if (from in ids) errors += "redirects.json: «$from» — существующий id"
            if (to !in ids) {
                errors += "redirects.json: «$from» ведёт на несуществующий id «$to»"
            } else if (to in redirects) {
                errors += "redirects.json: цепочка «$from» → «$to»"
            }
        }
    }

    private data class TypedObjects(
        val reference: Set<String>,
        val recipe: Set<String>,
        val calc: Set<String>,
    )

    private fun loadTypedObjects(
        packageDir: File,
        errors: MutableList<String>,
    ): TypedObjects {
        val reference = HashSet<String>()
        val recipe = HashSet<String>()
        val calc = HashSet<String>()
        for ((dir, target) in listOf("reference" to reference, "recipes" to recipe)) {
            val root = File(packageDir, dir)
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "json" }
                .forEach { file ->
                    val relative = file.relativeTo(packageDir).invariantSeparatorsPath
                    try {
                        addObjects(json.parseToJsonElement(file.readText(Charsets.UTF_8)), relative, target, errors)
                    } catch (error: Exception) {
                        errors += "$relative: не разбирается как JSON (${error.message})"
                    }
                }
        }
        val calcFile = File(packageDir, "calculators.json")
        if (calcFile.isFile) {
            try {
                addObjects(
                    json.parseToJsonElement(calcFile.readText(Charsets.UTF_8)),
                    "calculators.json",
                    calc,
                    errors,
                )
            } catch (error: Exception) {
                errors += "calculators.json: не разбирается как JSON (${error.message})"
            }
        }
        return TypedObjects(reference, recipe, calc)
    }

    private fun addObjects(
        element: JsonElement,
        where: String,
        target: MutableSet<String>,
        errors: MutableList<String>,
    ) {
        fun addObject(value: JsonElement) {
            val id = (value as? JsonObject)
                ?.get("id")
                ?.let { it as? JsonPrimitive }
                ?.takeIf { it.isString }
                ?.content
            if (id.isNullOrBlank()) {
                errors += "$where: у объекта нет строкового id"
            } else {
                target += id.trim()
            }
        }
        when (element) {
            is JsonArray -> element.forEach { addObject(it) }
            is JsonObject -> {
                val items = element["items"]
                if (items is JsonArray) {
                    if (element["id"] != null) addObject(element)
                    items.forEach { addObject(it) }
                } else {
                    addObject(element)
                }
            }
            else -> errors += "$where: элемент должен быть объектом с полем id"
        }
    }


    private fun validateLinks(
        articles: List<Article>,
        redirects: Map<String, String>,
        objects: TypedObjects,
        errors: MutableList<String>,
    ): Int {
        val ids = articles.mapTo(HashSet()) { it.id }
        var count = 0
        for (article in articles) {
            for (match in LINK_RE.findAll(article.body)) {
                count++
                val raw = match.groupValues[1]
                val target = raw.substringBefore('|').trim()
                if (target.isEmpty()) {
                    errors += "${article.packagePath}: пустая ссылка [[…]]"
                    continue
                }
                val resolved = when {
                    target.startsWith("calc:") ->
                        target.removePrefix("calc:") in objects.calc
                    target.startsWith("ref:") -> {
                        val key = target.removePrefix("ref:")
                        key in objects.reference || "reference/$key" in ids
                    }
                    target.startsWith("recipe:") -> {
                        val key = target.removePrefix("recipe:")
                        key in objects.recipe || "recipes/$key" in ids
                    }
                    target.startsWith("/articles/") -> target in redirects
                    target in ids -> true
                    target in redirects -> true
                    "/articles/$target" in redirects -> true
                    else -> false
                }
                if (!resolved) {
                    errors += "${article.packagePath}: битая ссылка [[$raw]]"
                }
            }
            if (OLD_LINK_RE.containsMatchIn(article.body)) {
                errors += "${article.packagePath}: осталась старая ссылка /articles/…"
            }
        }
        return count
    }

    private fun validateMedia(
        packageDir: File,
        articles: List<Article>,
        errors: MutableList<String>,
    ) {
        for (article in articles) {
            for (match in MEDIA_RE.findAll(article.body)) {
                val rawUrl = match.groupValues[2]
                if (Regex("^(https?:|data:|//)", RegexOption.IGNORE_CASE).containsMatchIn(rawUrl)) {
                    errors += "${article.packagePath}: внешняя картинка запрещена ($rawUrl)"
                    continue
                }
                val decoded = try {
                    URLDecoder.decode(rawUrl, "UTF-8")
                } catch (_: Exception) {
                    rawUrl
                }
                if (decoded.split('/').contains("..")) {
                    errors += "${article.packagePath}: путь картинки выходит за пакет ($rawUrl)"
                    continue
                }
                val clean = decoded.removePrefix("./")
                val parent = article.packagePath.substringBeforeLast('/', "")
                val relative = if (decoded.startsWith("/")) {
                    clean.trimStart('/')
                } else {
                    normalize(parent, clean)
                }
                val candidates = if (decoded.startsWith("/")) {
                    listOf(relative)
                } else {
                    listOf(relative, normalize("", clean))
                }
                val found = candidates.any { candidate ->
                    SafePath.isSafe(candidate) && File(packageDir, candidate).isFile
                }
                if (!found) {
                    errors += "${article.packagePath}: не найдена картинка $rawUrl"
                }
            }
        }
    }

    private fun normalize(parent: String, path: String): String {
        val joined = if (parent.isBlank()) path else "$parent/$path"
        return Paths.get(joined).normalize().toString().replace(File.separatorChar, '/')
    }

    private fun validateSearchIndex(
        packageDir: File,
        manifest: ContentManifest,
        articles: List<Article>,
        errors: MutableList<String>,
    ) {
        val file = File(packageDir, manifest.searchIndex)
        if (!file.isFile) {
            errors += "нет ${manifest.searchIndex}"
            return
        }
        val element = try {
            json.parseToJsonElement(file.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            errors += "${manifest.searchIndex} не разбирается (${error.message})"
            return
        }
        val root = element as? JsonObject
        if (root == null) {
            errors += "${manifest.searchIndex} должен быть объектом"
            return
        }
        val entries = root["entries"] as? JsonArray
        if (entries == null) {
            errors += "${manifest.searchIndex}: нет массива entries"
            return
        }
        val declaredCount = (root["count"] as? JsonPrimitive)?.intOrNull
        if (declaredCount != entries.size) {
            errors += "${manifest.searchIndex}: count=$declaredCount, записей ${entries.size}"
        }
        val indexIds = entries.mapNotNullTo(HashSet()) { entry ->
            ((entry as? JsonObject)?.get("id") as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
        }
        val articleIds = articles.mapTo(HashSet()) { it.id }
        if (indexIds != articleIds) {
            errors += "${manifest.searchIndex}: id не совпадают со статьями"
        }
    }

    private fun fail(errors: List<String>) {
        val shown = errors.take(20).joinToString("\n") { "• $it" }
        val tail = if (errors.size > 20) "\n• …ещё ${errors.size - 20}" else ""
        throw ContentException("Пакет не прошёл проверку:\n$shown$tail")
    }
}
