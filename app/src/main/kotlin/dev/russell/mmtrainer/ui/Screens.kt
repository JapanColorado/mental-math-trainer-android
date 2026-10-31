package dev.russell.mmtrainer.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.mmtrainer.Mode
import dev.russell.mmtrainer.Speech
import dev.russell.mmtrainer.Summary
import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.content.Problem
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.fmtG
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale

// ====================================================================== Root

private class NavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val screen: Screen, val open: (AppViewModel) -> Unit)

private val TABS = listOf(
    NavTab("Today", Icons.Outlined.Home, Icons.Filled.Home, Screen.Home) { it.goHome() },
    NavTab("Practice", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter, Screen.Practice) { it.openPractice() },
    NavTab("Library", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook, Screen.Library) { it.openLibrary() },
    NavTab("Progress", Icons.Outlined.ShowChart, Icons.Filled.ShowChart, Screen.Progress) { it.openProgress() },
)

/** A stable name for a screen's saved scroll and tab state. */
private fun Screen.stateKey(): String = when (this) {
    is Screen.LessonPage -> "lesson:$title"
    is Screen.Topic -> "topic:$itemId"
    else -> toString()
}

/** Tabs fade through; pages opened from them slide in from the side; sessions rise from below. */
private fun AnimatedContentTransitionScope<Screen>.screenTransition(): ContentTransform {
    val from = initialState
    val to = targetState
    val fade = tween<Float>(200)
    return when {
        from.isTopLevel && to.isTopLevel -> fadeIn(fade) togetherWith fadeOut(tween(120))
        to is Screen.Topic || to == Screen.SettingsPage ->
            (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(fade)) togetherWith fadeOut(tween(120))
        from is Screen.Topic || from == Screen.SettingsPage ->
            fadeIn(fade) togetherWith (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(160)))
        to.isTopLevel -> fadeIn(fade) togetherWith (slideOutVertically(tween(220)) { it / 10 } + fadeOut(tween(160)))
        else -> (slideInVertically(tween(260)) { it / 10 } + fadeIn(fade)) togetherWith fadeOut(tween(120))
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    MmtTheme(fontScale = (vm.settings?.fontScale ?: 1.0).toFloat(), dynamicColor = vm.settings?.dynamicColor ?: true) {
        Surface(Modifier.fillMaxSize(), color = Palette.BG) {
            if (!vm.ready) return@Surface  // behind the splash screen, until the first screen is known
            val current = vm.screen
            BackHandler(enabled = current != Screen.Home) { vm.back() }
            val saved = rememberSaveableStateHolder()
            Column(Modifier.fillMaxSize()) {
                val insets = if (current.isTopLevel) WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                else WindowInsets.safeDrawing
                Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(insets), contentAlignment = Alignment.TopCenter) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = { screenTransition() },
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                        label = "screen",
                    ) { screen ->
                        saved.SaveableStateProvider(screen.stateKey()) {
                            when (screen) {
                                Screen.Onboarding -> OnboardingScreen(vm)
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
                    }
                }
                AnimatedVisibility(current.isTopLevel) { BottomBar(current, vm) }
            }
            vm.lessonDialog?.let { lesson ->
                AlertDialog(
                    onDismissRequest = vm::closeLesson,
                    confirmButton = { TextButton(onClick = vm::closeLesson) { Text("Close") } },
                    title = { Text(lesson.title, style = Type.h2) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (vm.result == null) {
                                Text("Reading the lesson mid-question counts this one as a miss.", style = Type.muted, color = Palette.WARN)
                            }
                            RichText(lesson.body, style = Type.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                        }
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
                    text = { Text("Your answers so far are saved, and you'll see a summary.", style = Type.body) },
                    containerColor = Palette.SURFACE,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(current: Screen, vm: AppViewModel) {
    NavigationBar(containerColor = Palette.SURFACE) {
        for (tab in TABS) {
            val selected = current == tab.screen
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) tab.open(vm) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label, maxLines = 1) },
            )
        }
    }
}

// ====================================================================== Today

@Composable
fun HomeScreen(vm: AppViewModel) {
    val h = vm.home
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Mental Math") {
            IconButton(onClick = vm::openSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Palette.TEXT)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.fillMaxWidth(), color = Palette.ACCENT_CONTAINER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today", style = Type.h2, color = Palette.ON_ACCENT_CONTAINER, modifier = Modifier.weight(1f).semantics { heading() })
                    if (h != null && h.sessionsToday > 0) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.ON_ACCENT_CONTAINER, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${count(h.sessionsToday, "session")} done", style = Type.label, color = Palette.ON_ACCENT_CONTAINER)
                    }
                }
                Text(h?.headline.orEmpty(), style = Type.body, color = Palette.ON_ACCENT_CONTAINER)
                h?.nextTopic?.let { RichText(it, style = Type.muted.copy(color = Palette.ON_ACCENT_CONTAINER)) }
                Spacer(Modifier.height(10.dp))
                val done = (h?.sessionsToday ?: 0) > 0
                PrimaryButton(
                    "${if (done) "Another" else "Start"} ${h?.minutes ?: 3}-minute session",
                    Modifier.fillMaxWidth(), enabled = h != null, icon = Icons.Filled.PlayArrow,
                ) { vm.startDaily() }
            }
            if (h != null) {
                StreakCard(h.streak, h.activity)
                val benchmark: @Composable (Modifier) -> Unit = { m ->
                    StatCard("Benchmark", h.benchValue, h.benchNote, m,
                        noteColor = if (h.benchStale) Palette.WARN else Palette.MUTED,
                        onClick = { if (!h.benchTaken || h.benchStale) vm.startBenchmark() else vm.openProgress() })
                }
                val speed: @Composable (Modifier) -> Unit = { m ->
                    StatCard("7-day speed", h.paceValue, h.paceNote, m,
                        valueColor = if (h.paceOnTarget) Palette.GOOD else Palette.TEXT, onClick = vm::openProgress)
                }
                val topics: @Composable (Modifier) -> Unit = { m ->
                    StatCard("Topics", h.topicsValue, h.topicsNote, m, onClick = vm::openLibrary)
                }
                if (LocalDensity.current.fontScale > 1.25f) {  // three across would break words
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (card in listOf(benchmark, speed, topics)) card(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (card in listOf(benchmark, speed, topics)) card(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** The first letter of the weekday for each of the last [n] study days, oldest first. */
private fun weekdayLetters(n: Int): List<String> {
    val cal = Calendar.getInstance()
    cal.add(Calendar.HOUR_OF_DAY, -dev.russell.mmtrainer.ROLLOVER_HOUR)
    cal.add(Calendar.DAY_OF_YEAR, -(n - 1))
    return List(n) {
        val name = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.NARROW_FORMAT, Locale.getDefault()).orEmpty()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        name
    }
}

@Composable
private fun StreakCard(streak: Int, activity: List<Boolean>) {
    val letters = remember(activity) { weekdayLetters(activity.size) }
    val days = activity.count { it }
    CardBox(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "${if (streak > 0) "$streak-day streak. " else ""}Practised on $days of the last ${activity.size} days."
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null,
                tint = if (streak > 0) Palette.WARN else Palette.MUTED, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (streak > 0) "$streak-day streak" else "Start a streak today", style = Type.title,
                modifier = Modifier.clearAndSetSemantics {})
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            activity.forEachIndexed { i, active ->
                val today = i == activity.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(if (active) Palette.ACCENT else Palette.SURFACE_2)
                            .then(if (today && !active) Modifier.border(2.dp, Palette.ACCENT, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.ON_ACCENT, modifier = Modifier.size(18.dp))
                    }
                    Text(letters[i], style = Type.tag, color = if (today) Palette.TEXT else Palette.MUTED)
                }
            }
        }
    }
}

