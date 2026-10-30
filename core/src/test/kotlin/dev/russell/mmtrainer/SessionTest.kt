package dev.russell.mmtrainer

import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.pyStr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class Clock(var t: Double = 1_800_000_000.0) : () -> Double {
    override fun invoke() = t
}

fun memoryStore(): Store = Store(JdbcDb()).also { it.now = Clock() }

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

class SessionTest {
    private lateinit var store: Store

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))  // day boundaries (4 am local) at fixed clock times
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
    fun movesOldSessionDefaultsOnce() {
        val db = JdbcDb()
        val store = Store(db)
        store.setSetting(Settings.SESSION_MINUTES, 12)
        store.setSetting(Settings.NEW_CARDS, 10)
        db.exec("DELETE FROM settings WHERE key=?", listOf(Settings.SHORT_SESSIONS))  // as saved by an older version

        val moved = Store(db)
        assertEquals(3, moved.settingInt(Settings.SESSION_MINUTES))
        assertEquals(3, moved.settingInt(Settings.NEW_CARDS))

        moved.setSetting(Settings.SESSION_MINUTES, 12)  // chosen after the move: kept
        assertEquals(12, Store(db).settingInt(Settings.SESSION_MINUTES))
    }

    @Test
    fun keepsChosenSessionLengthWhenMovingDefaults() {
        val db = JdbcDb()
        Store(db).setSetting(Settings.SESSION_MINUTES, 20)
        db.exec("DELETE FROM settings WHERE key=?", listOf(Settings.SHORT_SESSIONS))
        assertEquals(20, Store(db).settingInt(Settings.SESSION_MINUTES))
    }

    @Test
    fun repeatSessionsInADayDoNotStretchSkillIntervals() {
        store.introduce("s_add")
        fun interval() = store.db.query("SELECT interval_days FROM items WHERE id='s_add'").single().double("interval_days")
        store.rescheduleSkill("s_add", 1.0, 1.0, false)
        assertEquals(2.5, interval(), 1e-9)
        store.rescheduleSkill("s_add", 1.0, 1.0, false)  // again the same day
        assertEquals(2.5, interval(), 1e-9)
        store.rescheduleSkill("s_add", 0.5, 1.0, false)  // a poor repeat still brings it back
        assertEquals(1.0, interval(), 1e-9)
        store.clock.t += DAY
        store.rescheduleSkill("s_add", 1.0, 1.0, false)  // due again
        assertEquals(2.5, interval(), 1e-9)
    }

    @Test
    fun countsSessionsToday() {
        assertEquals(0, store.sessionsToday())
        repeat(2) {
            val s = Session(store, Mode.DAILY, rng = Rng(9))
            s.budget = null
            play(s, limit = 3)
        }
        assertEquals(2, store.sessionsToday())
        assertEquals(1, store.streak())  // several sessions in a day still count as one streak day
        store.clock.t += DAY
        assertEquals(0, store.sessionsToday())
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
    fun newFactsComeOutOfOrderButEasyFirst() {
        store.introduce("d_times")
        val order = Content.DECKS.getValue("d_times").cards.map { it.id }
        val session = Session(store, Mode.PRACTICE, itemIds = listOf("d_times"), rng = Rng(6))
        val served = play(session, limit = 30).problems.filter { it.newCard }.map { order.indexOf(it.cardId) }
        assertEquals(30, served.size)
        assertTrue(served != served.sorted())
        for ((k, i) in served.withIndex()) assertTrue("card $i served ${k + 1}th", i < k + NEW_CARD_WINDOW)
    }

    @Test
    fun dueReviewsComeInRandomOrder() {
        store.introduce("d_times")
        val ids = Content.DECKS.getValue("d_times").cards.take(20).map { it.id }
        for ((k, id) in ids.withIndex()) {
            store.db.exec("INSERT INTO cards (id, deck_id, state, ease, interval_days, due_ts) VALUES (?, 'd_times', 'review', 2.5, 1.0, ?)",
                listOf(id, store.now() - 100 + k))
        }
        val session = Session(store, Mode.PRACTICE, itemIds = listOf("d_times"), rng = Rng(8))
        val served = play(session, limit = 20).problems.map { it.cardId }
        assertEquals(ids.toSet(), served.toSet())
        assertTrue(served != ids)
    }

    @Test
    fun productsAreAskedBothWaysRound() {
        store.introduce("d_times")
        val session = Session(store, Mode.PRACTICE, itemIds = listOf("d_times"), rng = Rng(7))
        val asked = play(session, limit = 40).problems.map { it.problem!!.prompt to Content.CARDS.getValue(it.cardId!!).second }
        assertTrue(asked.all { (prompt, card) -> prompt == card.prompt || prompt == card.altPrompt })
        assertTrue(asked.any { (prompt, card) -> prompt == card.prompt && card.altPrompt != null })
        assertTrue(asked.any { (prompt, card) -> prompt == card.altPrompt })
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
}
