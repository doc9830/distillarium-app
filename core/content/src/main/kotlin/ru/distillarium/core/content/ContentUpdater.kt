package ru.distillarium.core.content

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class UpdateStage {
    CHECKING,
    DOWNLOADING,
    INSTALLING,
}

sealed interface ManifestResponse {
    data class Fresh(val json: ByteArray, val etag: String?) : ManifestResponse
    data object NotModified : ManifestResponse
}

fun interface ManifestFetcher {
    fun fetch(url: String, etag: String?): ManifestResponse
}

fun interface PackDownloader {
    fun download(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (bytes: Long, total: Long) -> Unit,
    )
}

interface EtagStore {
    fun read(): String?
    fun write(etag: String?)
}

/** ETag хранится в кеше приложения: это не пользовательские данные. */
class FileEtagStore(private val file: File) : EtagStore {
    override fun read(): String? = runCatching {
        if (!file.isFile) return null
        file.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    override fun write(etag: String?) {
        if (etag.isNullOrBlank()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(etag, Charsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            if (!temp.renameTo(file)) {
                throw ContentException("Не удалось сохранить ETag: ${error.message}", error)
            }
        }
    }
}

sealed interface UpdateResult {
    data class Updated(val fromVersion: String, val toVersion: String) : UpdateResult
    data class UpToDate(val installedVersion: String) : UpdateResult
    data class NotModified(val installedVersion: String) : UpdateResult
    data class RequiresNewerApp(
        val minAppVersion: String,
        val appVersion: String,
    ) : UpdateResult
    data class IncompatibleSchema(
        val schemaVersion: Int,
        val supported: Int,
    ) : UpdateResult
}

/**
 * Проверка обновлений: манифест → сравнение версий → скачивание → установка.
 *
 * Любая ошибка до подмены `current` оставляет установленный пакет нетронутым.
 * Сетевые исключения пробрасываются вызывающему: UI переводит их в понятный
 * текст, а WorkManager решает, повторять ли запрос.
 */
class ContentUpdater(
    private val installer: ContentInstaller,
    private val manifestFetcher: ManifestFetcher,
    private val packDownloader: PackDownloader,
    private val etagStore: EtagStore,
) {
    fun update(
        manifestUrl: String,
        packUrl: String,
        onStage: (UpdateStage, Int) -> Unit = { _, _ -> },
    ): UpdateResult {
        require(manifestUrl.isNotBlank()) { "manifestUrl пуст" }
        require(packUrl.isNotBlank()) { "packUrl пуст" }
        val installed = installer.installedContent()
            ?: throw ContentException("Контент ещё не установлен")

        onStage(UpdateStage.CHECKING, 0)
        val cachedEtag = runCatching { etagStore.read() }.getOrNull()
        val response = manifestFetcher.fetch(manifestUrl, cachedEtag)
        if (response is ManifestResponse.NotModified) {
            return UpdateResult.NotModified(installed.state.contentVersion)
        }

        val fresh = response as ManifestResponse.Fresh
        val remote = ManifestParser.parse(fresh.json)
        if (remote.schemaVersion > SUPPORTED_CONTENT_SCHEMA) {
            return UpdateResult.IncompatibleSchema(
                schemaVersion = remote.schemaVersion,
                supported = SUPPORTED_CONTENT_SCHEMA,
            )
        }

        val comparison = VersionNumbers.compare(
            remote.contentVersion,
            installed.state.contentVersion,
        )
        if (comparison <= 0) {
            runCatching { etagStore.write(fresh.etag) }
            return UpdateResult.UpToDate(installed.state.contentVersion)
        }

        if (!VersionNumbers.isAtLeast(installer.appVersion, remote.minAppVersion)) {
            // ETag не сохраняем: при следующей проверке снова объясним причину.
            return UpdateResult.RequiresNewerApp(
                minAppVersion = remote.minAppVersion,
                appVersion = installer.appVersion,
            )
        }

        val (packSize, _) = remote.requirePack()
        val temp = installer.newPackDownloadFile()
        try {
            onStage(UpdateStage.DOWNLOADING, 0)
            packDownloader.download(packUrl, temp, packSize) { bytes, total ->
                val percent = if (total > 0) {
                    ((bytes * 100L) / total).coerceIn(0L, 100L).toInt()
                } else {
                    0
                }
                onStage(UpdateStage.DOWNLOADING, percent)
            }
            if (!temp.isFile || temp.length() != packSize) {
                throw ContentException("Скачанный архив повреждён: неверный размер")
            }
            onStage(UpdateStage.INSTALLING, 0)
            val result = installer.install(temp, remote, "download")
            etagStore.write(fresh.etag)
            onStage(UpdateStage.INSTALLING, 100)
            return UpdateResult.Updated(
                fromVersion = installed.state.contentVersion,
                toVersion = result.state.contentVersion,
            )
        } finally {
            temp.delete()
        }
    }
}
