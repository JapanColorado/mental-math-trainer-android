package dev.russell.mmtrainer.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    data object Onboarding : Screen
    data object Home : Screen
    data class LessonPage(val kicker: String, val title: String, val body: String, val button: String) : Screen
    data object Drill : Screen
    data object SummaryPage : Screen
    data object Progress : Screen
    data object Library : Screen
    data class Topic(val itemId: String) : Screen
    data object Practice : Screen
    data object SettingsPage : Screen
}

/** The sections in the bottom navigation bar. Everything else opens on top of these. */
val Screen.isTopLevel: Boolean
    get() = this == Screen.Home || this == Screen.Practice || this == Screen.Library || this == Screen.Progress

data class HomeData(
    val streak: Int,
    val activity: List<Boolean>,       // the last 7 study days, oldest first
    val minutes: Int,
    val headline: String,
    val nextTopic: String?,            // rich text
    val sessionsToday: Int,
    val benchValue: String,
    val benchNote: String,
    val benchTaken: Boolean,
    val benchStale: Boolean,
    val paceValue: String,
    val paceNote: String,
    val paceOnTarget: Boolean,
    val topicsValue: String,
    val topicsNote: String,
)

/** Everything a topic row needs: name, kind and a status line. */
data class TopicRow(
    val id: String, val name: String, val isSkill: Boolean, val status: String, val unlocked: Boolean,
    val mastered: Boolean, val progress: Float,
)

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
data class SummaryExtras(val benchmarks: List<Benchmark>, val audio: Boolean, val weakSpots: Boolean)

val DURATIONS: List<Pair<String, Double?>> =
    listOf("1 min" to 1.0, "5 min" to 5.0, "10 min" to 10.0, "No limit" to null)

/** "A", "A and B", "A, B and C". */
private fun namesList(ids: List<String>): String {
    val names = ids.map { Content.nameOf(it) }
    return if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
}

fun orderedIds(): List<String> {
    val ids = Curriculum.ORDER.filter { Content.exists(it) }
    return ids + (Content.SKILLS.keys + Content.DECKS.keys).filter { it !in ids }
}

