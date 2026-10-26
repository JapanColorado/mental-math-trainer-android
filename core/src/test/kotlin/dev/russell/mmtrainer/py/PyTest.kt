package dev.russell.mmtrainer.py

import dev.russell.mmtrainer.content.THIN
import dev.russell.mmtrainer.content.fmtNum
import dev.russell.mmtrainer.content.fmtSci
import org.junit.Assert.assertEquals
import org.junit.Test

class PyTest {
    @Test
    fun formatting() {
        assertEquals(listOf("0.1", "1e+16", "2.5e-07"), listOf(0.1, 1e16, 2.5e-7).map { pyStr(it) })
        assertEquals("2", fmtF(2.5, 0))           // round half to even
        assertEquals("0.12", fmtF(0.125, 2))
        assertEquals("1,234,567.89", fmtF(1234567.891, 2, grouping = true))
        assertEquals("1.23457e+06", fmtG(1234567.0))
        assertEquals(2.67, pyRound(2.675, 2), 0.0)  // 2.675 is really 2.67499…
        assertEquals(-2L, pyRound(-2.5))
        assertEquals("1${THIN}234${THIN}567", fmtNum(1234567.0))
        assertEquals("3.2 × 10<sup>4</sup>", fmtSci(32000.0, 2))
    }

    @Test
    fun rngIsDeterministic() {
        val rng = Rng(1)
        assertEquals(0.13436424411240122, rng.random(), 0.0)
        assertEquals(98, rng.randint(1, 100))
        val l = (0 until 8).toMutableList()
        rng.shuffle(l)
        assertEquals(listOf(5, 6, 7, 4, 3, 0, 2, 1), l)
    }

    @Test
    fun fractionBasics() {
        assertEquals("3/4", Fraction.of(6, 8).toString())
        assertEquals("-1/2", Fraction.of(1, -2).toString())
        assertEquals("5", (Fraction.of(5, 2) * 2).toString())
        assertEquals(Fraction.of(1, 8), Fraction.of(2).pow(-3))
        assertEquals(0.375, Fraction.of(3, 8).toDouble(), 0.0)
    }
}
