package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.Answers
import dev.russell.mmtrainer.py.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimationTest {
    private val n = 300
    private val skills = Estimation.SKILLS
    private val levels = skills.flatMap { s -> s.levels.mapIndexed { i, lv -> Triple(s, i, lv) } }

    /** pytest.approx: |actual − expected| <= max(rel·|expected|, abs). */
    private fun approx(actual: Double, expected: Double, rel: Double = 1e-6, abs: Double = 1e-12): Boolean =
        Math.abs(actual - expected) <= maxOf(rel * Math.abs(expected), abs)

    /** Leading number of a rich-text snippet ("≈ 1.37 × 10<sup>−6</sup>", "20 100"). */
    private fun toNumber(html: String): Double {
        var s = html.replace(THIN, "").replace(MINUS, "-")
        s = Regex("""\s*×\s*10<sup>(-?\d+)</sup>""").replace(s, "e$1")
        s = Regex("<[^>]+>").replace(s, "")
        val m = Regex("""\s*≈?\s*(-?\d+(?:\.\d+)?(?:e-?\d+)?)""").matchAt(s, 0)
        assertNotNull(html, m)
        return m!!.groupValues[1].toDouble()
    }

    private fun boldValue(expl: String): Double =
        toNumber(Regex("<b>(.*?)</b>").findAll(expl).map { it.groupValues[1] }.toList().last())

    /** Independently evaluate a prompt like "√(4.2 × 10<sup>7</sup>) × 0.035". */
    private fun promptValue(prompt: String): Double {
        var s = prompt.replace(THIN, "").replace(MINUS, "-").replace("×", "*").replace("÷", "/")
        s = Regex("""<sup>(-?\d+)</sup>""").replace(s, "**($1)").replace("√", "sqrt")
        assertTrue(s, Regex("""[\d.\s*/()+\-sqrt]+""").matches(s))
        return ExprParser(s).parse()
    }

    /** Tiny evaluator for the arithmetic expressions built by promptValue. */
    private class ExprParser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            skipWs()
            check(pos == s.length) { "trailing input in $s at $pos" }
            return v
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(tok: String): Boolean {
            skipWs()
            return s.startsWith(tok, pos)
        }

        private fun eat(tok: String): Boolean {
            if (peek(tok)) {
                pos += tok.length
                return true
            }
            return false
        }

        private fun expr(): Double {
            var v = mul()
            while (true) {
                v = when {
                    eat("+") -> v + mul()
                    eat("-") -> v - mul()
                    else -> return v
                }
            }
        }

        private fun mul(): Double {
            var v = unary()
            while (true) {
                v = when {
                    peek("**") -> return v
                    eat("*") -> v * unary()
                    eat("/") -> v / unary()
                    else -> return v
                }
            }
        }

        private fun unary(): Double = when {
            eat("-") -> -unary()
            eat("+") -> unary()
            else -> power()
        }

        private fun power(): Double {
            val base = atom()
            return if (eat("**")) Math.pow(base, unary()) else base
        }

        private fun atom(): Double {
            if (eat("(")) {
                val v = expr()
                check(eat(")")) { "missing ) in $s" }
                return v
            }
            if (eat("sqrt")) {
                check(eat("(")) { "missing ( in $s" }
                val v = expr()
                check(eat(")")) { "missing ) in $s" }
                return Math.sqrt(v)
            }
            skipWs()
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            check(pos > start) { "number expected in $s at $start" }
            return s.substring(start, pos).toDouble()
        }
    }

    @Test
    fun skillIdsAndTrack() {
        assertEquals(listOf("s_sci", "s_fermi", "s_recip"), skills.map { it.id })
        assertEquals(listOf(3, 3, 2), skills.map { it.levels.size })
        for (s in skills) {
            assertEquals("estimation", s.track)
            assertTrue(s.lesson.body.length in 401..4499)
            assertTrue(s.levels.all { it.targetSeconds > 0 })
        }
    }

    @Test
    fun generatedProblems() {
        for ((skill, i, level) in levels) {
            val id = "${skill.id}-L${i + 1}"
            val rng = Rng(1000L + i)
            repeat(n) {
                val p = level.generate(rng)
                assertTrue(id, p.answer.isFinite() && p.answer != 0.0)
                assertTrue(id, p.isCorrect(p.answer))
                for (text in listOf(p.prompt, p.explanation, p.displayAnswer, p.formatHint())) {
                    assertTrue(id, text.isNotBlank())
                }
                assertTrue(id, p.tolRel > 0 && p.tolRel < 0.2 && p.tolAbs == 0.0)
                // the prompt really means the graded answer
                assertTrue("$id ${p.prompt}", approx(promptValue(p.prompt), p.answer, rel = 1e-9))
                // the displayed answer and the explanation's final estimate are both acceptable
                assertTrue("$id ${p.prompt} ${p.displayAnswer}", p.isCorrect(toNumber(p.displayAnswer)))
                assertTrue("$id ${p.prompt} ${p.explanation}", p.isCorrect(boldValue(p.explanation)))
                assertTrue("$id ${p.explanation}", p.explanation.split("<br>").size - 1 <= 2)
            }
        }
    }

    @Test
    fun deterministic() {
        for ((skill, i, level) in levels) {
            val a = List(20) { level.generate(Rng(7)).prompt }
            val b = List(20) { level.generate(Rng(7)).prompt }
            assertEquals("${skill.id}-L${i + 1}", a, b)
        }
    }

    @Test
    fun sciExample() {
        val p = Estimation.sciProblem(listOf(6.0, 7.0), listOf(5, -3), listOf("×", "×"), 0.01, 1)
        assertEquals("(6 × 10<sup>5</sup>) × (7 × 10<sup>−3</sup>)", p.prompt)
        assertTrue(approx(p.answer, 4200.0))
        assertEquals("4.2 × 10<sup>3</sup>", p.displayAnswer)
        assertTrue("6 × 7 = 42" in p.explanation && "5 + (−3) = 2" in p.explanation)
        val q = Estimation.sciProblem(listOf(3.7, 6.2, 4.1), listOf(5, -3, 8), listOf("×", "×", "÷"), 0.05, 2)
        assertTrue(approx(q.answer, 3.7e5 * 6.2e-3 / 4.1e8))
        assertTrue("5 + (−3) − 8 = −6" in q.explanation)
        assertTrue(approx(boldValue(q.explanation), q.answer, rel = 0.005))
    }

    @Test
    fun fermiExample() {
        val p = Estimation.fermiProblem(listOf(347.0, 58.0), listOf("×", "×"), 0.05)
        assertEquals(347.0 * 58, p.answer, 0.0)
        assertTrue("350 × 60" in p.explanation)
        assertTrue(approx(boldValue(p.explanation), 20126.0, rel = 0.01))
        val q = Estimation.fermiProblem(listOf(347.0, 0.0219, 58.0, 7.3), listOf("×", "×", "×", "÷"), 0.10)
        assertTrue(approx(q.answer, 347 * 0.0219 * 58 / 7.3))
        assertEquals("347 × 0.0219 × 58 ÷ 7.3", q.prompt)
        assertTrue(approx(boldValue(q.explanation), q.answer, rel = 0.05))
    }

    @Test
    fun recipExamples() {
        var p = Estimation.recipProblem(37.0, 0.01)
        assertTrue("1/36" in p.explanation && "1/37" in p.explanation)
        assertTrue(approx(boldValue(p.explanation), 1 / 37.0, rel = 0.003))
        p = Estimation.recipProblem(24.0, 0.01)
        assertTrue("1/24 = 1/12 ÷ 2" in p.explanation)
        p = Estimation.recipProblem(0.0042, 0.015)
        assertEquals("1/0.0042", p.prompt)
        assertTrue(approx(boldValue(p.explanation), 238.1, rel = 0.005))
        p = Estimation.recipProblem(3.7e5, 0.015)
        assertEquals("1/(3.7 × 10<sup>5</sup>)", p.prompt)
        assertTrue(approx(boldValue(p.explanation), 1 / 3.7e5, rel = 0.005))
    }

    @Test
    fun niceReciprocalTable() {
        for ((k, text) in Estimation.NICE) {
            assertTrue(text, text.startsWith("1/$k"))
            assertTrue(text, approx(toNumber(text.substringAfterLast(" ")), 1.0 / k, rel = 0.01))
        }
    }

    @Test
    fun hintExamplesParse() {
        for (s in skills) {
            for (lv in s.levels) {
                val hint = lv.generate(Rng(3)).hint
                val example = hint.substringAfterLast(" ")
                assertNotNull(example, Answers.parse(example))
            }
        }
    }
}