// ====================================================================== Lesson

@Composable
fun LessonScreen(page: Screen.LessonPage, vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            TagChip(page.kicker)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.back() }) { Icon(Icons.Filled.Close, "End session", tint = Palette.MUTED) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(page.title, style = Type.h1, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
            RichText(page.body)
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(8.dp))
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

private fun promptSize(prompt: String): Pair<Int, Int> {
    val n = prompt.replace(Regex("<[^>]+>"), "").length
    return when {
        "<br>" in prompt || n > 26 -> 21 to 29
        n > 18 -> 28 to 36
        n > 12 -> 36 to 44
        else -> 44 to 54
    }
}

@Composable
fun DrillScreen(vm: AppViewModel) {
    // The screen stays visible while it transitions out, after the session has let go of its item.
    val lastItem = remember { mutableStateOf(vm.item) }
    vm.item?.let { lastItem.value = it }
    val item = lastItem.value ?: return
    val result = vm.result
    val settings = vm.settings
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()

    var elapsed by remember(item) { mutableDoubleStateOf(0.0) }
    var fraction by remember { mutableFloatStateOf(vm.fractionDone.toFloat()) }
    LaunchedEffect(item, result) {
        while (true) {
            fraction = vm.fractionDone.toFloat()
            if (vm.result == null) {
                elapsed = if (vm.listening) 0.0 else (android.os.SystemClock.elapsedRealtime() - vm.shownAt) / 1000.0
            }
            kotlinx.coroutines.delay(100)
        }
    }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(vm.wrongPulse) {
        if (vm.wrongPulse > 0) {
            if (settings?.haptics != false) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            }
            shake.snapTo(0f)
            shake.animateTo(0f, keyframes {
                durationMillis = 320
                -10f at 50
                9f at 110
                -6f at 170
                4f at 230
                -2f at 280
            })
        }
    }
    LaunchedEffect(result?.promotedTo) {
        if (result?.promotedTo != null && settings?.haptics != false && Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
    LaunchedEffect(item) { scroll.scrollTo(0) }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { focus.requestFocus() }
    }

    val keypad: @Composable (Dp) -> Unit = { keyHeight ->
        Keypad(
            keyHeight = keyHeight,
            enterLabel = when {
                result != null -> "Next"
                vm.input.isBlank() -> "Show"
                else -> "Enter"
            },
            enterEnabled = result != null ||
                (!vm.listening && (vm.input.isNotBlank() || elapsed * 1000 >= AppViewModel.SHOW_DELAY_MS)),
            onKey = { k ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                vm.key(k)
            },
        )
    }
    val problemArea: @Composable ColumnScope.() -> Unit = {
        ProblemArea(vm, item, result, elapsed, shake.value, Modifier.weight(1f).verticalScroll(scroll))
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown) hardwareKey(vm, e) else false }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val height = maxHeight
        val landscape = maxWidth > height
        Column(Modifier.fillMaxSize()) {
            Bar(fraction, Palette.ACCENT, Modifier.fillMaxWidth())
            DrillHeader(vm, item, if (result != null) vm.answeredAfter else elapsed)
            if (landscape) {
                val keyHeight = ((height - 76.dp - SYMBOL_HEIGHT - KEY_GAP * 4 - 16.dp) / 4).coerceIn(38.dp, KEY_HEIGHT)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), content = problemArea)
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) { keypad(keyHeight) }
                }
            } else {
                problemArea()
                keypad((height * 0.095f).coerceIn(52.dp, KEY_HEIGHT))
            }
        }
    }
}

