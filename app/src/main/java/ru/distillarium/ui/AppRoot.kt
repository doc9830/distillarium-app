package ru.distillarium.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.distillarium.R

private enum class RootTab {
    Knowledge,
    Updates,
}

/** Нижняя навигация M1. Позже сюда добавятся «Сейчас», «Партии» и «Расчёты». */
@Composable
fun DistillariumRoot(
    initialArticleId: String? = null,
    initialArticleHeading: String? = null,
) {
    val knowledgeViewModel: KnowledgeViewModel = viewModel()
    val updatesViewModel: UpdatesViewModel = viewModel()
    val knowledge by knowledgeViewModel.state.collectAsStateWithLifecycle()
    val updates by updatesViewModel.state.collectAsStateWithLifecycle()
    val knowledgeListState = rememberLazyListState()

    var tabName by rememberSaveable { mutableStateOf(RootTab.Knowledge.name) }
    var articlePath by rememberSaveable { mutableStateOf(initialArticleId.orEmpty()) }
    var initialArticleApplied by rememberSaveable { mutableStateOf(false) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }

    val tab = RootTab.entries.firstOrNull { it.name == tabName } ?: RootTab.Knowledge
    val library = knowledge.library
    val currentArticleId = articlePath
        .split(STACK_SEPARATOR)
        .lastOrNull()
        ?.takeIf { it.isNotBlank() }

    LaunchedEffect(initialArticleId) {
        if (!initialArticleApplied && !initialArticleId.isNullOrBlank()) {
            articlePath = initialArticleId
            initialArticleApplied = true
        }
    }

    fun closeArticle() {
        articlePath = if (STACK_SEPARATOR in articlePath) {
            articlePath.substringBeforeLast(STACK_SEPARATOR)
        } else {
            ""
        }
    }

    LaunchedEffect(tab, updates.installedVersion) {
        if (
            tab == RootTab.Knowledge &&
            library != null &&
            updates.installedVersion != null &&
            library.contentVersion != updates.installedVersion
        ) {
            knowledgeViewModel.refresh()
        }
    }

    if (currentArticleId != null) {
        if (library == null) {
            if (knowledge.loading) {
                ArticleLoadingScreen(onBack = ::closeArticle)
            } else {
                MissingArticleScreen(onBack = ::closeArticle)
            }
            return
        }
        val article = library.article(currentArticleId)
        if (article == null) {
            MissingArticleScreen(onBack = ::closeArticle)
        } else {
            ArticleScreen(
                article = article,
                library = library,
                selectedEquipment = equipment,
                fontScale = fontScale,
                initialHeading = initialArticleHeading,
                onBack = ::closeArticle,
                onOpenArticle = { id ->
                    articlePath = if (articlePath.isBlank()) {
                        id
                    } else {
                        articlePath + STACK_SEPARATOR + id
                    }
                },
                onEquipmentSelected = { equipment = it },
                onFontScaleChange = { value -> fontScale = value.coerceIn(0.85f, 1.6f) },
            )
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == RootTab.Knowledge,
                    onClick = { tabName = RootTab.Knowledge.name },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_knowledge)) },
                )
                NavigationBarItem(
                    selected = tab == RootTab.Updates,
                    onClick = { tabName = RootTab.Updates.name },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_updates)) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                RootTab.Knowledge -> KnowledgeScreen(
                    state = knowledge,
                    listState = knowledgeListState,
                    onQueryChange = knowledgeViewModel::onQueryChange,
                    onClearQuery = knowledgeViewModel::clearQuery,
                    onSelectTag = knowledgeViewModel::selectTag,
                    onToggleExpanded = knowledgeViewModel::toggleExpanded,
                    onOpenArticle = { id -> articlePath = id },
                    onRetry = knowledgeViewModel::retry,
                )
                RootTab.Updates -> UpdatesScreen(
                    state = updates,
                    onCheckUpdates = { updatesViewModel.checkUpdates() },
                    onRollback = updatesViewModel::rollback,
                )
            }
        }
    }
}

private const val STACK_SEPARATOR = "\u001F"

