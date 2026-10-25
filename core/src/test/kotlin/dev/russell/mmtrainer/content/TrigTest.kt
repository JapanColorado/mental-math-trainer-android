package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.Golden
import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of tests/test_trig.py. */
class TrigTest {
    private val n = 300
    private val levels: List<Triple<Skill, Int, Level>> =
        Trig.SKILLS.flatMap { s -> s.levels.mapIndexed { i, lv -> Triple(s, i, lv) } }

    private val funcs: Map<String, (Double) -> Double> = mapOf(
        "sin" to Math::sin, "cos" to Math::cos, "tan" to Math::tan,
        "arcsin" to Math::asin, "arccos" to Math::acos, "arctan" to Math::atan,
    )
    private val symbolic = mapOf(
        "1/2" to 0.5, "√2/2" to Math.sqrt(2.0) / 2, "√3/2" to Math.sqrt(3.0) / 2, "1" to 1.0,
        "√3" to Math.sqrt(3.0), "1/√3" to 1 / Math.sqrt(3.0), "2 − √3" to 2 - Math.sqrt(3.0),
        "2 + √3" to 2 + Math.sqrt(3.0),
    )
    private val units = mapOf("cm" to 0.01, "m" to 1.0, "km" to 1000.0)

    private fun clean(s: String): String = s.replace(" ", "")

    private fun toNumber(html: String): Double {
        var s = clean(html).replace("−", "-")
        s = s.replace(Regex("<[^>]+>"), "")
        val m = Regex("""^\s*≈?\s*(-?\d+(?:\.\d+)?(?:e-?\d+)?)""").find(s)
        assertNotNull(html, m)
        return m!!.groupValues[1].toDouble()
    }

    private fun boldValue(expl: String): Double =
        toNumber(Regex("<b>(.*?)</b>").findAll(expl).toList().last().groupValues[1])

    private fun num(s: String): Double = s.replace("−", "-").toDouble()

    /** pytest.approx semantics. */
    private fun approx(actual: Double, expected: Double, rel: Double? = null, abs: Double? = null): Boolean {
        val absTol = abs ?: 1e-12
        val tol = if (rel == null && abs != null) absTol else maxOf((rel ?: 1e-6) * Math.abs(expected), absTol)
        return actual == expected || Math.abs(actual - expected) <= tol
    }

    private fun fm(pattern: String, s: String): MatchResult? = Regex(pattern).matchEntire(s)

    /** Independently evaluate any trig-track prompt. */
    private fun recompute(prompt: String): Double {
        val p = clean(prompt)
        fm("""(\d+)° → rad""", p)?.let { return Math.toRadians(it.groupValues[1].toInt().toDouble()) }
        fm("""(\d*)π(?:/(\d+))? → °""", p)?.let { m ->
            val a = m.groupValues[1].ifEmpty { "1" }.toInt()
            val b = m.groupValues[2].ifEmpty { "1" }.toInt()
            return 180.0 * a / b
        }
        fm("""([\d.]+) rad → °""", p)?.let { return Math.toDegrees(it.groupValues[1].toDouble()) }
        fm("""(sin|cos|tan)[ (](−?[\d.]+)(°| rad)\)?""", p)?.let { m ->
            val x = num(m.groupValues[2])
            return funcs.getValue(m.groupValues[1])(if (m.groupValues[3] == "°") Math.toRadians(x) else x)
        }
        fm("""1 − cos ([\d.]+)(°| rad)""", p)?.let { m ->
            val x = m.groupValues[1].toDouble()
            return 1 - Math.cos(if (m.groupValues[2] == "°") Math.toRadians(x) else x)
        }
        fm("""([\d.]+) (cm|m|km) seen at ([\d.]+) (cm|m|km)""", p)?.let { m ->
            val s = m.groupValues[1].toDouble() * units.getValue(m.groupValues[2])
            val d = m.groupValues[3].toDouble() * units.getValue(m.groupValues[4])
            return Math.toDegrees(2 * Math.atan(s / (2 * d)))
        }
        fm("""([\d.]+)° at ([\d.]+) (m|km): size\?""", p)?.let { m ->
            return 2 * m.groupValues[2].toDouble() * Math.tan(Math.toRadians(m.groupValues[1].toDouble()) / 2)
        }
        fm("""([\d.]+) (m) spans ([\d.]+)°: distance\?""", p)?.let { m ->
            return m.groupValues[1].toDouble() / (2 * Math.tan(Math.toRadians(m.groupValues[3].toDouble()) / 2))
        }
        fm("""(arcsin|arccos|arctan)(?: (.+)|\((.+)\))""", p)?.let { m ->
            var arg = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val sign = if (arg.startsWith("−")) -1 else 1
            arg = arg.trimStart('−').trim('(', ')')
            val x = if (arg in symbolic && !Regex("""[\d.]+""").matches(arg)) symbolic.getValue(arg) else arg.toDouble()
            return Math.toDegrees(funcs.getValue(m.groupValues[1])(sign * x))
        }
        throw AssertionError("unparsed prompt '$prompt'")
    }

