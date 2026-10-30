package dev.russell.mmtrainer.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.russell.mmtrainer.Answers
import dev.russell.mmtrainer.Benchmark
import dev.russell.mmtrainer.Curriculum
import dev.russell.mmtrainer.DeckStats
import dev.russell.mmtrainer.Item
import dev.russell.mmtrainer.Mode
import dev.russell.mmtrainer.Result
import dev.russell.mmtrainer.Session
import dev.russell.mmtrainer.Settings
import dev.russell.mmtrainer.SkillStatus
import dev.russell.mmtrainer.Speech
import dev.russell.mmtrainer.Store
import dev.russell.mmtrainer.Summary
import dev.russell.mmtrainer.audio.Speaker
import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.content.Lesson
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.fmtG
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Every Store/Session call runs on this one thread: SQLite access stays serialized and off the UI thread. */
val StoreThread = Executors.newSingleThreadExecutor { r -> Thread(r, "store") }.asCoroutineDispatcher()

private val DECIMAL_ONLY = setOf("s_frac", "d_recip")  // the prompt is a fraction, so typing a fraction back isn't an answer

sealed interface Screen {
    data object Home : Screen
    data class LessonPage(val kicker: String, val title: String, val body: String, val button: String) : Screen
    data object Drill : Screen
    data object SummaryPage : Screen
    data object Progress : Screen
    data object Library : Screen
    data class Topic(val itemId: String) : Screen
    data class Practice(val audio: Boolean) : Screen
    data object SettingsPage : Screen
}

data class HomeData(
    val streak: Int,
    val minutes: Int,
    val lines: List<String>,
    val sessionsToday: Int,
    val benchValue: String,
    val benchNote: String,
    val benchStale: Boolean,
    val paceValue: String,
    val paceNote: String,
    val topicsValue: String,
    val topicsNote: String,
)

/** Everything a topic row needs: name, kind and a status line. */
data class TopicRow(val id: String, val name: String, val isSkill: Boolean, val status: String, val unlocked: Boolean)

data class TopicDetail(
    val row: TopicRow,
    val lesson: Lesson,
    val levels: List<String>,          // "L1: … (target 4 s)"; empty for decks
    val currentLevel: Int,
    val summaryLine: String,
    val prerequisites: String?,        // rich text "Builds on: …", null when the topic has none
)

data class SettingsData(
    val minutes: Int, val newTopics: Int, val learningLimit: Int, val newCards: Int,
    val autoAdvance: Boolean, val showTargetBar: Boolean, val fontScale: Double, val haptics: Boolean,
    val dynamicColor: Boolean, val autoSubmit: Boolean,
)

data class SkillRow(val id: String, val name: String, val track: String, val status: SkillStatus, val levelText: String)
data class DeckRow(val id: String, val name: String, val track: String, val stats: DeckStats)

data class ProgressData(
    val bench: List<Pair<Double, Double>>,
    val pace: List<Pair<Double, Double>>,
    val accuracy: List<Pair<Double, Double>>,
    val minutes: List<Pair<Double, Double>>,
    val skills: List<SkillRow>,
    val decks: List<DeckRow>,
)

/** Extra numbers the summary screen shows that aren't part of the core Summary. */
data class SummaryExtras(val benchmarks: List<Benchmark>, val audio: Boolean)

val DURATIONS: List<Pair<String, Double?>> =
    listOf("1-minute sprint" to 1.0, "5 minutes" to 5.0, "10 minutes" to 10.0, "Until I stop" to null)

/** "A", "A and B", "A, B and C". */
private fun namesList(ids: List<String>): String {
    val names = ids.map { Content.nameOf(it) }
    return if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
}

fun orderedIds(): List<String> {
    val ids = Curriculum.ORDER.filter { Content.exists(it) }
    return ids + (Content.SKILLS.keys + Content.DECKS.keys).filter { it !in ids }
}

class AppViewModel(private val store: Store, private val newSpeaker: () -> Speaker) : ViewModel() {
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var home by mutableStateOf<HomeData?>(null)
        private set
    var settings by mutableStateOf<SettingsData?>(null)
        private set