class AppViewModel(
    private val store: Store,
    private val newSpeaker: () -> Speaker,
) : ViewModel() {
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    /** The first screen is known and loaded; the splash screen holds until then. */
    var ready by mutableStateOf(false)
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
    private var goAgainAction: (() -> Unit)? = null
    private var weakSession = false

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
    /** Topics in the current weak-spot selection; null until loaded. */
    var weakCount by mutableStateOf<Int?>(null)
        private set
    var practiceDuration by mutableIntStateOf(1)
        private set
    var practiceAudio by mutableStateOf(false)
        private set
    var practiceExcluded by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        refreshSettings()
        io {
            class Startup(val home: HomeData, val fresh: Boolean, val duration: Int, val audio: Boolean, val excluded: Set<String>)
            val start = withContext(StoreThread) {
                val fresh = !store.settingBool(Settings.ONBOARDED) && store.introduced().isEmpty() && store.benchmarks().isEmpty()
                if (!fresh) store.setSetting(Settings.ONBOARDED, true)
                Startup(loadHome(), fresh, store.settingInt(Settings.PRACTICE_DURATION).coerceIn(DURATIONS.indices),
                    store.settingBool(Settings.PRACTICE_AUDIO),
                    store.settingString(Settings.PRACTICE_EXCLUDED).split(',').filter { it.isNotEmpty() }.toSet())
            }
            home = start.home
            practiceDuration = start.duration
            practiceAudio = start.audio
            practiceExcluded = start.excluded
            if (start.fresh) screen = Screen.Onboarding
            ready = true
        }
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

    fun openPractice() {
        screen = Screen.Practice
        io {
            withContext(StoreThread) { loadLibrary(onlyUnlocked = true) to store.weakSpots().size }.let { (rows, weak) ->
                practiceRows = rows
                weakCount = weak
            }
        }
    }

    /** The welcome flow is done; [then] picks where it leads. */
    fun finishOnboarding(then: () -> Unit = ::goHome) {
        io { withContext(StoreThread) { store.setSetting(Settings.ONBOARDED, true) } }
        then()
    }

    fun choosePracticeDuration(index: Int) {
        practiceDuration = index
        io { withContext(StoreThread) { store.setSetting(Settings.PRACTICE_DURATION, index) } }
    }

    fun setPracticeMode(audio: Boolean) {
        practiceAudio = audio
        io { withContext(StoreThread) { store.setSetting(Settings.PRACTICE_AUDIO, audio) } }
    }

    fun choosePracticeExcluded(ids: Set<String>) {
        practiceExcluded = ids
        io { withContext(StoreThread) { store.setSetting(Settings.PRACTICE_EXCLUDED, ids.joinToString(",")) } }
    }

    fun openSettings() {
        refreshSettings()
        screen = Screen.SettingsPage
    }

    /** System back. Returns false when the app should close. */
    fun back(): Boolean {
        when (screen) {
            Screen.Home -> return false
            Screen.Onboarding -> finishOnboarding()
            Screen.Drill -> confirmQuit = true
            is Screen.LessonPage -> if (inSession) finish() else goHome()
            is Screen.Topic -> openLibrary()
            Screen.Practice, Screen.SummaryPage, Screen.Progress, Screen.Library, Screen.SettingsPage -> goHome()
        }
        return true
    }

    fun dismissQuit() { confirmQuit = false }

    // ------------------------------------------------------------ loaders (store thread)

    private fun loadHome(): HomeData {
        val s = store
        val minutes = s.settingInt(Settings.SESSION_MINUTES)
        val intro = s.introduced()
        val headline: String
        var nextTopic: String? = null
        if (intro.isEmpty()) {
            headline = "Your first session starts with the basics and moves on quickly if you already know them."
        } else {
            val dueCards = s.dueCards().size
            val dueSkills = intro.keys.count { Content.isSkill(it) && s.skillStatus(it).due }
            headline = when {
                dueCards == 0 && dueSkills == 0 -> "You're all caught up. Another session keeps things sharp."
                dueCards == 0 -> "${count(dueSkills, "skill")} due for review."
                dueSkills == 0 -> "${count(dueCards, "fact")} due for review."
                else -> "${count(dueCards, "fact")} and ${count(dueSkills, "skill")} due for review."
            }
            val nxt = s.nextToIntroduce()
            val waiting = s.nextInOrder()
            nextTopic = when {
                nxt != null && s.canIntroduce() -> "Up next: <b>${Content.nameOf(nxt)}</b>"
                nxt != null -> "Up next, once current topics settle: ${Content.nameOf(nxt)}"
                waiting != null -> "Up next: ${Content.nameOf(waiting)}, after you master ${namesList(s.missingPrerequisites(waiting))}"
                else -> null
            }
        }
        val benches = s.benchmarks()
        var benchValue = "—"
        var benchNote = "Not taken yet"
        var stale = false
        if (benches.isNotEmpty()) {
            val last = benches.last()
            val first = benches.first()
            benchValue = fmtF(last.score, 0)
            stale = (System.currentTimeMillis() / 1000.0 - last.startTs) / 86400 >= 7
            benchNote = when {
                stale -> "Time to retake"
                benches.size > 1 && first.score > 0 -> "${signed0((last.score / first.score - 1) * 100)}% since first"
                else -> "Your baseline"
            }
        }
        val pace = s.dailyPace()
        val recentPace = pace.takeLast(7).map { it.pace }.takeIf { it.isNotEmpty() }?.average()
        val mastered = intro.keys.count { Content.isSkill(it) && s.skillStatus(it).mastered }
        val total = Content.SKILLS.size + Content.DECKS.size
        return HomeData(
            streak = s.streak(),
            activity = s.recentActivity(7),
            minutes = minutes,
            headline = headline,
            nextTopic = nextTopic,
            sessionsToday = s.sessionsToday(),
            benchValue = benchValue,
            benchNote = benchNote,
            benchTaken = benches.isNotEmpty(),
            benchStale = stale,
            paceValue = recentPace?.let { fmtF(it, 0) + "%" } ?: "—",
            paceNote = if (recentPace != null) "of target" else "No data yet",
            paceOnTarget = recentPace != null && recentPace >= 100,
            topicsValue = "${intro.size}/$total",
            topicsNote = if (mastered > 0) "$mastered mastered" else "unlocked",
        )
    }

    private fun statusText(itemId: String): String {
        if (!store.isIntroduced(itemId)) return "Locked"
        if (Content.isSkill(itemId)) {
            val st = store.skillStatus(itemId)
            if (st.mastered) return "Mastered"
            return "Level ${st.level + 1}/${st.nLevels}"
        }
        val s = store.deckStats(itemId)
        return "${s.seen}/${s.total} seen"
    }

    private fun topicRow(itemId: String): TopicRow {
        val unlocked = store.isIntroduced(itemId)
        val skill = Content.isSkill(itemId)
        val status = if (skill && unlocked) store.skillStatus(itemId) else null
        val progress = when {
            !unlocked -> 0f
            status != null -> status.progress.toFloat()
            else -> store.deckStats(itemId).let { if (it.total > 0) it.seen.toFloat() / it.total else 0f }
        }
        return TopicRow(itemId, Content.nameOf(itemId), skill, statusText(itemId), unlocked, status?.mastered == true, progress)
    }

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
            "Builds on " + ids.joinToString(" · ") { id ->
                if (store.prerequisiteMet(id)) "${Content.nameOf(id)} ✓" else "<b>${Content.nameOf(id)}</b> (not yet)"
            }
        }
        return if (Content.isSkill(itemId)) {
            val skill = Content.SKILLS.getValue(itemId)
            val current = if (store.isIntroduced(itemId)) store.skillStatus(itemId).level else 0
            TopicDetail(row, obj.lesson,
                skill.levels.mapIndexed { i, l -> "Level ${i + 1}: ${l.description} (target ${fmtTarget(l.targetSeconds)})" },
                current, count(skill.levels.size, "level"), prereqs)
        } else {
            val s = store.deckStats(itemId)
            TopicDetail(row, obj.lesson, emptyList(), 0,
                "${count(s.total, "fact")} · ${s.seen} seen · ${s.mature} solid · ${s.due} due", prereqs)
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
                if (st.mastered) "Mastered" else "Level ${st.level + 1} of ${st.nLevels}: ${skill.levels[st.level].description}")
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
                .map { (lvl, pts) -> "Level ${lvl + 1}" to pts.map { it.day to it.medianS } }
        }

    // ------------------------------------------------------------ settings

    private fun refreshSettings() =
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
        if (inSession) return
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

    /** A session on whatever is weakest right now: a quick session's length, off the review schedule. */
    fun startWeakSpots() {
        if (inSession) return
        io {
            val (weak, minutes) = withContext(StoreThread) { store.weakSpots() to store.settingInt(Settings.SESSION_MINUTES) }
            if (!weak.isEmpty) start(Mode.PRACTICE, weak.skills, minutes.toDouble(), focusCards = weak.cards, weak = true)
        }
    }

    /** Whether the summary can offer the same kind of session again. */
    val canGoAgain: Boolean get() = goAgainAction != null

    fun goAgain() {
        goAgainAction?.invoke()
    }

    fun startBenchmark() {
        if (inSession) return
        inSession = true
        goAgainAction = null
        io {
            session = withContext(StoreThread) { Session(store, Mode.BENCHMARK) }
            screen = Screen.LessonPage(
                kicker = "Benchmark",
                title = "Measure your speed",
                body = "<p>32 questions across every track, always at the same difficulty, so your score compares fairly " +
                    "from week to week. It takes about 6 minutes.</p>" +
                    "<h3>How it's scored</h3>" +
                    "<p>Target times of the questions you got right, divided by the total time you took, × 100. " +
                    "Every answer correct at target speed scores 100, and faster scores more.</p>" +
                    "<h3>Stuck on one?</h3>" +
                    "<p>Press <b>Show</b> on a blank answer to skip it. That's expected at the start, and it's " +
                    "exactly what should improve.</p>" +
                    "<tip>Take it now as your baseline, then about once a week.</tip>",
                button = "Begin",
            )
        }
    }

    private fun start(
        mode: Mode, itemIds: List<String>? = null, minutes: Double? = null, audio: Boolean = false,
        focusCards: List<String> = emptyList(), weak: Boolean = false,
    ) {
        if (inSession) return  // a second tap while the first is still starting
        inSession = true
        weakSession = weak
        goAgainAction = when {
            mode == Mode.DAILY -> ::startDaily
            weakSession -> ::startWeakSpots
            else -> ({ start(mode, itemIds, minutes, audio) })
        }
        this.audio = audio
        speechFailed = false
        if (audio && speaker == null) speaker = newSpeaker()  // started early: the engine takes a moment
        io {
            session = withContext(StoreThread) {
                Session(store, mode, minutes = minutes, itemIds = itemIds, audio = audio, focusCards = focusCards)
            }
            nextItem()
        }
    }

    /** Lesson "Start practising" button. */
    fun lessonDone() {
        if (inSession) nextItem() else goHome()
    }

    fun nextItem() {
        autoJob?.cancel()
        val s = session ?: return
        if (busy) return
        busy = true
        io {
            val next = withContext(StoreThread) { s.nextItem() }
            busy = false
            if (next == null) return@io finish()
            if (next.isLesson) {
                speaker?.stop()
                val lesson = next.lesson!!
                screen = Screen.LessonPage(if (next.sourceId.startsWith("d_")) "New facts" else "New technique",
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
        if (screen != Screen.Drill) return  // a tap during the transition away from the drill
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
            warning = "That isn't a number. Try something like 0.75, 3/4 or 2.4e7."
            return
        }
        if ("/" in text && it.sourceId in DECIMAL_ONLY) {
            warning = "Answer as a decimal, like 0.143."
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
                if (r.response.isNotEmpty()) wrongPulse++  // typed and wrong, not a blank answer revealed
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
        val wasAudio = audio
        val wasWeak = weakSession
        audio = false
        io {
            val (sum, benches) = withContext(StoreThread) { s.finish() to store.benchmarks() }
            if ((sum.n == 0 && sum.mode != Mode.DAILY) || (sum.mode == Mode.BENCHMARK && sum.score == null)) {
                goHome()
            } else {
                summary = sum
                summaryExtras = SummaryExtras(benches, wasAudio, wasWeak)
                screen = Screen.SummaryPage
            }
            item = null
            inSession = false
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

        fun factory(store: Store, newSpeaker: () -> Speaker) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(store, newSpeaker) as T
            }
    }
}

/** Median-style seconds, "4.2 s". */
fun fmtSecs(s: Double?): String = if (s == null) "—" else fmtF(s, 1) + " s"

/** Target seconds, "4 s" / "2.5 s". */
fun fmtTarget(s: Double): String = fmtG(s) + " s"

/** f"{x:+.0f}" */
fun signed0(x: Double): String = fmtF(x, 0).let { if (it.startsWith("-")) it else "+$it" }
