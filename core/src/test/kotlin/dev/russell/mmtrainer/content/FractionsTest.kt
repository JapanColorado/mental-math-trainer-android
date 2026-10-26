package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.Answers
import dev.russell.mmtrainer.Claims.checkClaims
import dev.russell.mmtrainer.Claims.numbers
import dev.russell.mmtrainer.Claims.plain
import dev.russell.mmtrainer.Claims.readable
import dev.russell.mmtrainer.content.Fractions.SKILLS
import dev.russell.mmtrainer.content.Fractions.fracProblem
import dev.russell.mmtrainer.content.Fractions.pctChangeProblem
import dev.russell.mmtrainer.content.Fractions.pctOfProblem
import dev.russell.mmtrainer.content.Fractions.reverseProblem
import dev.russell.mmtrainer.content.Fractions.successiveProblem
import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.gcd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionsTest {
    private val nSamples = 300

    /** pytest.approx(expected, rel, abs) */
    private fun approx(actual: Double, expected: Double, rel: Double = 1e-12, abs: Double = 1e-12) =
        Math.abs(actual - expected) <= maxOf(rel * Math.abs(expected), abs)

    /** '+20% then −15%' -> [20.0, -15.0] */
    private fun signedPcts(prompt: String): List<Double> =
        Regex("""([+−])(\d+(?:\.\d+)?)%""").findAll(plain(prompt))
            .map { it.groupValues[2].toDouble() * (if (it.groupValues[1] == "−") -1 else 1) }.toList()

    private fun recompute(skillId: String, idx: Int, prompt: String): Double {
        val n = numbers(prompt)
        if (skillId == "s_frac") return n[0] / n[1]
        if (idx <= 1) return n[0] * n[1] / 100                // p% of y
        if (idx == 2) return (n[1] - n[0]) / n[0] * 100       // percent change a → b
        if ("original" in prompt) {                           // reverse percent
            val ps = signedPcts(prompt)
            assertEquals(1, ps.size)
            return n.last() / (1 + ps[0] / 100)
        }
        val ps = signedPcts(prompt)                           // successive changes
        assertEquals(2, ps.size)
        val (p1, p2) = ps
        return ((1 + p1 / 100) * (1 + p2 / 100) - 1) * 100
    }

    @Test
    fun skillIdsAndShape() {
        assertEquals(listOf("s_frac", "s_pct"), SKILLS.map { it.id })
        assertEquals(listOf(4, 4), SKILLS.map { it.levels.size })
        for (s in SKILLS) {
            assertTrue(s.track == "fractions" && s.track in TRACKS)
            val words = plain(s.lesson.body).split(Regex("(?U)\\s+")).count { it.isNotEmpty() }  // str.split()
            assertTrue("${s.id}: $words words", words in 101..399)
            for (lv in s.levels) {
                assertTrue(lv.description.isNotEmpty() && lv.targetSeconds > 0)
            }
        }
    }

    private fun levelGeneratesValidProblems(skillIdx: Int, idx: Int) {
        val skill = SKILLS[skillIdx]
        val rng = Rng(4321L + idx)
        var claims = 0
        repeat(nSamples) {
            val p = skill.levels[idx].generate(rng)
            assertTrue(p.prompt.isNotEmpty())
            assertTrue(p.explanation.isNotEmpty())
            assertTrue(p.displayAnswer.isNotEmpty())
            assertTrue(p.answer.isFinite())
            assertTrue(p.isCorrect(p.answer))
            assertTrue(p.tolRel < 0.2 && 0 <= p.tolAbs && p.tolAbs <= 0.5)
            assertTrue(p.prompt, approx(p.answer, recompute(skill.id, idx, p.prompt)))
            val shown = Answers.parse(p.displayAnswer.removePrefix("≈ "))
            assertNotNull(p.displayAnswer, shown)
            assertTrue(p.displayAnswer, p.isCorrect(shown!!))
            if (p.tolAbs == 0.0 && p.tolRel == 0.0) {
                assertTrue(p.displayAnswer, approx(shown, p.answer))
            }
            assertTrue(p.explanation, p.displayAnswer.removePrefix("≈ ").trimEnd('%') in plain(p.explanation))
            claims += checkClaims(p.explanation)
        }
        if (!(skill.id == "s_frac" && idx in listOf(1, 2))) {  // repeating-decimal levels use ≈ only
            assertTrue("${skill.id} L${idx + 1}: $claims claims", claims >= nSamples / 4.0)
        }
    }

    @Test fun levelGeneratesValidProblemsSFracL1() = levelGeneratesValidProblems(0, 0)
    @Test fun levelGeneratesValidProblemsSFracL2() = levelGeneratesValidProblems(0, 1)
    @Test fun levelGeneratesValidProblemsSFracL3() = levelGeneratesValidProblems(0, 2)
    @Test fun levelGeneratesValidProblemsSFracL4() = levelGeneratesValidProblems(0, 3)
    @Test fun levelGeneratesValidProblemsSPctL1() = levelGeneratesValidProblems(1, 0)
    @Test fun levelGeneratesValidProblemsSPctL2() = levelGeneratesValidProblems(1, 1)
    @Test fun levelGeneratesValidProblemsSPctL3() = levelGeneratesValidProblems(1, 2)
    @Test fun levelGeneratesValidProblemsSPctL4() = levelGeneratesValidProblems(1, 3)

    @Test
    fun generatorsDeterministic() {
        for (s in SKILLS) {
            for (lv in s.levels) {
                val a = (0 until 5).map { lv.generate(Rng(3)).prompt }
                val b = (0 until 5).map { lv.generate(Rng(3)).prompt }
                assertEquals(a, b)
            }
        }
    }

    @Test
    fun fractionLevels() {
        val rng = Rng(8)
        val allowed = listOf(setOf(2, 4, 5, 8, 16, 20, 25, 40), setOf(3, 6, 9, 11, 12),
            setOf(7, 13, 14, 15, 16, 17, 18, 19))
        repeat(nSamples) {
            for ((idx, dens) in allowed.withIndex()) {
                val p = SKILLS[0].levels[idx].generate(rng)
                val nums = numbers(p.prompt)
                assertEquals(2, nums.size)
                val (num, den) = nums
                assertTrue(p.prompt, den.toInt() in dens && 0 < num && num < den &&
                    gcd(num.toInt(), den.toInt()) == 1)
                assertEquals(idx == 0, p.tolAbs == 0.0)
            }
            val p = SKILLS[0].levels[3].generate(rng)
            val nums = numbers(p.prompt)
            assertEquals(2, nums.size)
            val (num, den) = nums
            assertTrue(p.prompt, num > den && num % den != 0.0 && p.tolAbs == 0.0006)
        }
    }

    @Test
    fun roundingTolerance() {
        val p = fracProblem(2, 3)
        assertEquals("≈ 0.667", p.displayAnswer)
        assertTrue(p.isCorrect(0.667) && !p.isCorrect(0.666))   // round, don't truncate
        assertEquals("0.4375", fracProblem(7, 16, exact = true).displayAnswer)
    }

    @Test
    fun fractionExplanations() {
        val cases = listOf(
            Triple(13, 40, listOf("325/1 000", "0.325")),
            Triple(5, 8, listOf("0.625", "0.125")),
            Triple(7, 11, listOf("63", "0.6363")),
            Triple(7, 9, listOf("0.7777", "0.778")),
            Triple(3, 7, listOf("142857", "0.428571", "0.429")),
            Triple(5, 17, listOf("1/17", "0.2941", "0.294")),
            Triple(15, 17, listOf("1 − 2/17", "0.8824", "0.882")),
            Triple(23, 7, listOf("3 + 2/7", "3.286")),
            Triple(47, 12, listOf("3 + 11/12", "3.917")),
        )
        for ((num, den, fragments) in cases) {
            val e = readable(fracProblem(num, den, exact = den in listOf(8, 40)).explanation)
            for (f in fragments) {
                assertTrue("$f in $e", f in e)
            }
        }
    }

    @Test
    fun pctExplanations() {
        var e = readable(pctOfProblem(Fraction.of(17), 340).explanation)
        assertTrue(e, "10% = 34" in e && "7% = 7 × 3.4 = 23.8" in e && "57.8" in e)
        e = readable(pctOfProblem(Fraction.of(7, 2), 860).explanation)
        assertTrue(e, "0.5% = 4.3" in e && "30.1" in e)
        e = readable(pctOfProblem(Fraction.of(18), 50).explanation)
        assertTrue(e, "50% of 18" in e && "= 9" in e)
        e = readable(pctOfProblem(Fraction.of(92), 340).explanation)
        assertTrue(e, "100% − 8%" in e && "312.8" in e)
        e = readable(pctOfProblem(Fraction.of(25, 2), 624).explanation)
        assertTrue(e, "⅛" in e && "78" in e)
    }

    @Test
    fun pctChangeAndSuccessive() {
        var p = pctChangeProblem(80, 92)
        assertTrue(p.answer == 15.0 && p.tolAbs == 0.0 && p.displayAnswer == "+15%")
        assertTrue("12/80 = 3/20 = 0.15" in readable(p.explanation))
        p = pctChangeProblem(87, 74)
        assertTrue(p.tolAbs == 0.5 && p.displayAnswer == "≈ −14.9%")
        assertTrue(p.isCorrect(-15.0) && !p.isCorrect(-14.0))
        assertEquals("percent, e.g. 15 or −7.5", p.hint)
        p = successiveProblem(20, -15)
        assertTrue(approx(p.answer, 2.0, rel = 1e-6, abs = 1e-12) && "1.02" in p.explanation)
        assertTrue("20 − 15 − 3 = +2" in readable(p.explanation))
        p = successiveProblem(25, -20)
        assertTrue(approx(p.answer, 0.0, rel = 1e-6, abs = 1e-12) && p.isCorrect(0.0))
    }

    @Test
    fun reversePercent() {
        var p = reverseProblem(Fraction.of(25), true, 150)
        assertTrue(p.answer == 120.0 && "4/5" in p.explanation && "112.5" in p.explanation)
        p = reverseProblem(Fraction.of(20), false, 96)
        assertTrue(p.answer == 120.0 && "5/4" in p.explanation)
    }
}
