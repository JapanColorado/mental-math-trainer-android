package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.Golden
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.isInteger
import dev.russell.mmtrainer.py.pyRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of tests/test_logs.py. */
class LogsTest {
    private val n = 300
    private val levels: List<Triple<Skill, Int, Level>> =
        Logs.SKILLS.flatMap { s -> s.levels.mapIndexed { i, lv -> Triple(s, i, lv) } }

    /** Rich text -> parseable plain text: '10<sup>−3</sup>' -> '10^-3', thin spaces removed. */
    private fun plain(s0: String): String {
        var s = Regex("<sup>(.*?)</sup>").replace(s0, "^$1")
        s = Regex("<sub>(.*?)</sub>").replace(s, "_$1 ")
        s = Regex("<[^>]+>").replace(s, "")
        return s.replace("−", "-").replace(" ", "").replace(" ", "").replace(" ", "")
    }

    /** Parse a prompt number: '52 000', '0.003', '(4.9 × 10^7)', '10^6', '(1/8)', '√32'. */
    private fun num(s0: String): Double {
        val s = s0.trim().trim('(', ')').replace(" ", "")
        if (s.startsWith("√")) return Math.sqrt(num(s.substring(1)))
        if ("/" in s) {
            val (a, b) = s.split("/")
            return num(a) / num(b)
        }
        if ("×10^" in s) {
            val (a, k) = s.split("×10^")
            return a.toDouble() * Math.pow(10.0, k.toDouble())
        }
        if (s.startsWith("10^")) return Math.pow(10.0, s.substring(3).toDouble())
        return s.toDouble()
    }

    /** pytest.approx: abs only -> abs; rel only -> max(rel·|e|, 1e-12); neither -> rel 1e-6. */
    private fun approx(actual: Double, expected: Double, rel: Double? = null, abs: Double? = null): Boolean {
        val tol = when {
            abs != null && rel != null -> maxOf(abs, rel * Math.abs(expected))
            abs != null -> abs
            else -> maxOf((rel ?: 1e-6) * Math.abs(expected), 1e-12)
        }
        return actual == expected || Math.abs(actual - expected) <= tol
    }

    private fun assertApprox(msg: String, actual: Double, expected: Double, rel: Double? = null, abs: Double? = null) =
        assertTrue("$msg: $actual !≈ $expected", approx(actual, expected, rel, abs))

    @Test fun idsAndTrack() {
        assertEquals(listOf("s_logint", "s_log10", "s_antilog", "s_ln", "s_logb"), Logs.SKILLS.map { it.id })
        for (s in Logs.SKILLS) {
            assertEquals("logs", s.track)
            assertTrue(s.lesson.title.isNotEmpty() && s.lesson.body.trim().split(Regex("\\s+")).size > 80)
            assertTrue(s.levels.all { it.targetSeconds > 0 && it.description.isNotEmpty() })
        }
    }

    @Test fun levelGeneratesValidProblems() {
        for ((skill, idx, level) in levels) {
            val rng = Rng(1234L + idx)
            repeat(n) {
                val p = level.generate(rng)
                val where = "${skill.id}-L${idx + 1}: ${p.prompt}"
                assertTrue(where, p.answer.isFinite())
                assertTrue(where, p.isCorrect(p.answer))
                for (text in listOf(p.prompt, p.displayAnswer, p.explanation)) assertTrue(where, text.isNotBlank())
                assertTrue(where, p.tolRel >= 0 && p.tolRel < 0.2 && p.tolAbs >= 0)
                // the worked explanation's method must itself land within tolerance
                val est = Logs.mentalEstimate(p)
                if (est != null) assertTrue("$where ${p.answer} $est ${p.explanation}", p.isCorrect(est))
            }
        }
    }

    @Test fun deterministic() {
        for ((_, _, level) in levels) {
            val a = List(3) { level.generate(Rng(5)).prompt }
            val b = List(3) { level.generate(Rng(5)).prompt }
            assertEquals(a, b)
        }
    }

    private fun problems(skillId: String, levelIdx: Int, count: Int = n, seed: Long = 99): List<Problem> {
        val skill = Logs.SKILLS.first { it.id == skillId }
        val rng = Rng(seed)
        return List(count) { skill.levels[levelIdx].generate(rng) }
    }

    @Test fun logintRecompute() {
        for (lvl in listOf(0, 1)) {
            for (p in problems("s_logint", lvl)) {
                val m = Regex("log_(\\d+) (.+)").matchEntire(plain(p.prompt)) ?: error("no match: ${p.prompt}")
                val b = m.groupValues[1].toInt()
                val x = num(m.groupValues[2])
                assertApprox(p.prompt, Math.log(x) / Math.log(b.toDouble()), p.answer, abs = 1e-9)
                if (lvl == 0) {
                    assertTrue(p.prompt, p.answer.isInteger() && p.answer != 0.0 && p.answer != 1.0)
                } else {
                    assertFalse(p.prompt, p.answer.isInteger())
                }
            }
        }
    }

    @Test fun log10Recompute() {
        for (lvl in listOf(0, 1, 2)) {
            for (p in problems("s_log10", lvl)) {
                val m = Regex("log_10 \\((.+)\\)").matchEntire(plain(p.prompt)) ?: error("no match: ${p.prompt}")
                assertApprox(p.prompt, Math.log10(num(m.groupValues[1])), p.answer, abs = 1e-6)
            }
        }
    }