@Composable
private fun DrillHeader(vm: AppViewModel, item: dev.russell.mmtrainer.Item, shown: Double) {
    val parts = item.title.split(" · ", limit = 2)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.back() }) { Icon(Icons.Filled.Close, "End session", tint = Palette.MUTED) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(parts[0], style = Type.label.copy(color = Palette.TEXT), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (item.tag.isNotEmpty()) TagChip(item.tag)
            }
            parts.getOrNull(1)?.let {
                Text(it, style = Type.label.copy(fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("${fmtF(shown, 1)} s", style = Type.timer, modifier = Modifier.padding(horizontal = 8.dp)
            .semantics { contentDescription = "${fmtF(shown, 0)} seconds" })
        IconButton(onClick = vm::showLesson) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, "Lesson for this topic", tint = Palette.MUTED)
        }
    }
}

@Composable
private fun ProblemArea(
    vm: AppViewModel, item: dev.russell.mmtrainer.Item, result: dev.russell.mmtrainer.Result?, elapsed: Double, shake: Float, modifier: Modifier,
) {
    val problem = item.problem!!
    val settings = vm.settings
    val density = LocalDensity.current
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        if (vm.audio && vm.speechFailed) {
            RichText(colored("Text-to-speech isn't available, so the problems are shown instead.", Palette.WARN),
                style = Type.muted, textAlign = TextAlign.Center)
        }
        if (vm.audio && !vm.speechFailed && result == null) {
            AudioPrompt(vm.listening, vm::replay)
        } else {
            val (size, line) = promptSize(problem.prompt)
            Text(richInline(problem.prompt),
                style = Type.prompt.copy(fontSize = size.sp, lineHeight = line.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = Speech.speakable(problem.prompt) })
        }

        val revealed = result != null && result.response.isEmpty()
        val right = result != null && result.correct && !result.hinted
        val border by animateColorAsState(
            when {
                result == null -> Palette.ACCENT
                right -> Palette.GOOD
                revealed -> Palette.BORDER
                else -> Palette.BAD
            }, tween(120), label = "answerBorder")
        val fill by animateColorAsState(
            when {
                result == null || revealed -> Palette.SURFACE
                right -> lerp(Palette.SURFACE, Palette.GOOD, 0.16f)
                else -> lerp(Palette.SURFACE, Palette.BAD, 0.14f)
            }, tween(120), label = "answerFill")
        val shown = vm.input.ifEmpty { if (revealed) "—" else " " }
        Box(
            Modifier.widthIn(min = 220.dp, max = 420.dp).fillMaxWidth(0.8f)
                .graphicsLayer { translationX = shake * density.density }
                .clip(RoundedCornerShape(14.dp)).background(fill)
                .border(2.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = if (vm.input.isEmpty()) "Your answer: empty" else "Your answer: ${vm.input}"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(shown, style = if (shown.length > 14) Type.answer.copy(fontSize = 24.sp, lineHeight = 41.sp) else Type.answer, maxLines = 1)
        }
        if (result == null) {
            val hint = vm.warning?.let { colored(it, Palette.WARN) }
                ?: if (item.newCard) "New fact. Type it if you know it, or press Show to see it."
                else formatHint(problem)
            Box(Modifier.semantics { if (vm.warning != null) liveRegion = LiveRegionMode.Polite }) {
                RichText(hint, style = Type.muted, textAlign = TextAlign.Center)
            }
            if (settings?.showTargetBar != false) {
                val target = if (item.targetSeconds > 0) item.targetSeconds else 1.0
                Bar((elapsed / target).toFloat(), if (elapsed > target) Palette.WARN else Palette.GOOD,
                    Modifier.widthIn(max = 420.dp).fillMaxWidth(0.8f).clearAndSetSemantics {}, animated = false)
            }
        } else {
            val reveal = remember(result) { Animatable(0f) }
            LaunchedEffect(result) { reveal.animateTo(1f, tween(160)) }
            Feedback(vm, Modifier.graphicsLayer {
                alpha = reveal.value
                translationY = (1 - reveal.value) * 10 * density.density
            })
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun formatHint(p: Problem): String = p.formatHint().let { if (it == "exact") "Exact answer" else it }

private fun hardwareKey(vm: AppViewModel, e: androidx.compose.ui.input.key.KeyEvent): Boolean {
    when (e.key) {
        Key.Enter, Key.NumPadEnter -> vm.key(AppViewModel.ENTER)
        Key.Backspace -> vm.key(AppViewModel.BACKSPACE)
        Key.Escape -> vm.back()
        Key.F1 -> vm.showLesson()
        Key.R -> vm.replay()
        else -> {
            val c = e.utf16CodePoint.toChar()
            if (c in "0123456789.-/ e^,x*") vm.key(if (c == '-') "−" else c.toString()) else return false
        }
    }
    return true
}

/** Audio practice: the problem is only heard. Tapping the speaker says it again. */
@Composable
private fun AudioPrompt(listening: Boolean, onReplay: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Palette.SURFACE_2)
                .border(2.dp, if (listening) Palette.ACCENT else Palette.BORDER, CircleShape)
                .clickable(onClickLabel = "Hear the problem again", onClick = onReplay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, "Replay",
                tint = if (listening) Palette.ACCENT else Palette.TEXT, modifier = Modifier.size(40.dp))
        }
        Text(if (listening) "Listening…" else "Tap to hear it again", style = Type.muted)
    }
}

