package ru.distillarium.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.distillarium.DistillariumApp
import ru.distillarium.batches.BatchDetails
import ru.distillarium.batches.BatchKind
import ru.distillarium.batches.BatchRules
import ru.distillarium.batches.BatchStatus
import ru.distillarium.batches.JournalKind
import ru.distillarium.recipes.RecipeTemplate

data class BatchesUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val cards: List<BatchDetails> = emptyList(),
    val recipes: List<RecipeTemplate> = emptyList(),
    val recipesError: String? = null,
    val selectedId: Long? = null,
    val selected: BatchDetails? = null,
    val detailLoading: Boolean = false,
    val error: String? = null,
)

class BatchesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as DistillariumApp).container.batchRepository
    private val wikiRepository = (application as DistillariumApp).container.wikiRepository
    private val _state = MutableStateFlow(BatchesUiState())
    val state: StateFlow<BatchesUiState> = _state.asStateFlow()

    init {
        refresh()
        refreshRecipes()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val cards = repository.all()
                _state.update { it.copy(cards = cards, loading = false) }
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось прочитать партии", error)
                _state.update {
                    it.copy(loading = false, error = userMessage(error))
                }
            }
        }
    }

    fun refreshRecipes() {
        viewModelScope.launch {
            try {
                val recipes = wikiRepository.recipes()
                _state.update { it.copy(recipes = recipes, recipesError = null) }
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось прочитать рецепты", error)
                _state.update { it.copy(recipesError = userMessage(error)) }
            }
        }
    }

    fun openBatch(batchId: Long) {
        _state.update {
            it.copy(
                selectedId = batchId,
                selected = null,
                detailLoading = true,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val details = repository.details(batchId)
                _state.update {
                    if (it.selectedId != batchId) {
                        it
                    } else {
                        it.copy(selected = details, detailLoading = false)
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось открыть партию", error)
                _state.update {
                    if (it.selectedId != batchId) {
                        it
                    } else {
                        it.copy(detailLoading = false, error = userMessage(error))
                    }
                }
            }
        }
    }

    fun closeBatch() {
        _state.update {
            it.copy(selectedId = null, selected = null, detailLoading = false)
        }
    }

    fun createBatch(
        recipe: RecipeTemplate?,
        title: String,
        kind: BatchKind,
        volume: Double,
        onCreated: (Long) -> Unit,
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = if (recipe == null) {
                    repository.createBlank(title, kind, volume)
                } else {
                    repository.createFromRecipe(recipe, title, kind, volume)
                }
                _state.update { it.copy(selectedId = id) }
                reloadAll()
                onCreated(id)
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось создать партию", error)
                _state.update { it.copy(error = userMessage(error)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun completeStep(stepId: Long, batchId: Long, done: Boolean) =
        mutate { repository.setStepDone(stepId, batchId, done) }

    fun addStep(batchId: Long, title: String, afterDays: Int) = mutate {
        val details = _state.value.selected
        val anchor = details?.steps?.maxByOrNull { it.position }?.dueAt
            ?: System.currentTimeMillis()
        val dueAt = BatchRules.dueAt(
            BatchRules.localDate(anchor).plusDays(afterDays.toLong()),
        )
        repository.addStep(batchId, title, dueAt)
    }

    fun addMeasurement(
        batchId: Long,
        gravity: Double,
        temp: Double?,
        note: String?,
    ) = mutate { repository.addMeasurement(batchId, gravity, temp, note) }

    fun addJournal(batchId: Long, kind: JournalKind, text: String) =
        mutate { repository.addJournal(batchId, kind, text) }

    fun setStatus(batchId: Long, status: BatchStatus) =
        mutate { repository.setStatus(batchId, status) }

    fun updateNotes(batchId: Long, notes: String?) =
        mutate { repository.updateNotes(batchId, notes) }

    fun repeatBatch(batchId: Long, onCreated: (Long) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = repository.repeat(batchId)
                _state.update { it.copy(selectedId = id) }
                reloadAll()
                onCreated(id)
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось повторить партию", error)
                _state.update { it.copy(error = userMessage(error)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
                reloadAll()
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось изменить партию", error)
                _state.update { it.copy(error = userMessage(error)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun reloadAll() {
        val selectedId = _state.value.selectedId
        val cards = repository.all()
        val selected = selectedId?.let { repository.details(it) }
        _state.update {
            it.copy(
                cards = cards,
                selected = selected,
                loading = false,
                detailLoading = false,
            )
        }
    }

    private fun userMessage(error: Throwable): String =
        error.message ?: DEFAULT_ERROR

    private companion object {
        const val TAG = "BatchesViewModel"
        const val DEFAULT_ERROR = "Не удалось сохранить изменения. Попробуйте ещё раз."
    }
}
