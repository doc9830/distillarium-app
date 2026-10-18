package ru.distillarium.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import ru.distillarium.R
import ru.distillarium.knowledge.SearchResult
import ru.distillarium.knowledge.SearchText
import ru.distillarium.knowledge.WikiLibrary
import ru.distillarium.knowledge.WikiNode
import ru.distillarium.knowledge.WikiTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    state: KnowledgeUiState,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelectTag: (String?) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.knowledge_title))
                        state.library?.contentVersion?.let { version ->
                            Text(
                                text = stringResource(R.string.knowledge_version, version),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
            )
            state.library?.let { library ->
                TagRow(
                    tags = library.tags,
                    selected = state.selectedTag,
                    onSelect = onSelectTag,
                )
            }
            when {
                state.loading && state.library == null -> LoadingState()
                state.library == null -> ErrorState(
                    message = state.error ?: stringResource(R.string.knowledge_empty_text),
                    onRetry = onRetry,
                )
                else -> KnowledgeContent(
                    state = state,
                    library = state.library,
                    listState = listState,
                    onToggleExpanded = onToggleExpanded,
                    onOpenArticle = onOpenArticle,
                    onSelectTag = onSelectTag,
                    onClearQuery = onClearQuery,
                    onRetry = onRetry,
                )
            }
        }
    }
}


@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.knowledge_search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.knowledge_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun TagRow(
    tags: List<WikiTag>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.knowledge_all_tags)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        items(tags, key = { it.key }) { tag ->
            FilterChip(
                selected = selected == tag.key,
                onClick = { onSelect(if (selected == tag.key) null else tag.key) },
                label = { Text("${tag.label} · ${tag.count}") },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KnowledgeContent(
    state: KnowledgeUiState,
    library: WikiLibrary,
    listState: LazyListState,
    onToggleExpanded: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onSelectTag: (String?) -> Unit,
    onClearQuery: () -> Unit,
    onRetry: () -> Unit,
) {
    var lastFilter by remember {
        mutableStateOf(state.query to state.selectedTag)
    }
    LaunchedEffect(state.query, state.selectedTag) {
        val filter = state.query to state.selectedTag
        if (filter != lastFilter) {
            lastFilter = filter
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        state.error?.let { message ->
            item(key = "error") {
                ErrorBanner(message = message, onRetry = onRetry)
            }
        }

        if (state.searching) {
            item(key = "results-count") {
                Text(
                    text = stringResource(R.string.knowledge_search_results, state.results.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (state.results.isEmpty()) {
                item(key = "results-empty") {
                    EmptyMessage(
                        title = stringResource(R.string.knowledge_search_empty_title),
                        text = stringResource(R.string.knowledge_search_empty_text),
                        action = stringResource(R.string.knowledge_search_clear),
                        onAction = onClearQuery,
                    )
                }
            } else {
                items(state.results, key = { it.article.id }) { result ->
                    SearchResultCard(
                        result = result,
                        query = state.query,
                        onClick = { onOpenArticle(result.article.id) },
                    )
                }
            }
            return@LazyColumn
        }

        if (state.roots.isEmpty()) {
            item(key = "tree-empty") {
                if (state.selectedTag != null) {
                    EmptyMessage(
                        title = stringResource(R.string.knowledge_tag_empty_title),
                        text = stringResource(R.string.knowledge_tag_empty_text),
                        action = stringResource(R.string.knowledge_all_tags),
                        onAction = { onSelectTag(null) },
                    )
                } else {
                    EmptyMessage(
                        title = stringResource(R.string.knowledge_empty_title),
                        text = stringResource(R.string.knowledge_empty_text),
                        action = stringResource(R.string.knowledge_retry),
                        onAction = onRetry,
                    )
                }
            }
            return@LazyColumn
        }

        state.roots.forEach { root ->
            stickyHeader(key = root.key) {
                BranchRow(
                    node = root,
                    expanded = root.key in state.expanded,
                    root = true,
                    onClick = { onToggleExpanded(root.key) },
                )
            }
            if (root.key !in state.expanded) return@forEach
            root.children.forEach { child ->
                if (child.isArticle) {
                    item(key = child.key) {
                        ArticleRow(node = child, onClick = { onOpenArticle(child.articleId!!) })
                    }
                } else {
                    item(key = child.key) {
                        BranchRow(
                            node = child,
                            expanded = child.key in state.expanded,
                            root = false,
                            onClick = { onToggleExpanded(child.key) },
                        )
                    }
                    if (child.key in state.expanded) {
                        items(child.children, key = { it.key }) { leaf ->
                            ArticleRow(
                                node = leaf,
                                onClick = { onOpenArticle(leaf.articleId!!) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchRow(
    node: WikiNode,
    expanded: Boolean,
    root: Boolean,
    onClick: () -> Unit,
) {
    val background = if (root) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }
    Surface(color = background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (root) 56.dp else 48.dp)
                .clickable(
                    onClickLabel = stringResource(
                        if (expanded) {
                            R.string.knowledge_tree_collapse
                        } else {
                            R.string.knowledge_tree_expand
                        },
                        node.title,
                    ),
                    onClick = onClick,
                )
                .padding(
                    start = if (root) 16.dp else 32.dp,
                    end = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = node.title,
                modifier = Modifier.weight(1f),
                style = if (root) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (root) FontWeight.SemiBold else FontWeight.Medium,
            )
            CountBadge(node.count)
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleRow(
    node: WikiNode,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                onClickLabel = stringResource(R.string.knowledge_open_article, node.title),
                onClick = onClick,
            )
            .padding(start = 36.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .heightIn(min = 22.dp),
        )
        Text(
            text = node.title,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.knowledge_article_count, count),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    query: String,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = highlight(result.article.title, query, highlightColor),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = result.breadcrumb,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val snippet = result.snippet.ifBlank { result.article.short }
            if (snippet.isNotBlank()) {
                Text(
                    text = highlight(snippet, query, highlightColor),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.knowledge_loading))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.error_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.knowledge_retry))
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.knowledge_retry))
            }
        }
    }
}

@Composable
private fun EmptyMessage(
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onAction,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(action)
        }
    }
}

@Composable
private fun highlight(text: String, query: String, color: Color): AnnotatedString =
    remember(text, query, color) {
        val stems = SearchText.tokens(query).filter { it.length >= 2 }
        if (stems.isEmpty() || text.isEmpty()) {
            return@remember AnnotatedString(text)
        }
        val variants = stems.flatMap { SearchText.variants(it) }.toSet()
        buildAnnotatedString {
            append(text)
            SearchText.tokenRegex.findAll(text).forEach { match ->
                val word = match.value.lowercase(Locale.ROOT).replace('ё', 'е')
                if (SearchText.stem(word) in variants) {
                    addStyle(
                        style = SpanStyle(color = color, fontWeight = FontWeight.Bold),
                        start = match.range.first,
                        end = match.range.last + 1,
                    )
                }
            }
        }
    }