@Composable
private fun Feedback(vm: AppViewModel, modifier: Modifier) {
    val r = vm.result ?: return
    val p = r.item.problem!!
    val t = r.timeS
    val target = r.item.targetSeconds
    val right = r.correct && !r.hinted
    val approx = p.tolAbs > 0 || p.tolRel > 0
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardBox(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint, head) = when {
                    right -> Triple(Icons.Filled.Check, Palette.GOOD, "Correct")
                    r.item.newCard && r.response.isEmpty() -> Triple(Icons.Filled.Visibility, Palette.ACCENT, "New fact")
                    r.response.isEmpty() -> Triple(Icons.Filled.Visibility, Palette.WARN, "Answer")
                    else -> Triple(Icons.Filled.Close, Palette.BAD, "Not quite")
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(head, style = Type.title, color = tint, modifier = Modifier.weight(1f))
                if (right) {
                    Text(fmtF(t, 1) + " s", style = Type.title.copy(fontFeatureSettings = "tnum"),
                        color = if (t <= target) Palette.GOOD else Palette.WARN)
                    Text("  target " + fmtTarget(target), style = Type.label.copy(fontWeight = FontWeight.Normal))
                } else if (r.response.isNotEmpty()) {
                    Text("You said ", style = Type.muted)
                    Text(r.response, style = Type.title, color = Palette.BAD)
                }
            }
            if (right) {
                val v = r.value
                if (approx && v != null && p.answer != 0.0) {
                    val err = Math.abs(v - p.answer) / Math.abs(p.answer) * 100
                    RichText("Exact: <b>${p.displayAnswer}</b>. You were off by ${fmtG(err, 2)}%.", style = Type.muted)
                }
            } else {
                Text(richInline("<b>${equation(p)}</b>"), style = Type.body.copy(fontSize = 24.sp, lineHeight = 32.sp),
                    modifier = Modifier.padding(vertical = 4.dp))
                if (p.explanation.isNotEmpty()) RichText(p.explanation, style = Type.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                if (r.item.isCard) Text("You'll see this again in a few questions.", style = Type.muted)
            }
        }
        r.promotedTo?.let { lvl ->
            val skill = Content.SKILLS.getValue(r.item.sourceId)
            LevelUpBanner(skill.name, lvl + 1, skill.levels[lvl].description)
        }
    }
}

