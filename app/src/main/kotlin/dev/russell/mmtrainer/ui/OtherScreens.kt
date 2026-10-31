package dev.russell.mmtrainer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.russell.mmtrainer.BuildConfig
import dev.russell.mmtrainer.py.fmtF
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// ====================================================================== Progress

@Composable
fun ProgressScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val data = vm.progress
    Column(Modifier.fillMaxSize()) {
        TopBar("Progress")
        TabRow(selectedTabIndex = tab, containerColor = Palette.BG, contentColor = Palette.ACCENT) {
            listOf("Overview", "Skills", "Facts", "History").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) },
                    selectedContentColor = Palette.ACCENT, unselectedContentColor = Palette.MUTED)
            }
        }
        if (data == null) return@Column
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineChart("Benchmark score", "Same difficulty every time. Higher is better.", listOf("Score" to data.bench),
                        reference = 100.0 to "fluent", emptyText = "Take a benchmark to set your baseline",
                        format = { fmtF(it, 0) })
                    LineChart("Daily speed", "Median speed against target. 100% is on target.", listOf("Speed" to data.pace),
                        reference = 100.0 to "target", format = { fmtF(it, 0) + "%" })
                    LineChart("Daily accuracy", "", listOf("Accuracy" to data.accuracy), format = { fmtF(it, 0) + "%" })
                    LineChart("Minutes practised", "Per day", listOf("Minutes" to data.minutes), format = { fmtMinutes(it) })
                }
                1 -> SkillsTable(data.skills)
                2 -> DecksTable(data.decks)
                3 -> SkillHistory(vm, data.skills)
            }
        }
    }
}

private fun fmtMinutes(m: Double): String = if (m < 10) fmtF(m, 1) + " min" else fmtF(m, 0) + " min"

/** A small centered note for a tab with nothing to show yet. */
@Composable
private fun EmptyNote(text: String) {
    Text(text, style = Type.muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsTable(rows: List<SkillRow>) {
    if (rows.isEmpty()) return EmptyNote("Skills you unlock will show up here.")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.id }) { r ->
            val st = r.status
            CardBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = Type.title, modifier = Modifier.weight(1f))
                    if (st.mastered) TagChip("Mastered", Palette.GOOD) else if (st.due) TagChip("Due", Palette.WARN)
                }
                if (!st.mastered) Text(r.levelText, style = Type.muted)
                Bar(st.progress.toFloat(), if (st.mastered) Palette.GOOD else Palette.ACCENT, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                val fast = st.medianS != null && st.medianS!! <= st.targetS
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Accuracy " + (st.accuracy?.let { fmtF(it * 100, 0) + "%" } ?: "—"), style = Type.label.copy(fontWeight = FontWeight.Normal))
                    Text("Median " + fmtSecs(st.medianS), style = Type.label.copy(fontWeight = FontWeight.Normal), color = if (fast) Palette.GOOD else Palette.MUTED)
                    Text("Target " + fmtTarget(st.targetS), style = Type.label.copy(fontWeight = FontWeight.Normal))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecksTable(rows: List<DeckRow>) {
    if (rows.isEmpty()) return EmptyNote("Fact decks you unlock will show up here.")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("A fact is solid once its review gap reaches 3 weeks.", style = Type.muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        items(rows, key = { it.id }) { r ->
            val s = r.stats
            CardBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = Type.title, modifier = Modifier.weight(1f))
                    if (s.due > 0) TagChip("${s.due} due", Palette.WARN)
                }
                Text(r.track, style = Type.muted)
                Bar(if (s.total > 0) s.mature.toFloat() / s.total else 0f, Palette.GOOD, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Seen ${s.seen}/${s.total}", style = Type.label.copy(fontWeight = FontWeight.Normal))
                    Text("Learning ${s.learning}", style = Type.label.copy(fontWeight = FontWeight.Normal))
                    Text("Solid ${s.mature}", style = Type.label.copy(fontWeight = FontWeight.Normal), color = Palette.GOOD)
                }
            }
        }
    }
}

@Composable
private fun SkillHistory(vm: AppViewModel, skills: List<SkillRow>) {
    if (skills.isEmpty()) return EmptyNote("Once you've practised a skill, its history shows up here.")
    var selected by rememberSaveable { mutableStateOf(skills.first().id) }
    if (skills.none { it.id == selected }) selected = skills.first().id
    var series by remember { mutableStateOf<List<Pair<String, List<Pair<Double, Double>>>>>(emptyList()) }
    LaunchedEffect(selected) { series = vm.skillHistory(selected) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Dropdown(skills.map { it.name }, skills.indexOfFirst { it.id == selected }, label = "Skill") { selected = skills[it].id }
        LineChart("Median time", "Seconds per day, one line per level. Lower is faster.", series,
            emptyText = "No correct answers for this skill yet", format = { fmtF(it, 1) + " s" })
    }
}

