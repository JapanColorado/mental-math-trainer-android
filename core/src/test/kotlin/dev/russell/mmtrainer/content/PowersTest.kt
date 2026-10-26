package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.isInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PowersTest {
    private val n = 300
    private val levels: List<Triple<Skill, Int, Level>> =
        Powers.SKILLS.flatMap { s -> s.levels.mapIndexed { i, lv -> Triple(s, i, lv) } }

    private fun plain(s0: String): String {
        var s = Regex("<sup>(.*?)</sup>").replace(s0, "^$1")
        s = Regex("<[^>]+>").replace(s, "")
        return s.replace("−", "-").replace(" ", "").replace(" ", "").replace(" ", "")
            .replace(" ", "")
    }

    private fun num(s0: String): Double {
        val s = s0.trim('(', ')')
        if ("×10^" in s) {
            val (a, k) = s.split("×10^")
            return a.toDouble() * Math.pow(10.0, k.toDouble())
        }
        return s.toDouble()
    }

    /** float(Fraction(s)) for '2/3', '-2/3', '0.75', '6.5', '3'. */
    private fun frac(s: String): Double {
        if ("/" in s) {
            val (a, b) = s.split("/")
            return Fraction.of(a.toLong(), b.toLong()).toDouble()
        }
        val neg = s.startsWith("-")
        val body = s.removePrefix("-")
        val dot = body.indexOf('.')
        val decimals = if (dot >= 0) body.length - dot - 1 else 0
        val digits = body.replace(".", "").toLong()
        var den = 1L
        repeat(decimals) { den *= 10 }
        return Fraction.of(if (neg) -digits else digits, den).toDouble()
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
        assertEquals(listOf("s_exp", "s_pow", "s_root", "s_growth"), Powers.SKILLS.map { it.id })
        for (s in Powers.SKILLS) {
            assertEquals("logs", s.track)
            assertTrue(s.lesson.title.isNotEmpty() && s.lesson.body.trim().split(Regex("\\s+")).size > 80)
            assertTrue(s.levels.all { it.targetSeconds > 0 && it.description.isNotEmpty() })
        }
    }

    @Test fun levelGeneratesValidProblems() {
        for ((skill, idx, level) in levels) {
            val rng = Rng(4321L + idx)
            repeat(n) {
                val p = level.generate(rng)
                val where = "${skill.id}-L${idx + 1}: ${p.prompt}"
                assertTrue(where, p.answer.isFinite())
                assertTrue(where, p.isCorrect(p.answer))
                for (text in listOf(p.prompt, p.displayAnswer, p.explanation)) assertTrue(where, text.isNotBlank())
                assertTrue(where, p.tolRel >= 0 && p.tolRel < 0.2 && p.tolAbs >= 0)
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

    private fun problems(skillId: String, levelIdx: Int, count: Int = n, seed: Long = 77): List<Problem> {
        val skill = Powers.SKILLS.first { it.id == skillId }
        val rng = Rng(seed)
        return List(count) { skill.levels[levelIdx].generate(rng) }
    }

    @Test fun expRecompute() {
        for (lvl in listOf(0, 1, 2)) {
            for (p in problems("s_exp", lvl)) {
                val m = Regex("e\\^(.+)").matchEntire(plain(p.prompt)) ?: error("no match: ${p.prompt}")
                val x = m.groupValues[1].toDouble()
                assertTrue(p.prompt, x != 0.0)
                assertApprox(p.prompt, Math.exp(x), p.answer, rel = 1e-12)
                if (lvl == 1) assertTrue(p.prompt, Math.abs(x) <= 0.3)
            }
        }
    }

    @Test fun powRecompute() {
        for (lvl in listOf(0, 1, 2)) {
            for (p in problems("s_pow", lvl)) {
                val m = Regex("([\\d.]+)\\^(.+)").matchEntire(plain(p.prompt)) ?: error("no match: ${p.prompt}")
                val a = m.groupValues[1].toDouble()
                val b = frac(m.groupValues[2])
                assertApprox(p.prompt, Math.pow(a, b), p.answer, rel = 1e-9)
                if (lvl == 0) {   // exact: the displayed answer is the exact value
                    assertTrue(p.prompt, b.isInteger() && p.tolRel == 0.0 && p.tolAbs == 0.0)
                    assertApprox(p.prompt, plain(p.displayAnswer).toDouble(), p.answer, rel = 1e-12)
                } else {
                    assertFalse(p.prompt, b.isInteger())
                }
            }
        }
    }

    @Test fun rootRecompute() {
        for (lvl in listOf(0, 1, 2, 3)) {
            for (p in problems("s_root", lvl)) {
                val s = plain(p.prompt)
                val nn: Int
                val x: Double
                if (s.startsWith("√")) {
                    nn = 2; x = num(s.substring(1))
                } else if (s.startsWith("∛")) {
                    nn = 3; x = num(s.substring(1))
                } else {
                    val m = Regex("\\^(\\d+)√(.+)").matchEntire(s) ?: error("no match: ${p.prompt}")
                    nn = m.groupValues[1].toInt(); x = num(m.groupValues[2])
                }
                assertTrue(p.prompt, nn == listOf(2, 2, 3, null)[lvl] || lvl == 3)
                assertApprox(p.prompt, Math.pow(x, 1.0 / nn), p.answer, rel = 1e-9)
            }
        }
    }

    @Test fun growthRecompute() {
        for (lvl in 0 until 3) {
            for (p in problems("s_growth", lvl)) {
                val s = p.prompt
                var m: MatchResult?
                val expect: Double
                if (Regex("Doubling time at ([\\d.]+)% per period").matchEntire(s).also { m = it } != null) {
                    expect = Math.log(2.0) / Math.log1p(m!!.groupValues[1].toDouble() / 100)
                } else if (Regex("Doubling time at (\\d+)% continuous growth").matchEntire(s).also { m = it } != null) {
                    expect = Math.log(2.0) / (m!!.groupValues[1].toInt() / 100.0)
                } else if (Regex("([\\d.]+)\\^(\\d+)").matchEntire(plain(s)).also { m = it } != null) {
                    expect = Math.pow(m!!.groupValues[1].toDouble(), m!!.groupValues[2].toInt().toDouble())
                } else if (Regex("\\$([\\d ]+) at ([\\d.]+)% for (\\d+) years")
                        .matchEntire(s.replace(" ", " ")).also { m = it } != null) {
                    val g = m!!.groupValues
                    expect = g[1].replace(" ", "").toDouble() * Math.pow(1 + g[2].toDouble() / 100, g[3].toInt().toDouble())
                } else if (Regex("Half-life if (\\d+)% decays per period").matchEntire(s).also { m = it } != null) {
                    expect = Math.log(2.0) / -Math.log1p(-m!!.groupValues[1].toInt() / 100.0)
                } else if (Regex("Annual rate to (\\w+)(?: (\\d+)×)? in (\\d+) years\\?").matchEntire(s).also { m = it } != null) {
                    val g = m!!.groupValues
                    val f = mapOf("double" to 2, "triple" to 3, "quadruple" to 4)[g[1]] ?: g[2].toInt()
                    expect = 100 * (Math.pow(f.toDouble(), 1.0 / g[3].toInt()) - 1)
                } else if (Regex("Growth factor: (\\d+)% continuous for (\\d+) years").matchEntire(s).also { m = it } != null) {
                    val g = m!!.groupValues
                    expect = Math.exp((g[1].toInt() * g[2].toInt()) / 100.0)
                } else if (Regex("Years to grow (\\d+)× at (\\d+)%/yr\\?").matchEntire(s).also { m = it } != null) {
                    val g = m!!.groupValues
                    expect = Math.log(g[1].toInt().toDouble()) / Math.log1p(g[2].toInt() / 100.0)
                } else {
                    fail("unrecognised prompt '$s'")
                    return
                }
                assertApprox(s, expect, p.answer, rel = 1e-9)
            }
        }
    }

    @Test fun ruleOf72Explanations() {
        var p = problems("s_growth", 0, 200).first { it.prompt == "Doubling time at 8% per period" }
        assertTrue(p.explanation, "72 / 8 = 9.00" in p.explanation)
        p = problems("s_growth", 0, 200).first { it.prompt == "Doubling time at 1% per period" }
        val est = Logs.mentalEstimate(p)
        assertNotNull(est)
        assertTrue("69.3/r + 0.35" in p.explanation && p.isCorrect(est!!))
    }

    @Test fun expEstExamples() {
        var (v, text) = Powers.expEst(0.698)        // ≈ ln 2 + 0.005 → 2.01
        assertTrue(approx(v, 2.01) && "ln 2 = 0.693" in text)
        v = Powers.expEst(2.0).first
        assertTrue(approx(v, 7.389))
        for (y in listOf(0.1, 0.37, 1.5, 2.9, 3.4, 4.6, 6.2)) {
            assertApprox("exp_est($y)", Powers.expEst(y).first, Math.exp(y), rel = 0.005)
        }
    }

    @Test fun explanationNumbers() {
        // √52 ≈ 7 + 3/14
        var (est, text) = Powers.sqrtParts(52.0)
        assertTrue(approx(est, 7 + 3 / 14.0) && "7 + 3/14" in text)
        // ∛70 ≈ 4 + 6/48 = 4.125
        val c = Powers.cbrtParts(70.0)
        est = c.first; text = c.second
        assertTrue(approx(est, 4.125) && "6/48" in text)
        // 47² = 50 × 44 + 9
        assertTrue("50 × 44 + 9" in Powers.squareExpl(47))
        // 13³ = 169 × 13 = 1 690 + 507
        assertTrue("169 × 13 = 1 690 + 507" in Powers.splitExpl(13, 3).replace(" ", " "))
        assertTrue(approx(Powers.eHalf(3.5).first, 20.09 * 1.649))
    }
}
