package dev.russell.mmtrainer

import dev.russell.mmtrainer.content.Card
import dev.russell.mmtrainer.content.ContentModule
import dev.russell.mmtrainer.content.Problem
import dev.russell.mmtrainer.py.Rng
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Loads the fixtures written by tools/golden/dump_golden.py from the Python reference app. */
object Golden {
    fun load(name: String): JsonObject {
        val stream = Golden::class.java.getResourceAsStream("/golden/$name.json")
            ?: error("missing golden fixture $name.json — run `pixi run golden`")
        return Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
    }

    /**
     * Compare a content module against its fixture: skill ids/names/tracks/lessons/levels, every
     * generated problem for the recorded seeds, and every deck card. Returns the number of checks.
     */
    fun checkModule(name: String, module: ContentModule): Int {
        val g = load(name)
        var checks = 0
        val skills = g.arr("skills")
        assertEquals("$name: skill ids", skills.map { it.obj.str("id") }, module.SKILLS.map { it.id })
        for ((sj, skill) in skills.zip(module.SKILLS)) {
            val s = sj.obj
            assertEquals("${skill.id}: name", s.str("name"), skill.name)
            assertEquals("${skill.id}: track", s.str("track"), skill.track)
            assertEquals("${skill.id}: lesson title", s.obj("lesson").str("title"), skill.lesson.title)
            assertEquals("${skill.id}: lesson body", s.obj("lesson").str("body"), skill.lesson.body)
            val levels = s.arr("levels")
            assertEquals("${skill.id}: level count", levels.size, skill.levels.size)
            for ((i, lj) in levels.withIndex()) {
                val lv = skill.levels[i]
                val l = lj.obj
                assertEquals("${skill.id} L${i + 1}: description", l.str("description"), lv.description)
                assertEquals("${skill.id} L${i + 1}: target", l.dbl("target_seconds"), lv.targetSeconds, 0.0)
                for (run in l.arr("runs")) {
                    val seed = run.obj.long("seed")
                    val rng = Rng(seed)
                    for ((k, pj) in run.obj.arr("problems").withIndex()) {
                        val where = "${skill.id} L${i + 1} seed $seed #$k"
                        val p = try {
                            lv.generate(rng)
                        } catch (e: Throwable) {
                            throw AssertionError("$where: generator threw (expected prompt ${pj.obj.str("prompt")})", e)
                        }
                        checkProblem(where, pj.obj, p)
                        checks++
                    }
                }
            }
        }
        val decks = g.arr("decks")
        assertEquals("$name: deck ids", decks.map { it.obj.str("id") }, module.DECKS.map { it.id })
        for ((dj, deck) in decks.zip(module.DECKS)) {
            val d = dj.obj
            assertEquals("${deck.id}: name", d.str("name"), deck.name)
            assertEquals("${deck.id}: track", d.str("track"), deck.track)
            assertEquals("${deck.id}: lesson title", d.obj("lesson").str("title"), deck.lesson.title)
            assertEquals("${deck.id}: lesson body", d.obj("lesson").str("body"), deck.lesson.body)
            assertEquals("${deck.id}: target", d.dbl("target_seconds"), deck.targetSeconds, 0.0)
            val cards = d.arr("cards")
            assertEquals("${deck.id}: card ids", cards.map { it.obj.str("id") }, deck.cards.map { it.id })
            for ((cj, card) in cards.zip(deck.cards)) {
                checkCard(cj.obj, card)
                checks++
            }
        }
        return checks
    }

    fun checkProblem(where: String, e: JsonObject, p: Problem) {
        assertEquals("$where: prompt", e.str("prompt"), p.prompt)
        assertClose("$where: answer", e.dbl("answer"), p.answer)
        assertEquals("$where: display", e.str("display"), p.displayAnswer)
        assertClose("$where: tol_abs", e.dbl("tol_abs"), p.tolAbs)
        assertClose("$where: tol_rel", e.dbl("tol_rel"), p.tolRel)
        assertEquals("$where: explanation", e.str("explanation"), p.explanation)
        assertEquals("$where: hint", e.str("hint"), p.hint)
        assertEquals("$where: format_hint", e.str("format_hint"), p.formatHint())
    }

    fun checkCard(e: JsonObject, c: Card) {
        val where = "card ${c.id}"
        assertEquals("$where: prompt", e.str("prompt"), c.prompt)
        assertClose("$where: answer", e.dbl("answer"), c.answer)
        assertEquals("$where: display", e.str("display"), c.displayAnswer)
        assertClose("$where: tol_abs", e.dbl("tol_abs"), c.tolAbs)
        assertClose("$where: tol_rel", e.dbl("tol_rel"), c.tolRel)
        assertEquals("$where: note", e.str("note"), c.note)
        assertEquals("$where: hint", e.str("hint"), c.hint)
    }

    /** Numeric values may differ in the last ulp (libm vs. java.lang.Math); strings may not. */
    fun assertClose(msg: String, expected: Double, actual: Double) {
        if (expected == actual || (expected.isNaN() && actual.isNaN())) return
        val tol = 1e-12 * maxOf(1.0, Math.abs(expected))
        assertTrue("$msg: expected $expected but was $actual", Math.abs(expected - actual) <= tol)
    }
}

val JsonElement.obj: JsonObject get() = jsonObject
fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
fun JsonObject.arr(key: String): JsonArray = getValue(key).jsonArray
fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
fun JsonObject.strOrNull(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
fun JsonObject.dbl(key: String): Double = getValue(key).jsonPrimitive.doubleOrNull ?: error("$key is not a number")
fun JsonObject.dblOrNull(key: String): Double? = (get(key) as? JsonPrimitive)?.doubleOrNull
fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.content.toLong()
fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.booleanOrNull ?: error("$key is not a bool")
fun JsonElement.isNull(): Boolean = this is JsonNull
