package ru.distillarium.core.content

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Максимальная схема контента, которую понимает это приложение. */
const val SUPPORTED_CONTENT_SCHEMA: Int = 1

/** Верхняя граница размера архива: защита от подмены манифеста. */
const val MAX_PACK_SIZE_BYTES: Long = 64L * 1024 * 1024

private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024

@Serializable
data class ContentFile(
    val path: String,
    val sha256: String,
    val size: Long,
)

@Serializable
data class ContentManifest(
    val contentVersion: String,
    val schemaVersion: Int,
    val minAppVersion: String,
    val released: String,
    val packSize: Long? = null,
    val packSha256: String? = null,
    val files: List<ContentFile>,
    val searchIndex: String,
    val searchIndexSha256: String,
    val redirects: String,
) {
    /** Манифест релиза обязан содержать размер и хеш архива. */
    fun requirePack(): Pair<Long, String> {
        val size = packSize
        val hash = packSha256
        if (size == null || hash == null) {
            throw ContentException("В манифесте нет packSize или packSha256")
        }
        return size to hash
    }

    fun validateBasic() {
        if (!Regex("^\\d{4}\\.\\d{1,2}\\.\\d+$").matches(contentVersion)) {
            throw ContentException("manifest.json: contentVersion должен быть вида 2026.10.1")
        }
        if (schemaVersion < 1) {
            throw ContentException("manifest.json: schemaVersion должен быть положительным")
        }
        if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(minAppVersion)) {
            throw ContentException("manifest.json: minAppVersion должен быть вида 0.1.0")
        }
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(released)) {
            throw ContentException("manifest.json: released должен быть датой ГГГГ-ММ-ДД")
        }
        if (packSize != null && packSize <= 0) {
            throw ContentException("manifest.json: packSize должен быть положительным")
        }
        if (packSize != null && packSize > MAX_PACK_SIZE_BYTES) {
            throw ContentException("manifest.json: пакет больше допустимых 64 МБ")
        }
        if ((packSize == null) != (packSha256 == null)) {
            throw ContentException("manifest.json: packSize и packSha256 должны быть вместе")
        }
        packSha256?.let {
            if (!Sha256.isValidHex(it)) {
                throw ContentException("manifest.json: packSha256 должен быть sha256 в hex")
            }
        }
        if (files.isEmpty()) throw ContentException("manifest.json: files пуст")
        if (files.size > 10_000) throw ContentException("manifest.json: слишком много файлов")
        val paths = HashSet<String>()
        for (entry in files) {
            SafePath.requireSafe(entry.path, "manifest.json: files")
            if (entry.path == "manifest.json") {
                throw ContentException("manifest.json: manifest.json не указывается в files")
            }
            if (!paths.add(entry.path)) {
                throw ContentException("manifest.json: файл ${entry.path} указан дважды")
            }
            if (!Sha256.isValidHex(entry.sha256)) {
                throw ContentException("manifest.json: ${entry.path} — некорректный sha256")
            }
            if (entry.size < 0) {
                throw ContentException("manifest.json: ${entry.path} — отрицательный размер")
            }
        }
        if (searchIndex != "search/index.json") {
            throw ContentException("manifest.json: searchIndex должен быть \"search/index.json\"")
        }
        if (redirects != "redirects.json") {
            throw ContentException("manifest.json: redirects должен быть \"redirects.json\"")
        }
        if (!Sha256.isValidHex(searchIndexSha256)) {
            throw ContentException("manifest.json: некорректный searchIndexSha256")
        }
        val searchEntry = files.firstOrNull { it.path == searchIndex }
            ?: throw ContentException("manifest.json: $searchIndex отсутствует в files")
        if (Sha256.normalizeHex(searchEntry.sha256) != Sha256.normalizeHex(searchIndexSha256)) {
            throw ContentException("manifest.json: searchIndexSha256 не совпадает с files")
        }
        if (files.none { it.path == redirects }) {
            throw ContentException("manifest.json: $redirects отсутствует в files")
        }
    }

    /** Сравнение релизного и внутреннего манифеста без полей архива. */
    fun sameContentAs(other: ContentManifest): Boolean {
        if (
            contentVersion != other.contentVersion ||
            schemaVersion != other.schemaVersion ||
            minAppVersion != other.minAppVersion ||
            released != other.released ||
            searchIndex != other.searchIndex ||
            Sha256.normalizeHex(searchIndexSha256) !=
            Sha256.normalizeHex(other.searchIndexSha256) ||
            redirects != other.redirects
        ) {
            return false
        }
        val left = files.associateBy { it.path }
        val right = other.files.associateBy { it.path }
        if (left.keys != right.keys) return false
        return left.all { (path, entry) ->
            val otherEntry = right.getValue(path)
            entry.size == otherEntry.size &&
                Sha256.normalizeHex(entry.sha256) == Sha256.normalizeHex(otherEntry.sha256)
        }
    }
}

object ManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    fun parse(bytes: ByteArray): ContentManifest {
        if (bytes.isEmpty()) throw ContentException("manifest.json пуст")
        if (bytes.size > MAX_MANIFEST_BYTES) {
            throw ContentException("manifest.json слишком большой")
        }
        val manifest = try {
            json.decodeFromString<ContentManifest>(bytes.toString(Charsets.UTF_8))
        } catch (error: SerializationException) {
            throw ContentException("manifest.json не разбирается: ${error.message}", error)
        } catch (error: IllegalArgumentException) {
            throw ContentException("manifest.json не разбирается: ${error.message}", error)
        }
        manifest.validateBasic()
        return manifest
    }

    fun parseFile(file: File): ContentManifest {
        if (!file.isFile) throw ContentException("manifest.json не найден")
        if (file.length() > MAX_MANIFEST_BYTES) {
            throw ContentException("manifest.json слишком большой")
        }
        return try {
            parse(file.readBytes())
        } catch (error: ContentException) {
            throw error
        } catch (error: Exception) {
            throw ContentException("Не удалось прочитать manifest.json: ${error.message}", error)
        }
    }
}