    @Test fun antilogRecompute() {
        for (lvl in listOf(0, 1)) {
            for (p in problems("s_antilog", lvl)) {
                val m = Regex("10\\^(.+)").matchEntire(plain(p.prompt)) ?: error("no match: ${p.prompt}")
                assertApprox(p.prompt, Math.pow(10.0, m.groupValues[1].toDouble()), p.answer, rel = 1e-9)
            }
        }
    }

    @Test fun lnL1Recompute() {
        for (p in problems("s_ln", 0)) {
            val arg = plain(p.prompt).substring(3)
            val m = Regex("\\((\\d+)(/?)e(?:\\^(-?\\d+))?\\)").matchEntire(arg)
            val expect = if (m != null) {
                val k = (m.groupValues[3].ifEmpty { "1" }).toInt() * (if (m.groupValues[2].isNotEmpty()) -1 else 1)
                Math.log(m.groupValues[1].toInt().toDouble()) + k
            } else {
                Math.log(num(arg))
            }
            assertApprox(p.prompt, expect, p.answer, abs = 1e-9)
        }
    }

    @Test fun lnL2Recompute() {
        for (p in problems("s_ln", 1)) {
            assertApprox(p.prompt, Math.log(num(plain(p.prompt).substring(3))), p.answer, abs = 1e-6)
        }
    }

    @Test fun logbRecompute() {
        for (lvl in listOf(0, 1)) {
            for (p in problems("s_logb", lvl)) {
                val m = Regex("log_(\\d+) (.+)").matchEntire(plain(p.prompt)) ?: error("no match: ${p.prompt}")
                val b = m.groupValues[1].toInt()
                val x = num(m.groupValues[2])
                assertApprox(p.prompt, Math.log(x) / Math.log(b.toDouble()), p.answer, abs = 1e-6)
            }
        }
    }

    @Test fun anchorTableAccurate() {
        for (a in Logs.ANCHORS) {
            assertTrue(1 <= a.value && a.value <= 10)
            assertApprox("log ${a.value}", a.log(), Math.log10(a.value), abs = 0.0016)
            assertApprox("ln ${a.value}", a.ln(), Math.log(a.value), abs = 0.004)
        }
        // every factored value is a genuine product/quotient of its digits
        for (a in Logs.ANCHORS) {
            val v = a.num.fold(1L) { x, y -> x * y }.toDouble() / a.den.fold(1L) { x, y -> x * y }.toDouble() *
                Math.pow(10.0, a.shift.toDouble())
            assertApprox("anchor ${a.value}", v, a.value)
        }
    }

    @Test fun mentalLogExamples() {
        // log 6.13 ≈ log 6 + 0.434·0.13/6 = 0.778 + 0.009 = 0.787
        var e = Logs.logEst(6.13)
        assertTrue(e.anchor.value == 6.0 && approx(e.corr, 0.009) && approx(e.value, 0.787))
        assertTrue("0.787" in e.lines().last())
        // log 4.7 ≈ log 4.8 − 0.434·0.1/4.8 = 0.681 − 0.009 = 0.672 (4.8 = 6 × 8 / 10 is nearer than 5)
        e = Logs.logEst(4.7)
        assertTrue(e.anchor.value == 4.8 && approx(e.value, 0.672))
        assertEquals("log 4.8 = log(6 × 8 / 10) = 0.778 + 0.903 − 1 = 0.681", e.lines()[0])
        // log 1.3 via 1.25 = 5/4: 0.097 + 0.017 = 0.114
        e = Logs.logEst(1.3)
        assertTrue(e.anchor.value == 1.25 && approx(e.value, 0.114))
        // ln 3.7 via a nearby factorable anchor (3.75 = 3 × 10 / 8)
        e = Logs.logEst(3.7, "ln")
        assertTrue(Math.abs(e.anchor.value - 3.7) <= 0.2 && approx(e.value, Math.log(3.7), abs = 0.005))
    }

    @Test fun mentalPow10Examples() {
        // 10^0.84: 0.005 below log 7 → 7 − 7 × 0.005/0.434 ≈ 6.92
        var e = Logs.pow10Est(3.84)
        assertTrue(e.k == 3 && e.anchor.value == 7.0 && approx(e.d, -0.005))
        assertTrue(approx(e.value, 6919.4, rel = 1e-3))
        assertTrue(plain(e.lines().last()).endsWith("6920"))
        e = Logs.pow10Est(-2.523)
        assertTrue(e.k == -3 && e.anchor.value == 3.0 && approx(e.value, 0.003))
    }

    @Test fun explanationsEndWithEstimate() {
        for (lvl in 0 until 3) {
            for (p in problems("s_log10", lvl, 50)) {
                val est = Logs.mentalEstimate(p)
                assertNotNull(est)
                assertTrue(p.explanation, Logs.f3(est!!) in p.explanation.split("<br>").last())
            }
        }
        for (p in problems("s_ln", 1, 50)) {
            val est = Logs.mentalEstimate(p)
            assertNotNull(est)
            assertTrue(p.explanation, Logs.f3(est!!) in p.explanation.split("<br>").last())
        }
    }

    @Test fun logintFractionHints() {
        for (p in problems("s_logint", 1)) {
            if (p.tolAbs != 0.0) {
                assertTrue(p.tolAbs == 0.01 && "0.667" in p.hint)
                assertTrue(p.isCorrect(pyRound(p.answer, 2)))
            } else {
                assertEquals("fraction or decimal", p.hint)
            }
        }
    }

    @Test fun matchesPythonGolden() {
        assertTrue(Golden.checkModule("logs", Logs) > 0)
    }
}
