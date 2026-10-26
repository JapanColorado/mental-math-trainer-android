package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.Answers
import dev.russell.mmtrainer.py.pyRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FactsTest {
    private val DECK_IDS = listOf("d_times", "d_teens", "d_squares", "d_cubes", "d_pow2", "d_powers", "d_recip",
        "d_log10", "d_ln", "d_exp", "d_consts", "d_trig")
    private val BY_ID = Facts.DECKS.associateBy { it.id }
    private val ALL_CARDS = Facts.DECKS.flatMap { it.cards }

    private fun deck(id: String) = BY_ID.getValue(id)

    private fun supInt(s: String): Int = s.replace("−", "-").toInt()

    private fun displayValue(text: String): Double? = Answers.parse(text.removePrefix("≈ ").trimEnd('…'))

    /** pytest.approx(expected, rel=rel) with its default abs=1e-12. */
    private fun approx(actual: Double, expected: Double, rel: Double = 1e-6): Boolean =
        Math.abs(actual - expected) <= maxOf(rel * Math.abs(expected), 1e-12)

    private fun card(cid: String) = ALL_CARDS.first { it.id == cid }

    @Test
    fun deckIdsAndMetadata() {
        assertEquals(DECK_IDS, Facts.DECKS.map { it.id })
        for (d in Facts.DECKS) {
            assertTrue(d.track in TRACKS)
            assertTrue(d.name.isNotEmpty() && d.cards.isNotEmpty())
            val words = d.lesson.body.replace(Regex("<[^>]+>"), "").split(Regex("(?U)\\s+")).filter { it.isNotEmpty() }
            assertTrue(d.id, words.size in 80..350)
            assertTrue(d.targetSeconds in 1.0..5.0)
        }
    }

    @Test
    fun cardIdsUnique() {
        val ids = ALL_CARDS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun cardBasics() {
        for (card in ALL_CARDS) {
            val id = card.id
            assertTrue(id, card.answer.isFinite())
            assertTrue(id, card.prompt.isNotEmpty() && card.displayAnswer.isNotEmpty() && card.note.isNotEmpty())
            assertTrue(id, card.note.split("<br>").size - 1 <= 1 && card.note.length < 200)
            assertTrue(id, card.tolRel < 0.2)
            val p = card.asProblem()
            assertTrue(id, p.isCorrect(card.answer))
            // the shown answer (rounded or truncated) must itself be graded correct
            val shown = displayValue(card.displayAnswer)
            assertNotNull(card.displayAnswer, shown)
            assertTrue("$id ${card.displayAnswer}", p.isCorrect(shown!!))
            assertTrue(id, card.displayAnswer.startsWith("≈ ") || "…" in card.displayAnswer ||
                approx(shown, card.answer, rel = 1e-12))
        }
    }

    @Test
    fun integerDecksRecompute() {
        for (c in deck("d_times").cards + deck("d_teens").cards) {
            val (a, b) = c.prompt.split(" × ").map { it.toInt() }
            assertTrue(c.answer == (a * b).toDouble() && c.tolAbs == 0.0 && c.tolRel == 0.0)
            assertTrue((a * b).toString() in c.note)
        }
        val times = deck("d_times").cards.map { c -> c.prompt.split(" × ").map { it.toInt() } }.toSet()
        assertTrue(times.size == 55 && times.all { (a, b) -> a <= b })
        assertFalse(times.any { pair -> pair.any { it == 1 || it == 10 } })
        assertEquals(9 * 8, deck("d_teens").cards.size)
        for (c in deck("d_times").cards + deck("d_teens").cards) {  // asked both ways round, except squares
            val (a, b) = c.prompt.split(" × ")
            assertEquals(c.id, if (a == b) null else "$b × $a", c.altPrompt)
            assertEquals(c.altPrompt ?: c.prompt, c.asProblem(alt = true).prompt)
        }
        for (d in Facts.DECKS.filter { it.id != "d_times" && it.id != "d_teens" }) {
            assertTrue(d.id, d.cards.all { it.altPrompt == null })
        }
        for (c in deck("d_squares").cards) {
            val n = c.prompt.trimEnd('²').toInt()
            assertTrue(c.answer == (n * n).toDouble() && (n * n).toString() in c.note)
        }
        for (c in deck("d_cubes").cards) {
            val n = c.prompt.trimEnd('³').toInt()
            assertTrue(c.answer == (n * n * n).toDouble() && c.displayAnswer in c.note)
        }
        for (c in deck("d_powers").cards) {
            val (b, e) = Regex("""(\d+)<sup>(\d+)</sup>""").matchEntire(c.prompt)!!.destructured
            assertEquals(Math.pow(b.toDouble(), e.toDouble()), c.answer, 0.0)
        }
        for (c in deck("d_pow2").cards) {
            val e = supInt(Regex("""2<sup>(.+)</sup>""").matchEntire(c.prompt)!!.groupValues[1])
            assertEquals(Math.pow(2.0, e.toDouble()), c.answer, 0.0)
        }
    }

    @Test
    fun noDuplicateFactsAcrossPowerDecks() {
        val cubes = deck("d_cubes").cards.map { it.prompt.trimEnd('³') }.toSet()
        for (c in deck("d_powers").cards) {
            val (b, e) = Regex("""(\d+)<sup>(\d+)</sup>""").matchEntire(c.prompt)!!.destructured
            assertFalse(e == "3" && b in cubes)
        }
    }

    @Test
    fun generatedNotes() {
        val cases = listOf(
            "times:7x8" to "56 = 7 × 8",
            "times:6x8" to "(7 − 1)(7 + 1) = 7² − 1 = 49 − 1 = 48",
            "times:7x9" to "70 − 7 = 63",
            "teens:17x6" to "60 + 42 = 102",
            "teens:19x7" to "140 − 7 = 133",
            "squares:13" to "160 + 9 = 169",
            "squares:27" to "24 × 30 + 3² = 720 + 9 = 729",
            "squares:25" to "2 × 3 = 6, then append 25 → 625",
            "pow2:13" to "8 × 1024 = 8192",
        )
        for ((cid, fragment) in cases) {
            assertTrue(cid, fragment in card(cid).note)
        }
    }

    @Test
    fun generatedNoteArithmetic() {
        // every "x = y" chain in the times/teens/squares notes should hold numerically
        val chainRe = Regex("""(?=\b(\d+) ([+−]) (\d+) = (\d+))""")
        for (c in deck("d_times").cards + deck("d_teens").cards + deck("d_squares").cards) {
            val parts = c.prompt.split(" × ")
            if (parts.first() == parts.last()) continue  // a × a: the note's last step is about the neighbours
            val chains = chainRe.findAll(c.note).toList()
            if (chains.isNotEmpty()) {
                val g = chains.last().groupValues
                val x = g[1].toInt()
                val op = g[2]
                val y = g[3].toInt()
                val total = g[4].toInt()
                val r = if (op == "+") x + y else x - y
                assertTrue(c.note, r == total && total.toDouble() == c.answer)
            }
        }
    }

    @Test
    fun recipAnswers() {
        for (c in deck("d_recip").cards) {
            val (num, den) = c.prompt.split("/").map { it.toInt() }
            assertEquals(num.toDouble() / den, c.answer, 0.0)
            assertTrue(c.hint == "3 decimal places" && approx(c.tolAbs, 0.0006))
            assertTrue(c.asProblem().isCorrect(pyRound(c.answer, 3)))
            assertFalse(c.asProblem().isCorrect(pyRound(c.answer, 3) + 0.002))
        }
    }

    private val EXPECTED: Map<String, Double> = linkedMapOf(
        "log10:2" to Math.log10(2.0), "log10:3" to Math.log10(3.0), "log10:7" to Math.log10(7.0),
        "log10:9" to Math.log10(9.0), "log10:1.5" to Math.log10(1.5), "log10:pi" to Math.log10(Math.PI),
        "log10:e" to 1 / Math.log(10.0), "log10:10^0.5" to Math.sqrt(10.0), "log10:10^0.25" to Math.pow(10.0, 0.25),
        "log10:10^0.9" to Math.pow(10.0, 0.9),
        "ln:2" to Math.log(2.0), "ln:7" to Math.log(7.0), "ln:1.1" to Math.log1p(0.1),
        "ln:0.5" to -Math.log(2.0), "ln:inv10" to Math.log10(Math.E), "ln:inv2" to Math.log(Math.E) / Math.log(2.0),
        "exp:1" to Math.E, "exp:-1" to 1 / Math.E, "exp:ln2" to 2.0, "exp:ln10" to 10.0,
        "exp:3" to 20.085537, "exp:10" to 22026.466,
        "consts:pi" to 3.14159265, "consts:phi" to 1.6180340, "consts:sqrt7" to 2.6457513,
        "consts:cbrt2" to 1.2599210, "consts:sqrt2pi" to 2.5066283, "consts:pi2" to 9.8696044,
        "trig:sin30" to Math.sin(Math.toRadians(30.0)), "trig:sin60" to Math.sin(Math.toRadians(60.0)),
        "trig:tan30" to Math.tan(Math.toRadians(30.0)), "trig:sin15" to Math.sin(Math.toRadians(15.0)),
        "trig:cos15" to Math.cos(Math.toRadians(15.0)), "trig:tan15" to Math.tan(Math.toRadians(15.0)),
        "trig:tan75" to Math.tan(Math.toRadians(75.0)), "trig:sin18" to Math.sin(Math.toRadians(18.0)),
        "trig:cos36" to Math.cos(Math.toRadians(36.0)), "trig:rad" to Math.toDegrees(1.0),
        "trig:deg1" to Math.toRadians(1.0), "trig:deg60" to Math.toRadians(60.0),
    )

    @Test
    fun expectedValues() {
        for ((cid, expected) in EXPECTED) {
            // exp:ln2 / exp:ln10 use the rounded exponents 0.693 / 2.303 so only ≈ 2 / 10
            val rel = if (cid.startsWith("exp:ln")) 1e-3 else 1e-6
            assertTrue(cid, approx(card(cid).answer, expected, rel))
        }
    }

    @Test
    fun trigPromptsMatchMath() {
        val funcs = mapOf<String, (Double) -> Double>("sin" to Math::sin, "cos" to Math::cos, "tan" to Math::tan)
        for (c in deck("d_trig").cards) {
            val m = Regex("""(sin|cos|tan) (\d+)°""").matchEntire(c.prompt) ?: continue
            val f = funcs.getValue(m.groupValues[1])
            assertTrue(c.id, approx(c.answer, f(Math.toRadians(m.groupValues[2].toInt().toDouble()))))
        }
    }

    @Test
    fun reverseLogPromptsMatch() {
        for (c in deck("d_log10").cards) {
            val m = Regex("""10<sup>([\d.]+)</sup>""").matchEntire(c.prompt)
            if (m != null) {
                assertTrue(c.id, approx(c.answer, Math.pow(10.0, m.groupValues[1].toDouble())))
            } else {
                val arg = c.prompt.removePrefix("log<sub>10</sub> ")
                val x = mapOf("π" to Math.PI, "e" to Math.E)[arg] ?: arg.toDouble()
                assertTrue(c.id, approx(c.answer, Math.log10(x)))
            }
        }
    }

    @Test
    fun expPromptsMatch() {
        for (c in deck("d_exp").cards) {
            val x = Regex("""e<sup>(.+)</sup>""").matchEntire(c.prompt)!!.groupValues[1].replace("−", "-").toDouble()
            assertTrue(c.id, approx(c.answer, Math.exp(x)))
        }
    }

    @Test
    fun hintPrecisionPasses() {
        // An answer given at the hinted precision must be accepted.
        val cases = listOf<Pair<String, ((Double) -> Double)?>>(
            "d_log10" to null, "d_ln" to { x -> pyRound(x, 3) }, "d_exp" to { x -> sigRound(x, 3) },
            "d_consts" to { x -> sigRound(x, 4) }, "d_trig" to null,
        )
        for ((deckId, rounder) in cases) {
            for (c in deck(deckId).cards) {
                val rounded = rounder?.invoke(c.answer)
                    ?: if ("decimal" in c.hint) pyRound(c.answer, 3) else sigRound(c.answer, 3)
                assertTrue("${c.id} $rounded", c.asProblem().isCorrect(rounded))
            }
        }
    }

    @Test
    fun tolerancesRejectSloppyAnswers() {
        for (c in deck("d_consts").cards) {  // off by 2 in the 4th significant figure
            assertFalse(c.id, c.asProblem().isCorrect(c.answer * 1.002))
        }
        for (c in deck("d_log10").cards.take(3)) {
            assertFalse(c.id, c.asProblem().isCorrect(c.answer + 0.01))
        }
    }
}
