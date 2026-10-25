package dev.russell.mmtrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
