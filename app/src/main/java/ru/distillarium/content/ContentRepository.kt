package ru.distillarium.content

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.distillarium.core.content.ContentException
import ru.distillarium.core.content.ContentInstaller
import ru.distillarium.core.content.ContentUpdater
import ru.distillarium.core.content.InstalledContent
import ru.distillarium.core.content.ManifestParser
import ru.distillarium.core.content.UpdateResult
import ru.distillarium.core.content.UpdateStage

/**
 * Android-обёртка конвейера контента: встроенные assets, IO-поток и Mutex.
 * Все операции с `current/previous` сериализованы.
 */
class ContentRepository(
    private val context: Context,
    private val installer: ContentInstaller,
    private val updater: ContentUpdater,
    private val manifestUrl: String,
    private val packUrl: String,
) {
    private val mutex = Mutex()

    suspend fun ensureInstalled(): InstalledContent = withContext(Dispatchers.IO) {
        mutex.withLock { ensureInstalledLocked() }
    }

    suspend fun installed(): InstalledContent? = withContext(Dispatchers.IO) {
        mutex.withLock { installer.installedContent() }
    }

    /** Каталог активного пакета; при необходимости сначала ставит встроенный. */
    suspend fun currentDirectory(): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (installer.installedContent() == null) ensureInstalledLocked()
            File(context.filesDir, ContentInstaller.CONTENT_DIR)
                .resolve(ContentInstaller.CURRENT_DIR)
        }
    }

    suspend fun previous(): InstalledContent? = withContext(Dispatchers.IO) {
        mutex.withLock { installer.previousContent() }
    }

    suspend fun embeddedVersion(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.assets.open(EMBEDDED_MANIFEST).use { it.readBytes() }
            ManifestParser.parse(bytes).contentVersion
        }.getOrNull()
    }

    suspend fun checkForUpdates(
        onStage: (UpdateStage, Int) -> Unit = { _, _ -> },
    ): UpdateResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureInstalledLocked()
            if (manifestUrl.isBlank() || packUrl.isBlank()) {
                throw ContentException("Адрес обновлений не настроен")
            }
            updater.update(manifestUrl, packUrl, onStage)
        }
    }

    suspend fun rollback(): InstalledContent = withContext(Dispatchers.IO) {
        mutex.withLock { installer.rollback() }
    }

    private fun ensureInstalledLocked(): InstalledContent {
        val embedded = copyEmbeddedAssets()
        return installer.ensureInstalled(embedded.first, embedded.second)
    }

    private fun copyEmbeddedAssets(): Pair<File, File> {
        val directory = File(context.cacheDir, "embedded-content")
        directory.mkdirs()
        val pack = File(directory, EMBEDDED_PACK)
        val manifest = File(directory, EMBEDDED_MANIFEST)
        try {
            context.assets.open(EMBEDDED_PACK).use { input ->
                pack.outputStream().use { output -> input.copyTo(output) }
            }
            context.assets.open(EMBEDDED_MANIFEST).use { input ->
                manifest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (error: Exception) {
            throw ContentException(
                "Встроенный контент повреждён: ${error.message}",
                error,
            )
        }
        return pack to manifest
    }

    private companion object {
        const val EMBEDDED_PACK = "content-pack.zip"
        const val EMBEDDED_MANIFEST = "manifest.json"
    }
}
