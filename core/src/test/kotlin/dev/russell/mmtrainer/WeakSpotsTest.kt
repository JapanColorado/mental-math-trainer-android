package dev.russell.mmtrainer

import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.py.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class WeakSpotsTest {
    private lateinit var store: Store
    private val skill = Content.SKILLS.keys.first()
    private val strong = Content.SKILLS.keys.drop(1).first()
    private val deck = Content.DECKS.values.first()
    private val card = deck.cards[0].id
    private val other = deck.cards[1].id

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        store = memoryStore()
    }

    private fun skillTry(id: String, correct: Boolean, timeMs: Double = 1000.0, targetMs: Double = 4000.0) {
        store.introduce(id)
        store.record(1, "daily", "skill", id, level = 0, correct = correct, timeMs = timeMs, targetMs = targetMs)
    }

    private fun cardTry(id: String, correct: Boolean, timeMs: Double = 1000.0, mode: String = "daily", hinted: Boolean = false) {
        store.introduce(deck.id)
        store.record(1, mode, "card", deck.id, cardId = id, correct = correct, timeMs = timeMs, targetMs = 2000.0, hinted = hinted)
    }

    @Test
    fun nothingIsWeakWithoutHistory() {
        assertTrue(store.weakSpots().isEmpty)
    }

    @Test
    fun aSkillNeedsEnoughAttemptsToJudge() {
        repeat(WEAK_MIN_SKILL_ATTEMPTS - 1) { skillTry(skill, correct = false) }
        assertTrue(store.weakSpots().isEmpty)
        skillTry(skill, correct = false)
        assertEquals(listOf(skill), store.weakSpots().skills)
    }

    @Test
    fun slowButCorrectSkillIsWeakAndFastAccurateOneIsNot() {
        repeat(10) { skillTry(skill, correct = true, timeMs = 10_000.0) }
        repeat(10) { skillTry(strong, correct = true, timeMs = 1000.0) }
        assertEquals(listOf(skill), store.weakSpots().skills)
    }

    @Test
    fun onlyLatestAttemptsCount() {
        repeat(WEAK_SKILL_WINDOW) { skillTry(skill, correct = false) }
        repeat(WEAK_SKILL_WINDOW) { skillTry(skill, correct = true) }
        assertTrue(store.weakSpots().skills.isEmpty())
    }

    @Test
    fun aFactsFirstAttemptIsIgnored() {
        cardTry(card, correct = false)  // revealed the first time
        cardTry(card, correct = true)
        cardTry(card, correct = true)
        assertTrue(store.weakSpots().cards.isEmpty())
    }

    @Test
    fun missedOrSlowFactsAreWeakWorstFirst() {
        repeat(4) { cardTry(card, correct = false) }
        repeat(4) { cardTry(other, correct = true, timeMs = 4000.0) }
        assertEquals(listOf(card, other), store.weakSpots().cards)
    }

    @Test
    fun hintedAnswersCountAsMissesAndBenchmarksDoNotCount() {
        repeat(4) { cardTry(card, correct = true, hinted = true) }
        repeat(4) { cardTry(other, correct = false, mode = "benchmark") }
        assertEquals(listOf(card), store.weakSpots().cards)
    }

    @Test
    fun aSessionDrillsOnlyWeakFactsAndLeavesTheirScheduleAlone() {
        store.introduce(deck.id)
        store.reviewCard(card, GOOD, Rng(1))
        store.reviewCard(other, GOOD, Rng(1))
        val before = store.cardRow(card)!!.dueTs
        val session = Session(store, Mode.PRACTICE, minutes = 1.0, itemIds = emptyList(),
            focusCards = listOf(card, other), rng = Rng(2))
        val played = play(session, limit = 12)
        assertEquals(setOf(card, other), played.problems.map { it.cardId }.toSet())
        assertTrue(played.problems.all { it.tag == "Weak spot" })
        val ids = played.problems.map { it.cardId }
        assertFalse("no fact twice in a row", ids.zipWithNext().any { (a, b) -> a == b })
        assertEquals(before, store.cardRow(card)!!.dueTs, 0.0)
    }

    @Test
    fun aSessionMixesWeakSkillsWithWeakFacts() {
        store.introduce(skill)
        store.introduce(deck.id)
        store.reviewCard(card, GOOD, Rng(1))
        val session = Session(store, Mode.PRACTICE, minutes = 1.0, itemIds = listOf(skill),
            focusCards = listOf(card), rng = Rng(3))
        val played = play(session, limit = 30)
        assertTrue(played.problems.any { it.sourceId == skill })
        assertTrue(played.problems.any { it.cardId == card })
    }

    @Test
    fun recentActivityMarksDaysWithAFinishedSession() {
        fun quickSession() = Session(store, Mode.DAILY, rng = Rng(7)).also { it.budget = null }.let { play(it, limit = 2) }
        quickSession()
        store.clock.t += 2 * DAY
        quickSession()
        assertEquals(listOf(false, false, false, false, true, false, true), store.recentActivity(7))
    }
}
