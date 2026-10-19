package ru.distillarium.appupdate

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface AppUpdateCheck {
    data class UpToDate(val installedVersion: String) : AppUpdateCheck
    data class Available(val manifest: AppUpdateManifest) : AppUpdateCheck
}

/**
 * Проверка app-manifest.json и загрузка APK во внутреннее хранилище.
 * Скачанный файл не устанавливается здесь: установку запускает UI через системный installer.
 */
class AppUpdateRepository(
    context: Context,
    private val client: HttpAppUpdateClient,
    private val manifestUrl: String,
    private val currentVersionCode: Int,
    private val currentVersionName: String,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val updateDir = File(appContext.filesDir, UPDATE_DIR)

    suspend fun checkForUpdate(): AppUpdateCheck = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (manifestUrl.isBlank()) {
                throw AppUpdateException("Адрес обновлений приложения не настроен")
            }
            val manifest = AppUpdateManifestParser.parse(client.fetchManifest(manifestUrl))
            if (manifest.minSdk > Build.VERSION.SDK_INT) {
                throw AppUpdateException(
                    "Обновление ${manifest.versionName} требует Android " +
                        "${manifest.minSdk} или новее",
                )
            }
            if (manifest.versionCode <= currentVersionCode) {
                cleanupOldFiles(keep = null)
                AppUpdateCheck.UpToDate(currentVersionName)
            } else {
                AppUpdateCheck.Available(manifest)
            }
        }
    }

    suspend fun download(
        manifest: AppUpdateManifest,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = File(updateDir, "distillarium-${manifest.versionName}.apk")
            if (target.isFile && target.length() == manifest.apkSize &&
                sha256(target) == manifest.apkSha256
            ) {
                cleanupOldFiles(keep = target)
                return@withLock target
            }
            updateDir.mkdirs()
            cleanupOldFiles(keep = target)
            target.delete()
            val temp = File(updateDir, "download.tmp")
            temp.delete()
            try {
                client.downloadApk(
                    url = manifest.apkUrl,
                    destination = temp,
                    expectedSize = manifest.apkSize,
                    expectedSha256 = manifest.apkSha256,
                    onProgress = onProgress,
                )
                if (!temp.renameTo(target)) {
                    throw AppUpdateException("Не удалось сохранить скачанный APK")
                }
                target
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        }
    }

    suspend fun cleanupOldFiles() = withContext(Dispatchers.IO) {
        mutex.withLock { cleanupOldFiles(keep = null) }
    }

    private fun cleanupOldFiles(keep: File?) {
        if (!updateDir.isDirectory) return
        updateDir.listFiles()?.forEach { file ->
            if (file != keep && file.name != "etag.txt") file.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val UPDATE_DIR = "app-update"
    }
}
