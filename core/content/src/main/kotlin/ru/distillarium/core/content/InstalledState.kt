package ru.distillarium.core.content

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Состояние установленного пакета (`files/installed.json`).
 *
 * `source` — расширение формата: откуда пришёл пакет (embedded/download/rollback).
 * Оно не влияет на проверки и нужно только экрану «Обновления».
 */
@Serializable
data class InstalledState(
    val contentVersion: String,
    val schemaVersion: Int,
    val installedAt: String,
    val packSha256: String,
    val source: String = "unknown",
) {
    fun validate(where: String = "installed.json") {
        if (!Regex("^\\d{4}\\.\\d{1,2}\\.\\d+$").matches(contentVersion)) {
            throw ContentException("$where: некорректный contentVersion")
        }
        if (schemaVersion < 1) {
            throw ContentException("$where: некорректный schemaVersion")
        }
        if (installedAt.isBlank()) {
            throw ContentException("$where: пустой installedAt")
        }
        if (packSha256.isNotEmpty() && !Sha256.isValidHex(packSha256)) {
            throw ContentException("$where: некорректный packSha256")
        }
    }
}

data class InstalledContent(
    val state: InstalledState,
    val manifest: ContentManifest,
    val validation: PackageValidation? = null,
)

object InstalledStateJson {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    fun parse(bytes: ByteArray): InstalledState {
        val state = try {
            json.decodeFromString<InstalledState>(bytes.toString(Charsets.UTF_8))
        } catch (error: SerializationException) {
            throw ContentException("installed.json не разбирается: ${error.message}", error)
        } catch (error: IllegalArgumentException) {
            throw ContentException("installed.json не разбирается: ${error.message}", error)
        }
        state.validate()
        return state
    }

    fun parseFile(file: File): InstalledState {
        if (!file.isFile) throw ContentException("installed.json не найден")
        return parse(file.readBytes())
    }

    fun toBytes(state: InstalledState): ByteArray =
        (json.encodeToString(state) + "\n").toByteArray(Charsets.UTF_8)
}