    @Test fun skillIdsAndTrack() {
        assertEquals(listOf("s_degrad", "s_trig", "s_small", "s_invtrig"), Trig.SKILLS.map { it.id })
        assertEquals(listOf(2, 3, 2, 2), Trig.SKILLS.map { it.levels.size })
        for (s in Trig.SKILLS) {
            assertEquals("trig", s.track)
            assertTrue(s.lesson.body.length in 401..2999)
        }
    }

    @Test fun generatedProblems() {
        for ((skill, i, level) in levels) {
            val id = "${skill.id}-L${i + 1}"
            val rng = Rng(2000L + i)
            repeat(n) {
                val p = level.generate(rng)
                assertTrue(id, p.answer.isFinite())
                assertTrue(id, p.isCorrect(p.answer))
                for (text in listOf(p.prompt, p.explanation, p.displayAnswer, p.formatHint())) {
                    assertTrue("$id: blank text in $p", text.isNotBlank())
                }
                assertTrue(id, p.tolRel < 0.2 && p.tolAbs <= 2 && (p.tolRel > 0 || p.tolAbs > 0))
                val r = recompute(p.prompt)
                assertTrue("$id: ${p.prompt} recomputed $r vs ${p.answer}", approx(r, p.answer, rel = 1e-9, abs = 1e-12))
                // displayed answer and the explanation's final mental estimate both pass
                val disp = p.displayAnswer.split("≈").last()
                assertTrue("$id: ${p.prompt} / ${p.displayAnswer}", p.isCorrect(toNumber(disp)))
                assertTrue("$id: ${p.prompt} / ${p.explanation}", p.isCorrect(boldValue(p.explanation)))
                assertTrue(id, p.explanation.split("<br>").size - 1 <= 2)
            }
        }
    }

    @Test fun deterministicAndRngOnly() {
        for ((_, _, level) in levels) {
            val a = (0 until 20).map { level.generate(Rng(5)).prompt }
            val b = (0 until 20).map { level.generate(Rng(5)).prompt }
            assertEquals(a, b)
        }
    }

    @Test fun specialAnglesCoverQuadrants() {
        var p = Trig.specialTrig("sin", 225)
        assertTrue(approx(p.answer, -Math.sqrt(2.0) / 2))
        assertTrue("180° + 45°" in p.explanation && "−√2/2" in p.displayAnswer)
        p = Trig.specialTrig("cos", -30)
        assertTrue(p.prompt == "cos(−30°)" && "330°" in p.explanation)
        assertTrue(approx(p.answer, Math.sqrt(3.0) / 2))
        p = Trig.specialTrig("tan", 120)
        assertTrue(approx(p.answer, -Math.sqrt(3.0)) && "−√3" in p.displayAnswer)
        p = Trig.specialTrig("sin", 270)
        assertTrue(approx(p.answer, -1.0) && boldValue(p.explanation) == -1.0)
        // tan is never asked where undefined
        val rng = Rng(0)
        val lv = Trig.SKILLS[1].levels[0]
        repeat(2000) {
            val q = lv.generate(rng)
            assertTrue(Math.abs(q.answer) < 10)
        }
    }

