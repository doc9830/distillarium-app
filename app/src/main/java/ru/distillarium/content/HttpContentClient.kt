package ru.distillarium.content

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.distillarium.core.content.ContentException
import ru.distillarium.core.content.ManifestFetcher
import ru.distillarium.core.content.ManifestResponse
import ru.distillarium.core.content.PackDownloader

/** HTTP-реализация загрузчика. Работает только по HTTPS. */
class HttpContentClient(
    private val client: OkHttpClient,
    private val userAgent: String,
) : ManifestFetcher, PackDownloader {
    override fun fetch(url: String, etag: String?): ManifestResponse {
        requireHttps(url)
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("Cache-Control", "no-cache")
        etag?.takeIf { it.isNotBlank() }?.let { builder.header("If-None-Match", it) }
        client.newCall(builder.build()).execute().use { response ->
            when (response.code) {
                304 -> return ManifestResponse.NotModified
                200 -> {
                    val body = response.body
                        ?: throw ContentException("Сервер вернул пустой манифест")
                    if (body.contentLength() > MAX_MANIFEST_BYTES) {
                        throw ContentException("Манифест обновлений слишком большой")
                    }
                    val bytes = body.byteStream().use { input ->
                        readLimited(input, MAX_MANIFEST_BYTES)
                    }
                    return ManifestResponse.Fresh(bytes, response.header("ETag"))
                }
                else -> throw ContentException(
                    "Сервер обновлений ответил кодом ${response.code}",
                )
            }
        }
    }

    override fun download(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ) {
        requireHttps(url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/zip")
            .header("User-Agent", userAgent)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ContentException(
                        "Не удалось скачать пакет: сервер ответил кодом ${response.code}",
                    )
                }
                val body = response.body
                    ?: throw ContentException("Сервер вернул пустой пакет")
                if (body.contentLength() > maxBytes) {
                    throw ContentException("Пакет больше, чем указано в манифесте")
                }
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) {
                                throw ContentException("Пакет больше, чем указано в манифесте")
                            }
                            output.write(buffer, 0, read)
                            val percent = if (maxBytes > 0) {
                                ((total * 100L) / maxBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(total, maxBytes)
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun requireHttps(url: String) {
        if (!url.startsWith("https://")) {
            throw ContentException("Адрес обновлений должен начинаться с https://")
        }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > limit) {
                throw ContentException("Манифест обновлений слишком большой")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    }
}
