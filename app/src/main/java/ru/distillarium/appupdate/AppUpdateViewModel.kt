package ru.distillarium.appupdate

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.distillarium.BuildConfig
import ru.distillarium.DistillariumApp

enum class AppUpdateStatus {
    Idle,
    Checking,
    UpToDate,
    Available,
    Downloading,
    ReadyToInstall,
    NeedsPermission,
    Error,
}

data class AppUpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
    val availableVersion: String? = null,
    val notes: String? = null,
    val apkSize: Long? = null,
    val progress: Int? = null,
    val error: String? = null,
) {
    val busy: Boolean
        get() = status == AppUpdateStatus.Checking || status == AppUpdateStatus.Downloading
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as DistillariumApp).container
    private val repository = container.appUpdateRepository
    private val installer = container.appUpdateInstaller

    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private var availableManifest: AppUpdateManifest? = null
    private var downloadedFile: File? = null

    init {
        checkForUpdate(silent = true)
    }

    fun checkForUpdate(silent: Boolean = false) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    status = AppUpdateStatus.Checking,
                    error = null,
                    progress = null,
                )
            }
            try {
                when (val result = repository.checkForUpdate()) {
                    is AppUpdateCheck.UpToDate -> {
                        availableManifest = null
                        downloadedFile = null
                        repository.cleanupOldFiles()
                        _state.update {
                            it.copy(
                                status = AppUpdateStatus.UpToDate,
                                availableVersion = null,
                                notes = null,
                                apkSize = null,
                                progress = null,
                            )
                        }
                    }
                    is AppUpdateCheck.Available -> {
                        availableManifest = result.manifest
                        downloadedFile = null
                        _state.update {
                            it.copy(
                                status = AppUpdateStatus.Available,
                                availableVersion = result.manifest.versionName,
                                notes = result.manifest.notes,
                                apkSize = result.manifest.apkSize,
                                progress = null,
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Проверка обновления приложения не удалась", error)
                val message = userMessage(error)
                _state.update {
                    if (silent) {
                        it.copy(status = AppUpdateStatus.Idle, error = null)
                    } else {
                        it.copy(status = AppUpdateStatus.Error, error = message)
                    }
                }
            }
        }
    }


    fun downloadAndInstall() {
        val manifest = availableManifest ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    status = AppUpdateStatus.Downloading,
                    error = null,
                    progress = 0,
                )
            }
            try {
                val file = repository.download(manifest) { bytes, total ->
                    val percent = if (total > 0) {
                        ((bytes * 100L) / total).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    _state.update { it.copy(progress = percent) }
                }
                downloadedFile = file
                installDownloaded()
            } catch (error: Exception) {
                Log.w(TAG, "Загрузка APK не удалась", error)
                _state.update {
                    it.copy(
                        status = AppUpdateStatus.Error,
                        progress = null,
                        error = userMessage(error),
                    )
                }
            }
        }
    }


    fun openInstallPermissionSettings(): Intent = installer.unknownSourcesIntent()

    /** Вызывается при возврате из системных настроек. */
    fun onResume() {
        if (_state.value.status != AppUpdateStatus.NeedsPermission) return
        if (!installer.canInstallPackages()) return
        installDownloaded()
    }

    fun installDownloaded() {
        val manifest = availableManifest ?: return
        val file = downloadedFile ?: return
        try {
            installer.verify(file, manifest)
            if (!installer.canInstallPackages()) {
                _state.update { it.copy(status = AppUpdateStatus.NeedsPermission, error = null) }
                return
            }
            installer.install(file)
            _state.update { it.copy(status = AppUpdateStatus.ReadyToInstall, error = null) }
        } catch (error: Exception) {
            Log.w(TAG, "Установка APK не начата", error)
            _state.update {
                it.copy(status = AppUpdateStatus.Error, error = userMessage(error))
            }
        }
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is AppUpdateException -> error.message ?: DEFAULT_ERROR
        is IOException -> NETWORK_ERROR
        else -> DEFAULT_ERROR
    }

    private companion object {
        const val TAG = "AppUpdateViewModel"
        const val NETWORK_ERROR =
            "Не удалось проверить обновление приложения. Проверьте интернет."
        const val DEFAULT_ERROR = "Не удалось обновить приложение."
    }
}
