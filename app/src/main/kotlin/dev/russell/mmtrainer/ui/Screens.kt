package dev.russell.mmtrainer.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.mmtrainer.Mode
import dev.russell.mmtrainer.Summary
import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.content.Problem
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.fmtG
import kotlinx.coroutines.android.awaitFrame

@Composable
fun AppRoot(vm: AppViewModel) {
    MmtTheme(fontScale = (vm.settings?.fontScale ?: 1.0).toFloat()) {
        Surface(Modifier.fillMaxSize(), color = Palette.BG) {
            val screen = vm.screen
            BackHandler(enabled = screen != Screen.Home) { vm.back() }
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    is Screen.LessonPage -> LessonScreen(screen, vm)
                    Screen.Drill -> DrillScreen(vm)
                    Screen.SummaryPage -> SummaryScreen(vm)
                    Screen.Progress -> ProgressScreen(vm)
                    Screen.Library -> LibraryScreen(vm)
                    is Screen.Topic -> TopicScreen(screen.itemId, vm)
                    Screen.Practice -> PracticeScreen(vm)
                    Screen.SettingsPage -> SettingsScreen(vm)
                }
            }
            vm.lessonDialog?.let { lesson ->
                AlertDialog(
                    onDismissRequest = vm::closeLesson,
                    confirmButton = { TextButton(onClick = vm::closeLesson) { Text("Close") } },
                    title = { Text(lesson.title, style = Type.h2) },
                    text = {
                        RichText(lesson.body, Modifier.verticalScroll(rememberScrollState()), style = Type.body.copy(fontSize = 15.sp))
                    },
                    containerColor = Palette.SURFACE,
                )
            }
            if (vm.confirmQuit) {
                AlertDialog(
                    onDismissRequest = vm::dismissQuit,
                    confirmButton = { TextButton(onClick = vm::finish) { Text("End session") } },
                    dismissButton = { TextButton(onClick = vm::dismissQuit) { Text("Keep going") } },
                    title = { Text("End this session?", style = Type.h2) },
                    text = { Text("Your answers so far are saved and summarized.", style = Type.body) },
                    containerColor = Palette.SURFACE,
                )
            }
        }
    }
}

// ====================================================================== Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val h = vm.home
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mental Math Trainer", style = Type.h1, modifier = Modifier.weight(1f))
            if (h != null && h.streak > 0) Text("${h.streak}-day streak", style = Type.h2, color = Palette.WARN)
        }
        CardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's session", style = Type.h2)
                if (h?.doneToday == true) {
                    Spacer(Modifier.width(10.dp))
                    Text("✓ done", style = Type.muted, color = Palette.GOOD)
                }
            }
            if (h?.doneToday == true) Text("Extra sessions still help.", style = Type.muted)
            h?.lines?.forEach { RichText(it, style = Type.muted) }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Start daily session (${h?.minutes ?: 12} min)", Modifier.fillMaxWidth(), enabled = h != null) {
                vm.startDaily()
            }
        }
        if (h != null) {
            StatGrid(listOf(
                { m -> StatCard("Benchmark", h.benchValue,
                    h.benchNote + if (h.benchStale) " · " + colored("a week since the last one — retake it", Palette.WARN) else "", m) },
                { m -> StatCard("Speed (7 days)", h.paceValue, h.paceNote, m) },
                { m -> StatCard("Topics", h.topicsValue, h.topicsNote, m) },
            ))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Free practice", Modifier.weight(1f)) { vm.openPractice() }
                SecondaryButton("Benchmark", Modifier.weight(1f)) { vm.startBenchmark() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Progress", Modifier.weight(1f)) { vm.openProgress() }
                SecondaryButton("Library", Modifier.weight(1f)) { vm.openLibrary() }
                SecondaryButton("Settings", Modifier.weight(1f)) { vm.openSettings() }
            }
        }
    }
}

// ====================================================================== Lesson

@Composable
fun LessonScreen(page: Screen.LessonPage, vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagChip(page.kicker)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.back() }) { Icon(Icons.Filled.Close, "End session", tint = Palette.MUTED) }
        }
        Text(page.title, style = Type.h1, modifier = Modifier.padding(vertical = 8.dp))
        CardBox(Modifier.fillMaxWidth().weight(1f)) {
            RichText(page.body, Modifier.verticalScroll(rememberScrollState()))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(page.button, Modifier.fillMaxWidth()) { vm.lessonDone() }
    }
}