private class ChartColors(val grid: Color, val muted: Color, val series: List<Color>)

/** Round axis ticks that span [lo]..[hi], with how many decimals they need. */
private fun niceTicks(lo: Double, hi: Double, targetCount: Int = 4): Pair<List<Double>, Int> {
    val range = (hi - lo).takeIf { it > 0 } ?: maxOf(1.0, Math.abs(hi))
    val rough = range / targetCount
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough * 0.999 }
    val start = floor(lo / step) * step
    val end = ceil(hi / step) * step
    val ticks = generateSequence(start) { it + step }.takeWhile { it <= end + step / 2 }.toList()
    val decimals = (0..4).first { d -> Math.abs(step * 10.0.pow(d) - Math.rint(step * 10.0.pow(d))) < 1e-9 }
    return ticks to decimals
}

/** A small line chart over time. */
@Composable
fun LineChart(
    title: String,
    subtitle: String,
    series: List<Pair<String, List<Pair<Double, Double>>>>,
    reference: Pair<Double, String>? = null,
    emptyText: String = "No data yet",
    format: (Double) -> String,
) {
    val measurer = rememberTextMeasurer()
    val shown = series.filter { it.second.isNotEmpty() }
    val colors = ChartColors(Palette.BORDER, Palette.MUTED, Palette.SERIES)
    val latest = shown.lastOrNull()?.second?.lastOrNull()?.second
    CardBox(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.title)
                if (subtitle.isNotEmpty()) Text(subtitle, style = Type.label.copy(fontWeight = FontWeight.Normal))
            }
            if (latest != null) {
                Spacer(Modifier.width(8.dp))
                Text(format(latest), style = Type.bigStat.copy(fontSize = 20.sp, lineHeight = 26.sp))
            }
        }
        if (shown.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                shown.forEachIndexed { i, (name, _) ->
                    Text("— $name", style = Type.label, color = Palette.SERIES[i % Palette.SERIES.size])
                }
            }
        }
        if (shown.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ShowChart, contentDescription = null, tint = Palette.MUTED, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(emptyText, style = Type.muted)
            }
            return@CardBox
        }
        val points = shown.sumOf { it.second.size }
        Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(170.dp).semantics {
            contentDescription = "$title chart with ${count(points, "point")}" + (latest?.let { ". Latest ${format(it)}." } ?: ".")
        }) {
            drawChart(shown, reference, measurer, colors)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChart(
    series: List<Pair<String, List<Pair<Double, Double>>>>,
    reference: Pair<Double, String>?,
    measurer: TextMeasurer,
    colors: ChartColors,
) {
    val labelStyle = TextStyle(color = colors.muted, fontSize = 11.sp)
    val left = 34.dp.toPx()
    val bottom = 22.dp.toPx()
    val top = 8.dp.toPx()
    val right = 8.dp.toPx()
    val plotW = size.width - left - right
    val plotH = size.height - top - bottom
    val pts = series.flatMap { it.second }
    var x0 = pts.minOf { it.first }
    var x1 = pts.maxOf { it.first }
    val singleDay = x1 - x0 < 86400
    if (singleDay) { x0 -= 43200; x1 += 43200 }
    val ys = pts.map { it.second } + listOfNotNull(reference?.first)
    val lowest = ys.min()
    val (ticks, decimals) = niceTicks(if (lowest >= 0) maxOf(0.0, lowest - (ys.max() - lowest) * 0.1) else lowest, ys.max())
    val y0 = ticks.first()
    val y1 = ticks.last().let { if (it > y0) it else y0 + 1 }
    fun px(x: Double, y: Double) = Offset(
        (left + (x - x0) / (x1 - x0) * plotW).toFloat(),
        (top + plotH - (y - y0) / (y1 - y0) * plotH).toFloat())

    for (y in ticks) {
        val p = px(x0, y)
        drawLine(colors.grid, Offset(left, p.y), Offset(left + plotW, p.y), 1f)
        val m = measurer.measure(fmtF(y, decimals), labelStyle)
        drawText(m, topLeft = Offset(left - m.size.width - 6.dp.toPx(), p.y - m.size.height / 2))
    }
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    if (singleDay) {
        val m = measurer.measure(fmt.format(Date((pts.first().first * 1000).toLong())), labelStyle)
        drawText(m, topLeft = Offset(left + plotW / 2 - m.size.width / 2, top + plotH + 4.dp.toPx()))
    } else {
        var last: String? = null
        for (i in 0..3) {
            val x = x0 + (x1 - x0) * i / 3
            val label = fmt.format(Date((x * 1000).toLong()))
            if (label == last) continue
            last = label
            val m = measurer.measure(label, labelStyle)
            val cx = px(x, y0).x - m.size.width / 2
            drawText(m, topLeft = Offset(cx.coerceIn(0f, size.width - m.size.width), top + plotH + 4.dp.toPx()))
        }
    }
    reference?.let { (ry, rl) ->
        val p = px(x0, ry)
        drawLine(colors.muted, Offset(left, p.y), Offset(left + plotW, p.y), 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        drawText(measurer.measure(rl, labelStyle), topLeft = Offset(left + 4.dp.toPx(), p.y - 16.dp.toPx()))
    }
    series.forEachIndexed { i, (_, s) ->
        val color: Color = colors.series[i % colors.series.size]
        if (s.size > 1) {
            val path = Path()
            s.forEachIndexed { j, (x, y) ->
                val p = px(x, y)
                if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            if (series.size == 1) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(px(s.last().first, y0).x, top + plotH)
                    lineTo(px(s.first().first, y0).x, top + plotH)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), top, top + plotH))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
        val radius = if (s.size > 30) 0f else 3.dp.toPx()
        for ((x, y) in s) drawCircle(color, if (s.size == 1) 4.dp.toPx() else radius, px(x, y))
    }
}

// ====================================================================== Library

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val tracks = vm.library
    Column(Modifier.fillMaxSize()) {
        TopBar("Library")
        if (tracks == null) return@Column
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "intro") {
                Text("Topics unlock through your sessions as the ones they build on get solid. You can also open and practise anything here.",
                    style = Type.muted)
            }
            for ((track, rows) in tracks) {
                item(key = "h:$track") {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(track, Modifier.weight(1f))
                        Text("${rows.count { it.unlocked }}/${rows.size}", style = Type.label)
                    }
                }
                item(key = "g:$track") {
                    ListGroup(rows.map { r -> { TopicListRow(r) { vm.openTopic(r.id) } } })
                }
            }
        }
    }
}

