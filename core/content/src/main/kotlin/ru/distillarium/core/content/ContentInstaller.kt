package ru.distillarium.core.content

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Установка, откат и восстановление пакета контента.
 *
 * Пишет только в `files/content/` и `files/installed.json`. Каталог
 * `databases/` (user.db) не открывается и не изменяется.
 *
 * Транзакция: распаковка в `update.tmp`, проверка, затем переименования
 * `current → previous`, `update.tmp → current`, запись `installed.json`.
 * При сбое на любом шаге старый `current` возвращается на место.
 */
class ContentInstaller(
    private val rootDir: File,
    val appVersion: String,
    private val supportedSchemaVersion: Int = SUPPORTED_CONTENT_SCHEMA,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val lock = Any()
    private val contentRoot = File(rootDir, CONTENT_DIR)
    private val currentDir = File(contentRoot, CURRENT_DIR)
    private val previousDir = File(contentRoot, PREVIOUS_DIR)
    private val updateDir = File(contentRoot, UPDATE_TMP_DIR)
    private val brokenDir = File(contentRoot, BROKEN_TMP_DIR)
    private val installedFile = File(rootDir, INSTALLED_JSON)
    private val previousStateFile = File(contentRoot, PREVIOUS_STATE_JSON)
    private val downloadTemp = File(contentRoot, DOWNLOAD_TMP)

    init {
        if (!VersionNumbers.isValid(appVersion)) {
            throw IllegalArgumentException("appVersion должен быть вида x.y.z: $appVersion")
        }
        rootDir.mkdirs()
        contentRoot.mkdirs()
    }

    fun newPackDownloadFile(): File {
        contentRoot.mkdirs()
        downloadTemp.delete()
        return downloadTemp
    }

    fun installedState(): InstalledState? = synchronized(lock) {
        readStateOrNull(installedFile)
    }

    fun installedContent(): InstalledContent? = synchronized(lock) {
        val state = readStateOrNull(installedFile) ?: return null
        val manifest = readManifestOrNull(currentDir) ?: return null
        InstalledContent(state, manifest)
    }

    fun previousContent(): InstalledContent? = synchronized(lock) {
        val manifest = readManifestOrNull(previousDir) ?: return null
        val state = readStateOrNull(previousStateFile)
            ?: newState(manifest, "", "recovered")
        InstalledContent(state, manifest)
    }

    /**
     * Гарантирует рабочий `current`. При первом запуске ставит встроенный пакет;
     * после сбоя обновления восстанавливает previous или продолжает работать
     * на встроенном. Пользовательские файлы не трогает.
     */
    fun ensureInstalled(embeddedPack: File, embeddedManifest: File): InstalledContent =
        synchronized(lock) {
            contentRoot.mkdirs()
            updateDir.deleteRecursively()
            downloadTemp.delete()

            val current = readValidated(currentDir)
            if (current != null) {
                val existing = readStateOrNull(installedFile)
                val state = reconcileState(current.manifest, existing)
                writeState(installedFile, state)
                brokenDir.deleteRecursively()
                return@synchronized InstalledContent(state, current.manifest, current.validation)
            }

            if (currentDir.exists()) {
                brokenDir.deleteRecursively()
                moveDir(currentDir, brokenDir)
            }

            // Процесс мог умереть посреди отката: current уже убран, а старый
            // пакет лежит в broken.tmp. Возвращаем его, если он цел.
            if (!currentDir.exists() && brokenDir.isDirectory) {
                val recovered = readValidated(brokenDir)
                if (recovered != null) {
                    moveDir(brokenDir, currentDir)
                    val oldState = readStateOrNull(installedFile)
                    val state = newState(
                        recovered.manifest,
                        oldState?.packSha256.orEmpty(),
                        "recovered",
                    )
                    writeState(installedFile, state)
                    val previous = readValidated(previousDir)
                    if (previous != null) {
                        if (readStateOrNull(previousStateFile) == null) {
                            writeState(
                                previousStateFile,
                                newState(previous.manifest, "", "recovered"),
                            )
                        }
                    } else {
                        previousStateFile.delete()
                    }
                    return@synchronized InstalledContent(
                        state,
                        recovered.manifest,
                        recovered.validation,
                    )
                }
            }

            val previous = readValidated(previousDir)
            if (previous != null) {
                val previousState = readStateOrNull(previousStateFile)
                val state = if (previousState?.contentVersion == previous.manifest.contentVersion) {
                    previousState.copy(installedAt = clock().toString())
                } else {
                    newState(previous.manifest, "", "recovered")
                }
                moveDir(previousDir, currentDir)
                writeState(installedFile, state)
                previousStateFile.delete()
                brokenDir.deleteRecursively()
                return@synchronized InstalledContent(
                    state,
                    previous.manifest,
                    previous.validation,
                )
            }

            brokenDir.deleteRecursively()
            if (!embeddedPack.isFile || !embeddedManifest.isFile) {
                throw ContentException("Встроенный пакет контента не найден")
            }
            val manifest = ManifestParser.parseFile(embeddedManifest)
            return@synchronized installLocked(
                embeddedPack,
                manifest,
                "embedded",
                preserveCurrent = false,
            )
        }

    fun install(
        packFile: File,
        manifest: ContentManifest,
        source: String,
    ): InstalledContent = synchronized(lock) {
        contentRoot.mkdirs()
        installLocked(packFile, manifest, source, preserveCurrent = true)
    }

    /**
     * Возврат к previous. После успеха текущий (новый) пакет становится
     * previous, поэтому откат обратим.
     */
    fun rollback(): InstalledContent = synchronized(lock) {
        if (!previousDir.isDirectory) {
            throw ContentException("Предыдущая версия контента не найдена")
        }
        val previousManifest = ManifestParser.parseFile(File(previousDir, MANIFEST_JSON))
        val previousValidation = PackageValidator.validate(previousDir, previousManifest)
        val previousState = readStateOrNull(previousStateFile)
            ?.takeIf { it.contentVersion == previousManifest.contentVersion }
            ?: newState(previousManifest, "", "recovered")
        val currentState = readStateOrNull(installedFile)
            ?: readManifestOrNull(currentDir)?.let { newState(it, "", "recovered") }

        brokenDir.deleteRecursively()
        var stage = 0
        try {
            moveDir(currentDir, brokenDir)
            stage = 1
            moveDir(previousDir, currentDir)
            stage = 2
            val restored = previousState.copy(
                installedAt = clock().toString(),
                source = "rollback",
            )
            writeState(installedFile, restored)
            stage = 3
            // Возвращённый пакет оставляем как previous: откат обратим.
            runCatching {
                moveDir(brokenDir, previousDir)
                if (currentState != null) {
                    writeState(previousStateFile, currentState)
                } else {
                    previousStateFile.delete()
                }
            }.onFailure {
                previousStateFile.delete()
                brokenDir.deleteRecursively()
            }
            return@synchronized InstalledContent(
                restored,
                previousManifest,
                previousValidation,
            )
        } catch (error: Exception) {
            runCatching {
                if (stage >= 2 && currentDir.exists()) {
                    moveDir(currentDir, previousDir)
                }
                if (stage >= 1 && brokenDir.exists()) {
                    moveDir(brokenDir, currentDir)
                }
                if (currentState != null) {
                    writeState(installedFile, currentState)
                }
            }
            if (error is ContentException) throw error
            throw ContentException("Не удалось вернуться к предыдущей версии: ${error.message}", error)
        }
    }


    private fun installLocked(
        packFile: File,
        manifest: ContentManifest,
        source: String,
        preserveCurrent: Boolean,
    ): InstalledContent {
        manifest.validateBasic()
        if (manifest.schemaVersion > supportedSchemaVersion) {
            throw ContentException(
                "Пакет контента новее приложения (схема ${manifest.schemaVersion}). " +
                    "Обновите приложение.",
            )
        }
        if (!VersionNumbers.isAtLeast(appVersion, manifest.minAppVersion)) {
            throw ContentException(
                "Для этого контента нужно приложение ${manifest.minAppVersion} или новее. " +
                    "Обновите приложение.",
            )
        }
        val (packSize, packHash) = manifest.requirePack()
        if (!packFile.isFile) throw ContentException("Архив контента не найден")
        if (packFile.length() != packSize) {
            throw ContentException("Размер архива не совпадает с манифестом")
        }
        if (!Sha256.matches(packFile, packHash)) {
            throw ContentException("sha256 архива не совпадает с манифестом")
        }

        updateDir.deleteRecursively()
        val inner: ContentManifest
        val validation: PackageValidation
        try {
            SafeZip.extract(packFile, updateDir, packSize)
            inner = ManifestParser.parseFile(File(updateDir, MANIFEST_JSON))
            if (!manifest.sameContentAs(inner)) {
                throw ContentException("Внутренний манифест не совпадает с релизным")
            }
            validation = PackageValidator.validate(updateDir, inner)
        } catch (error: Exception) {
            updateDir.deleteRecursively()
            throw error
        }

        val oldManifest = if (preserveCurrent && currentDir.isDirectory) {
            try {
                ManifestParser.parseFile(File(currentDir, MANIFEST_JSON))
            } catch (error: Exception) {
                updateDir.deleteRecursively()
                throw error
            }
        } else {
            null
        }
        val oldState = if (oldManifest != null) {
            readStateOrNull(installedFile)
                ?.takeIf { it.contentVersion == oldManifest.contentVersion }
                ?: newState(oldManifest, "", "recovered")
        } else {
            null
        }

        var currentMoved = false
        var newCurrent = false
        try {
            if (oldManifest != null) {
                previousDir.deleteRecursively()
                previousStateFile.delete()
                moveDir(currentDir, previousDir)
                currentMoved = true
                writeState(previousStateFile, oldState!!)
            } else {
                previousDir.deleteRecursively()
                previousStateFile.delete()
            }
            moveDir(updateDir, currentDir)
            newCurrent = true
            val state = newState(inner, packHash.lowercase(), source)
            writeState(installedFile, state)
            brokenDir.deleteRecursively()
            return InstalledContent(state, inner, validation)
        } catch (error: Exception) {
            updateDir.deleteRecursively()
            runCatching {
                if (newCurrent && currentDir.exists()) currentDir.deleteRecursively()
                if (currentMoved && previousDir.exists()) moveDir(previousDir, currentDir)
                if (oldState != null) writeState(installedFile, oldState) else installedFile.delete()
                previousStateFile.delete()
            }
            if (error is ContentException) throw error
            throw ContentException("Не удалось установить пакет: ${error.message}", error)
        }
    }


    private data class Validated(
        val manifest: ContentManifest,
        val validation: PackageValidation,
    )

    private fun readValidated(dir: File): Validated? {
        if (!dir.isDirectory) return null
        return try {
            val manifest = ManifestParser.parseFile(File(dir, MANIFEST_JSON))
            if (manifest.schemaVersion > supportedSchemaVersion) return null
            Validated(manifest, PackageValidator.validate(dir, manifest))
        } catch (_: Exception) {
            null
        }
    }

    private fun readManifestOrNull(dir: File): ContentManifest? = try {
        if (dir.isDirectory) ManifestParser.parseFile(File(dir, MANIFEST_JSON)) else null
    } catch (_: Exception) {
        null
    }

    private fun readStateOrNull(file: File): InstalledState? = try {
        if (file.isFile) InstalledStateJson.parseFile(file) else null
    } catch (_: Exception) {
        null
    }

    private fun reconcileState(
        manifest: ContentManifest,
        existing: InstalledState?,
    ): InstalledState {
        if (existing != null && existing.contentVersion == manifest.contentVersion) {
            return existing.copy(schemaVersion = manifest.schemaVersion)
        }
        return newState(
            manifest,
            existing?.packSha256.orEmpty(),
            existing?.source ?: "recovered",
        )
    }

    private fun newState(
        manifest: ContentManifest,
        packSha256: String,
        source: String,
    ): InstalledState = InstalledState(
        contentVersion = manifest.contentVersion,
        schemaVersion = manifest.schemaVersion,
        installedAt = clock().toString(),
        packSha256 = packSha256,
        source = source,
    )

    private fun writeState(file: File, state: InstalledState) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(InstalledStateJson.toBytes(state))
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
                throw ContentException("Не удалось записать ${file.name}: ${error.message}", error)
            }
        }
    }

    private fun moveDir(from: File, to: File) {
        if (!from.exists()) return
        if (to.exists()) to.deleteRecursively()
        to.parentFile?.mkdirs()
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        } catch (error: IOException) {
            if (!from.renameTo(to)) {
                throw ContentException("Не удалось переместить ${from.name}: ${error.message}", error)
            }
        }
    }

    companion object {
        const val CONTENT_DIR = "content"
        const val CURRENT_DIR = "current"
        const val PREVIOUS_DIR = "previous"
        const val UPDATE_TMP_DIR = "update.tmp"
        const val BROKEN_TMP_DIR = "broken.tmp"
        const val INSTALLED_JSON = "installed.json"
        const val PREVIOUS_STATE_JSON = "previous.json"
        const val DOWNLOAD_TMP = "download.tmp"
        const val MANIFEST_JSON = "manifest.json"
    }
}
