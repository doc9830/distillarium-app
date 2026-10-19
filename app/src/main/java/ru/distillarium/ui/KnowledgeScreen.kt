package ru.distillarium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
import ru.distillarium.knowledge.ReferenceTable
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
    onOpenReference: (String) -> Unit,
    onSelectMode: (KnowledgeMode) -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(
        enabled = state.selectedTag != null && state.mode == KnowledgeMode.Articles,
    ) {
        onSelectTag(null)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.knowledge_title),
                subtitle = state.library?.contentVersion?.let { version ->
                    stringResource(R.string.knowledge_version, version)
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
            ModeRow(
                selected = state.mode,
                onSelect = onSelectMode,
            )
            if (state.mode == KnowledgeMode.Articles) {
                state.library?.let { library ->
                    TagFilterRow(
                        tags = library.tags,
                        selectedTag = state.selectedTag,
                        onSelectTag = onSelectTag,
                    )
                }
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
                    onOpenReference = onOpenReference,
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
    SearchInput(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.knowledge_search_placeholder),
        onClear = onClear,
        clearLabel = stringResource(R.string.knowledge_search_clear),
    )
}

@Composable
private fun ModeRow(
    selected: KnowledgeMode,
    onSelect: (KnowledgeMode) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ModeSegment(
                text = stringResource(R.string.knowledge_mode_articles),
                selected = selected == KnowledgeMode.Articles,
                onClick = { onSelect(KnowledgeMode.Articles) },
                modifier = Modifier.weight(1f),
            )
            ModeSegment(
                text = stringResource(R.string.knowledge_mode_tables),
                selected = selected == KnowledgeMode.Tables,
                onClick = { onSelect(KnowledgeMode.Tables) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun TagFilterRow(
    tags: List<WikiTag>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
) {
    if (tags.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TagChip(
            text = stringResource(R.string.knowledge_all_tags),
            count = null,
            selected = selectedTag == null,
            onClick = { onSelectTag(null) },
        )
        tags.forEach { tag ->
            TagChip(
                text = tag.label,
                count = tag.count,
                selected = tag.key == selectedTag,
                onClick = { onSelectTag(tag.key) },
            )
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KnowledgeContent(
    state: KnowledgeUiState,
    library: WikiLibrary,
    listState: LazyListState,
    onToggleExpanded: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenReference: (String) -> Unit,
    onSelectTag: (String?) -> Unit,
    onClearQuery: () -> Unit,
    onRetry: () -> Unit,
) {
    var lastFilter by remember {
        mutableStateOf(Triple(state.query, state.selectedTag, state.mode))
    }

    LaunchedEffect(state.query, state.selectedTag, state.mode) {
        val filter = Triple(state.query, state.selectedTag, state.mode)
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
            val count = if (state.mode == KnowledgeMode.Articles) {
                state.results.size
            } else {
                state.tableResults.size
            }
            item(key = "results-count") {
                Text(
                    text = stringResource(R.string.knowledge_search_results, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            val empty = if (state.mode == KnowledgeMode.Articles) {
                state.results.isEmpty()
            } else {
                state.tableResults.isEmpty()
            }
            if (empty) {
                item(key = "results-empty") {
                    EmptyMessage(
                        title = stringResource(R.string.knowledge_search_empty_title),
                        text = stringResource(R.string.knowledge_search_empty_text),
                        action = stringResource(R.string.knowledge_search_clear),
                        onAction = onClearQuery,
                    )
                }
            } else if (state.mode == KnowledgeMode.Articles) {
                items(state.results, key = { "a:${it.article.id}" }) { result ->
                    SearchResultCard(
                        result = result,
                        query = state.query,
                        onClick = { onOpenArticle(result.article.id) },
                    )
                }
            } else {
                items(state.tableResults, key = { "t:${it.id}" }) { table ->
                    ReferenceListCard(table = table, onClick = { onOpenReference(table.id) })
                }
            }
            return@LazyColumn
        }

        if (state.mode == KnowledgeMode.Tables) {
            val tables = library.referenceTables
            if (tables.isEmpty()) {
                item(key = "tables-empty") {
                    EmptyMessage(
                        title = stringResource(R.string.knowledge_tables_empty_title),
                        text = stringResource(R.string.knowledge_tables_empty_text),
                        action = stringResource(R.string.knowledge_retry),
                        onAction = onRetry,
                    )
                }
            } else {
                item(key = "tables-count") {
                    Text(
                        text = stringResource(R.string.knowledge_table_rows, tables.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(tables, key = { "t:${it.id}" }) { table ->
                    ReferenceListCard(table = table, onClick = { onOpenReference(table.id) })
                }
            }
            return@LazyColumn
        }

        if (state.roots.isEmpty()) {
            item(key = "tree-empty") {
                EmptyMessage(
                    title = stringResource(
                        if (state.selectedTag == null) {
                            R.string.knowledge_empty_title
                        } else {
                            R.string.knowledge_tag_empty_title
                        },
                    ),
                    text = stringResource(
                        if (state.selectedTag == null) {
                            R.string.knowledge_empty_text
                        } else {
                            R.string.knowledge_tag_empty_text
                        },
                    ),
                    action = stringResource(
                        if (state.selectedTag == null) {
                            R.string.knowledge_retry
                        } else {
                            R.string.knowledge_all_tags
                        },
                    ),
                    onAction = if (state.selectedTag == null) {
                        onRetry
                    } else {
                        { onSelectTag(null) }
                    },
                )
            }
            return@LazyColumn
        }

        items(
            items = visibleTreeRows(state.roots, state.expanded),
            key = { row -> row.node.key },
        ) { row ->
            val node = row.node
            if (node.isArticle) {
                ArticleRow(
                    node = node,
                    depth = row.depth,
                    onClick = { onOpenArticle(node.articleId!!) },
                )
            } else {
                SectionRow(
                    node = node,
                    depth = row.depth,
                    expanded = node.key in state.expanded,
                    onClick = { onToggleExpanded(node.key) },
                )
            }
        }
    }
}

private data class TreeRow(
    val node: WikiNode,
    val depth: Int,
)

/** Разворачивает дерево в плоский список с учётом раскрытых разделов. */
private fun visibleTreeRows(
    roots: List<WikiNode>,
    expanded: Set<String>,
): List<TreeRow> {
    val rows = ArrayList<TreeRow>()
    fun visit(node: WikiNode, depth: Int) {
        rows += TreeRow(node, depth)
        if (!node.isArticle && node.key in expanded) {
            node.children.forEach { child -> visit(child, depth + 1) }
        }
    }
    roots.forEach { root -> visit(root, depth = 0) }
    return rows
}

@Composable
private fun SectionRow(
    node: WikiNode,
    depth: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val hasChildren = node.children.isNotEmpty()
    val root = depth == 0
    Column(
        modifier = if (root) {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
        } else {
            Modifier
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (root) 54.dp else 44.dp)
                .clickable(
                    enabled = hasChildren,
                    onClickLabel = if (hasChildren) {
                        stringResource(
                            if (expanded) {
                                R.string.knowledge_tree_collapse
                            } else {
                                R.string.knowledge_tree_expand
                            },
                            node.title,
                        )
                    } else {
                        null
                    },
                    onClick = onClick,
                )
                .padding(
                    start = 20.dp + (depth * 16).dp,
                    end = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = node.title,
                modifier = Modifier.weight(1f),
                style = if (root) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (root) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (node.count > 0) {
                CountBadge(node.count)
            }
            if (hasChildren) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (root) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ArticleRow(
    node: WikiNode,
    depth: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                onClickLabel = stringResource(R.string.knowledge_open_article, node.title),
                onClick = onClick,
            )
            .padding(start = 24.dp + (depth * 16).dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    shape = CircleShape,
                ),
        )
        Text(
            text = node.title,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    Text(
        text = stringResource(R.string.knowledge_article_count, count),
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    query: String,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = highlight(result.article.title, query, highlightColor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = result.breadcrumb,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val snippet = result.snippet.ifBlank { result.article.short }
            if (snippet.isNotBlank()) {
                Text(
                    text = highlight(snippet, query, highlightColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReferenceListCard(
    table: ReferenceTable,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_calc_table_chart),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = table.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (table.short.isNotBlank()) {
                    Text(
                        text = table.short,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.knowledge_table_rows, table.rowCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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


