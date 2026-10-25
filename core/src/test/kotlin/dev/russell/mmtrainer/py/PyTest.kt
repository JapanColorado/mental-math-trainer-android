package dev.russell.mmtrainer.py

import dev.russell.mmtrainer.Golden
import dev.russell.mmtrainer.arr
import dev.russell.mmtrainer.content.fmtNum
import dev.russell.mmtrainer.content.fmtSci
import dev.russell.mmtrainer.content.sigRound
import dev.russell.mmtrainer.dbl
import dev.russell.mmtrainer.long
import dev.russell.mmtrainer.obj
import dev.russell.mmtrainer.str
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/** Python-semantics helpers and the RNG, checked against values recorded from CPython. */
class PyTest {
    private val g = Golden.load("py")

    private fun strings(key: String, c: kotlinx.serialization.json.JsonObject) =
        c.arr(key).map { if (it is JsonNull) null else it.jsonPrimitive.content }

    @Test
    fun formatting() {
        for (cj in g.arr("cases")) {
            val c = cj.obj
            val x = c.dbl("x")
            val w = "x=${c.str("repr")}"
            assertEquals(w, c.str("repr"), pyStr(x))
            assertEquals(w, strings("f", c), (0..6).map { fmtF(x, it) })
            assertEquals(w, strings("g", c), (1..7).map { fmtG(x, it) })
            assertEquals(w, c.str("g_default"), fmtG(x))
            assertEquals(w, strings("e", c), (0..3).map { fmtE(x, it) })
            assertEquals(w, c.arr("round").map { it.jsonPrimitive.doubleOrNull }, (-3..6).map { pyRound(x, it) })
            if (c["round_int"] !is JsonNull) assertEquals(w, c.long("round_int"), pyRound(x))
            if (c["f_grouped"] !is JsonNull) assertEquals(w, c.str("f_grouped"), fmtF(x, 10, grouping = true))
            if (c["fmt_num"] !is JsonNull) {
                assertEquals(w, c.str("fmt_num"), fmtNum(x))
                assertEquals(w, strings("fmt_num_sig", c), listOf(1, 2, 3, 4).map { fmtNum(x, sig = it) })
                assertEquals(w, strings("fmt_num_dec", c), listOf(0, 1, 2, 3).map { fmtNum(x, decimals = it) })
            }
            if (c["fmt_sci"] !is JsonNull) assertEquals(w, strings("fmt_sci", c), listOf(1, 2, 3, 4).map { fmtSci(x, it) })
            if (c["sig_round"] !is JsonNull) {
                assertEquals(w, c.arr("sig_round").map { it.jsonPrimitive.doubleOrNull }, listOf(1, 2, 3, 4).map { sigRound(x, it) })
            }
        }
    }

    @Test
    fun rngMatchesCPython() {
        for (rj in g.arr("rngs")) {
            val r = rj.obj
            val rng = Rng(r.long("seed"))
            val w = "seed ${r.long("seed")}"
            assertEquals(w, r.arr("random").map { it.jsonPrimitive.content.toDouble() }, List(5) { rng.random() })
            assertEquals(w, r.arr("randint").map { it.jsonPrimitive.content.toInt() }, List(5) { rng.randint(1, 100) })
            assertEquals(w, r.arr("randint_wide").map { it.jsonPrimitive.content.toInt() }, List(3) { rng.randint(-50, 1_000_000) })
            assertEquals(w, r.arr("choice").map { it.jsonPrimitive.content.toInt() }, List(5) { rng.choice((0 until 7).toList()) })
            assertEquals(w, r.arr("uniform").map { it.jsonPrimitive.content.toDouble() }, List(3) { rng.uniform(0.5, 2.0) })
            assertEquals(w, r.arr("randrange").map { it.jsonPrimitive.content.toInt() },
                listOf(rng.randrange(10), rng.randrange(3, 30), rng.randrange(0, 100, 5)))
            val lst = (0 until 12).toMutableList()
            rng.shuffle(lst)
            assertEquals(w, r.arr("shuffle").map { it.jsonPrimitive.content.toInt() }, lst)
            assertEquals(w, r.arr("choices").map { it.jsonPrimitive.content },
                List(5) { rng.choices("abcde".map { it.toString() }, listOf(1.0, 2.0, 3.0, 4.5, 0.5)) })
            assertEquals(w, r.arr("getrandbits40").map { it.jsonPrimitive.content.toLong() }, List(2) { rng.getrandbits(40) })
        }
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
