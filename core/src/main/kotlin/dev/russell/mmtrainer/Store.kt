package dev.russell.mmtrainer

import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.median
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Persistent progress: SQLite storage plus the learning model built on top of it.
 *
 * - Cards (memorized facts) use an SM-2 style spaced-repetition schedule where the grade is
 *   derived from correctness *and* response time, so facts only space out once recall is fast.
 * - Skills (procedures) have levels. A level is passed once recent accuracy and median
 *   response time beat the level's target. Skills are also spaced: a skill practised well
 *   comes back after a growing interval, one practised poorly is due again tomorrow.
 */

const val DAY = 86400.0
const val ROLLOVER_HOUR = 4  // a "day" ends at 4 am local time

// Card grades
const val AGAIN = 0
const val HARD = 1
const val GOOD = 2
const val EASY = 3

const val PACE_CAP = 3.0  // one lucky instant answer shouldn't dominate a pace figure

// Skill mastery
const val WINDOW = 12      // attempts considered when judging a level
const val FAST_TRACK = 5   // ... or this many perfect, quick attempts in a row

// Prerequisites: a skill is solid once half its levels are passed, a deck once most cards are mature
const val PREREQ_MATURE_DAYS = 7.0
const val PREREQ_MATURE_SHARE = 0.8

object Settings {
    const val SESSION_MINUTES = "session_minutes"
    const val MAX_NEW_TOPICS = "max_new_topics"    // topics still "in learning" introduced per daily session
    const val LEARNING_LIMIT = "learning_limit"    // don't introduce more while this many topics are in learning
    const val NEW_CARDS = "new_cards"              // new facts to learn per session (already-known ones are free)
    const val AUTO_ADVANCE = "auto_advance"
    const val SHOW_TARGET_BAR = "show_target_bar"
    const val FONT_SCALE = "font_scale"
    const val HAPTICS = "haptics"                  // Android only: vibrate on wrong answers
    const val DYNAMIC_COLOR = "dynamic_color"      // Android only: Material You wallpaper colors
    const val AUTO_SUBMIT = "auto_submit"          // Android only: submit an exact answer once it's typed correctly

    val DEFAULTS: Map<String, JsonPrimitive> = mapOf(
        SESSION_MINUTES to JsonPrimitive(12),
        MAX_NEW_TOPICS to JsonPrimitive(2),
        LEARNING_LIMIT to JsonPrimitive(3),
        NEW_CARDS to JsonPrimitive(10),
        AUTO_ADVANCE to JsonPrimitive(true),
        SHOW_TARGET_BAR to JsonPrimitive(true),
        FONT_SCALE to JsonPrimitive(1.0),
        HAPTICS to JsonPrimitive(true),
        DYNAMIC_COLOR to JsonPrimitive(true),
        AUTO_SUBMIT to JsonPrimitive(true),
    )
}

