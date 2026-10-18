package ru.distillarium.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.distillarium.DistillariumApp
import ru.distillarium.core.content.ContentException
import ru.distillarium.core.content.InstalledContent
import ru.distillarium.core.content.UpdateResult
import ru.distillarium.core.content.UpdateStage

data class UpdatesUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val installedVersion: String? = null,
    val schemaVersion: Int? = null,
    val released: String? = null,
    val installedAt: String? = null,
    val source: String? = null,
    val articles: Int? = null,
    val previousVersion: String? = null,
    val embeddedVersion: String? = null,
    val status: String? = null,
    val error: String? = null,
    val progress: Int? = null,
) {
    val canRollback: Boolean get() = !busy && previousVersion != null
}

class UpdatesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as DistillariumApp).container.contentRepository
    private val _state = MutableStateFlow(UpdatesUiState())
    val state: StateFlow<UpdatesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val content = repository.ensureInstalled()
                applyContent(content, previous = repository.previous())
                val embedded = repository.embeddedVersion()
                _state.update {
                    it.copy(
                        loading = false,
                        embeddedVersion = embedded,
                    )
                }
                checkUpdates(silent = true)
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось установить встроенный контент", error)
                _state.update {
                    it.copy(loading = false, error = userMessage(error))
                }
            }
        }
    }

    fun checkUpdates(silent: Boolean = false) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = true,
                    error = null,
                    progress = null,
                    status = "Проверяем обновления…",
                )
            }
            try {
                val result = repository.checkForUpdates { stage, percent ->
                    val status = when (stage) {
                        UpdateStage.CHECKING -> "Проверяем обновления…"
                        UpdateStage.DOWNLOADING -> "Скачиваем пакет…"
                        UpdateStage.INSTALLING -> "Проверяем и устанавливаем…"
                    }
                    _state.update {
                        it.copy(
                            status = status,
                            progress = if (stage == UpdateStage.DOWNLOADING) percent else null,
                        )
                    }
                }
                val status = when (result) {
                    is UpdateResult.Updated ->
                        "Контент обновлён до ${result.toVersion}."
                    is UpdateResult.UpToDate ->
                        "Обновлений нет. Установлена версия ${result.installedVersion}."
                    is UpdateResult.NotModified ->
                        "Обновлений нет. Установлена версия ${result.installedVersion}."
                    is UpdateResult.RequiresNewerApp ->
                        "Доступно обновление контента, но нужно приложение " +
                            "${result.minAppVersion} или новее."
                    is UpdateResult.IncompatibleSchema ->
                        "Пакет требует более новой версии приложения."
                }
                refreshContent(status)
            } catch (error: IOException) {
                Log.w(TAG, "Проверка обновлений: сеть недоступна", error)
                _state.update {
                    if (silent) {
                        it.copy(status = null, error = null)
                    } else {
                        it.copy(error = NETWORK_ERROR)
                    }
                }
            } catch (error: ContentException) {
                Log.w(TAG, "Проверка обновлений отклонена", error)
                _state.update {
                    if (silent) {
                        it.copy(status = null, error = null)
                    } else {
                        it.copy(error = userMessage(error))
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Проверка обновлений не удалась", error)
                _state.update {
                    if (silent) {
                        it.copy(status = null, error = null)
                    } else {
                        it.copy(error = userMessage(error))
                    }
                }
            } finally {
                _state.update { it.copy(busy = false, progress = null) }
            }
        }
    }


    fun rollback() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = true,
                    error = null,
                    status = "Возвращаем предыдущую версию…",
                    progress = null,
                )
            }
            try {
                val content = repository.rollback()
                refreshContent("Вернулись к версии ${content.state.contentVersion}.")
            } catch (error: ContentException) {
                Log.w(TAG, "Откат отклонён", error)
                _state.update { it.copy(error = userMessage(error)) }
            } catch (error: Exception) {
                Log.e(TAG, "Откат не удался", error)
                _state.update { it.copy(error = userMessage(error)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun refreshContent(status: String? = null) {
        val content = repository.installed() ?: repository.ensureInstalled()
        applyContent(content, repository.previous())
        val embedded = repository.embeddedVersion()
        _state.update {
            it.copy(
                loading = false,
                embeddedVersion = embedded,
                status = status ?: it.status,
            )
        }
    }

    private fun applyContent(content: InstalledContent, previous: InstalledContent?) {
        _state.update {
            it.copy(
                installedVersion = content.state.contentVersion,
                schemaVersion = content.state.schemaVersion,
                released = content.manifest.released,
                installedAt = formatDate(content.state.installedAt),
                source = sourceLabel(content.state.source),
                articles = content.manifest.files.count { file ->
                    file.path.startsWith("wiki/") && file.path.endsWith(".md")
                },
                previousVersion = previous?.state?.contentVersion,
            )
        }
    }

    private fun formatDate(value: String): String = runCatching {
        Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }.getOrDefault(value)

    private fun sourceLabel(source: String): String = when (source) {
        "embedded" -> "встроенный"
        "download" -> "скачанный"
        "rollback" -> "после отката"
        else -> "восстановленный"
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is ContentException -> error.message ?: DEFAULT_ERROR
        is IOException -> NETWORK_ERROR
        else -> DEFAULT_ERROR
    }

    private companion object {
        const val TAG = "UpdatesViewModel"
        const val NETWORK_ERROR =
            "Не удалось проверить обновления. Проверьте интернет — контент остался прежним."
        const val DEFAULT_ERROR =
            "Не удалось обновить контент. Контент остался прежним."
    }
}
