package ru.distillarium.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.commonmark.node.Heading
import org.commonmark.node.Node
import ru.distillarium.R
import ru.distillarium.knowledge.Article
import ru.distillarium.knowledge.ArticleHeading
import ru.distillarium.knowledge.ArticleSegment
import ru.distillarium.knowledge.ArticleTab
import ru.distillarium.knowledge.MarkdownParser
import ru.distillarium.knowledge.WikiLabels
import ru.distillarium.knowledge.WikiLibrary
import ru.distillarium.knowledge.WikiLinks

private sealed interface ArticleItem {
    data class Breadcrumb(val text: String) : ArticleItem
    data class Title(val text: String) : ArticleItem
    data class Meta(val article: Article) : ArticleItem
    data class ShortAnswer(val text: String) : ArticleItem
    data class Body(val node: Node) : ArticleItem
    data class Tabs(val tabs: List<ArticleTab>) : ArticleItem
    data class Backlinks(val articles: List<Article>) : ArticleItem
    data class Sources(val sources: List<String>) : ArticleItem
}

private data class TocEntry(
    val title: String,
    val level: Int,
    val itemIndex: Int,
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    article: Article,
    library: WikiLibrary,
    selectedEquipment: String?,
    fontScale: Float,
    initialHeading: String? = null,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenReference: (String) -> Unit,
    onOpenCalculator: (String) -> Unit,
    onEquipmentSelected: (String) -> Unit,
    onFontScaleChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showToc by rememberSaveable { mutableStateOf(false) }
    val shareChooser = stringResource(R.string.article_share_chooser)
    val backlinks = remember(article.id) { library.backlinks(article.id) }
    val openResolved: (String) -> Unit = { target ->
        when {
            target.startsWith("ref:") -> onOpenReference(WikiLinks.referenceTarget(target))
            target.startsWith("calc:") -> onOpenCalculator(target.removePrefix("calc:").trim())
            else -> library.resolve(target)?.id?.let(onOpenArticle)
        }
    }
    val items = remember(article.id, backlinks) {
        buildArticleItems(article, library, backlinks)
    }
    val toc = remember(items) {
        items.mapIndexedNotNull { index, item ->
            val heading = (item as? ArticleItem.Body)?.node as? Heading
                ?: return@mapIndexedNotNull null
            TocEntry(
                title = MarkdownParser.nodeText(heading).trim(),
                level = heading.level,
                itemIndex = index,
            )
        }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(article.id, initialHeading, toc) {
        listState.scrollToItem(0)
        val heading = initialHeading ?: return@LaunchedEffect
        toc.firstOrNull { entry -> entry.title.contains(heading, ignoreCase = true) }
            ?.let { entry -> listState.scrollToItem(entry.itemIndex) }
    }

    val showUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 640
        }
    }
    val showTopTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 180
        }
    }

    Scaffold(
        topBar = {
            DistillariumTopBar(
                title = if (showTopTitle) article.title else "",
                onBack = onBack,
                backLabel = stringResource(R.string.article_back),
                actions = {
                    IconButton(onClick = { showToc = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.article_toc),
                        )
                    }
                    IconButton(
                        onClick = {
                            val text = buildString {
                                appendLine(article.title)
                                appendLine()
                                append(MarkdownParser.plainText(article.body))
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, article.title)
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, shareChooser))
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.article_share),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (showUp) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.article_up),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 96.dp,
            ),
        ) {
            itemsIndexed(items, key = { index, _ -> index }) { _, item ->
                when (item) {
                    is ArticleItem.Breadcrumb -> Breadcrumb(item.text)
                    is ArticleItem.Title -> ArticleTitle(item.text)
                    is ArticleItem.Meta -> ArticleMeta(item.article)
                    is ArticleItem.ShortAnswer -> ShortAnswer(item.text)
                    is ArticleItem.Body -> MarkdownBlock(
                        node = item.node,
                        textScale = fontScale,
                        onWikiLink = openResolved,
                    )
                    is ArticleItem.Tabs -> EquipmentTabs(
                        tabs = item.tabs,
                        selected = selectedEquipment,
                        textScale = fontScale,
                        onSelected = onEquipmentSelected,
                        onWikiLink = openResolved,
                    )
                    is ArticleItem.Backlinks -> Backlinks(
                        articles = item.articles,
                        library = library,
                        onOpenArticle = onOpenArticle,
                    )
                    is ArticleItem.Sources -> Sources(item.sources)
                }
            }
        }
    }

    if (showToc) {
        ModalBottomSheet(onDismissRequest = { showToc = false }) {
            TocSheet(
                entries = toc,
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
                onSelect = { entry ->
                    showToc = false
                    scope.launch { listState.animateScrollToItem(entry.itemIndex) }
                },
            )
        }
    }
}

