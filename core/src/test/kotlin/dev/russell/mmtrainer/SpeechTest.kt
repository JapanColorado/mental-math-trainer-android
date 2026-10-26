package dev.russell.mmtrainer

import dev.russell.mmtrainer.Speech.speakable
import dev.russell.mmtrainer.content.Content
import dev.russell.mmtrainer.py.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTest {
    private val PLAIN = Regex("""[A-Za-z0-9 .,?'-]+""")

    @Test
    fun readsPrompts() {
        val cases = listOf(
            "61 + 65" to "61 plus 65",
            "10 000 − 2 034" to "10000 minus 2034",
            "75<sup>2</sup>" to "75 squared",
            "(8 × 10<sup>−11</sup>) ÷ (8 × 10<sup>−4</sup>)" to
                "8 times 10 to the minus 11, divided by 8 times 10 to the minus 4",
            "√(0.00083) × 2.21" to "the square root of 0.00083, times 2.21",
            "<sup>5</sup>√7 000 000" to "the fifth root of 7000000",
            "81<sup>−1/4</sup>" to "81 to the power minus 1 over 4",
            "log<sub>10</sub>(8 000)" to "log base 10 of 8000",
            "ln (5e<sup>2</sup>)" to "natural log of 5 e squared",
            "1 / ln 10" to "1 over natural log of 10",
            "454 → 298<br>% change?" to "454 to 298. percent change?",
            "After −40%: 210<br>original value?" to "After minus 40 percent, 210. original value?",
            "5π/3 → °" to "5 pi over 3 in degrees",
            "1° in radians" to "1 degree in radians",
            "tan(−120°)" to "tangent of minus 120 degrees",
            "arccos(−√3/2)" to "arc cosine of minus the square root of 3 over 2",
            "0.14° at 9 400 m: size?" to "0.14 degrees at 9400 meters, size?",
            "$20 000 at 7% for 35 years" to "20000 dollars at 7 percent for 35 years",
            "Years to grow 10× at 9%/yr?" to "Years to grow 10 times at 9 percent per year?",
            "Half-life if 3% decays per period" to "Half-life if 3 percent decays per period",
            "φ (golden ratio)" to "phi, the golden ratio",
        )
        for ((rich, spoken) in cases) assertEquals(rich, spoken, speakable(rich))
    }

    @Test
    fun readsAnswers() {
        assertEquals("about 3.672", speakable("≈ 3.672"))
        assertEquals("1 over 3, about 0.333", speakable("1/3 ≈ 0.333"))
        assertEquals("5 over 2 equals 2.5", speakable("5/2 = 2.5"))
        assertEquals("about 1.33 times 10 to the 5", speakable("≈ 1.33 × 10<sup>5</sup>"))
    }

    /** Every prompt and answer the content can produce comes out as plain words and digits. */
    @Test
    fun everythingIsPlainText() {
        val texts = mutableListOf<String>()
        for (skill in Content.SKILLS.values) for (level in skill.levels) {
            val rng = Rng(7)
            repeat(200) {
                val p = level.generate(rng)
                texts += p.prompt
                texts += p.displayAnswer
            }
        }
        for ((_, card) in Content.CARDS.values) {
            texts += card.prompt
            texts += card.displayAnswer
        }
        for (t in texts.toSet()) {
            val s = speakable(t)
            assertTrue("$t -> $s", s.isNotEmpty() && PLAIN.matches(s) && "  " !in s && ",," !in s)
        }
    }
}
