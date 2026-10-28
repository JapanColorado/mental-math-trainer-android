package dev.russell.mmtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswersTest {
    @Test
    fun parses() {
        val cases = listOf(
            "1234" to 1234.0, "1,234" to 1234.0, "1 234" to 1234.0, "-0.5" to -0.5, ".5" to 0.5,
            "−3" to -3.0, "3/8" to 0.375, "2 3/8" to 2.375, "3.2e5" to 3.2e5, "3.2e-5" to 3.2e-5,
            "3.2x10^5" to 3.2e5, "3.2*10^-5" to 3.2e-5, "3.2×10^5" to 3.2e5, "10^2" to 100.0,
            "45%" to 45.0, "30°" to 30.0, "  7 " to 7.0,
        )
        for ((text, value) in cases) {
            val got = Answers.parse(text)
            assertEquals(text, value, got!!, 1e-6 * Math.abs(value))
        }
    }

    @Test
    fun rejects() {
        for (text in listOf("", "abc", "1/0", "inf", "nan", "3..2", "--1")) {
            assertNull(text, Answers.parse(text))
        }
    }

    @Test
    fun fullLength() {
        val full = listOf("43" to 42.0, "−41" to -42.0, "0.124" to 0.125, "2.5" to 2.5, "0" to 0.0, "1 235" to 1234.0)
        for ((text, answer) in full) assertTrue("$text for $answer", Answers.fullLength(text, answer))
        val short = listOf(
            "4" to 42.0, "41" to -42.0, "0.12" to 0.125, ".124" to 0.125, "1/3" to 0.5, "1e2" to 100.0,
            "3.0" to 3.0, "" to 7.0, "−" to -7.0,
        )
        for ((text, answer) in short) assertFalse("$text for $answer", Answers.fullLength(text, answer))
    }
}
