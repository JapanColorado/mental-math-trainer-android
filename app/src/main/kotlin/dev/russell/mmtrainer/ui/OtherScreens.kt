package dev.russell.mmtrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.fmtG
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================== Progress

@Composable
fun ProgressScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val data = vm.progress
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TopBar("Progress", onBack = vm::goHome)
        TabRow(selectedTabIndex = tab, containerColor = Palette.BG, contentColor = Palette.TEXT) {
            listOf("Overview", "Skills", "Facts", "History").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) },
                    selectedContentColor = Palette.TEXT, unselectedContentColor = Palette.MUTED)
            }
        }
        if (data == null) return@Column
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)) {
            when (tab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineChart("Benchmark score (fixed difficulty — higher is better)", listOf("Score" to data.bench),
                        reference = 100.0 to "fluent target", emptyText = "Take a benchmark from the home screen")
                    LineChart("Daily pace (median speed vs. target, 100 = target)", listOf("Pace" to data.pace),
                        reference = 100.0 to "target")
                    LineChart("Daily accuracy (%)", listOf("Accuracy" to data.accuracy))
                    LineChart("Minutes practised per day", listOf("Minutes" to data.minutes))
                }
                1 -> SkillsTable(data.skills)
                2 -> DecksTable(data.decks)
                3 -> SkillHistory(vm, data.skills)
            }
        }
    }
}

@Composable
private fun SkillsTable(rows: List<SkillRow>) {
    if (rows.isEmpty()) return Text("No skills unlocked yet.", style = Type.muted)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.id }) { r ->
            val st = r.status
            CardBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = Type.h2.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
                    if (st.due) TagChip("due", Palette.WARN)
                }
                Text(r.levelText, style = Type.muted, color = if (st.mastered) Palette.GOOD else Palette.MUTED)
                Bar(st.progress.toFloat(), if (st.mastered) Palette.GOOD else Palette.ACCENT, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                val fast = st.medianS != null && st.medianS!! <= st.targetS
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${fmtF(st.progress * 100, 0)}%", style = Type.muted)
                    Text("acc " + (st.accuracy?.let { "${fmtF(it * 100, 0)}%" } ?: "—"), style = Type.muted)
                    Text("median " + fmtSecs(st.medianS), style = Type.muted, color = if (fast) Palette.GOOD else Palette.MUTED)
                    Text("target " + fmtTarget(st.targetS), style = Type.muted)
                }
            }
        }
    }
}

@Composable
private fun DecksTable(rows: List<DeckRow>) {
    if (rows.isEmpty()) return Text("No fact decks unlocked yet.", style = Type.muted)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { it.id }) { r ->
            val s = r.stats
            CardBox(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = Type.h2.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
                    if (s.due > 0) TagChip("${s.due} due", Palette.WARN)
                }
                Text(r.track, style = Type.muted)
                Bar(if (s.total > 0) s.mature.toFloat() / s.total else 0f, Palette.GOOD, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("seen ${s.seen}/${s.total}", style = Type.muted)
                    Text("learning ${s.learning}", style = Type.muted)
                    Text("solid (≥3 wk) ${s.mature}", style = Type.muted, color = Palette.GOOD)
                }
            }
        }
    }
}

@Composable
private fun SkillHistory(vm: AppViewModel, skills: List<SkillRow>) {
    if (skills.isEmpty()) return Text("No skills unlocked yet.", style = Type.muted)
    var selected by rememberSaveable { mutableStateOf(skills.first().id) }
    if (skills.none { it.id == selected }) selected = skills.first().id
    var series by remember { mutableStateOf<List<Pair<String, List<Pair<Double, Double>>>>>(emptyList()) }
    LaunchedEffect(selected) { series = vm.skillHistory(selected) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Dropdown(skills.map { it.name }, skills.indexOfFirst { it.id == selected }) { selected = skills[it].id }
        LineChart("Median time per day (seconds — lower is faster), one line per level", series)
    }
}

