package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.Answers
import dev.russell.mmtrainer.Claims.checkClaims
import dev.russell.mmtrainer.Claims.numbers
import dev.russell.mmtrainer.Claims.plain
import dev.russell.mmtrainer.Claims.readable
import dev.russell.mmtrainer.Golden
import dev.russell.mmtrainer.content.Arithmetic.SKILLS
import dev.russell.mmtrainer.content.Arithmetic.addProblem
import dev.russell.mmtrainer.content.Arithmetic.divApproxProblem
import dev.russell.mmtrainer.content.Arithmetic.divProblem
import dev.russell.mmtrainer.content.Arithmetic.mul1Problem
import dev.russell.mmtrainer.content.Arithmetic.mul2Problem
import dev.russell.mmtrainer.content.Arithmetic.squareProblem
import dev.russell.mmtrainer.content.Arithmetic.subProblem
import dev.russell.mmtrainer.py.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of tests/test_arithmetic.py. */
class ArithmeticTest {
    private val nSamples = 300

    private val recompute: Map<String, (List<Double>) -> Double> = mapOf(
        "s_add" to { n -> n.sum() },
        "s_sub" to { n -> n[0] - n[1] },
        "s_mul1" to { n -> n[0] * n[1] },
        "s_mul2" to { n -> n[0] * n[1] },
        "s_square" to { n -> n[0] * n[0] },
        "s_div" to { n -> n[0] / n[1] },
    )

    private fun approx(got: Double, want: Double, rel: Double = 1e-12) =
        Math.abs(got - want) <= maxOf(rel * Math.abs(want), 1e-12)

    @Test fun matchesPythonGolden() { assertTrue(Golden.checkModule("arithmetic", Arithmetic) > 0) }

    @Test fun skillIdsAndShape() {
        assertEquals(listOf("s_add", "s_sub", "s_mul1", "s_mul2", "s_square", "s_div"), SKILLS.map { it.id })
        assertEquals(listOf(4, 4, 4, 3, 4, 3), SKILLS.map { it.levels.size })
        for (s in SKILLS) {
            assertTrue(s.track == "arithmetic" && s.track in TRACKS)
            val words = plain(s.lesson.body).split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            assertTrue("${s.id}: $words words", words in 101..399)
            for (lv in s.levels) assertTrue(lv.description.isNotEmpty() && lv.targetSeconds > 0)
        }
    }

    @Test fun levelGeneratesValidProblems() {
        for (skill in SKILLS) {
            for (idx in skill.levels.indices) {
                val tag = "${skill.id}-L${idx + 1}"
                val rng = Rng(1234L + idx)
                var claims = 0
                repeat(nSamples) {
                    val p = skill.levels[idx].generate(rng)
                    assertTrue(tag, p.prompt.isNotEmpty())
                    assertTrue(tag, p.explanation.isNotEmpty())
                    assertTrue(tag, p.displayAnswer.isNotEmpty())
                    assertTrue(tag, p.answer.isFinite() && p.answer > 0)
                    assertTrue(tag, p.isCorrect(p.answer))
                    assertTrue(tag, p.tolRel < 0.2 && p.tolAbs >= 0)
                    // independent recomputation from the prompt's numbers
                    val expected = recompute.getValue(skill.id)(numbers(p.prompt))
                    assertTrue("$tag: ${p.prompt}", approx(p.answer, expected))
                    // the shown answer is accepted, and appears at the end of the explanation
                    val shown = Answers.parse(p.displayAnswer.removePrefix("≈ "))
                    assertNotNull(tag, shown)
                    assertTrue(tag, p.isCorrect(shown!!))
                    assertTrue("$tag: ${p.explanation}", p.displayAnswer.removePrefix("≈ ") in plain(p.explanation))
                    if (p.tolRel == 0.0 && p.tolAbs == 0.0) assertEquals(tag, p.answer, shown, 0.0)
                    claims += checkClaims(p.explanation)
                }
                assertTrue("$tag: $claims claims", claims > nSamples / 2.0)
            }
        }
    }

    @Test fun generatorsDeterministic() {
        for (s in SKILLS) {
            for (lv in s.levels) {
                val a = List(5) { lv.generate(Rng(7)).prompt }
                val b = List(5) { lv.generate(Rng(7)).prompt }
                assertEquals(a, b)
            }
        }
    }