    // ------------------------------------------------------------ session / drill state
    private var session: Session? = null
    var item by mutableStateOf<Item?>(null)
        private set
    var input by mutableStateOf("")
        private set
    var result by mutableStateOf<Result?>(null)
        private set
    var warning by mutableStateOf<String?>(null)
        private set
    var hinted by mutableStateOf(false)
        private set
    var lessonDialog by mutableStateOf<Lesson?>(null)
        private set
    var confirmQuit by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    /** elapsedRealtime (ms) when the current item appeared; the answer time is measured from here. */
    var shownAt by mutableStateOf(0L)
        private set
    var answeredAfter by mutableStateOf(0.0)
        private set
    var wrongPulse by mutableStateOf(0)
        private set
    private var autoJob: Job? = null
    private var inSession = false

    // audio practice: the problem is read aloud instead of shown. The clock starts as its last word is
    // said: not at the end of the speech, so typing during the last word doesn't make an answer instant.
    var audio by mutableStateOf(false)
        private set
    /** The current problem is still being read out for the first time, up to its last word; the clock hasn't started. */
    var listening by mutableStateOf(false)
        private set
    /** Text-to-speech isn't available, so audio practice falls back to showing the problems. */
    var speechFailed by mutableStateOf(false)
        private set
    private var speaker: Speaker? = null

    var summary by mutableStateOf<Summary?>(null)
        private set
    var summaryExtras by mutableStateOf<SummaryExtras?>(null)
        private set

    // ------------------------------------------------------------ other screens
    var progress by mutableStateOf<ProgressData?>(null)
        private set
    var library by mutableStateOf<List<Pair<String, List<TopicRow>>>?>(null)
        private set
    var topic by mutableStateOf<TopicDetail?>(null)
        private set
    var practiceRows by mutableStateOf<List<Pair<String, List<TopicRow>>>?>(null)
        private set

