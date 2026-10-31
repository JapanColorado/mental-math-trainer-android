package dev.russell.mmtrainer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CARD_SHAPE = RoundedCornerShape(16.dp)
private val BUTTON_SHAPE = RoundedCornerShape(14.dp)

/** A rounded surface, optionally with a hairline border. */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    border: Color? = null,
    color: Color = Palette.SURFACE,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CARD_SHAPE)
            .background(color)
            .then(if (border != null) Modifier.border(1.dp, border, CARD_SHAPE) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** A label over a big number, with an optional short note under it. */
@Composable
fun StatCard(
    title: String, value: String, note: String = "", modifier: Modifier = Modifier,
    valueColor: Color = Palette.TEXT, noteColor: Color = Palette.MUTED, onClick: (() -> Unit)? = null,
) {
    CardBox(modifier, onClick = onClick) {
        Text(title, style = Type.label)
        Text(value, style = Type.bigStat, color = valueColor, maxLines = 1)
        if (note.isNotEmpty()) Text(note, style = Type.label.copy(fontWeight = FontWeight.Normal), color = noteColor)
    }
}

/** Two stat cards per row. */
@Composable
fun StatGrid(cards: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (pair in cards.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (c in pair) c(Modifier.weight(1f))
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        enabled = enabled,
        shape = BUTTON_SHAPE,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.ACCENT, contentColor = Palette.ON_ACCENT),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    text: String, modifier: Modifier = Modifier, color: Color = Palette.TEXT, border: Color = Palette.BORDER,
    icon: ImageVector? = null, onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = BUTTON_SHAPE,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = color),
        border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small soft-tinted pill. */
@Composable
fun TagChip(text: String, color: Color = Palette.ACCENT) {
    Text(
        text,
        style = Type.tag,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.TEXT)
            }
        }
        Text(title, style = Type.h1, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() })
        actions()
    }
}

/** A section title inside a scrolling page. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Palette.TEXT) {
    Text(text, style = Type.h2, color = color, modifier = modifier.semantics { heading() })
}

/** Rows on one rounded surface, split by hairlines. */
@Composable
fun ListGroup(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(CARD_SHAPE).background(Palette.SURFACE)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = Palette.BORDER.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp))
            row()
        }
    }
}

/** A progress bar that glides to its value. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, animated: Boolean = true) {
    val target = fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, tween(if (animated) 450 else 0), label = "bar")
    LinearProgressIndicator(
        progress = { shown },
        modifier = modifier.height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = Palette.SURFACE_2,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** A number that counts up to [value] when it first appears. */
@Composable
fun CountUpText(value: Double, format: (Double) -> String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(value) { shown.animateTo(value.toFloat(), tween(650, easing = FastOutSlowInEasing)) }
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = format(value) }) {
        Text(format(shown.value.toDouble()), style = style, color = color,
            modifier = Modifier.clearAndSetSemantics {})
    }
}

/** A ring that sweeps to [fraction], with [center] inside it. */
@Composable
fun ProgressRing(fraction: Float, color: Color, size: Dp, stroke: Dp = 10.dp, center: @Composable () -> Unit) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(fraction) { sweep.animateTo(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing)) }
    val track = Palette.SURFACE_2
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            drawArc(color, -90f, 360f * sweep.value, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        center()
    }
}

/** A round tinted icon, used to lead list rows and tiles. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = Palette.ACCENT, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** One-of-a-few choice shown as joined buttons. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    // Equal heights, for when large text wraps one label onto two lines.
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                modifier = Modifier.fillMaxHeight(),
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                label = { Text(o, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) },
            )
        }
    }
}

/** A button showing the current choice, opening a menu of the others. */
@Composable
fun Dropdown(options: List<String>, selected: Int, modifier: Modifier = Modifier, label: String = "", onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = BUTTON_SHAPE,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Palette.TEXT),
            border = BorderStroke(1.dp, Palette.BORDER),
            contentPadding = PaddingValues(start = 14.dp, end = 8.dp),
        ) {
            Text(options.getOrNull(selected).orEmpty(), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { if (label.isNotEmpty()) contentDescription = "$label: ${options.getOrNull(selected).orEmpty()}" })
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Palette.MUTED)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SURFACE_2) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

/** "<span style='color:…'>text</span>" for RichText strings built in the UI. */
fun colored(text: String, color: Color): String {
    val rgb = color.toArgb() and 0xFFFFFF
    return "<span style=\"color:#%06x\">$text</span>".format(rgb)
}

fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** "1 fact", "2 facts". */
fun count(n: Int, noun: String): String = "$n $noun" + if (n == 1) "" else "s"