private val SCHEMA = """
CREATE TABLE IF NOT EXISTS items (
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,                 -- 'skill' | 'deck'
    introduced_ts REAL NOT NULL,
    level INTEGER NOT NULL DEFAULT 0,   -- skills: current level index
    interval_days REAL NOT NULL DEFAULT 0,
    due_ts REAL NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cards (
    id TEXT PRIMARY KEY,
    deck_id TEXT NOT NULL,
    state TEXT NOT NULL,                -- 'learning' | 'review'
    ease REAL NOT NULL,
    interval_days REAL NOT NULL,
    due_ts REAL NOT NULL,
    reps INTEGER NOT NULL DEFAULT 0,
    lapses INTEGER NOT NULL DEFAULT 0,
    last_ts REAL
);
CREATE TABLE IF NOT EXISTS attempts (
    id INTEGER PRIMARY KEY,
    ts REAL NOT NULL,
    session_id INTEGER,
    mode TEXT NOT NULL,                 -- 'daily' | 'practice' | 'benchmark'
    kind TEXT NOT NULL,                 -- 'skill' | 'card'
    item_id TEXT NOT NULL,              -- skill id or deck id
    card_id TEXT,
    level INTEGER,
    prompt TEXT,
    answer TEXT,
    response TEXT,
    correct INTEGER NOT NULL,
    time_ms INTEGER NOT NULL,
    target_ms INTEGER NOT NULL,
    hinted INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS attempts_item ON attempts(item_id, level, ts);
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY,
    mode TEXT NOT NULL,
    start_ts REAL NOT NULL,
    end_ts REAL,
    n INTEGER DEFAULT 0,
    n_correct INTEGER DEFAULT 0,
    score REAL,
    details TEXT
);
CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""

/** Start (4 am) of the study day containing [ts], in the device's current time zone. */
fun dayStart(ts: Double): Double {
    val zone = ZoneId.systemDefault()
    val secs = Math.floor(ts).toLong()
    val dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(secs, ((ts - secs) * 1e9).toLong()), zone)
    var start = dt.withHour(ROLLOVER_HOUR).withMinute(0).withSecond(0).withNano(0)
    if (dt < start) start = start.minusDays(1)
    return start.atZone(zone).toEpochSecond().toDouble()
}

fun dayEnd(ts: Double): Double = dayStart(ts) + DAY

data class SkillStatus(
    val skillId: String,
    val level: Int,              // current level index
    val nLevels: Int,
    val nRecent: Int,            // attempts at this level in the window
    val accuracy: Double?,
    val medianS: Double?,        // median time of recent correct answers
    val targetS: Double,
    val mastered: Boolean,       // top level passed
    val due: Boolean,
) {
    /** 0..1 progress through all levels, counting partial fluency at the current one. */
    val progress: Double
        get() {
            if (mastered) return 1.0
            var part = 0.0
            if (accuracy != null && medianS != null && medianS != 0.0) {
                part = accuracy * minOf(1.0, targetS / medianS) * minOf(1.0, nRecent.toDouble() / WINDOW)
            }
            return (level + 0.9 * part) / nLevels
        }

    val inLearning: Boolean get() = level == 0 && !mastered
}

data class DeckStats(val total: Int, val seen: Int, val learning: Int, val mature: Int, val due: Int)

data class Benchmark(val id: Long, val startTs: Double, val score: Double)

class Store(val db: Db, val prerequisites: Map<String, List<String>> = Curriculum.PREREQUISITES) {
    var now: () -> Double = { System.currentTimeMillis() / 1000.0 }  // overridable in tests

    init {
        db.exec(SCHEMA)
    }

    // ------------------------------------------------------------ settings

    fun setting(key: String): JsonPrimitive {
        val row = db.query("SELECT value FROM settings WHERE key=?", listOf(key)).firstOrNull()
        return row?.let { Json.parseToJsonElement(it.string("value")).jsonPrimitive } ?: Settings.DEFAULTS.getValue(key)
    }

    fun settingInt(key: String): Int = setting(key).let { it.content.toDoubleOrNull()?.toInt() ?: it.int }
    fun settingBool(key: String): Boolean = setting(key).boolean
    fun settingDouble(key: String): Double = setting(key).double

    fun setSetting(key: String, value: JsonElement) {
        db.exec("INSERT OR REPLACE INTO settings VALUES (?, ?)", listOf(key, value.toString()))
    }

    fun setSetting(key: String, value: Int) = setSetting(key, JsonPrimitive(value))
    fun setSetting(key: String, value: Boolean) = setSetting(key, JsonPrimitive(value))
    fun setSetting(key: String, value: Double) = setSetting(key, JsonPrimitive(value))

    // ------------------------------------------------------------ introduction

    /** Introduced items in introduction order, id → row. */
    fun introduced(): LinkedHashMap<String, Row> {
        val rows = db.query("SELECT * FROM items ORDER BY introduced_ts, rowid")
        val out = LinkedHashMap<String, Row>()
        for (r in rows) {
            val id = r.string("id")
            if (Content.exists(id)) out[id] = r
        }
        return out
    }

    fun isIntroduced(itemId: String): Boolean =
        db.query("SELECT 1 FROM items WHERE id=?", listOf(itemId)).isNotEmpty()

    fun introduce(itemId: String) {
        val kind = if (Content.isSkill(itemId)) "skill" else "deck"
        db.exec("INSERT OR IGNORE INTO items (id, kind, introduced_ts, due_ts) VALUES (?, ?, ?, ?)",
            listOf(itemId, kind, now(), now()))
    }

    /** The earliest topic in ORDER not yet introduced whose prerequisites are all solid. */
    fun nextToIntroduce(): String? {
        val intro = introduced()
        val met = HashMap<String, Boolean>()
        return Curriculum.ORDER.firstOrNull { id ->
            id !in intro && Content.exists(id) &&
                prerequisites[id].orEmpty().all { met.getOrPut(it) { prerequisiteMet(it) } }
        }
    }

    /** The earliest topic in ORDER not yet introduced, whether or not its prerequisites are met. */
    fun nextInOrder(): String? {
        val intro = introduced()
        return Curriculum.ORDER.firstOrNull { it !in intro && Content.exists(it) }
    }

    /** Whether [itemId] is solid enough to build on: half its levels passed, or most of its cards mature. */
    fun prerequisiteMet(itemId: String): Boolean {
        if (!Content.exists(itemId)) return true
        if (!isIntroduced(itemId)) return false
        if (Content.isSkill(itemId)) {
            val st = skillStatus(itemId)
            val passed = if (st.mastered) st.nLevels else st.level
            return passed >= (st.nLevels + 1) / 2
        }
        val mature = (db.query("SELECT COUNT(*) AS n FROM cards WHERE deck_id=? AND state='review' AND interval_days >= ?",
            listOf(itemId, PREREQ_MATURE_DAYS)).first().long("n")).toInt()
        return mature >= PREREQ_MATURE_SHARE * Content.DECKS.getValue(itemId).cards.size
    }

    /** Prerequisites of [itemId] that are not solid yet. */
    fun missingPrerequisites(itemId: String): List<String> =
        prerequisites[itemId].orEmpty().filter { !prerequisiteMet(it) }

    /** How many introduced topics are still being learned. */
    fun learningLoad(): Int {
        var load = 0
        for ((itemId, row) in introduced()) {
            load += if (row.string("kind") == "skill") {
                if (skillStatus(itemId).inLearning) 1 else 0
            } else {
                if (deckUnlearned(itemId) >= 5) 1 else 0
            }
        }
        return load
    }

    fun canIntroduce(): Boolean =
        learningLoad() < settingInt(Settings.LEARNING_LIMIT) && nextToIntroduce() != null

    // ------------------------------------------------------------ attempts

    fun record(
        sessionId: Long?, mode: String, kind: String, itemId: String, cardId: String? = null,
        level: Int? = null, prompt: String = "", answer: String = "", response: String = "",
        correct: Boolean = false, timeMs: Double = 0.0, targetMs: Double = 0.0, hinted: Boolean = false,
    ) {
        db.exec(
            "INSERT INTO attempts (ts, session_id, mode, kind, item_id, card_id, level, prompt, answer," +
                " response, correct, time_ms, target_ms, hinted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            listOf(now(), sessionId, mode, kind, itemId, cardId, level, prompt, answer, response,
                if (correct) 1 else 0, timeMs.toLong(), targetMs.toLong(), if (hinted) 1 else 0))
    }

    // ------------------------------------------------------------ skills

    data class Attempt(val correct: Boolean, val timeMs: Long, val hinted: Boolean)

    fun recentAttempts(skillId: String, level: Int, n: Int = WINDOW): List<Attempt> =
        db.query(
            "SELECT correct, time_ms, hinted FROM attempts WHERE kind='skill' AND item_id=? AND level=?" +
                " AND mode != 'benchmark' ORDER BY id DESC LIMIT ?", listOf(skillId, level, n))
            .map { Attempt(it.bool("correct"), it.long("time_ms"), it.bool("hinted")) }

    private fun levelPassed(skillId: String, level: Int): Boolean {
        val targetMs = Content.SKILLS.getValue(skillId).levels[level].targetSeconds * 1000
        val recent = recentAttempts(skillId, level)
        val ok = recent.map { it.correct && !it.hinted }
        val times = recent.map { it.timeMs.toDouble() }
        if (recent.size >= FAST_TRACK && ok.take(FAST_TRACK).all { it } &&
            median(times.take(FAST_TRACK)) <= 0.8 * targetMs
        ) return true
        if (recent.size >= WINDOW && ok.count { it } >= WINDOW - 1) {
            val correctTimes = times.zip(ok).filter { it.second }.map { it.first }
            return median(correctTimes) <= targetMs
        }
        return false
    }

    fun skillStatus(skillId: String): SkillStatus {
        val skill = Content.SKILLS.getValue(skillId)
        val row = db.query("SELECT level, due_ts FROM items WHERE id=?", listOf(skillId)).firstOrNull()
        val level = if (row != null) minOf(row.int("level"), skill.levels.size - 1) else 0
        val recent = recentAttempts(skillId, level)
        var acc: Double? = null
        var med: Double? = null
        if (recent.isNotEmpty()) {
            val ok = recent.map { it.correct && !it.hinted }
            acc = ok.count { it }.toDouble() / ok.size
            val correctTimes = recent.zip(ok).filter { it.second }.map { it.first.timeMs / 1000.0 }
            med = if (correctTimes.isNotEmpty()) median(correctTimes) else null
        }
        val mastered = level == skill.levels.size - 1 && levelPassed(skillId, level)
        val due = row != null && row.double("due_ts") < dayEnd(now())
        return SkillStatus(skillId, level, skill.levels.size, recent.size, acc, med,
            skill.levels[level].targetSeconds, mastered, due)
    }

    /** After an attempt: promote the skill if its current level is passed. Returns the new level. */
    fun checkPromotion(skillId: String): Int? {
        val skill = Content.SKILLS.getValue(skillId)
        val level = skillStatus(skillId).level
        if (level + 1 < skill.levels.size && levelPassed(skillId, level)) {
            db.exec("UPDATE items SET level=? WHERE id=?", listOf(level + 1, skillId))
            return level + 1
        }
        return null
    }

    fun setSkillLevel(skillId: String, level: Int) {
        introduce(skillId)
        db.exec("UPDATE items SET level=? WHERE id=?", listOf(level, skillId))
    }

    /** Space a skill after a session. pace = target / median time (>1 is faster than target). */
    fun rescheduleSkill(skillId: String, accuracy: Double, pace: Double, promoted: Boolean) {
        val row = db.query("SELECT interval_days FROM items WHERE id=?", listOf(skillId)).firstOrNull() ?: return
        val interval = if (promoted || accuracy < 0.85 || pace < 0.8) {
            1.0
        } else {
            minOf(30.0, maxOf(1.0, row.double("interval_days")) * (if (pace >= 1) 2.5 else 1.6))
        }
        val due = dayStart(now()) + interval * DAY
        db.exec("UPDATE items SET interval_days=?, due_ts=? WHERE id=?", listOf(interval, due, skillId))
    }

    // ------------------------------------------------------------ cards

    data class CardRow(
        val id: String, val deckId: String, val state: String, val ease: Double, val intervalDays: Double,
        val dueTs: Double, val reps: Int, val lapses: Int, val lastTs: Double?,
    )

    fun cardRow(cardId: String): CardRow? =
        db.query("SELECT * FROM cards WHERE id=?", listOf(cardId)).firstOrNull()?.let {
            CardRow(it.string("id"), it.string("deck_id"), it.string("state"), it.double("ease"),
                it.double("interval_days"), it.double("due_ts"), it.int("reps"), it.int("lapses"),
                it.doubleOrNull("last_ts"))
        }

    fun dueCards(deckIds: Collection<String>? = null): List<String> =
        db.query("SELECT id, deck_id FROM cards WHERE state='learning' OR due_ts < ? ORDER BY due_ts",
            listOf(dayEnd(now())))
            .filter { it.string("id") in Content.CARDS && (deckIds == null || it.string("deck_id") in deckIds) }
            .map { it.string("id") }

    fun unseenCards(deckId: String): List<String> {
        val seen = db.query("SELECT id FROM cards WHERE deck_id=?", listOf(deckId)).map { it.string("id") }.toSet()
        return Content.DECKS.getValue(deckId).cards.map { it.id }.filter { it !in seen }
    }

    fun deckUnlearned(deckId: String): Int {
        val learning = (db.query("SELECT COUNT(*) AS n FROM cards WHERE deck_id=? AND state='learning'",
            listOf(deckId)).first().long("n")).toInt()
        return unseenCards(deckId).size + learning
    }

    fun deckStats(deckId: String): DeckStats {
        val rows = db.query("SELECT state, interval_days, due_ts FROM cards WHERE deck_id=?", listOf(deckId))
        val end = dayEnd(now())
        return DeckStats(
            total = Content.DECKS.getValue(deckId).cards.size,
            seen = rows.size,
            learning = rows.count { it.string("state") == "learning" },
            mature = rows.count { it.string("state") == "review" && it.double("interval_days") >= 21 },
            due = rows.count { it.string("state") == "learning" || it.double("due_ts") < end },
        )
    }

    fun reviewCard(cardId: String, grade: Int, rng: Rng = Rng()) {
        val deck = Content.CARDS.getValue(cardId).first
        val now = now()
        val row = cardRow(cardId)
        var state: String
        var ease: Double
        var interval: Double
        var reps: Int
        var lapses: Int
        if (row == null) {  // first exposure: a fast correct answer means it's already known
            state = "learning"; ease = 2.5; interval = 0.0; reps = 0; lapses = 0
            if (grade == EASY) {
                state = "review"; interval = 4.0
            } else if (grade == GOOD || grade == HARD) {
                state = "review"; interval = 1.0
            }
        } else {
            state = row.state; ease = row.ease; interval = row.intervalDays
            reps = row.reps; lapses = row.lapses
            if (grade == AGAIN) {
                if (state == "review") {
                    lapses += 1
                    ease = maxOf(1.3, ease - 0.2)
                }
                state = "learning"; interval = 0.0
            } else if (state == "learning") {
                state = "review"; interval = 1.0
            } else if (grade == HARD) {
                ease = maxOf(1.3, ease - 0.15)
                interval = maxOf(1.0, interval * 1.2)
            } else if (grade == GOOD) {
                interval = maxOf(1.0, interval * ease)
            } else {
                ease += 0.1
                interval = maxOf(2.0, interval * ease * 1.3)
            }
            interval = if (interval != 0.0) minOf(365.0, interval * rng.uniform(0.95, 1.05)) else 0.0
        }
        reps += 1
        val due = if (state == "learning") now else dayStart(now) + interval * DAY
        db.exec(
            "INSERT OR REPLACE INTO cards (id, deck_id, state, ease, interval_days, due_ts, reps, lapses," +
                " last_ts) VALUES (?,?,?,?,?,?,?,?,?)",
            listOf(cardId, deck.id, state, ease, interval, due, reps, lapses, now))
    }

    // ------------------------------------------------------------ sessions

    fun startSession(mode: String): Long =
        db.insert("INSERT INTO sessions (mode, start_ts) VALUES (?, ?)", listOf(mode, now()))

    fun endSession(sessionId: Long, n: Int, nCorrect: Int, score: Double? = null, details: String? = null) {
        db.exec("UPDATE sessions SET end_ts=?, n=?, n_correct=?, score=?, details=? WHERE id=?",
            listOf(now(), n, nCorrect, score, details, sessionId))
    }

    fun benchmarks(): List<Benchmark> =
        db.query("SELECT id, start_ts, score FROM sessions WHERE mode='benchmark' AND score IS NOT NULL ORDER BY start_ts")
            .map { Benchmark(it.long("id"), it.double("start_ts"), it.double("score")) }

    /** Consecutive study days (ending today or yesterday) with a finished daily session. */
    fun streak(): Int {
        val days = db.query("SELECT start_ts FROM sessions WHERE mode='daily' AND end_ts IS NOT NULL AND n > 0")
            .map { dayStart(it.double("start_ts")) }.toSet()
        val today = dayStart(now())
        var d = if (today in days) today else today - DAY
        var count = 0
        while (d in days) {
            count += 1
            d = dayStart(d - DAY / 2)  // step back a day, robust to DST shifts
        }
        return count
    }

    fun didDailyToday(): Boolean =
        db.query("SELECT 1 FROM sessions WHERE mode='daily' AND end_ts IS NOT NULL AND n > 0 AND start_ts >= ?",
            listOf(dayStart(now()))).isNotEmpty()

    data class DayPace(val day: Double, val pace: Double, val accuracy: Double)

    /**
     * Per study day: (day start, pace, accuracy) over skill attempts in daily/practice.
     * pace = 100 × median(target / time) over correct answers; 100 means 'at target speed'.
     */
    fun dailyPace(): List<DayPace> {
        val rows = db.query("SELECT ts, correct, hinted, time_ms, target_ms FROM attempts WHERE kind='skill'" +
            " AND mode != 'benchmark' ORDER BY ts")
        val byDay = sortedMapOf<Double, MutableList<Row>>()
        for (r in rows) byDay.getOrPut(dayStart(r.double("ts"))) { mutableListOf() }.add(r)
        val out = mutableListOf<DayPace>()
        for ((d, rs) in byDay) {
            val ok = rs.filter { it.bool("correct") && !it.bool("hinted") && it.long("time_ms") > 0 }
            if (ok.isNotEmpty()) {
                val pace = 100 * median(ok.map { minOf(PACE_CAP, it.long("target_ms").toDouble() / it.long("time_ms")) })
                out.add(DayPace(d, pace, ok.size.toDouble() / rs.size))
            }
        }
        return out
    }

    fun practiceMinutesByDay(): Map<Double, Double> {
        val out = sortedMapOf<Double, Double>()
        for (r in db.query("SELECT ts, time_ms FROM attempts")) {
            val d = dayStart(r.double("ts"))
            out[d] = (out[d] ?: 0.0) + r.long("time_ms") / 60000.0
        }
        return out
    }

    data class HistoryPoint(val day: Double, val level: Int, val medianS: Double, val accuracy: Double)

    /** Per day and level: (day, level, median seconds of correct answers, accuracy). */
    fun skillHistory(skillId: String): List<HistoryPoint> {
        val rows = db.query("SELECT ts, level, correct, hinted, time_ms FROM attempts WHERE kind='skill' AND item_id=?" +
            " AND mode != 'benchmark' ORDER BY ts", listOf(skillId))
        val groups = sortedMapOf<Pair<Double, Int>, MutableList<Row>>(compareBy({ it.first }, { it.second }))
        for (r in rows) groups.getOrPut(dayStart(r.double("ts")) to r.int("level")) { mutableListOf() }.add(r)
        val out = mutableListOf<HistoryPoint>()
        for ((key, rs) in groups) {
            val ok = rs.filter { it.bool("correct") && !it.bool("hinted") }.map { it.long("time_ms") / 1000.0 }
            if (ok.isNotEmpty()) out.add(HistoryPoint(key.first, key.second, median(ok), ok.size.toDouble() / rs.size))
        }
        return out
    }

    fun reset() {
        db.transaction {
            for (table in listOf("items", "cards", "attempts", "sessions")) db.exec("DELETE FROM $table")
        }
    }

    companion object {
        /** A card grade from correctness and speed: only fast recall counts as EASY. */
        fun grade(correct: Boolean, hinted: Boolean, timeS: Double, targetS: Double): Int = when {
            !correct || hinted -> AGAIN
            timeS <= targetS -> EASY
            timeS <= 2 * targetS -> GOOD
            else -> HARD
        }
    }
}