@Composable
private fun LevelUpBanner(skill: String, level: Int, description: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = 0.85f) + fadeIn()) {
        CardBox(Modifier.fillMaxWidth(), color = Palette.ACCENT_CONTAINER) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Palette.ON_ACCENT_CONTAINER, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Level up! $skill is now level $level", style = Type.title, color = Palette.ON_ACCENT_CONTAINER)
                    Text(description, style = Type.muted, color = Palette.ON_ACCENT_CONTAINER)
                }
            }
        }
    }
}

private val DIGITS = listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"))
private val SYMBOLS = listOf("/", " ", "^", "e", "−")
private val KEY_HEIGHT = 72.dp
private val SYMBOL_HEIGHT = 48.dp
private val KEY_GAP = 8.dp

/**
 * A slim row of rarely used symbols over a calculator-style digit block, with ⌫ and a tall Enter
 * on the right. Keys fire on touch-down; holding ⌫ clears the whole answer.
 */
@Composable
private fun Keypad(keyHeight: Dp, enterLabel: String, enterEnabled: Boolean, onKey: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            for (k in SYMBOLS) KeyButton(k, Modifier.weight(1f).height(SYMBOL_HEIGHT), onKey, small = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                for (row in DIGITS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        for (k in row) KeyButton(k, Modifier.weight(1f).height(keyHeight), onKey)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                    KeyButton("0", Modifier.weight(2f).height(keyHeight), onKey)
                    KeyButton(".", Modifier.weight(1f).height(keyHeight), onKey)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                KeyButton(AppViewModel.BACKSPACE, Modifier.fillMaxWidth().height(keyHeight), onKey)
                KeyButton(AppViewModel.ENTER, Modifier.fillMaxWidth().height(keyHeight * 3 + KEY_GAP * 2), onKey,
                    label = enterLabel, primary = true, enabled = enterEnabled)
            }
        }
    }
}