@Composable
private fun TopicListRow(r: TopicRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            r.mastered -> IconBadge(Icons.Filled.Check, Palette.GOOD)
            !r.unlocked -> IconBadge(Icons.Outlined.Lock, Palette.MUTED)
            r.isSkill -> IconBadge(Icons.Outlined.Bolt)
            else -> IconBadge(Icons.Outlined.Style)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, style = Type.title, color = if (r.unlocked) Palette.TEXT else Palette.MUTED)
            Text("${if (r.isSkill) "Skill" else "Facts"} · ${r.status}", style = Type.label.copy(fontWeight = FontWeight.Normal))
            if (r.unlocked && !r.mastered) {
                Bar(r.progress, Palette.ACCENT, Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {})
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.MUTED)
    }
}

@Composable
fun TopicScreen(itemId: String, vm: AppViewModel) {
    val t = vm.topic
    Column(Modifier.fillMaxSize()) {
        TopBar(t?.row?.name.orEmpty(), onBack = vm::openLibrary)
        if (t == null || t.row.id != itemId) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${t.summaryLine} · ${t.row.status}", style = Type.muted)
            t.prerequisites?.let { RichText(it, style = Type.muted) }
            if (t.lesson.title != t.row.name) SectionTitle(t.lesson.title, Modifier.padding(top = 4.dp))
            RichText(t.lesson.body)
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (t.levels.isNotEmpty()) {
                Dropdown(t.levels, t.currentLevel, label = "Level") { vm.setSkillLevel(itemId, it) }
            }
            Segmented(DURATIONS.map { it.first }, vm.practiceDuration) { vm.choosePracticeDuration(it) }
            PrimaryButton(if (t.row.unlocked) "Practise" else "Unlock and practise", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) {
                vm.practiseTopic(itemId, DURATIONS[vm.practiceDuration].second)
            }
        }
    }
}

// ====================================================================== Practice