// ====================================================================== Drill

/** 'prompt = answer', or just the answer for multi-line (word-problem) prompts. */
fun equation(p: Problem): String {
    val ans = p.displayAnswer
    if ("<br>" in p.prompt) return "Answer: $ans"
    return if (ans.startsWith("≈")) "${p.prompt} $ans" else "${p.prompt} = $ans"
}

@Composable
fun DrillScreen(vm: AppViewModel) {
    val item = vm.item ?: return
    val problem = item.problem!!
    val result = vm.result
    val settings = vm.settings
    val view = LocalView.current
    val focus = remember { FocusRequester() }

    var elapsed by remember(item) { mutableDoubleStateOf(0.0) }
    var fraction by remember { mutableFloatStateOf(vm.fractionDone.toFloat()) }
    LaunchedEffect(item, result) {
        while (true) {
            fraction = vm.fractionDone.toFloat()
            if (vm.result == null) elapsed = (android.os.SystemClock.elapsedRealtime() - vm.shownAt) / 1000.0
            kotlinx.coroutines.delay(100)
        }
    }
    LaunchedEffect(vm.wrongPulse) {
        if (vm.wrongPulse > 0 && settings?.haptics != false) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        }
    }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { focus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown) hardwareKey(vm, e) else false }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Bar(fraction, Palette.ACCENT, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.Filled.Close, "End session", tint = Palette.MUTED) }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = Type.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.tag.isNotEmpty()) TagChip(item.tag)
            }
            val shown = if (result != null) vm.answeredAfter else elapsed
            Text("${fmtF(shown, 1)} s", style = Type.timer, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = vm::showLesson) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, "Lesson for this topic", tint = Palette.MUTED)
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            val long = "<br>" in problem.prompt || problem.prompt.replace(Regex("<[^>]+>"), "").length > 26
            Text(richInline(problem.prompt),
                style = if (long) Type.prompt.copy(fontSize = 21.sp, lineHeight = 29.sp) else Type.prompt,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            val border = when {
                result == null -> Palette.ACCENT
                result.correct && !result.hinted -> Palette.GOOD
                else -> Palette.BAD
            }
            Box(
                Modifier.widthIn(min = 220.dp, max = 420.dp).fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(10.dp)).background(Palette.SURFACE)
                    .border(2.dp, border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(vm.input.ifEmpty { " " }, style = Type.answer, maxLines = 1)
            }
            val hint = vm.warning?.let { colored(it, Palette.WARN) }
                ?: if (item.newCard && result == null) "New fact — type it if you know it, or press Show to see it"
                else problem.formatHint()
            RichText(hint, style = Type.muted, textAlign = TextAlign.Center)
            if (settings?.showTargetBar != false && result == null) {
                val target = if (item.targetSeconds > 0) item.targetSeconds else 1.0
                Bar((elapsed / target).toFloat(), if (elapsed > target) Palette.WARN else Palette.GOOD,
                    Modifier.widthIn(max = 420.dp).fillMaxWidth(0.8f))
            }
            if (result != null) Feedback(vm)
            Spacer(Modifier.height(8.dp))
        }

        Keypad(
            enterLabel = when {
                result != null -> "Next"
                vm.input.isBlank() -> "Show"
                else -> "Enter"
            },
            onKey = { k ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                vm.key(k)
            },
        )
    }
}

private fun hardwareKey(vm: AppViewModel, e: androidx.compose.ui.input.key.KeyEvent): Boolean {
    when (e.key) {
        Key.Enter, Key.NumPadEnter -> vm.key(AppViewModel.ENTER)
        Key.Backspace -> vm.key(AppViewModel.BACKSPACE)
        Key.Escape -> vm.back()
        Key.F1 -> vm.showLesson()
        else -> {
            val c = e.utf16CodePoint.toChar()
            if (c in "0123456789.-/ e^,x*") vm.key(if (c == '-') "−" else c.toString()) else return false
        }
    }
    return true
}

