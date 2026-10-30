package dev.russell.mmtrainer

import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.content.Lesson
import dev.russell.mmtrainer.content.Problem
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.median
import dev.russell.mmtrainer.py.pyRound
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Session engine: decides what to show next and applies the results. UI-agnostic.
 *
 * Quick session structure (Mode.DAILY; several a day are fine)
 *   1. Introduce a new topic when the learning load allows it and the topics it builds on are
 *      solid (Curriculum.PREREQUISITES): lesson, then a short
 *      *blocked* run (several problems of just that topic) to acquire the method.
 *   2. Everything else is *interleaved*: due fact reviews, new facts (first shown as a
 *      retrieval attempt — if you already know it, it skips straight to long intervals),
 *      and skill problems weighted towards skills that are due, weak or new. The same topic
 *      never appears twice in a row, and missed facts come back a few items later. Facts come
 *      in random order (new ones from a small window of the next unseen cards), and products
 *      are asked both ways round, so no answer can be read off the one before.
 *   3. Stops once the time budget is used and every missed fact has been answered again.
 */

const val BLOCKED_PROBLEMS = 5     // problems in the focused run right after a skill's lesson
const val BLOCKED_CARDS = 6        // new facts presented back to back right after a deck's lesson
const val NEW_CARD_WINDOW = 10     // new facts are drawn at random from this many next unseen cards of a deck
const val MAX_NEW_EXPOSURES = 40   // hard cap on new-card exposures per session
const val OVERTIME = 60.0          // seconds allowed past the budget to finish relearning missed facts

enum class Mode(val id: String) { DAILY("daily"), PRACTICE("practice"), BENCHMARK("benchmark") }

data class Item(
    val kind: String,                       // "lesson" | "problem"
    val sourceId: String,                   // skill or deck id
    val title: String,
    val lesson: Lesson? = null,
    val problem: Problem? = null,
    val level: Int? = null,
    val cardId: String? = null,
    val targetSeconds: Double = 0.0,
    val newCard: Boolean = false,           // first exposure: blank answer reveals it
    val relearn: Boolean = false,
    val tag: String = "",                   // e.g. "Learning 2/5", "Review"
) {
    val isCard: Boolean get() = cardId != null
    val isLesson: Boolean get() = kind == "lesson"
}

data class Result(
    val item: Item,
    val response: String,
    val value: Double?,
    val correct: Boolean,
    val timeS: Double,
    val hinted: Boolean,
    var promotedTo: Int? = null,
)

data class SkillSummary(
    val n: Int,
    val ok: Int,
    val times: List<Double>,
    val accuracy: Double,
    val median: Double?,
    val pace: Double,                       // median(target / time), capped; 0 if nothing correct
)

data class Summary(
    val mode: Mode,
    val results: List<Result>,
    val durationS: Double,
    val introduced: List<String>,
    val promotions: List<Pair<String, Int>>,
    val score: Double? = null,
    val perSkill: Map<String, SkillSummary> = emptyMap(),
) {
    val n: Int get() = results.size
    val nCorrect: Int get() = results.count { it.correct && !it.hinted }
    val misses: List<Result> get() = results.filter { !it.correct || it.hinted }
}

fun problemTitle(skillId: String, level: Int): String {
    val skill = Content.SKILLS.getValue(skillId)
    return "${skill.name} · L${level + 1}: ${skill.levels[level].description}"
}

