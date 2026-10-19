package ru.distillarium.appupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** Проверка скачанного APK и запуск штатного установщика Android. */
class AppUpdateInstaller(private val context: Context) {
    private val packageManager = context.packageManager

    fun canInstallPackages(): Boolean = packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun verify(file: File, manifest: AppUpdateManifest) {
        if (!file.isFile) throw AppUpdateException("Скачанный APK не найден")
        val archive = try {
            packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags())
        } catch (error: Exception) {
            throw AppUpdateException("Не удалось прочитать скачанный APK", error)
        } ?: throw AppUpdateException("Скачанный APK повреждён")

        if (archive.packageName != context.packageName) {
            throw AppUpdateException("APK относится к другому приложению")
        }
        if (versionCode(archive) != manifest.versionCode.toLong()) {
            throw AppUpdateException("Версия в APK не совпадает с манифестом")
        }
        val installed = try {
            packageManager.getPackageInfo(context.packageName, signatureFlags())
        } catch (error: Exception) {
            throw AppUpdateException("Не удалось проверить подпись установленного приложения", error)
        }
        if (versionCode(installed) >= manifest.versionCode) {
            throw AppUpdateException("Установленная версия уже не старше предложенной")
        }
        val installedCerts = certificateHashes(installed)
        val archiveCerts = certificateHashes(archive)
        if (installedCerts.isEmpty() || archiveCerts.isEmpty() || installedCerts != archiveCerts) {
            throw AppUpdateException("Подпись APK не совпадает с установленным приложением")
        }
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            throw AppUpdateException("Не удалось открыть системный установщик", error)
        }
    }

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun certificateHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }
}