@Composable
private fun PracticeTile(title: String, note: String, icon: ImageVector, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    CardBox(modifier, onClick = if (enabled) onClick else null) {
        IconBadge(icon, if (enabled) Palette.ACCENT else Palette.MUTED)
        Spacer(Modifier.height(6.dp))
        Text(title, style = Type.title, color = if (enabled) Palette.TEXT else Palette.MUTED)
        Text(note, style = Type.label.copy(fontWeight = FontWeight.Normal))
    }
}

@Composable
fun PracticeScreen(vm: AppViewModel) {
    val tracks = vm.practiceRows
    val excluded = vm.practiceExcluded
    val weak = vm.weakCount
    Column(Modifier.fillMaxSize()) {
        TopBar("Practice")
        if (tracks == null) return@Column
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "tiles") {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PracticeTile("Weak spots",
                        when {
                            weak == null -> ""
                            weak == 0 -> "Nothing stands out yet. Keep practising."
                            weak == 1 -> "1 thing to sharpen"
                            else -> "$weak things to sharpen"
                        },
                        Icons.Outlined.TrackChanges, Modifier.weight(1f).fillMaxHeight(), enabled = (weak ?: 0) > 0) { vm.startWeakSpots() }
                    PracticeTile("Benchmark", "32 questions, about 6 minutes", Icons.Outlined.Speed, Modifier.weight(1f).fillMaxHeight()) { vm.startBenchmark() }
                }
            }
            item(key = "mix") { SectionTitle("Custom mix", Modifier.padding(top = 12.dp)) }
            if (tracks.isEmpty()) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Nothing's unlocked yet. Do a session, or unlock a topic in the Library.", style = Type.muted)
                        SecondaryButton("Open the Library", onClick = vm::openLibrary)
                    }
                }
                return@LazyColumn
            }
            item(key = "mode") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Segmented(listOf("On screen", "Read aloud"), if (vm.practiceAudio) 1 else 0) { vm.setPracticeMode(it == 1) }
                    if (vm.practiceAudio) {
                        Text("You hear each problem and type the answer. The clock starts on the last word. " +
                            "Voice and speed follow your system's text-to-speech settings.", style = Type.muted)
                    }
                }
            }
            for ((track, rows) in tracks) {
                item(key = "t:$track") {
                    val ids = rows.map { it.id }
                    val n = ids.count { it !in excluded }
                    val state = when (n) { 0 -> ToggleableState.Off; ids.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
                    val toggle = { vm.choosePracticeExcluded(if (state == ToggleableState.On) excluded + ids else excluded - ids.toSet()) }
                    ListGroup(buildList {
                        add {
                            Row(Modifier.fillMaxWidth().triStateToggleable(state, role = Role.Checkbox, onClick = toggle).padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                TriStateCheckbox(state, onClick = null, modifier = Modifier.size(48.dp))
                                Text(track, style = Type.title.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                            }
                        }
                        for (r in rows) add {
                            val included = r.id !in excluded
                            Row(Modifier.fillMaxWidth().toggleable(included, role = Role.Checkbox, onValueChange = {
                                vm.choosePracticeExcluded(if (it) excluded - r.id else excluded + r.id)
                            }).padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(included, onCheckedChange = null, modifier = Modifier.size(48.dp))
                                Text(r.name, style = Type.body, modifier = Modifier.weight(1f))
                                Text(r.status, style = Type.label.copy(fontWeight = FontWeight.Normal))
                            }
                        }
                    })
                }
            }
        }
        if (tracks.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(DURATIONS.map { it.first }, vm.practiceDuration) { vm.choosePracticeDuration(it) }
                val ids = tracks.flatMap { it.second }.map { it.id }.filter { it !in excluded }
                PrimaryButton(if (ids.isEmpty()) "Choose a topic" else "Start with " + count(ids.size, "topic"), Modifier.fillMaxWidth(),
                    enabled = ids.isNotEmpty(), icon = Icons.Filled.PlayArrow) {
                    vm.startPractice(ids, DURATIONS[vm.practiceDuration].second, vm.practiceAudio)
                }
            }
        }
    }
}

// ====================================================================== Settings