class Session(
    val store: Store,
    val mode: Mode = Mode.DAILY,
    minutes: Double? = null,
    itemIds: List<String>? = null,
    val audio: Boolean = false,                             // Android only: problems are read aloud
    val rng: Rng = Rng(),
    private val clock: () -> Double = { System.nanoTime() / 1e9 },
) {
    var budget: Double? = minutes?.let { it * 60 }
    val sessionId: Long
    private val started: Double = clock()
    val results = mutableListOf<Result>()
    val introduced = mutableListOf<String>()
    val promotions = mutableListOf<Pair<String, Int>>()
    var index = 0                                           // items served
        private set
    private var recentSources = listOf<String>()
    private val lastPrompt = mutableMapOf<String, String>()
    private var relearn = mutableListOf<Pair<Int, String>>() // (ready at index, card id)
    private val servedCards = mutableSetOf<String>()
    private var newLearned = 0                               // new cards not already known
    private var newExposures = 0
    private var blocked = mutableListOf<Item>()              // queued focused-run items
    private var pendingLesson: Item? = null
    var finished = false
        private set

    private var benchmarkQueue = mutableListOf<Item>()
    var benchmarkTotal = 0
        private set
    private val skillIds = mutableListOf<String>()
    private val deckIds = mutableListOf<String>()

    init {
        if (mode == Mode.DAILY && minutes == null) budget = store.settingInt(Settings.SESSION_MINUTES) * 60.0
        sessionId = store.startSession(mode.id, audio)
        when (mode) {
            Mode.BENCHMARK -> {
                val battery = mutableListOf<Pair<String, Int>>()
                repeat(Curriculum.BENCHMARK_ROUNDS) {  // shuffle each round separately to keep repeats apart
                    val round = Curriculum.BENCHMARK.filter { Content.isSkill(it.first) }.toMutableList()
                    rng.shuffle(round)
                    battery += round
                }
                benchmarkQueue = battery.map { (s, lvl) -> skillItem(s, lvl) }.toMutableList()
                benchmarkTotal = battery.size
            }
            Mode.PRACTICE -> {
                val ids = itemIds?.takeIf { it.isNotEmpty() } ?: store.introduced().keys.toList()
                skillIds += ids.filter { Content.isSkill(it) }
                deckIds += ids.filter { Content.isDeck(it) }
            }
            Mode.DAILY -> {
                val intro = store.introduced().keys
                skillIds += intro.filter { Content.isSkill(it) }
                deckIds += intro.filter { Content.isDeck(it) }
            }
        }
    }

    // ------------------------------------------------------------ timing

    val elapsed: Double get() = clock() - started

    val fractionDone: Double
        get() {
            if (mode == Mode.BENCHMARK) return index.toDouble() / maxOf(1, benchmarkTotal)
            val b = budget
            if (b == null || b == 0.0) return 0.0
            return minOf(1.0, elapsed / b)
        }

    // ------------------------------------------------------------ item builders

    private fun skillItem(skillId: String, level: Int, tag: String = ""): Item {
        val skill = Content.SKILLS.getValue(skillId)
        val lvl = skill.levels[level]
        var problem = lvl.generate(rng)
        repeat(5) {  // avoid repeating the exact previous problem
            if (problem.prompt == lastPrompt[skillId]) problem = lvl.generate(rng)
        }
        lastPrompt[skillId] = problem.prompt
        return Item("problem", skillId, problemTitle(skillId, level), problem = problem, level = level,
            targetSeconds = lvl.targetSeconds, tag = tag)
    }

    private fun cardItem(cardId: String, relearn: Boolean = false, tag: String = ""): Item {
        val (deck, card) = Content.CARDS.getValue(cardId)
        val new = store.cardRow(cardId) == null
        if (new) newExposures += 1
        val alt = card.altPrompt != null && rng.random() < 0.5  // ask 6 × 2 as well as 2 × 6
        return Item("problem", deck.id, deck.name, problem = card.asProblem(alt), cardId = cardId,
            targetSeconds = deck.targetSeconds, newCard = new, relearn = relearn,
            tag = tag.ifEmpty { if (new) "New fact" else if (relearn) "Again" else "Review" })
    }

    private fun lessonItem(itemId: String): Item {
        val obj = Content.get(itemId)
        return Item("lesson", itemId, obj.name, lesson = obj.lesson)
    }

    // ------------------------------------------------------------ introduction

    private fun maybeStartIntro(): Boolean {
        if (mode != Mode.DAILY || pendingLesson != null || blocked.isNotEmpty()) return false
        budget?.let { if (it != 0.0 && elapsed > 0.5 * it) return false }
        val stillLearning = introduced.count { inLearning(it) }
        if (stillLearning >= store.settingInt(Settings.MAX_NEW_TOPICS) || introduced.size >= 5) return false
        if (!store.canIntroduce()) return false
        val itemId = store.nextToIntroduce()!!
        store.introduce(itemId)
        introduced += itemId
        if (Content.isSkill(itemId)) {
            skillIds += itemId
            blocked = (0 until BLOCKED_PROBLEMS)
                .map { k -> skillItem(itemId, 0, "Learning ${k + 1}/$BLOCKED_PROBLEMS") }.toMutableList()
        } else {
            deckIds += itemId
            val cards = store.unseenCards(itemId).take(NEW_CARD_WINDOW).toMutableList()
                .also { rng.shuffle(it) }.take(BLOCKED_CARDS)
            blocked = cards.map { cardItem(it, tag = "New fact") }.toMutableList()
            servedCards += cards
        }
        pendingLesson = lessonItem(itemId)
        return true
    }

    private fun inLearning(itemId: String): Boolean =
        if (Content.isSkill(itemId)) store.skillStatus(itemId).inLearning else store.deckUnlearned(itemId) >= 5

    // ------------------------------------------------------------ selection

    fun nextItem(): Item? {
        if (finished) return null
        val item = select() ?: return null
        if (item.kind == "problem") {
            index += 1
            recentSources = (recentSources + item.sourceId).takeLast(3)
        }
        return item
    }

    private fun select(): Item? {
        if (mode == Mode.BENCHMARK) return benchmarkQueue.removeFirstOrNull()

        pendingLesson?.let {
            pendingLesson = null
            return it
        }
        if (blocked.isNotEmpty()) return blocked.removeAt(0)

        val b = budget
        val overBudget = b != null && elapsed >= b
        if (overBudget && (relearn.isEmpty() || elapsed >= b!! + OVERTIME)) return null

        // Warm up with a couple of interleaved items before a new lesson, unless nothing to review.
        if (index >= 2 || !(skillIds.isNotEmpty() || store.dueCards(deckIds).isNotEmpty())) {
            if (maybeStartIntro()) return select()
        }

        val ready = relearn.filter { it.first <= index }.map { it.second }
        if (ready.isNotEmpty()) {
            val card = ready[0]
            relearn = relearn.filter { it.second != card }.toMutableList()
            return cardItem(card, relearn = true)
        }

        val card = if (overBudget) null else pickCard()
        val hasSkills = skillIds.isNotEmpty()
        val factShare = if (store.dueCards(deckIds).size > 20) 0.45 else 0.35
        if (card != null && (!hasSkills || rng.random() < factShare)) {
            servedCards += card
            return cardItem(card)
        }
        if (hasSkills) return pickSkill()
        if (relearn.isNotEmpty()) {  // only missed facts left: serve the next even if early
            val (_, c) = relearn.removeAt(0)
            return cardItem(c, relearn = true)
        }
        if (card != null) {
            servedCards += card
            return cardItem(card)
        }
        if (mode == Mode.PRACTICE && deckIds.isNotEmpty()) return randomSeenCard()  // free practice: drill seen cards
        return null
    }

    private fun pickCard(): String? {
        val pending = relearn.map { it.second }.toSet()
        val avoid = recentSources.takeLast(1)
        val due = store.dueCards(deckIds).filter { it !in servedCards && it !in pending }
        if (due.isNotEmpty()) {
            val preferred = due.filter { Content.CARDS.getValue(it).first.id !in avoid }
            return rng.choice(preferred.ifEmpty { due })  // random order, so one answer doesn't cue the next
        }
        if (newLearned >= store.settingInt(Settings.NEW_CARDS) || newExposures >= MAX_NEW_EXPOSURES) return null
        for (deckId in deckIds) {  // earliest introduced deck first; easy facts first, but not in sequence
            val unseen = store.unseenCards(deckId).filter { it !in servedCards }
            if (unseen.isNotEmpty()) return rng.choice(unseen.take(NEW_CARD_WINDOW))
        }
        return null
    }

    private fun randomSeenCard(): Item {
        val placeholders = deckIds.joinToString(",") { "?" }
        var rows = store.db.query("SELECT id FROM cards WHERE deck_id IN ($placeholders)", deckIds)
            .map { it.string("id") }.filter { it in Content.CARDS }
        if (rows.isEmpty()) rows = deckIds.flatMap { d -> Content.DECKS.getValue(d).cards.map { it.id } }
        return cardItem(rng.choice(rows), tag = "Practice")
    }

    private fun pickSkill(): Item {
        var candidates = skillIds.toList()
        val avoid = (if (candidates.size >= 3) recentSources.takeLast(2) else recentSources.takeLast(1)).toSet()
        candidates = candidates.filter { it !in avoid }.ifEmpty { candidates }
        val weights = candidates.map { s ->
            val st = store.skillStatus(s)
            var w = 1.0 + 2.0 * (1.0 - st.progress)
            if (st.due) w += 2.0
            if (s in introduced) w += 2.0
            if (st.mastered) w *= 0.5
            w
        }
        val skillId = rng.choices(candidates, weights)
        val level = store.skillStatus(skillId).level
        if (level > 0 && rng.random() < 0.2) {  // occasionally revisit an earlier level
            return skillItem(skillId, rng.randrange(level), "Review")
        }
        return skillItem(skillId, level)
    }

    // ------------------------------------------------------------ results

    fun submit(item: Item, response: String, value: Double?, timeS: Double, hinted: Boolean = false): Result {
        val p = item.problem!!
        val correct = value != null && p.isCorrect(value)
        val result = Result(item, response, value, correct, timeS, hinted)
        results += result
        store.record(
            sessionId = sessionId, mode = mode.id, kind = if (item.isCard) "card" else "skill",
            itemId = item.sourceId, cardId = item.cardId, level = item.level, prompt = p.prompt,
            answer = p.displayAnswer, response = response, correct = correct, timeMs = timeS * 1000,
            targetMs = item.targetSeconds * 1000, hinted = hinted)
        if (mode == Mode.BENCHMARK) return result

        if (item.isCard) {
            val cardId = item.cardId!!
            val row = store.cardRow(cardId)
            val scheduled = row == null || row.state == "learning" || cardId in servedCards || mode == Mode.DAILY
            val grade = Store.grade(correct, hinted, timeS, item.targetSeconds)
            if (scheduled) {
                if (row == null && grade == AGAIN) newLearned += 1
                store.reviewCard(cardId, grade, rng)
            }
            if (grade == AGAIN) relearn.add((index + rng.randint(3, 6)) to cardId)
        } else {
            val current = store.skillStatus(item.sourceId).level
            if (item.level == current) {
                val newLevel = store.checkPromotion(item.sourceId)
                if (newLevel != null) {
                    result.promotedTo = newLevel
                    promotions += item.sourceId to newLevel
                    // the blocked run is moot once the skill is passed; finish it at the new level
                    blocked = blocked.map { b ->
                        if (b.sourceId == item.sourceId) skillItem(b.sourceId, newLevel, b.tag) else b
                    }.toMutableList()
                }
            }
        }
        return result
    }

    // ------------------------------------------------------------ wrap-up

    fun finish(): Summary {
        finished = true
        class Acc(var n: Int = 0, var ok: Int = 0, val times: MutableList<Double> = mutableListOf(),
                  val targets: MutableList<Double> = mutableListOf())
        val acc = LinkedHashMap<String, Acc>()
        for (r in results) {
            if (r.item.isCard) continue
            val d = acc.getOrPut(r.item.sourceId) { Acc() }
            d.n += 1
            if (r.correct && !r.hinted) {
                d.ok += 1
                d.times += r.timeS
                d.targets += r.item.targetSeconds
            }
        }
        val perSkill = LinkedHashMap<String, SkillSummary>()
        for ((id, d) in acc) {
            val pace = if (d.times.isNotEmpty())
                median(d.targets.zip(d.times).map { (t, x) -> minOf(PACE_CAP, t / maxOf(x, 0.01)) }) else 0.0
            perSkill[id] = SkillSummary(d.n, d.ok, d.times, d.ok.toDouble() / d.n,
                if (d.times.isNotEmpty()) median(d.times) else null, pace)
        }

        var score: Double? = null
        var details: String? = null
        if (mode == Mode.BENCHMARK && results.isNotEmpty() && benchmarkQueue.isEmpty()) {
            val total = results.sumOf { it.timeS }
            val earned = results.filter { it.correct }.sumOf { it.item.targetSeconds }
            score = if (total != 0.0) 100 * earned / total else 0.0
            details = buildJsonObject {
                put("items", buildJsonArray {
                    for (r in results) add(buildJsonObject {
                        put("skill", r.item.sourceId)
                        put("level", r.item.level)
                        put("correct", r.correct)
                        put("time", JsonPrimitive(pyRound(r.timeS, 2)))
                        put("target", r.item.targetSeconds)
                    })
                })
            }.toString()
        } else if (mode == Mode.DAILY) {
            val promoted = promotions.map { it.first }.toSet()
            for ((skillId, d) in perSkill) store.rescheduleSkill(skillId, d.accuracy, d.pace, skillId in promoted)
        }

        store.endSession(sessionId, results.size, results.count { it.correct && !it.hinted }, score, details)
        return Summary(mode, results.toList(), elapsed, introduced.toList(), promotions.toList(), score, perSkill)
    }
}
