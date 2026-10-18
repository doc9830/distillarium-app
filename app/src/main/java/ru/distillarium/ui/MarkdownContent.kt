package ru.distillarium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.ThematicBreak

/** Один блок Markdown: заголовок, абзац, список, таблица или код. */
@Composable
fun MarkdownBlock(
    node: Node,
    textScale: Float,
    onWikiLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is Heading -> HeadingBlock(node, textScale, onWikiLink, modifier)
        is Paragraph -> ParagraphBlock(node, textScale, onWikiLink, modifier)
        is BulletList -> MarkdownList(node, ordered = false, textScale, onWikiLink, modifier)
        is OrderedList -> MarkdownList(node, ordered = true, textScale, onWikiLink, modifier)
        is BlockQuote -> QuoteBlock(node, textScale, onWikiLink, modifier)
        is FencedCodeBlock -> CodeBlock(node.literal.orEmpty(), modifier)
        is IndentedCodeBlock -> CodeBlock(node.literal.orEmpty(), modifier)
        is ThematicBreak -> HorizontalDivider(
            modifier = modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        is TableBlock -> MarkdownTable(node, textScale, onWikiLink, modifier)
        is HtmlBlock -> Unit
        else -> {
            var child = node.firstChild
            while (child != null) {
                MarkdownBlock(child, textScale, onWikiLink, modifier)
                child = child.next
            }
        }
    }
}

@Composable
private fun HeadingBlock(
    node: Heading,
    textScale: Float,
    onWikiLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = when (node.level) {
        1 -> 26f
        2 -> 22f
        3 -> 19f
        4 -> 17.5f
        else -> 17f
    }.sp * textScale
    Text(
        text = inlineText(node, onWikiLink),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 6.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = size,
            lineHeight = size * 1.3f,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
private fun ParagraphBlock(
    node: Paragraph,
    textScale: Float,
    onWikiLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp * textScale,
        lineHeight = 26.sp * textScale,
    )
    SelectionContainer {
        Text(
            text = inlineText(node, onWikiLink),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            style = style,
        )
    }
}


@Composable
private fun MarkdownList(
    node: Node,
    ordered: Boolean,
    textScale: Float,
    onWikiLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = (node as? OrderedList)?.startNumber ?: 1
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        var index = start
        var child = node.firstChild
        while (child != null) {
            if (child is ListItem) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (ordered) "$index." else "•",
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp * textScale,
                            lineHeight = 26.sp * textScale,
                        ),
                        fontWeight = if (ordered) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        var itemChild = child.firstChild
                        while (itemChild != null) {
                            if (itemChild is Paragraph) {
                                Text(
                                    text = inlineText(itemChild, onWikiLink),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 17.sp * textScale,
                                        lineHeight = 26.sp * textScale,
                                    ),
                                )
                            } else {
                                MarkdownBlock(itemChild, textScale, onWikiLink)
                            }
                            itemChild = itemChild.next
                        }
                    }
                }
                index++
            }
            child = child.next
        }
    }
}

@Composable
private fun QuoteBlock(
    node: BlockQuote,
    textScale: Float,
    onWikiLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            var child = node.firstChild
            while (child != null) {
                MarkdownBlock(child, textScale, onWikiLink)
                child = child.next
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = code.trimEnd(),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}


@Composable
private fun MarkdownTable(
    table: TableBlock,
    textScale: Float,
    onWikiLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(table) { tableRows(table) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        rows.forEach { row ->
            Row {
                row.cells.forEachIndexed { index, cell ->
                    Box(
                        modifier = Modifier
                            .width(if (index == 0) 160.dp else 190.dp)
                            .background(
                                if (row.header) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                },
                            )
                            .padding(10.dp),
                    ) {
                        Text(
                            text = inlineText(cell, onWikiLink),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp * textScale,
                                lineHeight = 22.sp * textScale,
                                fontWeight = if (row.header) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            ),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private data class TableRowView(
    val cells: List<TableCell>,
    val header: Boolean,
)

private fun tableRows(table: TableBlock): List<TableRowView> {
    val result = mutableListOf<TableRowView>()
    var section = table.firstChild
    while (section != null) {
        var row = section.firstChild
        while (row != null) {
            if (row is TableRow) {
                val cells = buildList {
                    var cell = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) add(cell)
                        cell = cell.next
                    }
                }
                result += TableRowView(cells, header = section is TableHead)
            }
            row = row.next
        }
        section = section.next
    }
    return result
}


@Composable
private fun inlineText(node: Node, onWikiLink: (String) -> Unit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val uriHandler = LocalUriHandler.current
    val currentWikiLink by rememberUpdatedState(onWikiLink)
    return remember(node, linkColor, codeBackground, uriHandler) {
        buildAnnotatedString {
            appendInlineChildren(this, node, linkColor, codeBackground, currentWikiLink, uriHandler)
        }
    }
}

private fun appendInlineChildren(
    builder: AnnotatedString.Builder,
    node: Node,
    linkColor: Color,
    codeBackground: Color,
    onWikiLink: (String) -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    var child = node.firstChild
    while (child != null) {
        appendInline(builder, child, linkColor, codeBackground, onWikiLink, uriHandler)
        child = child.next
    }
}

private fun appendInline(
    builder: AnnotatedString.Builder,
    node: Node,
    linkColor: Color,
    codeBackground: Color,
    onWikiLink: (String) -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    when (node) {
        is MarkdownText -> builder.append(node.literal)
        is SoftLineBreak -> builder.append(' ')
        is HardLineBreak -> builder.append('\n')
        is Emphasis -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            appendInlineChildren(this, node, linkColor, codeBackground, onWikiLink, uriHandler)
        }
        is StrongEmphasis -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendInlineChildren(this, node, linkColor, codeBackground, onWikiLink, uriHandler)
        }
        is Code -> builder.withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = codeBackground,
            ),
        ) {
            append(node.literal)
        }
        is Image -> {
            val label = node.title ?: node.destination
            builder.append("[изображение: $label]")
        }
        is Link -> appendLink(builder, node, linkColor, codeBackground, onWikiLink, uriHandler)
        else -> appendInlineChildren(
            builder,
            node,
            linkColor,
            codeBackground,
            onWikiLink,
            uriHandler,
        )
    }
}

private fun appendLink(
    builder: AnnotatedString.Builder,
    node: Link,
    linkColor: Color,
    codeBackground: Color,
    onWikiLink: (String) -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val destination = node.destination
    val styles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        ),
    )
    val annotation = when {
        destination.startsWith("wiki:") -> {
            val target = destination.removePrefix("wiki:")
            LinkAnnotation.Clickable(
                tag = "wiki:$target",
                styles = styles,
                linkInteractionListener = LinkInteractionListener { onWikiLink(target) },
            )
        }
        destination.startsWith("http://") || destination.startsWith("https://") -> {
            LinkAnnotation.Url(
                url = destination,
                styles = styles,
                linkInteractionListener = LinkInteractionListener { uriHandler.openUri(destination) },
            )
        }
        else -> null
    }
    if (annotation == null) {
        appendInlineChildren(builder, node, linkColor, codeBackground, onWikiLink, uriHandler)
    } else {
        builder.withLink(annotation) {
            appendInlineChildren(this, node, linkColor, codeBackground, onWikiLink, uriHandler)
        }
    }
}
