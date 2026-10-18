package ru.distillarium.knowledge

import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser

/** Единый парсер Markdown для статьи, оглавления и поиска по тексту. */
object MarkdownParser {
    private val extensions = listOf(TablesExtension.create())
    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()
    private val wikiLink = Regex("""\[\[([^\]|]+)(?:\|([^\]]*))?]]""")

    fun parse(markdown: String): List<Node> = children(parser.parse(prepare(markdown)))

    fun headings(markdown: String): List<ArticleHeading> {
        val result = mutableListOf<ArticleHeading>()
        walk(parser.parse(prepare(markdown))) { node ->
            if (node is Heading) {
                val title = nodeText(node).trim()
                if (title.isNotEmpty()) {
                    result += ArticleHeading(title, node.level)
                }
            }
        }
        return result
    }

    fun plainText(markdown: String): String = nodeText(parser.parse(prepare(markdown)))
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    /** `[[id|подпись]]` → обычная Markdown-ссылка со схемой `wiki:`. */
    fun prepare(markdown: String): String = markdown.replace(wikiLink) { match ->
        val target = match.groupValues[1].trim()
        val label = match.groupValues[2].trim().ifEmpty { target }
        "[" + label.replace("]", "\\]") + "](wiki:" + target + ")"
    }

    fun nodeText(node: Node): String = buildString {
        appendText(node, this)
    }

    fun children(node: Node): List<Node> {
        val result = mutableListOf<Node>()
        var child = node.firstChild
        while (child != null) {
            result += child
            child = child.next
        }
        return result
    }

    private fun walk(node: Node, action: (Node) -> Unit) {
        var child = node.firstChild
        while (child != null) {
            action(child)
            walk(child, action)
            child = child.next
        }
    }

    private fun appendText(node: Node, output: StringBuilder) {
        when (node) {
            is Text -> output.append(node.literal)
            is Code -> output.append(node.literal)
            is SoftLineBreak, is HardLineBreak -> output.append('\n')
            is Paragraph -> {
                appendChildren(node, output)
                output.append("\n\n")
            }
            is Heading -> {
                appendChildren(node, output)
                output.append("\n\n")
            }
            is ListItem -> {
                appendChildren(node, output)
                output.append('\n')
            }
            is Image -> output.append(node.title ?: node.destination)
            is Link -> appendChildren(node, output)
            else -> appendChildren(node, output)
        }
    }

    private fun appendChildren(node: Node, output: StringBuilder) {
        var child = node.firstChild
        while (child != null) {
            appendText(child, output)
            child = child.next
        }
    }
}