private fun buildArticleItems(
    article: Article,
    library: WikiLibrary,
    backlinks: List<Article>,
): List<ArticleItem> {
    val items = mutableListOf<ArticleItem>()
    items += ArticleItem.Breadcrumb(library.breadcrumb(article.id))
    items += ArticleItem.Title(article.title)
    items += ArticleItem.Meta(article)
    val bodyStart = MarkdownParser.plainText(article.body)
        .replace(Regex("\\s+"), " ")
        .take(500)
    val short = article.short.replace(Regex("\\s+"), " ").trim()
    if (short.isNotBlank() && !bodyStart.contains(short.take(60))) {
        items += ArticleItem.ShortAnswer(short)
    }
    for (segment in article.segments) {
        when (segment) {
            is ArticleSegment.Markdown -> MarkdownParser.parse(segment.text)
                .forEach { node -> items += ArticleItem.Body(node) }
            is ArticleSegment.Tabs -> items += ArticleItem.Tabs(segment.tabs)
        }
    }
    if (backlinks.isNotEmpty()) items += ArticleItem.Backlinks(backlinks)
    if (article.sources.isNotEmpty()) items += ArticleItem.Sources(article.sources)
    return items
}

@Composable
private fun Breadcrumb(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ArticleTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ArticleMeta(article: Article) {
    val readingMinutes = remember(article.id) {
        val words = MarkdownParser.plainText(article.body)
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }
        (words / 180).coerceAtLeast(1)
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WikiLabels.category(article.category)?.let { category ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = stringResource(R.string.article_reading_time, readingMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        article.updated?.let { updated ->
            Text(
                text = stringResource(R.string.article_updated, updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun ShortAnswer(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stringResource(R.string.article_short).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TocSheet(
    entries: List<TocEntry>,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onSelect: (TocEntry) -> Unit,
) {
    val smaller = stringResource(R.string.article_text_smaller)
    val larger = stringResource(R.string.article_text_larger)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.article_toc),
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.article_text_size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { onFontScaleChange(fontScale - 0.05f) },
                enabled = fontScale > MIN_FONT_SCALE,
                modifier = Modifier.semantics { contentDescription = smaller },
            ) {
                Text("A−")
            }
            Text(
                text = stringResource(
                    R.string.article_text_size_value,
                    (fontScale * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedButton(
                onClick = { onFontScaleChange(fontScale + 0.05f) },
                enabled = fontScale < MAX_FONT_SCALE,
                modifier = Modifier.semantics { contentDescription = larger },
            ) {
                Text("A+")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.article_toc_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(entries) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(entry) }
                            .padding(start = ((entry.level - 2).coerceAtLeast(0) * 12).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.title,
                            style = if (entry.level <= 2) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentTabs(
    tabs: List<ArticleTab>,
    selected: String?,
    textScale: Float,
    onSelected: (String) -> Unit,
    onWikiLink: (String) -> Unit,
) {
    if (tabs.isEmpty()) return
    val active = tabs.firstOrNull { it.title == selected } ?: tabs.first()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs, key = { it.title }) { tab ->
                FilterChip(
                    selected = tab.title == active.title,
                    onClick = { onSelected(tab.title) },
                    label = { Text(tab.title) },
                )
            }
        }
        val nodes = remember(active) { MarkdownParser.parse(active.markdown) }
        nodes.forEach { node ->
            MarkdownBlock(
                node = node,
                textScale = textScale,
                onWikiLink = onWikiLink,
            )
        }
    }
}

@Composable
private fun Backlinks(
    articles: List<Article>,
    library: WikiLibrary,
    onOpenArticle: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.article_backlinks),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        articles.forEach { article ->
            Surface(
                onClick = { onOpenArticle(article.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = library.breadcrumb(article.id),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Sources(sources: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.article_sources),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        sources.forEach { source ->
            Text(
                text = source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleLoadingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.article_title),
                onBack = onBack,
                backLabel = stringResource(R.string.article_back),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.article_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingArticleScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.article_not_found),
                onBack = onBack,
                backLabel = stringResource(R.string.article_back),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.article_not_found),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.article_not_found_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.article_back))
            }
        }
    }
}

private const val MIN_FONT_SCALE = 0.85f
private const val MAX_FONT_SCALE = 1.6f