@Composable
private fun Feedback(vm: AppViewModel) {
    val r = vm.result ?: return
    val p = r.item.problem!!
    val t = r.timeS
    val target = r.item.targetSeconds
    val speed = colored("${fmtF(t, 1)} s", if (t <= target) Palette.GOOD else Palette.WARN)
    val approx = p.tolAbs > 0 || p.tolRel > 0
    val html = StringBuilder()
    if (r.correct && !r.hinted) {
        html.append("${colored("✓ Correct", Palette.GOOD)}  $speed ${colored("(target ${fmtTarget(target)})", Palette.MUTED)}")
        val v = r.value
        if (approx && v != null && p.answer != 0.0) {
            val err = Math.abs(v - p.answer) / Math.abs(p.answer) * 100
            html.append("<br>Exact: <b>${p.displayAnswer}</b> · you were off by ${fmtG(err, 2)}%")
        }
        r.promotedTo?.let { lvl ->
            val skill = Content.SKILLS.getValue(r.item.sourceId)
            html.append("<br>" + colored("▲ Level up! ${skill.name} → L${lvl + 1}: ${skill.levels[lvl].description}", Palette.ACCENT))
        }
    } else {
        val head = when {
            r.item.newCard && r.response.isEmpty() -> colored("New fact", Palette.ACCENT)
            r.response.isEmpty() -> colored("Answer", Palette.WARN)
            else -> colored("✗ Not quite", Palette.BAD) + "  you said ${escapeHtml(r.response)}"
        }
        html.append(head)
    }
    CardBox(Modifier.fillMaxWidth()) {
        RichText(html.toString(), style = Type.body)
        if (!(r.correct && !r.hinted)) {
            Text(richInline("<b>${equation(p)}</b>"), style = Type.body.copy(fontSize = 24.sp, lineHeight = 32.sp),
                modifier = Modifier.padding(vertical = 6.dp))
            if (p.explanation.isNotEmpty()) RichText(p.explanation, style = Type.body.copy(fontSize = 15.sp))
            if (r.item.isCard) Text("You'll see this again in a few questions.", style = Type.muted)
        }
    }
}

private val KEYS = listOf(
    listOf("7", "8", "9", "/", AppViewModel.BACKSPACE),
    listOf("4", "5", "6", " ", "^"),
    listOf("1", "2", "3", "−", "e"),
)

@Composable
private fun Keypad(enterLabel: String, onKey: (String) -> Unit) {
    val gap = 6.dp
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in KEYS) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (k in row) KeyButton(k, Modifier.weight(1f), onKey)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            KeyButton("0", Modifier.weight(2f), onKey)
            KeyButton(".", Modifier.weight(1f), onKey)
            KeyButton(AppViewModel.ENTER, Modifier.weight(2f), onKey, label = enterLabel, primary = true)
        }
    }
}

@Composable
private fun KeyButton(k: String, modifier: Modifier, onKey: (String) -> Unit, label: String? = null, primary: Boolean = false) {
    val text = label ?: when (k) {
        AppViewModel.BACKSPACE -> "⌫"
        " " -> "␣"
        "e" -> "×10ⁿ"
        else -> k
    }
    Surface(
        onClick = { onKey(k) },
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (primary) Palette.ACCENT else Palette.SURFACE_2,
        contentColor = if (primary) Palette.BG else Palette.TEXT,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = if (text.length > 2) 17.sp else 23.sp,
                fontWeight = if (primary) androidx.compose.ui.text.font.FontWeight.Bold else null)
        }
    }
}

// ====================================================================== Summary

@Composable
fun SummaryScreen(vm: AppViewModel) {
    val s = vm.summary ?: return
    val benches = vm.summaryExtras?.benchmarks.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(when (s.mode) { Mode.DAILY -> "Session complete"; Mode.PRACTICE -> "Practice complete"; Mode.BENCHMARK -> "Benchmark" },
                style = Type.h1)
            SummaryStats(s, benches)
            SummaryBody(s)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Back to home", Modifier.fillMaxWidth()) { vm.goHome() }
    }
}