    @Test fun levelConstraints() {
        val rng = Rng(99)
        repeat(nSamples) {
            var ab = numbers(SKILLS[1].levels[0].generate(rng).prompt)   // sub L1 borrows
            var a = ab[0]
            var b = ab[1]
            assertTrue(a % 10 < b % 10 && 10 <= b && b < a && a <= 99)
            ab = numbers(SKILLS[1].levels[1].generate(rng).prompt)       // complements
            a = ab[0]; b = ab[1]
            assertTrue((a == 1000.0 || a == 10000.0) && b % 10 != 0.0)
            var n = numbers(SKILLS[4].levels[0].generate(rng).prompt)[0] // squares ending in 5
            assertTrue(n % 10 == 5.0 && n < 100)
            n = numbers(SKILLS[4].levels[1].generate(rng).prompt)[0]
            assertTrue((40 < n && n < 60) || (90 < n && n < 110))
            assertTrue(n != 50.0 && n != 100.0)
            ab = numbers(SKILLS[5].levels[1].generate(rng).prompt)       // exact ÷ 2-digit
            a = ab[0]; b = ab[1]
            assertTrue(10 <= b && b <= 99 && a % b == 0.0 && a >= 100)
            var p = SKILLS[5].levels[2].generate(rng)                    // non-exact division
            ab = numbers(p.prompt)
            a = ab[0]; b = ab[1]
            assertTrue(a % b != 0.0 && p.tolRel == 0.005 && p.hint == "3 significant figures")
            p = SKILLS[2].levels[3].generate(rng)                        // decimal × digit
            val xd = numbers(p.prompt)
            val x = xd[0]
            val d = xd[1]
            assertTrue(x != x.toLong().toDouble() && 3 <= d && d <= 9)
        }
    }

    // ---- specific explanations pick the intended trick and get the numbers right

    @Test fun addExplanations() {
        var e = readable(addProblem(listOf(586, 298)).explanation)
        assertTrue("586 + 300 − 2" in e && "884" in e)
        e = readable(addProblem(listOf(347, 586, 219)).explanation)
        assertTrue("1 000" in e && "130" in e && "22" in e && "1 152" in e)
    }

    @Test fun subExplanations() {
        assertTrue("613" in readable(subProblem(1000, 387).explanation))
        assertTrue("10−7 = 3" in readable(subProblem(1000, 387).explanation))
        var e = readable(subProblem(724, 298).explanation)
        assertTrue("724 − 300 + 2" in e && "426" in e)
        e = readable(subProblem(612, 487).explanation)
        assertTrue("Count up" in e && "+13" in e && "125" in e)
        e = readable(subProblem(7352, 2868).explanation)
        assertTrue("+132" in e && "4 484" in e)
    }

    @Test fun mul1Explanations() {
        var e = readable(mul1Problem(49, 7).explanation)
        assertTrue("350 − 7" in e && "343" in e)
        e = readable(mul1Problem(4736, 7).explanation)
        assertTrue("28 000" in e && "32 900" in e && "252" in e && "33 152" in e)
        val p = mul1Problem(36, 7, decimals = 2)
        assertTrue(p.prompt == "0.36 × 7" && Math.abs(p.answer - 2.52) <= 1e-6 * 2.52)
        assertTrue("252" in p.explanation && "2.52" in p.explanation)
    }

    @Test fun mul2PicksTrick() {
        val cases = listOf(
            Triple(47, 53, "50² − 3²"),        // difference of squares
            Triple(48, 25, "48 ÷ 4 = 12"),     // factoring
            Triple(37, 25, "3 700"),
            Triple(35, 48, "70 × 24"),         // halve & double
            Triple(47, 59, "47 × 60"),         // near-round
            Triple(96, 93, "Near 100"),
            Triple(47, 63, "Split"),
            Triple(47, 11, "4+7 = 11"),
            Triple(13, 17, "13 + 7 = 20"),
            Triple(398, 47, "47 × 400"),
            Triple(103, 97, "100² − 3²"),
        )
        for ((a, b, fragment) in cases) {
            val p = mul2Problem(a, b)
            assertTrue("$a × $b: ${p.explanation}", fragment in readable(p.explanation))
            assertEquals((a * b).toDouble(), p.answer, 0.0)
            checkClaims(p.explanation)
        }
    }

    @Test fun squareExplanations() {
        val cases = listOf(
            65 to listOf("6 × 7 = 42", "4 225"),
            47 to listOf("25 − 3 = 22", "2 209"),
            104 to listOf("108", "10 816"),
            67 to listOf("64 × 70", "4 489"),
            497 to listOf("494 × 500", "247 009"),
            347 to listOf("300 × 394", "118 200", "2 209", "120 409"),
            345 to listOf("34 × 35 = 1 190", "119 025"),
        )
        for ((n, fragments) in cases) {
            val e = readable(squareProblem(n).explanation)
            for (f in fragments) assertTrue("$f / $e", f in e)
        }
    }

    @Test fun divExplanations() {
        var e = readable(divProblem(1035, 23).explanation)
        assertTrue("23 × 40 = 920" in e && "115" in e && "45" in e)
        e = readable(divProblem(1344, 48).explanation)
        assertTrue("336 ÷ 12" in e && "28" in e)
        e = readable(divProblem(435, 5).explanation)
        assertTrue("870" in e)
        var p = divApproxProblem(347, 23)
        assertTrue(p.displayAnswer == "≈ 15.1" && "remainder 2" in p.explanation)
        assertTrue(p.isCorrect(15.1) && !p.isCorrect(15.0))
        p = divApproxProblem(17, 43)
        assertTrue(p.displayAnswer == "≈ 0.395" && "170 ÷ 43" in p.explanation)
    }
}
