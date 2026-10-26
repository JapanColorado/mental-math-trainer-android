package dev.russell.mmtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A rounded surface with a hairline border. */
@Composable
fun CardBox(modifier: Modifier = Modifier, border: Color = Palette.BORDER, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SURFACE)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun StatCard(title: String, value: String, note: String = "", modifier: Modifier = Modifier, valueColor: Color = Palette.TEXT) {
    CardBox(modifier) {
        Text(title, style = Type.muted)
        Text(value, style = Type.bigStat, color = valueColor)
        if (note.isNotEmpty()) RichText(note, style = Type.muted)
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
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.ACCENT, contentColor = Palette.ON_ACCENT),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Palette.SURFACE_2, contentColor = Palette.TEXT),
        border = androidx.compose.foundation.BorderStroke(1.dp, Palette.BORDER),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text, fontSize = 15.sp)
    }
}

@Composable
fun TagChip(text: String, color: Color = Palette.ACCENT) {
    Text(
        text,
        style = Type.tag,
        color = if (color == Palette.ACCENT) Palette.ON_ACCENT else Palette.ON_STATUS,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color)
            .padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.TEXT)
        }
        Text(title, style = Type.h1, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = Palette.SURFACE_2,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** "<span style='color:…'>text</span>" for RichText strings built in the UI. */
fun colored(text: String, color: Color): String {
    val rgb = color.toArgb() and 0xFFFFFF
    return "<span style=\"color:#%06x\">$text</span>".format(rgb)
}

fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
