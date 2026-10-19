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
import ru.distillarium.knowledge.ReferenceTable
import ru.distillarium.knowledge.SearchResult
import ru.distillarium.knowledge.WikiLibrary
import ru.distillarium.knowledge.WikiNode

enum class KnowledgeMode {
    Articles,
    Tables,
}

data class KnowledgeUiState(
    val loading: Boolean = true,
    val library: WikiLibrary? = null,
    val roots: List<WikiNode> = emptyList(),
    val results: List<SearchResult> = emptyList(),
    val tableResults: List<ReferenceTable> = emptyList(),
    val mode: KnowledgeMode = KnowledgeMode.Articles,
    val query: String = "",
    val selectedTag: String? = null,
    val expanded: Set<String> = emptySet(),
    val error: String? = null,
) {
    val searching: Boolean get() = query.isNotBlank()
}

class KnowledgeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as DistillariumApp).container.wikiRepository
    private val _state = MutableStateFlow(KnowledgeUiState())
    val state: StateFlow<KnowledgeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load(clear = true)

    /** Перечитывает пакет после обновления контента, сохраняя фильтры. */
    fun refresh() = load(clear = false)

    fun load(clear: Boolean = false) {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    loading = clear || current.library == null,
                    error = null,
                )
            }
            try {
                val library = repository.load()
                _state.update { current ->
                    val tag = current.selectedTag?.takeIf { key ->
                        library.tags.any { it.key == key }
                    }
                    val expanded = if (clear || current.library == null) {
                        defaultExpanded(library)
                    } else {
                        current.expanded + library.roots.map { it.key }
                    }
                    val roots = library.rootsFor(tag)
                    current.copy(
                        loading = false,
                        library = library,
                        roots = roots,
                        selectedTag = tag,
                        expanded = expanded,
                        results = if (current.query.isBlank()) {
                            emptyList()
                        } else {
                            library.search(current.query, tag)
                        },
                        tableResults = if (current.query.isBlank()) {
                            emptyList()
                        } else {
                            library.searchTables(current.query)
                        },
                        error = null,
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Не удалось загрузить вики", error)
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message?.takeIf(String::isNotBlank)
                            ?: "Не удалось открыть статьи. Контент остался на месте.",
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { current ->
            val library = current.library
            current.copy(
                query = query,
                results = if (query.isBlank() || library == null) {
                    emptyList()
                } else {
                    library.search(query, current.selectedTag)
                },
                tableResults = if (query.isBlank() || library == null) {
                    emptyList()
                } else {
                    library.searchTables(query)
                },
            )
        }
    }

    fun clearQuery() = onQueryChange("")

    fun selectMode(mode: KnowledgeMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun selectTag(key: String?) {
        _state.update { current ->
            val library = current.library ?: return@update current
            val tag = key?.takeIf { candidate -> library.tags.any { it.key == candidate } }
            current.copy(
                selectedTag = tag,
                roots = library.rootsFor(tag),
                results = if (current.query.isBlank()) {
                    emptyList()
                } else {
                    library.search(current.query, tag)
                },
            )
        }
    }

    fun toggleExpanded(key: String) {
        _state.update { current ->
            current.copy(
                expanded = if (key in current.expanded) {
                    current.expanded - key
                } else {
                    current.expanded + key
                },
            )
        }
    }

    private fun defaultExpanded(library: WikiLibrary): Set<String> = buildSet {
        library.roots.forEach { root -> add(root.key) }
    }

    private companion object {
        const val TAG = "KnowledgeViewModel"
    }
}
