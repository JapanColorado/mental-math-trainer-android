package dev.russell.mmtrainer

import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.pyStr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class Clock(var t: Double = 1_800_000_000.0) : () -> Double {
    override fun invoke() = t
}

fun memoryStore(prerequisites: Map<String, List<String>> = Curriculum.PREREQUISITES): Store =
    Store(JdbcDb(), prerequisites).also { it.now = Clock() }

val Store.clock: Clock get() = now as Clock

class Played(val lessons: List<Item>, val problems: List<Item>, val summary: Summary)

/** Run a session to completion (or [limit] problems), answering as instructed. */
fun play(session: Session, answerCorrectly: (Item) -> Boolean = { true }, seconds: Double = 1.0, limit: Int = 400): Played {
    val lessons = mutableListOf<Item>()
    val problems = mutableListOf<Item>()
    while (problems.size < limit) {
        val item = session.nextItem() ?: break
        if (item.isLesson) {
            lessons += item
            continue
        }
        problems += item
        val p = item.problem!!
        val value = if (answerCorrectly(item)) p.answer else p.answer * 3 + 7
        session.submit(item, pyStr(value), value, seconds)
    }
    return Played(lessons, problems, session.finish())
}

/** Port of tests/test_session.py, plus trace-for-trace parity with the Python session engine. */
class SessionTest {
    private lateinit var store: Store

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))  // the golden traces were recorded in UTC
        store = memoryStore()
    }

    @Test
    fun curriculumCoversAllContent() {
        assertEquals(Curriculum.ORDER.toSet(), Content.SKILLS.keys + Content.DECKS.keys)
        assertEquals(Curriculum.ORDER.size, Curriculum.ORDER.toSet().size)
        for ((skillId, level) in Curriculum.BENCHMARK) {
            assertTrue(level < Content.SKILLS.getValue(skillId).levels.size)
        }
    }

    @Test
    fun prerequisitesComeEarlierInOrder() {
        for ((id, prereqs) in Curriculum.PREREQUISITES) {
            assertTrue(id in Curriculum.ORDER)
            for (p in prereqs) {
                assertTrue("$p before $id", Curriculum.ORDER.indexOf(p) in 0 until Curriculum.ORDER.indexOf(id))
            }
        }
    }

    private fun matureCards(deckId: String, n: Int) {
        for (card in Content.DECKS.getValue(deckId).cards.take(n)) {
            store.db.exec("INSERT OR REPLACE INTO cards (id, deck_id, state, ease, interval_days, due_ts) VALUES (?, ?, 'review', 2.5, ?, ?)",
                listOf(card.id, deckId, PREREQ_MATURE_DAYS, store.now() + 30 * DAY))
        }
    }

    @Test
    fun skipsTopicsWhosePrerequisitesAreNotSolid() {
        assertEquals("d_times", store.nextToIntroduce())
        store.introduce("d_times")
        assertEquals("s_add", store.nextToIntroduce())
        store.introduce("s_add")
        // everything else builds on times tables or addition
        assertNull(store.nextToIntroduce())
        assertEquals("s_mul1", store.nextInOrder())
        assertEquals(listOf("d_times", "s_add"), store.missingPrerequisites("s_mul1"))
        assertTrue(!store.canIntroduce())

        store.setSkillLevel("s_add", 1)  // 1 of 4 levels passed: not yet half
        assertNull(store.nextToIntroduce())
        store.setSkillLevel("s_add", 2)
        assertEquals("s_sub", store.nextToIntroduce())  // skips ahead past s_mul1 and d_squares

        val cards = Content.DECKS.getValue("d_times").cards.size
        val needed = Math.ceil(PREREQ_MATURE_SHARE * cards).toInt()
        matureCards("d_times", needed - 1)
        assertEquals(listOf("d_times"), store.missingPrerequisites("s_mul1"))
        matureCards("d_times", needed)
        assertEquals("s_mul1", store.nextToIntroduce())
    }

    @Test
    fun halfOfTwoLevelSkillIsSolid() {
        store.introduce("s_logint")
        assertTrue(!store.prerequisiteMet("s_logint"))
        store.setSkillLevel("s_logint", 1)  // half of two levels
        assertTrue(store.prerequisiteMet("s_logint"))
    }

    @Test
    fun dailySessionsOnlyIntroduceTopicsWithSolidPrerequisites() {
        repeat(3) { day ->
            val introducedBefore = store.introduced().keys
            val solid = Curriculum.ORDER.filter { store.prerequisiteMet(it) }.toSet()
            val session = Session(store, Mode.DAILY, rng = Rng(30L + day))
            session.budget = null
            val played = play(session, seconds = 0.5, limit = 80)
            for (id in played.summary.introduced) {
                assertTrue(id !in introducedBefore)
                // prerequisites can become solid mid-session (a skill levels up), but never a deck on its first day
                for (p in Curriculum.PREREQUISITES[id].orEmpty()) {
                    assertTrue("$id needs $p", p in solid || Content.isSkill(p))
                }
            }
            store.clock.t += DAY
        }
        assertTrue(!store.isIntroduced("s_mul1"))  // times tables can't be mature after three days
    }

    @Test
    fun firstDailySessionIntroducesAndInterleaves() {
        val session = Session(store, Mode.DAILY, minutes = 0.05, rng = Rng(1))
        session.budget = null  // control length via limit instead
        val played = play(session, limit = 60)
        assertTrue(played.lessons.isNotEmpty() && played.lessons[0].sourceId == Curriculum.ORDER[0])
        assertTrue(store.isIntroduced(Curriculum.ORDER[0]))
        // a fast, correct learner tests out quickly, so several topics get introduced
        assertTrue(played.summary.introduced.size >= 2)
        // after the blocked runs, the same topic shouldn't repeat back to back very often
        val sources = played.problems.drop(20).map { it.sourceId }
        val repeats = sources.zipWithNext().count { (a, b) -> a == b }
        assertTrue(repeats < sources.size / 3.0)
    }

    @Test
    fun fastCorrectAnswersPromoteSkills() {
        store.introduce("s_add")
        val session = Session(store, Mode.PRACTICE, itemIds = listOf("s_add"), rng = Rng(2))
        play(session, seconds = 0.5, limit = 5)
        assertEquals(1, store.skillStatus("s_add").level)
    }

    @Test
    fun audioSessionsAreTagged() {
        store.introduce("s_add")
        Session(store, Mode.PRACTICE, itemIds = listOf("s_add"), rng = Rng(2))
        Session(store, Mode.PRACTICE, itemIds = listOf("s_add"), audio = true, rng = Rng(2))
        assertEquals(listOf(0L, 1L), store.db.query("SELECT audio FROM sessions ORDER BY id").map { it.long("audio") })
    }

    @Test
    fun addsAudioColumnToOldDatabases() {
        val db = JdbcDb()
        db.exec("CREATE TABLE sessions (id INTEGER PRIMARY KEY, mode TEXT NOT NULL, start_ts REAL NOT NULL," +
            " end_ts REAL, n INTEGER DEFAULT 0, n_correct INTEGER DEFAULT 0, score REAL, details TEXT)")
        db.exec("INSERT INTO sessions (mode, start_ts) VALUES ('daily', 1)")
        Store(db)
        Store(db)  // reopening doesn't try to add it twice
        assertEquals(0L, db.query("SELECT audio FROM sessions").single().long("audio"))
    }

    @Test
    fun slowAnswersDoNotPromote() {
        store.introduce("s_add")
        val target = Content.SKILLS.getValue("s_add").levels[0].targetSeconds
        val session = Session(store, Mode.PRACTICE, itemIds = listOf("s_add"), rng = Rng(3))
        play(session, seconds = target * 2, limit = 30)
        assertEquals(0, store.skillStatus("s_add").level)
    }

    @Test
    fun missedCardsComeBackInSession() {
        store.introduce("d_pow2")
        val session = Session(store, Mode.PRACTICE, itemIds = listOf("d_pow2"), rng = Rng(4))
        val first = session.nextItem()!!
        session.submit(first, "", null, 2.0)
        val later = List(8) { session.nextItem() }
        assertTrue(later.any { it != null && it.cardId == first.cardId && it.relearn })
    }

    @Test
    fun cardSrsIntervals() {
        val card = Content.DECKS.getValue("d_pow2").cards[0].id
        store.reviewCard(card, EASY, Rng(0))  // known on first sight
        var row = store.cardRow(card)!!
        assertTrue(row.state == "review" && row.intervalDays == 4.0)
        store.clock.t += 5 * DAY
        assertTrue(card in store.dueCards())
        store.reviewCard(card, EASY, Rng(0))
        assertTrue(store.cardRow(card)!!.intervalDays > 10)
        store.reviewCard(card, AGAIN, Rng(0))
        row = store.cardRow(card)!!
        assertTrue(row.state == "learning" && row.lapses == 1)
    }

    @Test
    fun benchmarkScores() {
        val session = Session(store, Mode.BENCHMARK, rng = Rng(5))
        val targetTotal = Curriculum.BENCHMARK.sumOf { (s, lvl) -> Content.SKILLS.getValue(s).levels[lvl].targetSeconds }
        val played = play(session, seconds = targetTotal / Curriculum.BENCHMARK.size)
        assertEquals(Curriculum.BENCHMARK.size * Curriculum.BENCHMARK_ROUNDS, played.problems.size)
        // every skill is asked once per round, never twice within a round
        val firstRound = played.problems.take(Curriculum.BENCHMARK.size).map { it.sourceId }
        assertEquals(Curriculum.BENCHMARK.map { it.first }.sorted(), firstRound.sorted())
        assertEquals(100.0, played.summary.score!!, 1e-9)
        assertEquals(1, store.benchmarks().size)
        // benchmark attempts never affect mastery
        assertTrue(Curriculum.BENCHMARK.all { (s, lvl) -> store.recentAttempts(s, lvl).isEmpty() })
    }

    @Test
    fun abandonedBenchmarkIsNotScored() {
        val session = Session(store, Mode.BENCHMARK, rng = Rng(6))
        play(session, limit = 3)
        assertTrue(store.benchmarks().isEmpty())
    }

    @Test
    fun streak() {
        repeat(3) {
            val s = Session(store, Mode.DAILY, rng = Rng(7))
            s.budget = null
            play(s, limit = 3)
            store.clock.t += DAY
        }
        assertEquals(3, store.streak())
        store.clock.t += 2 * DAY
        assertEquals(0, store.streak())
    }

    // ------------------------------------------------------------ parity with the Python engine

    private class Scenario(
        val days: Int, val make: (Store, Int) -> Session, val rule: (Int) -> Boolean,
        val seconds: (Int) -> Double, val limit: Int,
    )

    private fun daily(seed: Long): (Store, Int) -> Session = { store, day ->
        Session(store, Mode.DAILY, rng = Rng(seed + day)).also { it.budget = null }
    }

    private val scenarios = mapOf(
        "daily_all_correct" to Scenario(1, daily(1), { true }, { 1.0 }, 60),
        "daily_mixed" to Scenario(1, daily(11), { it % 3 != 0 }, { 1.0 + (it % 5) * 0.7 }, 80),
        "multi_day" to Scenario(6, daily(21), { (it * 7) % 5 != 0 }, { 0.6 + (it % 7) * 0.9 }, 45),
        "practice_pow2" to Scenario(1, { s, _ ->
            s.introduce("d_pow2"); Session(s, Mode.PRACTICE, itemIds = listOf("d_pow2"), rng = Rng(4))
        }, { it % 4 != 1 }, { 2.0 }, 25),
        "practice_add" to Scenario(1, { s, _ ->
            s.introduce("s_add"); Session(s, Mode.PRACTICE, itemIds = listOf("s_add"), rng = Rng(2))
        }, { true }, { 0.5 }, 30),
        "benchmark" to Scenario(1, { s, _ -> Session(s, Mode.BENCHMARK, rng = Rng(5)) },
            { it % 5 != 2 }, { 2.0 + (it % 3) }, 400),
    )

    @Test
    fun matchesPythonSessionTraces() {
        val golden = Golden.load("session").arr("scenarios")
        assertEquals(scenarios.keys, golden.map { it.obj.str("name") }.toSet())
        for (gj in golden) {
            val g = gj.obj
            val name = g.str("name")
            val sc = scenarios.getValue(name)
            val st = memoryStore(prerequisites = emptyMap())  // the Python engine has no prerequisites
            var counter = 0
            val gSessions = g.arr("sessions")
            for (day in 0 until sc.days) {
                val session = sc.make(st, day)
                val expected = gSessions[day].obj.arr("items")
                var k = 0
                var n = 0
                while (n < sc.limit) {
                    val item = session.nextItem() ?: break
                    val e = expected.getOrNull(k++)?.obj ?: throw AssertionError("$name day $day: extra item $item")
                    val where = "$name day $day item ${k - 1}"
                    assertEquals("$where kind", e.str("kind"), item.kind)
                    assertEquals("$where source", e.str("source"), item.sourceId)
                    assertEquals("$where title", e.str("title"), item.title)
                    if (item.isLesson) continue
                    n++
                    counter++
                    val p = item.problem!!
                    assertEquals("$where card", e.strOrNull("card"), item.cardId)
                    assertEquals("$where level", e["level"]?.jsonPrimitive?.intOrNull, item.level)
                    assertEquals("$where tag", e.str("tag"), item.tag)
                    assertEquals("$where prompt", e.str("prompt"), p.prompt)
                    assertEquals("$where new_card", e.bool("new_card"), item.newCard)
                    assertEquals("$where relearn", e.bool("relearn"), item.relearn)
                    assertEquals("$where target", e.dbl("target"), item.targetSeconds, 0.0)
                    val ok = sc.rule(counter)
                    var value: Double? = if (ok) p.answer else p.answer * 3 + 7
                    val response = if (!ok && counter % 2 == 1) "" else pyStr(value!!)
                    if (response.isEmpty()) value = null
                    val secs = sc.seconds(counter)
                    val r = session.submit(item, response, value, secs)
                    assertEquals("$where correct", e.bool("correct"), r.correct)
                    assertEquals("$where promoted", e["promoted_to"]?.jsonPrimitive?.intOrNull, r.promotedTo)
                }
                assertEquals("$name day $day: item count", expected.size, k)
                val summary = session.finish()
                val gs = gSessions[day].obj
                assertEquals("$name introduced", gs.arr("introduced").map { it.jsonPrimitive.content }, summary.introduced)
                assertEquals("$name n_correct", gs.long("n_correct").toInt(), summary.nCorrect)
                val score = gs.dblOrNull("score")
                if (score == null) assertNull(summary.score) else assertEquals("$name score", score, summary.score!!, 1e-9)
                st.clock.t += DAY
            }
            checkState(name, g.obj("state"), st)
        }
    }

    private fun checkState(name: String, e: JsonObject, st: Store) {
        fun rows(sql: String) = st.db.query(sql)
        fun compareRows(table: String, expected: List<JsonObject>, actual: List<Row>) {
            assertEquals("$name $table count", expected.size, actual.size)
            for ((er, ar) in expected.zip(actual)) {
                for ((col, ev) in er) {
                    val av = ar[col]
                    val where = "$name $table $er.$col"
                    when {
                        ev is JsonNull -> assertNull(where, av)
                        // Python's json keeps int-valued targets as ints ("6"); compare numbers by value
                        col == "details" -> assertEquals(where, wholeFloats(Json.parseToJsonElement(ev.jsonPrimitive.content)),
                            wholeFloats(Json.parseToJsonElement(av as String)))
                        ev.jsonPrimitive.isString -> assertEquals(where, ev.jsonPrimitive.content, av)
                        else -> assertEquals(where, ev.jsonPrimitive.doubleOrNull!!, (av as Number).toDouble(), 1e-9)
                    }
                }
            }
        }
        compareRows("items", e.arr("items").map { it.obj },
            rows("SELECT id, kind, introduced_ts, level, interval_days, due_ts FROM items ORDER BY id"))
        compareRows("cards", e.arr("cards").map { it.obj },
            rows("SELECT id, deck_id, state, ease, interval_days, due_ts, reps, lapses, last_ts FROM cards ORDER BY id"))
        compareRows("sessions", e.arr("sessions").map { it.obj },
            rows("SELECT id, mode, start_ts, end_ts, n, n_correct, score, details FROM sessions ORDER BY id"))
        assertEquals("$name streak", e.long("streak").toInt(), st.streak())
        for ((sid, sj) in e.obj("skill_status")) {
            val s = sj.jsonObject()
            val a = st.skillStatus(sid)
            assertEquals("$name $sid level", s.long("level").toInt(), a.level)
            assertEquals("$name $sid n_recent", s.long("n_recent").toInt(), a.nRecent)
            assertEquals("$name $sid mastered", s.bool("mastered"), a.mastered)
            assertEquals("$name $sid due", s.bool("due"), a.due)
            assertEquals("$name $sid progress", s.dbl("progress"), a.progress, 1e-9)
            assertEquals("$name $sid accuracy", s.dblOrNull("accuracy"), a.accuracy)
            s.dblOrNull("median_s")?.let { assertEquals("$name $sid median", it, a.medianS!!, 1e-9) }
        }
        for ((did, dj) in e.obj("deck_stats")) {
            val d = dj.jsonObject()
            val a = st.deckStats(did)
            assertEquals("$name $did stats", listOf("total", "seen", "learning", "mature", "due").map { d.long(it).toInt() },
                listOf(a.total, a.seen, a.learning, a.mature, a.due))
        }
        val pace = e.arr("daily_pace").map { it.jsonArray.map { v -> v.jsonPrimitive.doubleOrNull!! } }
        val actualPace = st.dailyPace()
        assertEquals("$name pace days", pace.size, actualPace.size)
        for ((p, a) in pace.zip(actualPace)) {
            assertEquals(p[0], a.day, 1e-9); assertEquals(p[1], a.pace, 1e-9); assertEquals(p[2], a.accuracy, 1e-9)
        }
        assertEquals("$name due cards", e.arr("due_cards").map { it.jsonPrimitive.content }, st.dueCards())
        assertEquals("$name next", e.strOrNull("next_to_introduce"), st.nextToIntroduce())
        assertEquals("$name load", e.long("learning_load").toInt(), st.learningLoad())
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObject() = this as JsonObject

    private fun wholeFloats(e: kotlinx.serialization.json.JsonElement): String =
        e.toString().replace(Regex("""(?<=[:\[,])(-?\d+)\.0(?=[,}\]])"""), "$1")
}