    @Test fun linearTrigExample() {
        val (p, est) = Trig.linearTrig("sin", 37)
        assertTrue("sin 30°" in p.explanation && "0.866" in p.explanation)
        assertTrue(approx(est, 0.5 + Math.cos(Math.toRadians(30.0)) * Math.toRadians(7.0)))
        assertTrue(approx(boldValue(p.explanation), 0.606, abs = 0.001))
    }

    @Test fun radianTrigExamples() {
        var (p, est) = Trig.radianTrig("sin", 0.4)
        assertTrue("0.4³/6" in p!!.explanation && approx(est, 0.4 - Math.pow(0.4, 3.0) / 6))
        Trig.radianTrig("cos", 1.2).let { (q, e) -> p = q; est = e }
        assertTrue("5π/12" in p!!.explanation && Math.abs(est - Math.cos(1.2)) < 0.012)
        Trig.radianTrig("sin", 3.0).let { (q, e) -> p = q; est = e }
        assertTrue("π − 3" in p!!.explanation && Math.abs(est - Math.sin(3.0)) < 0.01)
    }

    @Test fun inverseExamples() {
        var p = Trig.exactInverse("arccos", 60, true, false)  // arccos(−0.866)
        assertTrue(p.prompt == "arccos(−0.866)" && approx(p.answer, 150.0, abs = 0.01))
        assertTrue(boldValue(p.explanation) == 150.0)
        p = Trig.exactInverse("arctan", 60, false, true)
        assertTrue(p.prompt == "arctan √3" && approx(p.answer, 60.0))
        var est: Double
        Trig.inverseEstimate("arctan", 0.3).let { (q, e) -> p = q; est = e }
        assertTrue("tan 15°" in p.explanation && approx(est, 16.7, abs = 0.1))
        Trig.inverseEstimate("arcsin", 0.8 - 0.01).let { (q, e) -> p = q; est = e }
        assertTrue("3-4-5" in p.explanation && Math.abs(est - p.answer) < 0.5)
        Trig.inverseEstimate("arccos", 0.1).let { (q, e) -> p = q; est = e }
        assertTrue("90° − arcsin" in p.explanation && approx(est, 90 - 0.1 * 57.3))
        Trig.inverseEstimate("arcsin", -0.3).let { (q, e) -> p = q; est = e }
        assertTrue("odd" in p.explanation && approx(boldValue(p.explanation), est, abs = 0.05))
    }

    @Test fun arcExample() {
        val p = Trig.arcAngle(1.8, "m", 2.5, "km")
        assertTrue(approx(p.answer, Math.toDegrees(1.8 / 2500), rel = 1e-4))
        assertTrue("1.8 m / 2500 m" in clean(p.explanation))
        assertTrue(approx(boldValue(p.explanation), 1.8 / 2500 * 57.3, rel = 0.002))
    }

    @Test fun degradFractionsReduced() {
        val rng = Rng(9)
        val lv = Trig.SKILLS[0].levels[0]
        repeat(500) {
            val p = lv.generate(rng)
            Regex("""(\d*)π(?:/(\d+))? → °""").matchEntire(p.prompt)?.let { m ->
                val a = m.groupValues[1].ifEmpty { "1" }.toLong()
                val d = m.groupValues[2].ifEmpty { "1" }.toLong()
                assertEquals(d, Fraction.of(a, d).den)
            }
        }
    }

    @Test fun matchesPythonGolden() {
        assertTrue(Golden.checkModule("trig", Trig) > 0)
    }
}
