package ru.distillarium.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.distillarium.R
import ru.distillarium.knowledge.Article
import ru.distillarium.knowledge.ReferenceColumn
import ru.distillarium.knowledge.ReferenceRow
import ru.distillarium.knowledge.ReferenceTable
import ru.distillarium.knowledge.WikiLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceScreen(
    tableId: String,
    library: WikiLibrary?,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenCalculator: (String) -> Unit,
) {
    val table = library?.referenceTable(tableId)
    BackHandler(onBack = onBack)
    if (library == null || table == null) {
        MissingReferenceScreen(onBack = onBack)
        return
    }

    var query by rememberSaveable(table.id) { mutableStateOf("") }
    var sortKey by rememberSaveable(table.id) { mutableStateOf<String?>(null) }
    var ascending by rememberSaveable(table.id) { mutableStateOf(true) }
    val rows = remember(table.id, query, sortKey, ascending) {
        val ordered = if (sortKey == null) table.rows else table.sortedRows(sortKey!!, ascending)
        ordered.filter { table.rowMatches(it, query) }
    }
    val related = remember(table.id) { library.articlesReferencingTable(table.id) }
    val horizontal = rememberScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = table.title,
                subtitle = stringResource(R.string.reference_title),
                onBack = onBack,
                backLabel = stringResource(R.string.reference_back),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchInput(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.reference_search_placeholder),
                onClear = { query = "" },
                clearLabel = stringResource(R.string.reference_search_clear),
            )
            Text(
                text = stringResource(R.string.reference_rows_count, rows.size, table.rowCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            if (table.short.isNotBlank()) {
                Text(
                    text = table.short,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            ReferenceRows(
                table = table,
                rows = rows,
                related = related,
                sortKey = sortKey,
                ascending = ascending,
                horizontal = horizontal,
                onSort = { key ->
                    if (sortKey == key) {
                        ascending = !ascending
                    } else {
                        sortKey = key
                        ascending = true
                    }
                },
                onClearQuery = { query = "" },
                onOpenArticle = onOpenArticle,
                onOpenCalculator = onOpenCalculator,
                modifier = Modifier.weight(1f),
            )
        }
    }
}



@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReferenceRows(
    table: ReferenceTable,
    rows: List<ReferenceRow>,
    related: List<Article>,
    sortKey: String?,
    ascending: Boolean,
    horizontal: androidx.compose.foundation.ScrollState,
    onSort: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenCalculator: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = stringResource(R.string.reference_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.reference_empty_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearQuery) {
                    Text(stringResource(R.string.knowledge_search_clear))
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            val fits = table.columns.size <= 3
            val firstFraction = if (table.columns.size == 2) 0.40f else 0.30f
            val firstWidth = if (fits) maxWidth * firstFraction else 128.dp
            val otherWidth = if (fits && table.columns.size > 1) {
                (maxWidth - firstWidth) / (table.columns.size - 1).toFloat()
            } else {
                120.dp
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                ),
            ) {
                Column {
                    ReferenceHeader(
                        table = table,
                        firstWidth = firstWidth,
                        otherWidth = otherWidth,
                        sortKey = sortKey,
                        ascending = ascending,
                        horizontalScroll = horizontal,
                        onSort = onSort,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                    rows.forEachIndexed { index, row ->
                        ReferenceRowView(
                            table = table,
                            row = row,
                            firstWidth = firstWidth,
                            otherWidth = otherWidth,
                            last = index == rows.lastIndex,
                            horizontalScroll = horizontal,
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.reference_sort_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
        )
        ReferenceFooter(
            table = table,
            related = related,
            onOpenArticle = onOpenArticle,
            onOpenCalculator = onOpenCalculator,
        )
    }
}




@Composable
private fun ReferenceHeader(
    table: ReferenceTable,
    firstWidth: Dp,
    otherWidth: Dp,
    sortKey: String?,
    ascending: Boolean,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    onSort: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val first = table.columns.firstOrNull()
        if (first != null) {
            HeaderCell(
                column = first,
                width = firstWidth,
                sortKey = sortKey,
                ascending = ascending,
                onSort = onSort,
            )
        }
        Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
            table.columns.drop(1).forEach { column ->
                HeaderCell(
                    column = column,
                    width = otherWidth,
                    sortKey = sortKey,
                    ascending = ascending,
                    onSort = onSort,
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(
    column: ReferenceColumn,
    width: Dp,
    sortKey: String?,
    ascending: Boolean,
    onSort: (String) -> Unit,
) {
    val indicator = when {
        sortKey != column.key -> "↕"
        ascending -> "↑"
        else -> "↓"
    }
    val title = if (column.unit.isNullOrBlank()) {
        "${column.title} $indicator"
    } else {
        "${column.title}, ${column.unit} $indicator"
    }
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = 50.dp)
            .clickable { onSort(column.key) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = if (column.numeric) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (column.numeric) {
                androidx.compose.ui.text.style.TextAlign.End
            } else {
                androidx.compose.ui.text.style.TextAlign.Start
            },
        )
    }
}

@Composable
private fun ReferenceRowView(
    table: ReferenceTable,
    row: ReferenceRow,
    firstWidth: Dp,
    otherWidth: Dp,
    last: Boolean,
    horizontalScroll: androidx.compose.foundation.ScrollState,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            table.columns.firstOrNull()?.let { column ->
                Box(
                    modifier = Modifier
                        .width(firstWidth)
                        .heightIn(min = 46.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = row[column.key],
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                table.columns.drop(1).forEach { column ->
                    Box(


                        modifier = Modifier
                            .width(otherWidth)
                            .heightIn(min = 46.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = if (column.numeric) {
                            Alignment.CenterEnd
                        } else {
                            Alignment.CenterStart
                        },
                    ) {
                        Text(
                            text = row[column.key],
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = if (column.numeric) {
                                androidx.compose.ui.text.style.TextAlign.End
                            } else {
                                androidx.compose.ui.text.style.TextAlign.Start
                            },
                        )
                    }
                }
            }
        }
        if (!last) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ReferenceFooter(
    table: ReferenceTable,
    related: List<Article>,
    onOpenArticle: (String) -> Unit,
    onOpenCalculator: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (table.note != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = table.note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        table.calcId?.let { calcId ->
            Button(
                onClick = { onOpenCalculator(calcId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.reference_open_calculator))
            }
        }
        table.sourceLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = stringResource(R.string.reference_source, label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        table.updated?.takeIf { it.isNotBlank() }?.let { updated ->
            Text(
                text = stringResource(R.string.reference_updated, updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (related.isNotEmpty()) {
            Text(
                text = stringResource(R.string.reference_related_articles),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            related.forEach { article ->
                Surface(
                    onClick = { onOpenArticle(article.id) },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingReferenceScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.reference_title),
                onBack = onBack,
                backLabel = stringResource(R.string.reference_back),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.reference_not_found),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.reference_not_found_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


private fun rowKey(table: ReferenceTable, row: ReferenceRow): String =
    table.columns.joinToString("|") { row[it.key] }.take(300)
