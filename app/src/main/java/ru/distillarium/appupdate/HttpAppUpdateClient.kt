package ru.distillarium.appupdate

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** HTTPS-клиент манифеста и APK. */
class HttpAppUpdateClient(
    private val client: OkHttpClient,
    private val userAgent: String,
) {
    fun fetchManifest(url: String): ByteArray {
        requireHttps(url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("Cache-Control", "no-cache")
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(MANIFEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return call.execute().use { response ->
            if (response.code != 200) {
                throw AppUpdateException(
                    "Сервер обновлений приложения ответил кодом ${response.code}",
                )
            }
            val body = response.body
                ?: throw AppUpdateException("Сервер вернул пустой манифест приложения")
            if (body.contentLength() > MAX_MANIFEST_BYTES) {
                throw AppUpdateException("Манифест обновления приложения слишком большой")
            }
            body.byteStream().use { input -> readLimited(input, MAX_MANIFEST_BYTES) }
        }
    }

    fun downloadApk(
        url: String,
        destination: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ) {
        requireHttps(url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.android.package-archive")
            .header("User-Agent", userAgent)
            .build()
        try {
            destination.parentFile?.mkdirs()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AppUpdateException(
                        "Не удалось скачать APK: сервер ответил кодом ${response.code}",
                    )
                }
                val body = response.body
                    ?: throw AppUpdateException("Сервер вернул пустой APK")
                if (body.contentLength() > expectedSize) {
                    throw AppUpdateException("APK больше, чем указано в манифесте")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > expectedSize) {
                                throw AppUpdateException("APK больше, чем указано в манифесте")
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            val percent = ((total * 100L) / expectedSize).toInt()
                                .coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(total, expectedSize)
                            }
                        }
                        if (total != expectedSize) {
                            throw AppUpdateException(
                                "APK скачан не полностью: $total из $expectedSize байт",
                            )
                        }
                    }
                }
                val actualSha256 = digest.digest().toHex()
                if (actualSha256 != expectedSha256.lowercase()) {
                    throw AppUpdateException("SHA-256 APK не совпадает с манифестом")
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun requireHttps(url: String) {
        if (!url.startsWith("https://")) {
            throw AppUpdateException("Адрес обновления приложения должен начинаться с https://")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val MANIFEST_TIMEOUT_SECONDS = 30L
    }
}
