package ru.distillarium.appupdate

import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Разбор и проверка app-manifest.json. */
object AppUpdateManifestParser {
    private val SHA256_RE = Regex("^[0-9a-fA-F]{64}$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun parse(bytes: ByteArray): AppUpdateManifest {
        val root = try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } catch (error: Exception) {
            throw AppUpdateException("Манифест обновления приложения повреждён", error)
        }
        val schemaVersion = root.int("schemaVersion")
        if (schemaVersion !in 1..AppUpdateManifest.SUPPORTED_SCHEMA) {
            throw AppUpdateException("Неизвестная версия схемы манифеста приложения: $schemaVersion")
        }
        val versionCode = root.long("versionCode")
        if (versionCode <= 0 || versionCode > Int.MAX_VALUE) {
            throw AppUpdateException("manifest: некорректный versionCode")
        }
        val versionName = root.string("versionName")
        val expectedCode = AppVersion.codeFor(versionName)
        if (versionCode != expectedCode.toLong()) {
            throw AppUpdateException(
                "manifest: versionCode $versionCode не соответствует versionName $versionName",
            )
        }
        val minSdk = root.int("minSdk")
        if (minSdk !in 1..100) throw AppUpdateException("manifest: некорректный minSdk")
        val released = root.string("released")
        if (!DATE_RE.matches(released)) {
            throw AppUpdateException("manifest: released должен быть датой ГГГГ-ММ-ДД")
        }
        val notes = root["notes"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val apk = root["apk"]?.jsonObject
            ?: throw AppUpdateException("manifest: нет объекта apk")
        val apkUrl = apk.string("url")
        if (!apkUrl.startsWith("https://")) {
            throw AppUpdateException("manifest: apk.url должен начинаться с https://")
        }
        val apkSize = apk.long("size")
        if (apkSize <= 0 || apkSize > AppUpdateManifest.MAX_APK_SIZE) {
            throw AppUpdateException("manifest: некорректный размер APK")
        }
        val apkSha256 = apk.string("sha256").lowercase()
        if (!SHA256_RE.matches(apkSha256)) {
            throw AppUpdateException("manifest: sha256 APK должен быть 64 hex-символа")
        }
        return AppUpdateManifest(
            schemaVersion = schemaVersion,
            versionCode = versionCode.toInt(),
            versionName = versionName,
            minSdk = minSdk,
            released = released,
            apkUrl = apkUrl,
            apkSize = apkSize,
            apkSha256 = apkSha256,
            notes = notes,
        )
    }

    private fun JsonObject.string(name: String): String {
        val value = this[name]?.jsonPrimitive?.contentOrNull?.trim()
        if (value.isNullOrEmpty()) throw AppUpdateException("manifest: не задано поле $name")
        return value
    }

    private fun JsonObject.int(name: String): Int {
        val value = this[name]?.jsonPrimitive?.intOrNull
            ?: throw AppUpdateException("manifest: некорректное поле $name")
        return value
    }

    private fun JsonObject.long(name: String): Long {
        val value = this[name]?.jsonPrimitive?.longOrNull
            ?: throw AppUpdateException("manifest: некорректное поле $name")
        return value
    }
}

internal fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (output.size() + read > limit) {
            throw AppUpdateException("Манифест обновления приложения слишком большой")
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