@Composable
private fun KeyButton(
    k: String, modifier: Modifier, onKey: (String) -> Unit,
    label: String? = null, primary: Boolean = false, small: Boolean = false, enabled: Boolean = true,
) {
    val text = label ?: when (k) {
        AppViewModel.BACKSPACE -> "⌫"
        " " -> "␣"
        "e" -> "×10ⁿ"
        else -> k
    }
    val spoken = when (k) {
        AppViewModel.BACKSPACE -> "Backspace. Hold to clear."
        " " -> "Space"
        "e" -> "Times ten to the power"
        "/" -> "Fraction bar"
        "^" -> "To the power"
        "−" -> "Minus"
        "." -> "Point"
        else -> text
    }
    val currentOnKey by rememberUpdatedState(onKey)
    var pressed by remember { mutableStateOf(false) }
    val base = if (primary) Palette.ACCENT else if (small) Palette.SURFACE else Palette.SURFACE_2
    // Labels keep one size: they already fill their keys, so the text-size settings would only clip them.
    val density = LocalDensity.current
    val labelSize = with(density) {
        when {
            small -> 19.dp
            text.length > 2 -> 20.dp
            else -> 32.dp
        }.toSp()
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) lerp(base, Palette.TEXT, 0.18f) else base)
            .pointerInput(k) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    currentOnKey(k)
                    if (k == AppViewModel.BACKSPACE) {
                        var held = true
                        withTimeoutOrNull(450) { waitForUpOrCancellation(); held = false }
                        if (held) {
                            currentOnKey(AppViewModel.CLEAR)
                            waitForUpOrCancellation()
                        }
                    } else {
                        waitForUpOrCancellation()
                    }
                    pressed = false
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (primary) text else spoken
                onClick { currentOnKey(k); true }
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text,
            modifier = Modifier.clearAndSetSemantics {},
            color = if (primary) Palette.ON_ACCENT else Palette.TEXT,
            fontSize = labelSize,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium)
    }
}

// ====================================================================== Summary

@Composable
fun SummaryScreen(vm: AppViewModel) {
    val s = vm.summary ?: return
    val benches = vm.summaryExtras?.benchmarks.orEmpty()
    val extras = vm.summaryExtras
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val title = when {
                s.mode == Mode.BENCHMARK -> "Benchmark complete"
                extras?.weakSpots == true -> "Weak spots practised"
                extras?.audio == true -> "Audio practice complete"
                s.mode == Mode.PRACTICE -> "Practice complete"
                else -> "Session complete"
            }
            Text(title, style = Type.h1, modifier = Modifier.semantics { heading() })
            SummaryHero(s, benches)
            SummaryBody(s)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.canGoAgain) {
                SecondaryButton("Go again", Modifier.weight(1f).height(54.dp)) { vm.goAgain() }
            }
            PrimaryButton("Done", Modifier.weight(1f)) { vm.goHome() }
        }
    }
}