@Composable
fun Dropdown(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        SecondaryButton((options.getOrNull(selected) ?: "") + "  ▾", Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SURFACE_2) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

private class ChartColors(val grid: Color, val muted: Color, val series: List<Color>)

/** A small line chart over time, as in the desktop's ui/chart.py. */
@Composable
fun LineChart(
    title: String,
    series: List<Pair<String, List<Pair<Double, Double>>>>,
    reference: Pair<Double, String>? = null,
    emptyText: String = "No data yet",
) {
    val measurer = rememberTextMeasurer()
    val shown = series.filter { it.second.isNotEmpty() }
    val colors = ChartColors(Palette.BORDER, Palette.MUTED, Palette.SERIES)
    CardBox(Modifier.fillMaxWidth()) {
        Text(title, style = Type.muted.copy(color = Palette.TEXT))
        if (shown.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.forEachIndexed { i, (name, _) ->
                    Text("— $name", style = Type.muted, color = Palette.SERIES[i % Palette.SERIES.size])
                }
            }
        }
        if (shown.isEmpty()) {
            Text(emptyText, style = Type.muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp))
            return@CardBox
        }
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
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
    val left = 40.dp.toPx()
    val bottom = 22.dp.toPx()
    val top = 6.dp.toPx()
    val plotW = size.width - left - 4.dp.toPx()
    val plotH = size.height - top - bottom
    val pts = series.flatMap { it.second }
    var x0 = pts.minOf { it.first }
    var x1 = pts.maxOf { it.first }
    if (x1 - x0 < 86400) { x0 -= 43200; x1 += 43200 }
    val ys = pts.map { it.second } + listOfNotNull(reference?.first)
    var y0 = ys.min()
    var y1 = ys.max()
    val pad = ((y1 - y0) * 0.1).takeIf { it != 0.0 } ?: maxOf(1.0, Math.abs(y1) * 0.1)
    y0 = if (y0 >= 0) maxOf(0.0, y0 - pad) else y0 - pad
    y1 += pad
    fun px(x: Double, y: Double) = Offset(
        (left + (x - x0) / (x1 - x0) * plotW).toFloat(),
        (top + plotH - (y - y0) / (y1 - y0) * plotH).toFloat())

    for (i in 0..4) {
        val y = y0 + (y1 - y0) * i / 4
        val p = px(x0, y)
        drawLine(colors.grid, Offset(left, p.y), Offset(left + plotW, p.y), 1f)
        val text = if (y1 - y0 >= 8) fmtF(y, 0) else fmtG(y, 3)
        val m = measurer.measure(text, labelStyle)
        drawText(m, topLeft = Offset(left - m.size.width - 6.dp.toPx(), p.y - m.size.height / 2))
    }
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
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
    reference?.let { (ry, rl) ->
        val p = px(x0, ry)
        drawLine(colors.muted, Offset(left, p.y), Offset(left + plotW, p.y), 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        drawText(measurer.measure(rl, labelStyle), topLeft = Offset(left + 4.dp.toPx(), p.y - 16.dp.toPx()))
    }
    series.forEachIndexed { i, (_, s) ->
        val color: Color = colors.series[i % colors.series.size]
        val path = Path()
        s.forEachIndexed { j, (x, y) ->
            val p = px(x, y)
            if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        for ((x, y) in s) drawCircle(color, 3.dp.toPx(), px(x, y))
    }
}

// ====================================================================== Library

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val tracks = vm.library
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TopBar("Library", onBack = vm::goHome)
        Text("Every topic with its lesson. Topics unlock automatically through daily sessions once the topics " +
            "they build on are solid; you can also unlock and practise anything here.", style = Type.muted, modifier = Modifier.padding(horizontal = 8.dp))
        if (tracks == null) return@Column
        LazyColumn(Modifier.padding(top = 8.dp)) {
            for ((track, rows) in tracks) {
                item(key = "h:$track") {
                    Text(track, style = Type.h2, color = Palette.ACCENT, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp))
                }
                items(rows, key = { it.id }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openTopic(r.id) }.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = Type.body, color = if (r.unlocked) Palette.TEXT else Palette.MUTED)
                            Text(if (r.isSkill) "Skill" else "Facts", style = Type.muted)
                        }
                        Text(r.status, style = Type.muted, color = if (r.status == "Mastered") Palette.GOOD else Palette.MUTED)
                    }
                    HorizontalDivider(color = Palette.BORDER)
                }
            }
        }
    }
}

@Composable
fun TopicScreen(itemId: String, vm: AppViewModel) {
    val t = vm.topic
    var duration by rememberSaveable { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TopBar(t?.row?.name ?: "", onBack = vm::openLibrary)
        if (t == null || t.row.id != itemId) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${t.summaryLine} · ${t.row.status}", style = Type.muted)
            t.prerequisites?.let { RichText(it, style = Type.muted) }
            CardBox(Modifier.fillMaxWidth()) {
                Text(t.lesson.title, style = Type.h2)
                Spacer(Modifier.height(4.dp))
                RichText(t.lesson.body)
            }
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.levels.isNotEmpty()) {
                Text("Level", style = Type.muted)
                Dropdown(t.levels, t.currentLevel) { vm.setSkillLevel(itemId, it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dropdown(DURATIONS.map { it.first }, duration, Modifier.weight(1f)) { duration = it }
                PrimaryButton(if (t.row.unlocked) "Practise" else "Unlock & practise", Modifier.weight(1f)) {
                    vm.practiseTopic(itemId, DURATIONS[duration].second)
                }
            }
        }
    }
}