@Composable
private fun SummaryStats(s: Summary, benches: List<dev.russell.mmtrainer.Benchmark>) {
    val mins = s.durationS / 60
    val cards = mutableListOf<@Composable (Modifier) -> Unit>()
    if (s.mode == Mode.BENCHMARK) {
        var note = "100 = every answer correct at target speed"
        if (benches.size > 1 && benches[0].score > 0) {
            val first = benches[0].score
            val prev = benches[benches.size - 2].score
            note = "previous ${fmtF(prev, 0)} · baseline ${fmtF(first, 0)} (${signed0((s.score!! / first - 1) * 100)}% vs baseline)"
        }
        cards += { m -> StatCard("Score", fmtF(s.score ?: 0.0, 0), note, m) }
        cards += { m -> StatCard("Correct", "${s.nCorrect}/${s.n}", "", m) }
        cards += { m -> StatCard("Time", "${fmtF(mins, 1)} min", "", m) }
    } else {
        val acc = if (s.n > 0) s.nCorrect * 100.0 / s.n else 0.0
        cards += { m -> StatCard("Answered", s.n.toString(), "${fmtF(mins, 1)} min", m) }
        cards += { m -> StatCard("Accuracy", "${fmtF(acc, 0)}%", "", m) }
        val paces = s.perSkill.values.map { it.pace }.filter { it != 0.0 }.sorted()
        if (paces.isNotEmpty()) {
            cards += { m -> StatCard("Pace", fmtF(paces[paces.size / 2] * 100, 0), "median speed vs target (100 = target)", m) }
        }
        if (s.promotions.isNotEmpty() || s.introduced.isNotEmpty()) {
            cards += { m -> StatCard("Progress", "${s.promotions.size}▲",
                "${s.promotions.size} level-ups · ${s.introduced.size} new topics", m) }
        }
    }
    StatGrid(cards)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Type.h2)
        content()
    }
}

@Composable
private fun SummaryBody(s: Summary) {
    if (s.mode == Mode.BENCHMARK) {
        Section("Breakdown") {
            for (r in s.results) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Content.nameOf(r.item.sourceId), style = Type.muted)
                        Text(richInline(r.item.problem!!.prompt), style = Type.body)
                    }
                    Text(if (r.correct) "✓" else "✗", color = if (r.correct) Palette.GOOD else Palette.BAD, style = Type.h2)
                    Spacer(Modifier.width(10.dp))
                    Text("${fmtF(r.timeS, 1)} / ${fmtTarget(r.item.targetSeconds)}", style = Type.muted)
                }
            }
        }
    } else {
        if (s.introduced.isNotEmpty()) Section("New topics") {
            s.introduced.forEach { Text("• ${Content.nameOf(it)}", style = Type.body) }
        }
        if (s.promotions.isNotEmpty()) Section("Level ups") {
            for ((skillId, lvl) in s.promotions) {
                val skill = Content.SKILLS.getValue(skillId)
                RichText("• <b>${escapeHtml(skill.name)}</b> → Level ${lvl + 1}: ${escapeHtml(skill.levels[lvl].description)}")
            }
        }
        if (s.perSkill.isNotEmpty()) Section("Skills practised") {
            Row {
                Text("Skill", style = Type.muted, modifier = Modifier.weight(1f))
                listOf("n", "Acc", "Median", "Pace").forEach {
                    Text(it, style = Type.muted, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                }
            }
            for ((skillId, d) in s.perSkill.entries.sortedByDescending { it.value.n }) {
                val pace = d.pace * 100
                Row {
                    Text(Content.SKILLS.getValue(skillId).name, style = Type.body, modifier = Modifier.weight(1f))
                    Text(d.n.toString(), style = Type.body, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                    Text("${fmtF(d.accuracy * 100, 0)}%", style = Type.body, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                    Text(fmtSecs(d.median), style = Type.body, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                    Text(if (d.times.isNotEmpty()) fmtF(pace, 0) else "—", style = Type.body,
                        color = if (d.times.isEmpty()) Palette.TEXT else if (pace >= 100) Palette.GOOD else Palette.WARN,
                        modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
                }
            }
        }
        val cards = s.results.filter { it.item.isCard }
        if (cards.isNotEmpty()) Section("Facts") {
            Text("${cards.size} reviews, ${cards.count { it.correct }} correct.", style = Type.body)
        }
    }
    val misses = s.misses
    if (misses.isNotEmpty()) Section("Review your misses") {
        val seen = mutableSetOf<String>()
        for (r in misses) {
            val p = r.item.problem!!
            if (!seen.add(p.prompt)) continue
            CardBox(Modifier.fillMaxWidth()) {
                val eq = equation(p).replace("Answer:", p.prompt.replace("<br>", " ") + " →")
                val you = if (r.response.isNotEmpty()) " " + colored("(you: ${escapeHtml(r.response)})", Palette.BAD) else ""
                Text(richInline("<b>$eq</b>$you"), style = Type.body)
                if (p.explanation.isNotEmpty()) RichText(p.explanation, style = Type.muted)
            }
        }
    }
    if (s.n == 0) Text("Nothing answered this time.", style = Type.body)
}
