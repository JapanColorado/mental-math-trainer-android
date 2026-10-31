package dev.russell.mmtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/*
 * Renders the content's rich-text subset (see content/Base.kt): <p>, <h3>, <ul>/<li>, <table>,
 * <ex> (a worked example), <tip> (a usage note), <br>, and inline <b>, <i>, <sup>, <sub>, <span style="color:…">. ASCII whitespace collapses as
 * in HTML; thin spaces and other unicode spacing are kept.
 */

sealed interface RichBlock {
    data class Para(val text: AnnotatedString) : RichBlock
    data class Heading(val text: AnnotatedString) : RichBlock
    data class Bullet(val text: AnnotatedString) : RichBlock
    data class Example(val text: AnnotatedString) : RichBlock
    data class Tip(val text: AnnotatedString) : RichBlock
    data class Table(val rows: List<List<AnnotatedString>>) : RichBlock
}

private val TOKEN = Regex("""<(/?)([a-zA-Z0-9]+)([^>]*)>|([^<]+)""")
private val WS = Regex("[ \t\r\n]+")
private val COLOR = Regex("""color\s*:\s*(#[0-9a-fA-F]{6})""")

private fun decode(s: String) = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

fun parseRich(html: String): List<RichBlock> {
    val blocks = mutableListOf<RichBlock>()
    var b = AnnotatedString.Builder()
    var kind = "p"
    var rows: MutableList<MutableList<AnnotatedString>>? = null
    var inCell = false
    val open = mutableListOf<String>()  // inline tags currently pushed, to re-apply across block breaks

    fun startBlock() {
        b = AnnotatedString.Builder()
        for (tag in open) pushInline(b, tag)
    }

    fun take(): AnnotatedString? {
        val s = b.toAnnotatedString()
        val text = s.text
        val start = text.indexOfFirst { it != ' ' && it != '\n' }
        if (start < 0) return null
        val end = text.indexOfLast { it != ' ' && it != '\n' } + 1
        return s.subSequence(start, end)
    }

    fun flush() {
        val s = take()
        if (inCell) {
            rows?.lastOrNull()?.add(s ?: AnnotatedString(""))
        } else if (s != null) {
            blocks += when (kind) {
                "h3" -> RichBlock.Heading(s)
                "li" -> RichBlock.Bullet(s)
                "ex" -> RichBlock.Example(s)
                "tip" -> RichBlock.Tip(s)
                else -> RichBlock.Para(s)
            }
        }
        startBlock()
    }

    for (m in TOKEN.findAll(html)) {
        val text = m.groupValues[4]
        if (text.isNotEmpty()) {
            if (rows != null && !inCell) continue  // whitespace between table tags
            b.append(decode(text.replace(WS, " ")))
            continue
        }
        val closing = m.groupValues[1] == "/"
        val tag = m.groupValues[2].lowercase()
        val attrs = m.groupValues[3]
        when (tag) {
            "br" -> b.append("\n")
            "p", "h3", "li", "div", "ex", "tip" -> {
                flush()
                kind = if (closing) "p" else tag
            }
            "ul", "ol" -> { flush(); kind = "p" }
            "table" -> {
                flush()
                if (closing) {
                    rows?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }?.let { blocks += RichBlock.Table(it) }
                    rows = null
                } else {
                    rows = mutableListOf()
                }
            }
            "tr" -> if (!closing) rows?.add(mutableListOf())
            "td", "th" -> {
                if (closing) {
                    flush()
                    inCell = false
                } else {
                    startBlock()
                    inCell = true
                    if (tag == "th") pushInline(b, "b")
                }
            }
            "b", "strong", "i", "em", "sup", "sub" -> {
                if (closing) {
                    val i = open.lastIndexOf(tag)
                    if (i >= 0) {
                        open.removeAt(i)
                        runCatching { b.pop() }
                    }
                } else {
                    open += tag
                    pushInline(b, tag)
                }
            }
            "span", "font" -> {
                val color = COLOR.find(attrs)?.groupValues?.get(1)
                val key = "span:" + (color ?: "")
                if (closing) {
                    val i = open.indexOfLast { it.startsWith("span:") }
                    if (i >= 0) {
                        open.removeAt(i)
                        runCatching { b.pop() }
                    }
                } else {
                    open += key
                    pushInline(b, key)
                }
            }
        }
    }
    flush()
    return blocks
}

private fun pushInline(b: AnnotatedString.Builder, tag: String) {
    val style = when {
        tag == "b" || tag == "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
        tag == "i" || tag == "em" -> SpanStyle(fontStyle = FontStyle.Italic)
        tag == "sup" -> SpanStyle(baselineShift = BaselineShift(0.45f), fontSize = 0.68.em)
        tag == "sub" -> SpanStyle(baselineShift = BaselineShift(-0.2f), fontSize = 0.68.em)
        tag.startsWith("span:#") -> SpanStyle(color = Color(android.graphics.Color.parseColor(tag.removePrefix("span:"))))
        else -> SpanStyle()
    }
    b.pushStyle(style)
}

private fun RichBlock.plainText(): AnnotatedString = when (this) {
    is RichBlock.Para -> text
    is RichBlock.Heading -> text
    is RichBlock.Example -> text
    is RichBlock.Tip -> text
    is RichBlock.Bullet -> text
    is RichBlock.Table -> AnnotatedString("")
}

/** Inline-only rich text (prompts, answers, notes): everything joined into one Text. */
fun richInline(html: String): AnnotatedString = buildAnnotatedString {
    parseRich(html).forEachIndexed { i, block ->
        if (i > 0) append("\n")
        when (block) {
            is RichBlock.Para, is RichBlock.Heading, is RichBlock.Example, is RichBlock.Tip -> append(block.plainText())
            is RichBlock.Bullet -> { append("• "); append(block.text) }
            is RichBlock.Table -> block.rows.forEach { r -> r.forEachIndexed { j, c -> if (j > 0) append("   "); append(c) }; append("\n") }
        }
    }
}

@Composable
fun RichText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    textAlign: TextAlign? = null,
) {
    val blocks = remember(html) { parseRich(html) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        for (block in blocks) {
            when (block) {
                is RichBlock.Para -> Text(block.text, style = style, textAlign = textAlign)
                is RichBlock.Heading -> Text(block.text, style = Type.h2,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                is RichBlock.Example -> Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(10.dp)).background(Palette.SURFACE_2),
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(Palette.ACCENT))
                    Text(block.text, style = style.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                }
                is RichBlock.Tip -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = "Tip", tint = Palette.ACCENT,
                        modifier = Modifier.padding(top = 1.dp).size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(block.text, style = style.copy(color = Palette.MUTED))
                }
                is RichBlock.Bullet -> Row {
                    Text("•", style = style, color = Palette.ACCENT)
                    Spacer(Modifier.width(10.dp))
                    Text(block.text, style = style)
                }
                is RichBlock.Table -> Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    // column-major so cells line up; cells are single-line in practice
                    val cols = block.rows.maxOf { it.size }
                    for (c in 0 until cols) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (row in block.rows) {
                                Text(row.getOrElse(c) { AnnotatedString("") }, style = style.copy(fontSize = style.fontSize * 0.92f),
                                    softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