// ====================================================================== Free practice

@Composable
fun PracticeScreen(vm: AppViewModel) {
    val tracks = vm.practiceRows
    var duration by rememberSaveable { mutableIntStateOf(1) }
    val unchecked = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TopBar("Free practice", onBack = vm::goHome)
        Text("Choose topics to mix. Fact decks review due/new facts first, then random ones.",
            style = Type.muted, modifier = Modifier.padding(horizontal = 8.dp))
        if (tracks == null) return@Column
        if (tracks.isEmpty()) {
            Text("Nothing unlocked yet — do a daily session or unlock topics in the Library.",
                style = Type.body, modifier = Modifier.padding(16.dp))
            return@Column
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            for ((track, rows) in tracks) {
                item(key = "h:$track") {
                    val ids = rows.map { it.id }
                    val n = ids.count { it !in unchecked }
                    val state = when (n) { 0 -> ToggleableState.Off; ids.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TriStateCheckbox(state, onClick = {
                            if (state == ToggleableState.On) unchecked.addAll(ids.filter { it !in unchecked }) else unchecked.removeAll(ids)
                        })
                        Text(track, style = Type.h2, color = Palette.ACCENT)
                    }
                }
                items(rows, key = { it.id }) { r ->
                    Row(Modifier.fillMaxWidth().clickable {
                        if (r.id in unchecked) unchecked.remove(r.id) else unchecked.add(r.id)
                    }.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(r.id !in unchecked, onCheckedChange = {
                            if (it) unchecked.remove(r.id) else unchecked.add(r.id)
                        })
                        Text(r.name, style = Type.body, modifier = Modifier.weight(1f))
                        Text(r.status, style = Type.muted, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(DURATIONS.map { it.first }, duration, Modifier.weight(1f)) { duration = it }
            val ids = tracks.flatMap { it.second }.map { it.id }.filter { it !in unchecked }
            PrimaryButton("Start", Modifier.weight(1f), enabled = ids.isNotEmpty()) {
                vm.startPractice(ids, DURATIONS[duration].second)
            }
        }
    }
}

// ====================================================================== Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings ?: return
    var confirmReset by remember { mutableStateOf(false) }
    fun save(d: SettingsData) = vm.saveSettings(d)
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        TopBar("Settings", onBack = vm::goHome)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Stepper("Daily session length", s.minutes, 3..60, " min") { save(s.copy(minutes = it)) }
            Stepper("New topics per session", s.newTopics, 0..5) { save(s.copy(newTopics = it)) }
            Stepper("Max topics in learning at once", s.learningLimit, 1..10) { save(s.copy(learningLimit = it)) }
            Stepper("New facts to learn per session", s.newCards, 0..40) { save(s.copy(newCards = it)) }
            Toggle("Move on automatically after a correct answer", s.autoAdvance) { save(s.copy(autoAdvance = it)) }
            Toggle("Show the target-time bar while answering", s.showTargetBar) { save(s.copy(showTargetBar = it)) }
            Toggle("Vibrate on a wrong answer", s.haptics) { save(s.copy(haptics = it)) }
            if (dynamicColorSupported) {
                Toggle("Use wallpaper colors (Material You)", s.dynamicColor) { save(s.copy(dynamicColor = it)) }
            }
            Text("Text size: ${fmtG(s.fontScale, 2)}×", style = Type.body, modifier = Modifier.padding(top = 10.dp))
            var scale by remember { mutableStateOf(s.fontScale.toFloat()) }
            Slider(scale, onValueChange = { scale = it }, valueRange = 0.7f..1.6f, steps = 8,
                onValueChangeFinished = { save(s.copy(fontScale = Math.round(scale * 10) / 10.0)) })
            Spacer(Modifier.height(20.dp))
            SecondaryButton("Reset all progress…") { confirmReset = true }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetProgress() }) { Text("Delete everything", color = Palette.BAD) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            title = { Text("Reset progress", style = Type.h2) },
            text = { Text("Delete all progress, history and benchmarks? Settings are kept. This cannot be undone.", style = Type.body) },
            containerColor = Palette.SURFACE,
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, modifier = Modifier.weight(1f))
        SecondaryButton("−", Modifier.width(48.dp)) { if (value > range.first) onChange(value - 1) }
        Text("$value$suffix", style = Type.body, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        SecondaryButton("+", Modifier.width(48.dp)) { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, modifier = Modifier.weight(1f))
        Switch(value, onCheckedChange = onChange)
    }
}