/** "7:00 PM" or "19:00", following the device's clock format. */
private fun formatTime(context: android.content.Context, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    return DateFormat.getTimeFormat(context).format(cal.time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings ?: return
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    fun save(d: SettingsData) = vm.saveSettings(d)
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save(s.copy(reminderEnabled = true))
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = vm::goHome)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSection("Sessions")
            ListGroup(listOf(
                { Stepper("Session length", "", s.minutes, 3..60, " min") { save(s.copy(minutes = it)) } },
                { Stepper("New topics per session", "", s.newTopics, 0..5) { save(s.copy(newTopics = it)) } },
                { Stepper("Topics learning at once", "New topics wait until these settle.", s.learningLimit, 1..10) { save(s.copy(learningLimit = it)) } },
                { Stepper("New facts per session", "", s.newCards, 0..40) { save(s.copy(newCards = it)) } },
            ))
            SettingsSection("Answering")
            ListGroup(listOf(
                { Toggle("Submit automatically", "Exact answers go in once they're right, or once you've typed enough digits.", s.autoSubmit) { save(s.copy(autoSubmit = it)) } },
                { Toggle("Move on after a correct answer", "", s.autoAdvance) { save(s.copy(autoAdvance = it)) } },
                { Toggle("Target-time bar", "Shows the time left to beat the target.", s.showTargetBar) { save(s.copy(showTargetBar = it)) } },
                { Toggle("Vibrate on a wrong answer", "", s.haptics) { save(s.copy(haptics = it)) } },
            ))
            SettingsSection("Reminder")
            val systemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            ListGroup(buildList {
                add {
                    Toggle("Daily reminder", "Skipped on days you've already done a session.", s.reminderEnabled) { on ->
                        val needsAsk = on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsAsk) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else save(s.copy(reminderEnabled = on))
                    }
                }
                if (s.reminderEnabled) add {
                    Row(Modifier.fillMaxWidth().clickable { pickTime = true }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Time", style = Type.body, modifier = Modifier.weight(1f))
                        Text(formatTime(context, s.reminderMinute), style = Type.title, color = Palette.ACCENT)
                    }
                }
                if (s.reminderEnabled && !systemAllowed) add {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notifications are off for this app, so the reminder can't appear.", style = Type.muted, color = Palette.WARN)
                        SecondaryButton("Open notification settings") {
                            context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    }
                }
            })
            SettingsSection("Appearance")
            ListGroup(buildList {
                if (dynamicColorSupported) add {
                    Toggle("Wallpaper colors", "Use Material You colors from your wallpaper.", s.dynamicColor) { save(s.copy(dynamicColor = it)) }
                }
                add {
                    var scale by remember { mutableFloatStateOf(s.fontScale.toFloat()) }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row {
                            Text("Text size", style = Type.body, modifier = Modifier.weight(1f))
                            Text("${Math.round(scale * 100)}%", style = Type.title)
                        }
                        Slider(scale, onValueChange = { scale = it }, valueRange = 0.7f..1.6f, steps = 8,
                            modifier = Modifier.semantics { stateDescription = "${Math.round(scale * 100)} percent" },
                            onValueChangeFinished = { save(s.copy(fontScale = Math.round(scale * 10) / 10.0)) })
                    }
                }
            })
            SettingsSection("Your data")
            SecondaryButton("Reset all progress", Modifier.fillMaxWidth(), color = Palette.BAD, border = Palette.BAD.copy(alpha = 0.5f),
                icon = Icons.Outlined.DeleteOutline) { confirmReset = true }
            Text("Mental Math ${BuildConfig.VERSION_NAME}", style = Type.label.copy(fontWeight = FontWeight.Normal),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(s.reminderMinute / 60, s.reminderMinute % 60, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    save(s.copy(reminderMinute = state.hour * 60 + state.minute))
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            title = { Text("Reminder time", style = Type.h2) },
            text = { TimePicker(state) },
            containerColor = Palette.SURFACE,
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetProgress() }) { Text("Delete everything", color = Palette.BAD) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            title = { Text("Reset all progress?", style = Type.h2) },
            text = { Text("This deletes your progress, history and benchmarks, and can't be undone. Your settings stay.", style = Type.body) },
            containerColor = Palette.SURFACE,
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    SectionTitle(title, Modifier.padding(top = 12.dp, start = 4.dp), color = Palette.ACCENT)
}

@Composable
private fun SettingLabel(title: String, note: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = Type.body)
        if (note.isNotEmpty()) Text(note, style = Type.label.copy(fontWeight = FontWeight.Normal))
    }
}

@Composable
private fun Stepper(label: String, note: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SettingLabel(label, note, Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Less: $label")
        }
        Text("$value$suffix", style = Type.title.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center, modifier = Modifier.width(62.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "More: $label")
        }
    }
}

@Composable
private fun Toggle(label: String, note: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange).heightIn(min = 56.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingLabel(label, note, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(value, onCheckedChange = null)
    }
}