@Composable
private fun SummaryHero(s: Summary, benches: List<dev.russell.mmtrainer.Benchmark>) {
    val mins = s.durationS / 60
    if (s.mode == Mode.BENCHMARK) {
        val score = s.score ?: 0.0
        CardBox(Modifier.fillMaxWidth(), color = Palette.ACCENT_CONTAINER) {
            Text("Score", style = Type.label, color = Palette.ON_ACCENT_CONTAINER)
            CountUpText(score, { fmtF(it, 0) }, Type.hero, color = Palette.ON_ACCENT_CONTAINER)
            val note = if (benches.size > 1 && benches[0].score > 0) {
                val first = benches[0].score
                val prev = benches[benches.size - 2].score
                "Previous ${fmtF(prev, 0)} · first ${fmtF(first, 0)} (${signed0((score / first - 1) * 100)}%)"
            } else "100 means every answer correct at target speed."
            Text(note, style = Type.muted, color = Palette.ON_ACCENT_CONTAINER)
        }
        StatGrid(listOf(
            { m -> StatCard("Correct", "${s.nCorrect}/${s.n}", modifier = m) },
            { m -> StatCard("Time", "${fmtF(mins, 1)} min", modifier = m) },
        ))
        return
    }
    if (s.n == 0) return
    val acc = s.nCorrect.toDouble() / s.n
    CardBox(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(acc.toFloat(), if (acc >= 0.85) Palette.GOOD else if (acc >= 0.6) Palette.WARN else Palette.BAD, 104.dp) {
                CountUpText(acc * 100, { fmtF(it, 0) + "%" }, Type.bigStat)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${s.nCorrect} of ${count(s.n, "answer")} correct", style = Type.title)
                Text("in ${fmtF(mins, 1)} min", style = Type.muted)
                val paces = s.perSkill.values.map { it.pace }.filter { it != 0.0 }.sorted()
                if (paces.isNotEmpty()) {
                    val pace = paces[paces.size / 2] * 100
                    Text("${fmtF(pace, 0)}% of target speed", style = Type.muted, color = if (pace >= 100) Palette.GOOD else Palette.MUTED)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun SummaryBody(s: Summary) {
    if (s.mode == Mode.BENCHMARK) {
        Section("Breakdown") {
            ListGroup(s.results.map { r ->
                {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (r.correct) Icons.Filled.Check else Icons.Filled.Close, contentDescription = if (r.correct) "Correct" else "Missed",
                            tint = if (r.correct) Palette.GOOD else Palette.BAD, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(richInline(r.item.problem!!.prompt), style = Type.body)
                            Text(Content.nameOf(r.item.sourceId), style = Type.label.copy(fontWeight = FontWeight.Normal))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${fmtF(r.timeS, 1)} / ${fmtTarget(r.item.targetSeconds)}", style = Type.muted.copy(fontFeatureSettings = "tnum"))
                    }
                }
            })
        }
    } else {
        if (s.promotions.isNotEmpty()) Section("Level ups") {
            for ((skillId, lvl) in s.promotions) {
                val skill = Content.SKILLS.getValue(skillId)
                LevelUpBanner(skill.name, lvl + 1, skill.levels[lvl].description)
            }
        }
        if (s.introduced.isNotEmpty()) Section("New topics") {
            ListGroup(s.introduced.map { id ->
                { Text(Content.nameOf(id), style = Type.body, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            })
        }
        if (s.perSkill.isNotEmpty()) Section("Skills practised") {
            ListGroup(s.perSkill.entries.sortedByDescending { it.value.n }.map { (skillId, d) ->
                {
                    val pace = d.pace * 100
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Content.SKILLS.getValue(skillId).name, style = Type.title)
                            Text("${d.ok} of ${d.n} correct · median ${fmtSecs(d.median)}", style = Type.muted)
                        }
                        if (d.times.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            TagChip("${fmtF(pace, 0)}% speed", if (pace >= 100) Palette.GOOD else Palette.WARN)
                        }
                    }
                }
            })
        }
        val cards = s.results.filter { it.item.isCard }
        if (cards.isNotEmpty()) Section("Facts") {
            Text("${count(cards.size, "review")}, ${cards.count { it.correct && !it.hinted }} correct.", style = Type.body)
        }
    }
    val misses = s.misses
    if (misses.isNotEmpty()) Section("Worth another look") {
        val seen = mutableSetOf<String>()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in misses) {
                val p = r.item.problem!!
                if (!seen.add(p.prompt)) continue
                CardBox(Modifier.fillMaxWidth()) {
                    val eq = equation(p).replace("Answer:", p.prompt.replace("<br>", " ") + " →")
                    Text(richInline("<b>$eq</b>"), style = Type.body)
                    if (r.response.isNotEmpty()) {
                        Text("You said ${r.response}", style = Type.label.copy(fontWeight = FontWeight.Normal), color = Palette.BAD)
                    }
                    if (p.explanation.isNotEmpty()) RichText(p.explanation, style = Type.muted)
                }
            }
        }
    }
    if (s.n == 0) Text("Nothing answered this time.", style = Type.body)
}