    init {
        refreshSettings()
        goHome()
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch { block() }

    // ------------------------------------------------------------ navigation

    fun goHome() {
        screen = Screen.Home
        io {
            home = withContext(StoreThread) { loadHome() }
        }
    }

    fun openProgress() {
        screen = Screen.Progress
        io { progress = withContext(StoreThread) { loadProgress() } }
    }

    fun openLibrary() {
        screen = Screen.Library
        io { library = withContext(StoreThread) { loadLibrary(onlyUnlocked = false) } }
    }

    fun openTopic(itemId: String) {
        topic = null
        screen = Screen.Topic(itemId)
        io { topic = withContext(StoreThread) { loadTopic(itemId) } }
    }

    fun openPractice(audio: Boolean = false) {
        screen = Screen.Practice(audio)
        io { practiceRows = withContext(StoreThread) { loadLibrary(onlyUnlocked = true) } }
    }

    fun openSettings() {
        refreshSettings()
        screen = Screen.SettingsPage
    }

    /** System back. Returns false when the app should close. */
    fun back(): Boolean {
        when (screen) {
            Screen.Home -> return false
            Screen.Drill -> confirmQuit = true
            is Screen.LessonPage -> if (inSession) finish() else goHome()
            is Screen.Topic -> openLibrary()
            is Screen.Practice, Screen.SummaryPage, Screen.Progress, Screen.Library, Screen.SettingsPage -> goHome()
        }
        return true
    }

    fun dismissQuit() { confirmQuit = false }

    // ------------------------------------------------------------ loaders (store thread)

    private fun loadHome(): HomeData {
        val s = store
        val streak = s.streak()
        val minutes = s.settingInt(Settings.SESSION_MINUTES)
        val intro = s.introduced()
        val lines = mutableListOf<String>()
        if (intro.isEmpty()) {
            lines += "Welcome! Each quick session mixes memorizing key facts, short lessons on mental " +
                "techniques, and timed interleaved drills. Your first session starts with the basics " +
                "and moves on quickly if you already know them."
            if (s.benchmarks().isEmpty()) {
                lines += "Tip: take the <b>Benchmark</b> first, so you can measure how much faster you get."
            }
        } else {
            val dueCards = s.dueCards().size
            val dueSkills = intro.keys.count { Content.isSkill(it) && s.skillStatus(it).due }
            lines += "$dueCards facts and $dueSkills skills due for review."
            val nxt = s.nextToIntroduce()
            val waiting = s.nextInOrder()
            if (nxt != null && s.canIntroduce()) lines += "Next new topic: <b>${Content.nameOf(nxt)}</b>"
            else if (nxt != null) lines += "Next new topic (once current ones settle): ${Content.nameOf(nxt)}"
            else if (waiting != null) lines += "Next new topic: ${Content.nameOf(waiting)}, after you master " +
                namesList(s.missingPrerequisites(waiting))
        }
        val benches = s.benchmarks()
        var benchValue = "—"
        var benchNote = "Take a 32-question baseline (~6 min)"
        var stale = false
        if (benches.isNotEmpty()) {
            val last = benches.last()
            val first = benches.first()
            benchValue = fmtF(last.score, 0)
            val days = (System.currentTimeMillis() / 1000.0 - last.startTs) / 86400
            val note = mutableListOf<String>()
            if (benches.size > 1 && first.score > 0) {
                val change = (last.score / first.score - 1) * 100
                note += "baseline ${fmtF(first.score, 0)} (${signed0(change)}%)"
            }
            if (days >= 7) stale = true
            benchNote = note.joinToString(" · ").ifEmpty { "100 = all correct at fluent target speed" }
        }
        val pace = s.dailyPace()
        val paceValue: String
        val paceNote: String
        if (pace.isNotEmpty()) {
            val recent = pace.takeLast(7).map { it.pace }
            paceValue = fmtF(recent.average(), 0)
            paceNote = "first days: ${fmtF(pace.take(3).map { it.pace }.average(), 0)} · 100 = target speed"
        } else {
            paceValue = "—"
            paceNote = "100 = answering at the fluent target time"
        }
        val mastered = intro.keys.count { Content.isSkill(it) && s.skillStatus(it).mastered }
        val total = Content.SKILLS.size + Content.DECKS.size
        return HomeData(streak, minutes, lines, s.sessionsToday(), benchValue, benchNote, stale, paceValue, paceNote,
            "${intro.size}/$total", "unlocked · $mastered skills mastered")
    }

    private fun statusText(itemId: String): String {
        if (!store.isIntroduced(itemId)) return "Locked"
        if (Content.isSkill(itemId)) {
            val st = store.skillStatus(itemId)
            if (st.mastered) return "Mastered"
            return "Level ${st.level + 1}/${st.nLevels}"
        }
        val s = store.deckStats(itemId)
        return "${s.seen}/${s.total} seen · ${s.mature} solid"
    }

    private fun topicRow(itemId: String) = TopicRow(itemId, Content.nameOf(itemId), Content.isSkill(itemId),
        statusText(itemId), store.isIntroduced(itemId))

    private fun loadLibrary(onlyUnlocked: Boolean): List<Pair<String, List<TopicRow>>> {
        val intro = store.introduced()
        val byTrack = linkedMapOf<String, MutableList<TopicRow>>()
        for (key in dev.russell.mmtrainer.content.TRACKS.keys) byTrack[key] = mutableListOf()
        for (id in orderedIds()) {
            if (onlyUnlocked && id !in intro) continue
            val track = Content.get(id).track
            byTrack.getOrPut(track) { mutableListOf() }.add(topicRow(id))
        }
        return byTrack.filterValues { it.isNotEmpty() }
            .map { (k, v) -> (dev.russell.mmtrainer.content.TRACKS[k] ?: k) to v }
    }

    private fun loadTopic(itemId: String): TopicDetail {
        val row = topicRow(itemId)
        val obj = Content.get(itemId)
        val prereqs = store.prerequisites[itemId].orEmpty().takeIf { it.isNotEmpty() }?.let { ids ->
            "Builds on: " + ids.joinToString(" · ") { id ->
                if (store.prerequisiteMet(id)) "${Content.nameOf(id)} ✓" else "<b>${Content.nameOf(id)}</b> (not yet)"
            }
        }
        return if (Content.isSkill(itemId)) {
            val skill = Content.SKILLS.getValue(itemId)
            val current = if (store.isIntroduced(itemId)) store.skillStatus(itemId).level else 0
            TopicDetail(row, obj.lesson,
                skill.levels.mapIndexed { i, l -> "L${i + 1}: ${l.description} (target ${fmtTarget(l.targetSeconds)})" },
                current, "${skill.levels.size} levels", prereqs)
        } else {
            val s = store.deckStats(itemId)
            TopicDetail(row, obj.lesson, emptyList(), 0,
                "${s.total} facts · ${s.seen} seen · ${s.mature} solid (interval ≥ 3 weeks) · ${s.due} due", prereqs)
        }
    }

    private fun loadProgress(): ProgressData {
        val s = store
        val pace = s.dailyPace()
        val intro = s.introduced()
        val skills = orderedIds().filter { Content.isSkill(it) && it in intro }.map { sid ->
            val st = s.skillStatus(sid)
            val skill = Content.SKILLS.getValue(sid)
            SkillRow(sid, skill.name, dev.russell.mmtrainer.content.TRACKS[skill.track] ?: skill.track, st,
                if (st.mastered) "Mastered" else "${st.level + 1}/${st.nLevels}: ${skill.levels[st.level].description}")
        }
        val decks = orderedIds().filter { Content.isDeck(it) && it in intro }.map { did ->
            val d = Content.DECKS.getValue(did)
            DeckRow(did, d.name, dev.russell.mmtrainer.content.TRACKS[d.track] ?: d.track, s.deckStats(did))
        }
        return ProgressData(
            bench = s.benchmarks().map { it.startTs to it.score },
            pace = pace.map { it.day to it.pace },
            accuracy = pace.map { it.day to it.accuracy * 100 },
            minutes = s.practiceMinutesByDay().map { (d, m) -> d to m },
            skills = skills,
            decks = decks,
        )
    }

    /** Per level: (level label, [(day, median seconds)]). */
    suspend fun skillHistory(skillId: String): List<Pair<String, List<Pair<Double, Double>>>> =
        withContext(StoreThread) {
            store.skillHistory(skillId).groupBy { it.level }.toSortedMap()
                .map { (lvl, pts) -> "L${lvl + 1}" to pts.map { it.day to it.medianS } }
        }

    // ------------------------------------------------------------ settings

    private fun refreshSettings() {
        io {
            settings = withContext(StoreThread) {
                SettingsData(
                    store.settingInt(Settings.SESSION_MINUTES), store.settingInt(Settings.MAX_NEW_TOPICS),
                    store.settingInt(Settings.LEARNING_LIMIT), store.settingInt(Settings.NEW_CARDS),
                    store.settingBool(Settings.AUTO_ADVANCE), store.settingBool(Settings.SHOW_TARGET_BAR),
                    store.settingDouble(Settings.FONT_SCALE), store.settingBool(Settings.HAPTICS),
                    store.settingBool(Settings.DYNAMIC_COLOR), store.settingBool(Settings.AUTO_SUBMIT),
                )
            }
        }
    }

    fun saveSettings(d: SettingsData) {
        settings = d
        io {
            withContext(StoreThread) {
                store.setSetting(Settings.SESSION_MINUTES, d.minutes)
                store.setSetting(Settings.MAX_NEW_TOPICS, d.newTopics)
                store.setSetting(Settings.LEARNING_LIMIT, d.learningLimit)
                store.setSetting(Settings.NEW_CARDS, d.newCards)
                store.setSetting(Settings.AUTO_ADVANCE, d.autoAdvance)
                store.setSetting(Settings.SHOW_TARGET_BAR, d.showTargetBar)
                store.setSetting(Settings.FONT_SCALE, Math.round(d.fontScale * 100) / 100.0)
                store.setSetting(Settings.HAPTICS, d.haptics)
                store.setSetting(Settings.DYNAMIC_COLOR, d.dynamicColor)
                store.setSetting(Settings.AUTO_SUBMIT, d.autoSubmit)
            }
        }
    }

    fun resetProgress() {
        io {
            withContext(StoreThread) { store.reset() }
            goHome()
        }
    }

    // ------------------------------------------------------------ library actions

    fun setSkillLevel(skillId: String, level: Int) {
        io {
            topic = withContext(StoreThread) {
                store.setSkillLevel(skillId, level)
                loadTopic(skillId)
            }
        }
    }

    fun practiseTopic(itemId: String, minutes: Double?) {
        io {
            withContext(StoreThread) { store.introduce(itemId) }
            start(Mode.PRACTICE, listOf(itemId), minutes)
        }
    }

    // ------------------------------------------------------------ session flow

    fun startDaily() = start(Mode.DAILY)

    fun startPractice(ids: List<String>, minutes: Double?, audio: Boolean = false) {
        if (ids.isNotEmpty()) start(Mode.PRACTICE, ids, minutes, audio)
    }

    fun startBenchmark() {
        io {
            session = withContext(StoreThread) { Session(store, Mode.BENCHMARK) }
            inSession = true
            screen = Screen.LessonPage(
                kicker = "BENCHMARK",
                title = "Benchmark",
                body = "<p>32 questions (two per skill) spanning every track, always at the same fixed difficulty — so the score is " +
                    "comparable from week to week no matter what you've unlocked.</p>" +
                    "<p><b>Score</b> = (sum of target times of the questions you got right) ÷ (total time taken) × 100. " +
                    "Answering everything correctly at the fluent target pace scores 100; faster scores more.</p>" +
                    "<p>Don't know how to do one yet? Press <b>Show</b> on a blank answer to skip it — that's expected at the " +
                    "start and is exactly what the benchmark should show improving.</p>" +
                    "<p>Take it now as your baseline, then about once a week.</p>",
                button = "Begin",
            )
        }
    }

    private fun start(mode: Mode, itemIds: List<String>? = null, minutes: Double? = null, audio: Boolean = false) {
        this.audio = audio
        speechFailed = false
        if (audio && speaker == null) speaker = newSpeaker()  // started early: the engine takes a moment
        io {
            session = withContext(StoreThread) { Session(store, mode, minutes = minutes, itemIds = itemIds, audio = audio) }
            inSession = true
            nextItem()
        }
    }

    /** Lesson "Start practising" button. */
    fun lessonDone() {
        if (inSession) nextItem() else goHome()
    }

    fun nextItem() {
        autoJob?.cancel()
        val s = session ?: return goHome()
        if (busy) return
        busy = true
        io {
            val next = withContext(StoreThread) { s.nextItem() }
            busy = false
            if (next == null) return@io finish()
            if (next.isLesson) {
                speaker?.stop()
                val lesson = next.lesson!!
                screen = Screen.LessonPage(if (next.sourceId.startsWith("d_")) "NEW FACTS" else "NEW TECHNIQUE",
                    lesson.title, lesson.body, "Start practising")
            } else {
                item = next
                input = ""
                result = null
                warning = null
                hinted = false
                screen = Screen.Drill
                shownAt = SystemClock.elapsedRealtime()
                if (audio && !speechFailed) {
                    listening = true
                    speakPrompt(next)
                } else {
                    listening = false
                }
            }
        }
    }

    private fun speakPrompt(current: Item) {
        speaker?.speak(Speech.speakable(current.problem!!.prompt)) { spoken ->
            if (item !== current || !listening) return@speak  // a replay after the clock started, or a stale callback
            if (!spoken) speechFailed = true
            listening = false
            shownAt = SystemClock.elapsedRealtime()
            if (typedInFull()) submit()  // typed before the last word
        }
    }

    /** Audio practice: say the current problem again. The clock keeps running once it has started. */
    fun replay() {
        val current = item ?: return
        if (audio && !speechFailed) speakPrompt(current)
    }

    val fractionDone: Double get() = session?.fractionDone ?: 0.0

    // keypad -----------------------------------------------------------------

    fun key(k: String) {
        if (result != null) {
            if (k == ENTER) advance()
            return
        }
        if (busy) return  // an answer is being graded; don't let more typing change what was sent
        warning = null
        if (k == ENTER && listening) return
        when (k) {
            // Show on a blank answer waits a moment, so a tap meant for the previous question's
            // Next (or one landing just after an auto-advance) doesn't skip this one.
            ENTER -> if (input.isNotBlank() || SystemClock.elapsedRealtime() - shownAt >= SHOW_DELAY_MS) submit()
            BACKSPACE -> input = input.dropLast(1)
            CLEAR -> input = ""
            else -> if (input.length < 24) input += k
        }
        if (k != ENTER && k != CLEAR && typedInFull()) submit()
    }

    /**
     * Auto-submit check: the answer is right, or as long as the right one and so finished (a miss).
     * Only exact problems qualify: with a tolerance, a prefix of a wrong answer can already be
     * within it (4 on the way to 4.5 when the answer is 4.1 ± 5%).
     */
    private fun typedInFull(): Boolean {
        val p = item?.problem ?: return false
        if (listening || settings?.autoSubmit == false || p.tolAbs > 0 || p.tolRel > 0) return false
        if ("/" in input && item?.sourceId in DECIMAL_ONLY) return false
        val value = Answers.parse(input) ?: return false
        return p.isCorrect(value) || Answers.fullLength(input, p.answer)
    }

    private fun submit() {
        val it = item ?: return
        val s = session ?: return
        if (busy || listening) return
        val text = input
        val value = Answers.parse(text)
        if (text.isNotBlank() && value == null) {
            warning = "Couldn't read that as a number — try e.g. 0.75, 3/4, 2.4e7"
            return
        }
        if ("/" in text && it.sourceId in DECIMAL_ONLY) {
            warning = "Answer as a decimal, e.g. 0.143"
            return
        }
        val seconds = (SystemClock.elapsedRealtime() - shownAt) / 1000.0
        answeredAfter = seconds
        busy = true
        io {
            val r = withContext(StoreThread) { s.submit(it, text.trim(), value, seconds, hinted) }
            busy = false
            result = r
            if (!(r.correct && !r.hinted)) {
                wrongPulse++
                if (audio) speaker?.speak(Speech.speakable(equation(r.item.problem!!)))
            } else if (settings?.autoAdvance != false && r.promotedTo == null) {
                val p = r.item.problem!!
                val approx = p.tolAbs > 0 || p.tolRel > 0
                autoJob = viewModelScope.launch {
                    delay(if (approx) 1300 else 650)
                    advance()
                }
            }
        }
    }

    fun advance() {
        autoJob?.cancel()
        if (result != null) nextItem()
    }

    /** Read this topic's lesson. Counts as a hint if asked before answering. */
    fun showLesson() {
        val it = item ?: return
        if (result == null) hinted = true
        autoJob?.cancel()
        lessonDialog = Content.get(it.sourceId).lesson
    }

    fun closeLesson() { lessonDialog = null }

    fun finish() {
        autoJob?.cancel()
        speaker?.stop()
        listening = false
        confirmQuit = false
        val s = session ?: return goHome()
        session = null
        inSession = false
        item = null
        val wasAudio = audio
        audio = false
        io {
            val (sum, benches) = withContext(StoreThread) { s.finish() to store.benchmarks() }
            if ((sum.n == 0 && sum.mode != Mode.DAILY) || (sum.mode == Mode.BENCHMARK && sum.score == null)) {
                goHome()
            } else {
                summary = sum
                summaryExtras = SummaryExtras(benches, wasAudio)
                screen = Screen.SummaryPage
            }
        }
    }

    override fun onCleared() {
        speaker?.shutdown()
    }

    companion object {
        const val ENTER = "\n"
        const val BACKSPACE = "\b"
        const val CLEAR = "\u0018"
        const val SHOW_DELAY_MS = 500L

        fun factory(store: Store, newSpeaker: () -> Speaker) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(store, newSpeaker) as T
        }
    }
}

/** Median-style seconds, "4.2 s". */
fun fmtSecs(s: Double?): String = if (s == null) "—" else fmtF(s, 1) + " s"

/** Target seconds, "4 s" / "2.5 s". */
fun fmtTarget(s: Double): String = fmtG(s) + " s"

/** f"{x:+.0f}" */
fun signed0(x: Double): String = fmtF(x, 0).let { if (it.startsWith("-")) it else "+$it" }
