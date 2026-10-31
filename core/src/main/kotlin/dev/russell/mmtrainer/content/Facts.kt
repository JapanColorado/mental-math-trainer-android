package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.fmtG
import dev.russell.mmtrainer.py.ipow
import dev.russell.mmtrainer.py.pyRound

/**
 * Memorization decks: the anchor facts every faster method is built on.
 *
 * Times tables, squares, cubes, powers, reciprocals, logarithms, exponentials,
 * constants and special-angle trig values. Each card carries a short note: a
 * mnemonic or a derivation from facts the learner already knows.
 */
object Facts : ContentModule {
    private data class R4(val id: String, val label: String, val value: Double, val note: String)
    private data class RD(val id: String, val prompt: String, val value: Double, val digits: Int, val note: String)
    private data class RA(val id: String, val prompt: String, val value: Double, val hint: String, val note: String)

    // ---------------------------------------------------------------- helpers

    private fun sup(n: Any): String = "<sup>${n.toString().replace("-", "−")}</sup>"

    /** Show x to [decimals] places, prefixed with ≈ unless that is exact. */
    private fun approxDec(x: Double, decimals: Int): String {
        if (Math.abs(pyRound(x, decimals) - x) < 1e-12) return fmtNum(pyRound(x, decimals))
        return "≈ " + fmtNum(x, decimals = decimals)
    }

    /** Show x to [sig] significant figures, prefixed with ≈ unless exact. */
    private fun approxSig(x: Double, sig: Int): String {
        val digits = sig - 1 - Math.floor(Math.log10(Math.abs(x))).toInt()
        if (Math.abs(pyRound(x, digits) - x) < 1e-12 * maxOf(1.0, Math.abs(x))) return fmtNum(pyRound(x, digits))
        return "≈ " + fmtNum(x, sig = sig)
    }

    /** Integers: group thousands only from 5 digits up (1024, but 65 536). */
    private fun intStr(n: Long): String = if (Math.abs(n) < 10_000) n.toString() else fmtNum(n)

    private fun intCard(cid: String, prompt: String, value: Long, note: String, altPrompt: String? = null) =
        Card(cid, prompt, value.toDouble(), intStr(value), note = note, altPrompt = altPrompt)

    /** a × b, also asked as b × a. */
    private fun productCard(cid: String, a: Int, b: Int, note: String) =
        intCard(cid, "$a × $b", (a * b).toLong(), note, if (a != b) "$b × $a" else null)

    // ---------------------------------------------------------------- times tables

    private val TIMES_FACTORS = listOf(2, 3, 4, 5, 6, 7, 8, 9, 11, 12)

    /** A memory hook for a × b (a ≤ b), using the most useful shortcut. */
    private fun timesNote(a: Int, b: Int): String {
        val p = a * b
        if (a == 7 && b == 8) return "5, 6, 7, 8: <b>56 = 7 × 8</b>."
        if (a == 6 && b == 7) return "6 × 7 = 6 × 6 + 6 = 36 + 6 = 42."
        if (a == 2) return "Just double: $b + $b = $p."
        if (a == b) return "$a² = $p. Its neighbours: ${a - 1} × ${a + 1} = $p − 1 = ${p - 1}."
        if (b == 11 && a <= 9) return "11 × a single digit repeats the digit: $p."
        if (b == 12) return "12 × $a = 10 × $a + 2 × $a = ${10 * a} + ${2 * a} = $p."
        if (a == 9 || b == 9) {
            val o = if (b == 9) a else b
            return "9 × $o = 10 × $o − $o = ${10 * o} − $o = $p (digits sum to 9)."
        }
        if (a == 5 || b == 5) {
            val o = if (b == 5) a else b
            return "5 × $o = half of 10 × $o = ${10 * o} ÷ 2 = $p."
        }
        if (b - a == 2) {
            val n = a + 1
            return "$a × $b = ($n − 1)($n + 1) = $n² − 1 = ${n * n} − 1 = $p."
        }
        if (a == 4 || b == 4) {
            val o = if (b == 4) a else b
            return "Double twice: $o → ${2 * o} → $p."
        }
        if (a == 8 || b == 8) {
            val o = if (b == 8) a else b
            return "Double three times: $o → ${2 * o} → ${4 * o} → $p."
        }
        if (a == 3) return "3 × $b = 2 × $b + $b = ${2 * b} + $b = $p."
        return "$a × $b = $a × ${b - 1} + $a = ${a * (b - 1)} + $a = $p."
    }

    private val TIMES_CARDS: List<Card> = TIMES_FACTORS.withIndex().flatMap { (i, a) ->
        TIMES_FACTORS.drop(i).map { b -> productCard("times:${a}x$b", a, b, timesNote(a, b)) }
    }

    // ---------------------------------------------------------------- teens × 1-digit

    private fun teensNote(n: Int, m: Int): String {
        val p = n * m
        val u = n - 10
        if (n == 11) return "11 × $m repeats the digit: $p."
        if (n == 19) return "19 × $m = 20 × $m − $m = ${20 * m} − $m = $p."
        if (n == 15) return "15 × $m = 10 × $m + half of that = ${10 * m} + ${5 * m} = $p."
        return "$n × $m = 10 × $m + $u × $m = ${10 * m} + ${u * m} = $p."
    }

    private val TEENS_CARDS: List<Card> = (11 until 20).flatMap { n ->
        (2 until 10).map { m -> productCard("teens:${n}x$m", n, m, teensNote(n, m)) }
    }

    // ---------------------------------------------------------------- squares 11–30

    private fun squareNote(n: Int): String {
        val p = n * n
        val t = n / 10
        val u = n % 10
        if (u == 5) return "Ends in 5: $t × ${t + 1} = ${t * (t + 1)}, then append 25 → $p."
        if (n < 20) return "Teen trick: ($n + $u) × 10 + $u² = ${(n + u) * 10} + ${u * u} = $p."
        // round to the nearest ten: n² = (n − d)(n + d) + d²
        val d = if (u < 5) u else u - 10
        val lo = n - d
        val hi = n + d
        val dd = Math.abs(d)
        return "$n² = ${minOf(lo, hi)} × ${maxOf(lo, hi)} + $dd² = ${lo * hi} + ${dd * dd} = $p."
    }

    private val SQUARES_CARDS: List<Card> = (11 until 31).filter { it % 10 != 0 }.map { n ->
        intCard("squares:$n", "$n²", (n * n).toLong(), squareNote(n))
    }

    // ---------------------------------------------------------------- cubes

    private val CUBE_NOTES = listOf(
        3 to "3 × 9 = 27.",
        4 to "4³ = 64 = 2⁶ = 8².",
        5 to "5³ = 125 = 1000 ÷ 8 (which is why 1/8 = 0.125).",
        6 to "6 × 36 = 216. Cute: 3³ + 4³ + 5³ = 27 + 64 + 125 = 216.",
        7 to "7 × 49 = 343, a palindrome (3-4-3).",
        8 to "8³ = 512 = 2⁹, half of 1024.",
        9 to "9 × 81 = 729 = 27², one less than 730.",
        11 to "Pascal's row 1 3 3 1: 11³ = 1331.",
        12 to "12 × 144 = 1728 — cubic inches in a cubic foot.",
        15 to "15 × 225 = 3375 (= 3³ × 5³ = 27 × 125).",
    )

    private val CUBES_CARDS: List<Card> = CUBE_NOTES.map { (n, note) -> intCard("cubes:$n", "$n³", ipow(n, 3), note) }

    // ---------------------------------------------------------------- powers of two

    private fun pow2Note(n: Int): String {
        if (n < 0) {
            val k = -n
            val v = Math.pow(2.0, n.toDouble())
            val extra = mapOf(2 to "a quarter", 3 to "an eighth: 1000/8 = 125",
                4 to "half of 0.125", 5 to "half of 0.0625, about 3 %").getValue(k)
            return "2${sup(n)} = 1/${ipow(2, k)} = ${fmtNum(v)} — $extra."
        }
        val v = ipow(2, n)
        if (n < 10) {
            val hook = mapOf(8 to " (values in one byte)")[n] ?: ""
            return "Double 2${sup(n - 1)} = ${ipow(2, n - 1)}: $v$hook."
        }
        if (n == 10) return "2¹⁰ = 1024 ≈ 10³ — the bridge between binary and decimal."
        if (n == 20) return "2²⁰ = 1024² = 1${THIN}048${THIN}576 ≈ 10⁶ (a binary mega)."
        val k = n - 10
        val hook = mapOf(16 to " (one past the 16-bit max)", 15 to " (one past the int16 max)")[n] ?: ""
        return "2${sup(n)} = 2${sup(k)} × 2¹⁰ = ${ipow(2, k)} × 1024 = ${intStr(v)}" +
            " ≈ ${ipow(2, k)} × 10³$hook."
    }

    private val POW2_CARDS: List<Card> = ((4 until 21).toList() + listOf(-2, -3, -4, -5)).map { n ->
        Card("pow2:$n", "2${sup(n)}", Math.pow(2.0, n.toDouble()),
            if (n >= 0) intStr(ipow(2, n)) else fmtNum(Math.pow(2.0, n.toDouble())), note = pow2Note(n))
    }

    // ---------------------------------------------------------------- other powers

    private val POWERS = listOf(
        Triple(3, 4, "3⁴ = 9² = 81."),
        Triple(3, 5, "3 × 81 = 243."),
        Triple(3, 6, "3⁶ = 27² = 9³ = 729."),
        Triple(3, 7, "3 × 729 = 2187."),
        Triple(3, 8, "3⁸ = 81² = 6561."),
        Triple(3, 9, "3⁹ = 3 × 6561 = 19 683 = 27³."),
        Triple(3, 10, "3¹⁰ = 243² = 59 049 ≈ 6 × 10⁴ (so 10 log 3 ≈ 4.77)."),
        Triple(4, 4, "4⁴ = 2⁸ = 256."),
        Triple(4, 5, "4⁵ = 2¹⁰ = 1024."),
        Triple(5, 4, "5⁴ = 25² = 625."),
        Triple(5, 5, "5⁵ = 10⁵ ÷ 2⁵ = 100 000 ÷ 32 = 3125."),
        Triple(5, 6, "5⁶ = 10⁶ ÷ 64 = 15 625 = 125²."),
        Triple(5, 7, "5⁷ = 10⁷ ÷ 128 = 78 125."),
        Triple(6, 4, "6⁴ = 36² = 1296."),
        Triple(6, 5, "6 × 1296 = 7776 — outcomes of rolling five dice."),
        Triple(7, 4, "7⁴ = 49² = (50 − 1)² = 2500 − 100 + 1 = 2401."),
        Triple(7, 5, "7 × 2401 = 16 807."),
        Triple(8, 4, "8⁴ = 2¹² = 4096."),
        Triple(9, 4, "9⁴ = 81² = 3⁸ = 6561."),
        Triple(11, 4, "Pascal's row 1 4 6 4 1: 11⁴ = 14 641."),
        Triple(13, 3, "13 × 169 = 2197."),
        Triple(14, 3, "14 × 196 = 2744."),
    )

    private val POWERS_CARDS: List<Card> = POWERS.map { (b, e, note) -> intCard("powers:$b^$e", "$b${sup(e)}", ipow(b, e), note) }

    // ---------------------------------------------------------------- reciprocals

    private val RECIPS = listOf(
        Triple(1, 3, "0.333… repeating; n/3 = 0.333…, 0.666…"),
        Triple(1, 6, "Half of 1/3: 0.1666…"),
        Triple(1, 7, "Cyclic 142857. Every n/7 is a rotation of the same six digits."),
        Triple(1, 8, "1000 ÷ 8 = 125, so 0.125."),
        Triple(1, 9, "n/9 = 0.nnn…, so 1/9 = 0.111…"),
        Triple(1, 11, "n/11 = 9n repeating: 1/11 = 0.0909…"),
        Triple(1, 12, "Half of 1/6 = 0.0833…"),
        Triple(1, 13, "13 × 77 = 1001, so 1/13 ≈ 77/1000 = 0.077 (exactly 0.076923…)."),
        Triple(1, 14, "Half of 1/7 = 0.0714…"),
        Triple(1, 15, "1/15 = 2/30 = 0.0666…"),
        Triple(1, 16, "Half of 0.125 = 0.0625."),
        Triple(1, 17, "17 × 6 = 102, so 1/17 ≈ 0.06 × (1 − 0.02) ≈ 0.0588."),
        Triple(1, 19, "1/19 = 0.05 ÷ 0.95 ≈ 0.05 × 1.053 ≈ 0.0526."),
        Triple(1, 24, "A third of 1/8: 0.125 ÷ 3 ≈ 0.0417."),
        Triple(2, 3, "Twice 0.333…: 0.666…, so ≈ 0.667."),
        Triple(5, 6, "1 − 1/6 = 0.8333…"),
        Triple(3, 8, "3 × 0.125 = 0.375."),
        Triple(5, 8, "5 × 0.125 = 0.625 (= 1/2 + 1/8)."),
        Triple(7, 8, "1 − 0.125 = 0.875."),
        Triple(2, 7, "Rotate 142857 to start at the next-biggest digit: 0.285714…"),
        Triple(3, 7, "Rotation of 142857 starting near 0.43: 0.428571…"),
        Triple(4, 7, "1 − 3/7: 0.571428…"),
        Triple(2, 9, "n/9 = 0.nnn…: 0.222…"),
        Triple(4, 9, "n/9 = 0.nnn…: 0.444…"),
        Triple(5, 12, "1/3 + 1/12 = 0.333 + 0.083 = 0.4166…"),
        Triple(7, 12, "1/2 + 1/12 = 0.5 + 0.0833 = 0.5833…"),
        Triple(3, 16, "3 × 0.0625 = 0.1875."),
        Triple(5, 16, "5 × 0.0625 = 0.3125 (= 1/4 + 1/16)."),
    )

    private fun isTerminating(den0: Int): Boolean {
        var den = den0
        for (p in listOf(2, 5)) {
            while (den % p == 0) den /= p
        }
        return den == 1
    }

    private fun recipCard(num: Int, den: Int, note: String): Card {
        val x = num.toDouble() / den
        val display = if (isTerminating(den)) fmtNum(x) else fmtF(Math.floor(x * 1e6) / 1e6, 6) + "…"
        return Card("recip:$num/$den", "$num/$den", x, display,
            tolAbs = 0.0006, note = note, hint = "3 decimal places")
    }

    private val RECIP_CARDS: List<Card> = RECIPS.map { (n, d, note) -> recipCard(n, d, note) }

    // ---------------------------------------------------------------- log10

    private const val LOG3 = "3 decimal places"

    private val LOG10_FWD = listOf(
        R4("2", "2", 2.0, "2¹⁰ = 1024 ≈ 10³, so log 2 ≈ 0.3 (exactly 0.30103)."),
        R4("3", "3", 3.0, "3² = 9 ≈ 10 → just under 0.5. Memorize 0.477."),
        R4("4", "4", 4.0, "2 log 2 = 2 × 0.301 = 0.602."),
        R4("5", "5", 5.0, "log(10/2) = 1 − 0.301 = 0.699."),
        R4("6", "6", 6.0, "log 2 + log 3 = 0.301 + 0.477 = 0.778."),
        R4("7", "7", 7.0, "7² = 49 ≈ 50: log 7 ≈ ½ log 50 = 0.849, minus a bit → 0.845."),
        R4("8", "8", 8.0, "3 log 2 = 3 × 0.301 = 0.903."),
        R4("9", "9", 9.0, "2 log 3 = 2 × 0.477 = 0.954."),
        R4("1.5", "1.5", 1.5, "log 3 − log 2 = 0.477 − 0.301 = 0.176."),
        R4("pi", "π", Math.PI, "π ≈ √10 = 3.162, so just under 0.5: 0.497."),
        R4("e", "e", Math.E, "log e = 1/ln 10 = 0.434 — the ln → log conversion factor."),
    )

    private val LOG10_REV = listOf(
        Pair(0.1, "≈ 1.25 = 10/8 (log 1.25 = 1 − 0.903 = 0.097). True: 1.259."),
        Pair(0.2, "≈ 1.6 = 16/10 (log 1.6 = 4 × 0.301 − 1 = 0.204). True: 1.585."),
        Pair(0.25, "√(√10) = √3.162 ≈ 1.778."),
        Pair(0.3, "≈ 2 (log 2 = 0.301). True: 1.995."),
        Pair(0.4, "≈ 2.5 = 10/4 (log 2.5 = 1 − 0.602 = 0.398). True: 2.512."),
        Pair(0.5, "√10 = 3.162 (≈ π + 0.02)."),
        Pair(0.6, "≈ 4 (log 4 = 0.602). True: 3.981."),
        Pair(0.7, "≈ 5 (log 5 = 0.699). True: 5.012."),
        Pair(0.75, "10 ÷ 10<sup>0.25</sup> = 10 ÷ 1.778 ≈ 5.623."),
        Pair(0.8, "≈ 6.3 (log 63 = log 7 + log 9 = 1.799). True: 6.310."),
        Pair(0.9, "≈ 8 (log 8 = 0.903), a shade under: 7.943."),
    )

    private val LN_ROWS = listOf(
        RD("2", "ln 2", Math.log(2.0), 3, "0.693 — the rule of 70: doubling time ≈ 70 / (% rate)."),
        RD("3", "ln 3", Math.log(3.0), 3, "1.0986 ≈ 1.1 (e ≈ 2.72, a bit less than 3)."),
        RD("4", "ln 4", Math.log(4.0), 3, "2 ln 2 = 2 × 0.693 = 1.386."),
        RD("5", "ln 5", Math.log(5.0), 3, "ln 10 − ln 2 = 2.303 − 0.693 = 1.609."),
        RD("6", "ln 6", Math.log(6.0), 3, "ln 2 + ln 3 = 0.693 + 1.099 = 1.792."),
        RD("7", "ln 7", Math.log(7.0), 3, "½ ln 49 ≈ ½ ln 50 = ½(4.605 − 0.693) = 1.956, minus 0.01 → 1.946."),
        RD("8", "ln 8", Math.log(8.0), 3, "3 ln 2 = 3 × 0.693 = 2.079."),
        RD("9", "ln 9", Math.log(9.0), 3, "2 ln 3 = 2 × 1.099 = 2.197."),
        RD("10", "ln 10", Math.log(10.0), 3, "2.303 = 1/0.434. log → ln: multiply by 2.303."),
        RD("1.5", "ln 1.5", Math.log(1.5), 3, "ln 3 − ln 2 = 1.099 − 0.693 = 0.405."),
        RD("1.1", "ln 1.1", Math.log(1.1), 4, "ln(1 + x) ≈ x − x²/2 + x³/3 = 0.1 − 0.005 + 0.0003 = 0.0953."),
        RD("1.2", "ln 1.2", Math.log(1.2), 3, "ln 6 − ln 5 = 1.792 − 1.609 = 0.182."),
        RD("0.5", "ln 0.5", Math.log(0.5), 3, "−ln 2 = −0.693: ln of a reciprocal is the negative."),
        RD("0.1", "ln 0.1", Math.log(0.1), 3, "−ln 10 = −2.303."),
        RD("inv10", "1 / ln 10", 1 / Math.log(10.0), 3, "= log<sub>10</sub> e = 0.434. ln → log: multiply by 0.434."),
        RD("inv2", "1 / ln 2", 1 / Math.log(2.0), 3, "= log<sub>2</sub> e = 1.443. ln → log₂: multiply by 1.443."),
    )

    private val EXP_ROWS = listOf(
        RD("0.1", "0.1", 0.1, 4, "1 + x + x²/2 = 1 + 0.1 + 0.005 = 1.105."),
        RD("0.2", "0.2", 0.2, 4, "1 + 0.2 + 0.02 + 0.0013 ≈ 1.221."),
        RD("0.5", "0.5", 0.5, 4, "√e = √2.718 ≈ 1.649."),
        RD("ln2", "0.693", 0.693, 4, "0.693 = ln 2, so e<sup>0.693</sup> ≈ 2."),
        RD("1", "1", 1.0, 6, "e = 2.718281828… (2.7, then 1828 twice)."),
        RD("1.5", "1.5", 1.5, 4, "e × √e = 2.718 × 1.649 ≈ 4.48."),
        RD("2", "2", 2.0, 4, "e² = 2.718² ≈ 7.389."),
        RD("ln10", "2.303", 2.303, 4, "2.303 = ln 10, so e<sup>2.303</sup> ≈ 10."),
        RD("3", "3", 3.0, 4, "≈ 20 (ln 20 = 0.693 + 2.303 = 2.996). True: 20.09."),
        RD("4", "4", 4.0, 4, "(e²)² = 7.389² ≈ 54.6."),
        RD("5", "5", 5.0, 4, "≈ 150 (ln 150 = 0.405 + 4.605 = 5.01). True: 148.4."),
        RD("10", "10", 10.0, 5, "10 / 2.303 = 4.343 decades: 10<sup>4.343</sup> ≈ 22 000."),
        RD("-0.5", "−0.5", -0.5, 4, "1/√e = 1/1.649 ≈ 0.607."),
        RD("-1", "−1", -1.0, 4, "1/e = 0.368 — the 37 % rule of optimal stopping."),
        RD("-2", "−2", -2.0, 4, "(1/e)² = 0.368² ≈ 0.135."),
        RD("-3", "−3", -3.0, 4, "1/e³ = 1/20.09 ≈ 0.0498 ≈ 5 %."),
    )

    private val CONSTS = listOf(
        R4("pi", "π", Math.PI, "3.14159 — \"How I wish I could calculate pi\" (word lengths 3.141592)."),
        R4("e", "e", Math.E, "2.718281828: 2.7, then 1828 twice."),
        R4("sqrt2", "√2", Math.sqrt(2.0), "1.41421; check: 1.414² = 1.999."),
        R4("sqrt3", "√3", Math.sqrt(3.0), "1.732 — George Washington was born in 1732."),
        R4("sqrt5", "√5", Math.sqrt(5.0), "2.236; check: 2.236² = 4.9997."),
        R4("sqrt6", "√6", Math.sqrt(6.0), "√2 × √3 = 1.414 × 1.732 ≈ 2.449."),
        R4("sqrt7", "√7", Math.sqrt(7.0), "2.6458; 2.65² = 7.02, so a little under 2.65."),
        R4("sqrt8", "√8", Math.sqrt(8.0), "2√2 = 2 × 1.4142 = 2.828."),
        R4("sqrt10", "√10", Math.sqrt(10.0), "3.162 ≈ π + 0.02. Also 10<sup>0.5</sup>."),
        R4("phi", "φ (golden ratio)", (1 + Math.sqrt(5.0)) / 2, "(1 + √5)/2 = (1 + 2.236)/2 = 1.618; 1/φ = 0.618."),
        R4("pi2", "π²", Math.pow(Math.PI, 2.0), "9.870 — close to g = 9.81 m/s²."),
        R4("invpi", "1/π", 1 / Math.PI, "0.3183 — a bit under 1/3."),
        R4("2pi", "2π", 2 * Math.PI, "6.2832, one full turn in radians."),
        R4("halfpi", "π/2", Math.PI / 2, "1.5708, a right angle in radians."),
        R4("sqrtpi", "√π", Math.sqrt(Math.PI), "1.7725 — slightly more than √3 = 1.732."),
        R4("invsqrt2", "1/√2", 1 / Math.sqrt(2.0), "√2/2 = 0.7071 = sin 45° = cos 45°."),
        R4("inve", "1/e", 1 / Math.E, "0.3679 — the 37 % rule."),
        R4("sqrt2pi", "√(2π)", Math.sqrt(2 * Math.PI), "2.5066 — the normal-distribution constant; ≈ 2.5."),
        R4("cbrt2", "∛2", Math.pow(2.0, 1.0 / 3), "1.26: 1.26³ = 2.0004. (Also 2<sup>1/3</sup>, a major third.)"),
        R4("cbrt10", "∛10", Math.pow(10.0, 1.0 / 3), "2.154: 2.15³ ≈ 9.94, a shade more."),
    )

    private val TRIG = listOf(
        R4("sin30", "sin 30°", 0.5, "Half an equilateral triangle: opposite = ½ hypotenuse."),
        R4("sin45", "sin 45°", Math.sqrt(2.0) / 2, "√2/2 = 1.4142/2 = 0.7071."),
        R4("sin60", "sin 60°", Math.sqrt(3.0) / 2, "√3/2 = 1.732/2 = 0.8660."),
        R4("cos30", "cos 30°", Math.sqrt(3.0) / 2, "cos 30° = sin 60° = √3/2 = 0.8660."),
        R4("cos45", "cos 45°", Math.sqrt(2.0) / 2, "cos 45° = sin 45° = √2/2 = 0.7071."),
        R4("cos60", "cos 60°", 0.5, "cos 60° = sin 30° = ½."),
        R4("tan30", "tan 30°", 1 / Math.sqrt(3.0), "1/√3 = √3/3 = 1.732/3 = 0.5774."),
        R4("tan60", "tan 60°", Math.sqrt(3.0), "√3 = 1.732."),
        R4("sin15", "sin 15°", (Math.sqrt(6.0) - Math.sqrt(2.0)) / 4, "(√6 − √2)/4 = (2.449 − 1.414)/4 = 0.2588."),
        R4("cos15", "cos 15°", (Math.sqrt(6.0) + Math.sqrt(2.0)) / 4, "(√6 + √2)/4 = (2.449 + 1.414)/4 = 0.9659."),
        R4("sin75", "sin 75°", (Math.sqrt(6.0) + Math.sqrt(2.0)) / 4, "sin 75° = cos 15° = 0.9659."),
        R4("cos75", "cos 75°", (Math.sqrt(6.0) - Math.sqrt(2.0)) / 4, "cos 75° = sin 15° = 0.2588."),
        R4("tan15", "tan 15°", 2 - Math.sqrt(3.0), "2 − √3 = 0.2679."),
        R4("tan75", "tan 75°", 2 + Math.sqrt(3.0), "2 + √3 = 3.732."),
        R4("sin18", "sin 18°", (Math.sqrt(5.0) - 1) / 4, "(√5 − 1)/4 = 1/(2φ) = 0.309."),
        R4("cos36", "cos 36°", (1 + Math.sqrt(5.0)) / 4, "φ/2 = 1.618/2 = 0.809."),
    )

    private val ANGLES = listOf(
        RA("rad", "1 rad in degrees", 180 / Math.PI, "degrees, 3 significant figures", "180/π = 57.30°. (sin x ≈ x works because 1° is tiny: 0.01745 rad.)"),
        RA("deg1", "1° in radians", Math.PI / 180, "radians, 3 significant figures", "π/180 = 0.01745 ≈ 7/400."),
        RA("deg30", "30° in radians", Math.PI / 6, "radians, 3 significant figures", "π/6 = 3.1416/6 = 0.5236."),
        RA("deg45", "45° in radians", Math.PI / 4, "radians, 3 significant figures", "π/4 = 0.7854."),
        RA("deg60", "60° in radians", Math.PI / 3, "radians, 3 significant figures", "π/3 = 1.0472, just over 1 rad (57.3°)."),
        RA("deg90", "90° in radians", Math.PI / 2, "radians, 3 significant figures", "π/2 = 1.5708."),
        RA("deg180", "180° in radians", Math.PI, "radians, 3 significant figures", "π = 3.1416."),
    )

    private val LOG10_CARDS: List<Card> = LOG10_FWD.map { (cid, label, x, note) ->
        Card("log10:$cid", "log<sub>10</sub> $label", Math.log10(x),
            approxDec(Math.log10(x), 3), tolAbs = 0.003, note = note, hint = LOG3)
    } + LOG10_REV.map { (p, note) ->
        Card("log10:10^${fmtG(p)}", "10${sup(fmtG(p))}", Math.pow(10.0, p), approxSig(Math.pow(10.0, p), 4),
            tolRel = 0.01, note = note, hint = "3 significant figures")
    }

    // ---------------------------------------------------------------- natural logs

    private val LN_CARDS: List<Card> = LN_ROWS.map { (cid, prompt, v, d, note) ->
        Card("ln:$cid", prompt, v, approxDec(v, d), tolAbs = 0.005, note = note, hint = LOG3)
    }

    // ---------------------------------------------------------------- exponentials

    private val EXP_CARDS: List<Card> = EXP_ROWS.map { (cid, label, x, sig, note) ->
        Card("exp:$cid", "e${sup(label)}", Math.exp(x), approxSig(Math.exp(x), sig),
            tolRel = 0.01, note = note, hint = "3 significant figures")
    }

    // ---------------------------------------------------------------- constants

    private val CONSTS_CARDS: List<Card> = CONSTS.map { (cid, prompt, v, note) ->
        Card("consts:$cid", prompt, v, approxSig(v, 6), tolRel = 0.0005, note = note,
            hint = "4 significant figures")
    }

    // ---------------------------------------------------------------- trig

    private val TRIG_CARDS: List<Card> = TRIG.map { (cid, prompt, v, note) ->
        Card("trig:$cid", prompt, v, approxDec(v, 4), tolAbs = 0.002, note = note,
            hint = "3 decimal places")
    } + ANGLES.map { (cid, prompt, v, hint, note) ->
        Card("trig:$cid", prompt, v, approxSig(v, 4), tolRel = 0.005, note = note, hint = hint)
    }

    // ---------------------------------------------------------------- lessons

    private val TIMES_LESSON = Lesson("Times tables, 2–12", """
<p>Every multi-digit product is a stack of single-digit products. If 7 × 8 costs a second of
hesitation, 47 × 68 costs four of them, plus the strain of holding intermediate results.</p>
<p>The goal is <b>instant recall</b>: under a second, no counting up, no rederiving. There are only
55 facts once you use a × b = b × a and drop the trivial ×1 and ×10.</p>
<h3>×2, ×4, ×8</h3>
<p>Double once, twice or three times.</p>
<ex>8 × 7<br>7 + 7 = 14<br>14 + 14 = 28<br>28 + 28 = <b>56</b></ex>
<h3>×5</h3>
<p>Half of ×10.</p>
<ex>5 × 8<br>10 × 8 = 80<br>80 ÷ 2 = <b>40</b></ex>
<h3>×9</h3>
<p>×10 minus one copy. The digits of the answer sum to 9 (except 9 × 11 = 99).</p>
<ex>9 × 7<br>10 × 7 = 70<br>70 − 7 = <b>63</b><br>Check: 6 + 3 = 9</ex>
<h3>×11 and ×12</h3>
<p>×11 repeats a single digit: 11 × 6 = 66. ×12 is ×10 plus ×2.</p>
<ex>12 × 7<br>10 × 7 = 70<br>2 × 7 = 14<br>70 + 14 = <b>84</b></ex>
<h3>Neighbours of squares</h3>
<p>Numbers either side of n multiply to one less than its square: (n−1)(n+1) = n² − 1.</p>
<ex>6 × 8<br>7² = 49<br>49 − 1 = <b>48</b></ex>
<h3>The hard middle</h3>
<p>These are the genuinely hard ones.</p>
<ex>6 × 7 = 42<br>6 × 8 = 48<br>7 × 7 = 49<br>7 × 8 = 56 (5-6-7-8)<br>7 × 9 = 63<br>8 × 8 = 64</ex>
<tip>Use the derivation when you miss, but aim to stop needing it: say the fact aloud, then retype it from memory.</tip>
""")

    private val TEENS_LESSON = Lesson("Teens × one digit", """
<p>Products like 17 × 6 = 102 or 14 × 8 = 112 turn up constantly: in percentages, in unit
conversions, inside every two-digit multiplication. They sit just past the times table, so know
them by heart rather than compute them each time.</p>
<h3>Split off the ten</h3>
<p>This derivation always works.</p>
<ex>17 × 6<br>10 × 6 = 60<br>7 × 6 = 42<br>60 + 42 = <b>102</b></ex>
<h3>19 × m and 18 × m</h3>
<p>Count down from 20. 19 × m is 20 × m − m. 18 × m can also be 20m − 2m.</p>
<ex>19 × 7<br>20 × 7 = 140<br>140 − 7 = <b>133</b></ex>
<ex>18 × 7<br>20 × 7 = 140<br>2 × 7 = 14<br>140 − 14 = <b>126</b></ex>
<h3>15 × m</h3>
<p>Take 10m, then add half of 10m.</p>
<ex>15 × 8<br>10 × 8 = 80<br>80 ÷ 2 = 40<br>80 + 40 = <b>120</b></ex>
<tip>Use these for 19, 18 and 15, where they beat splitting off the ten.</tip>
<h3>Landmarks</h3>
<p>Practise until the answer arrives before the derivation does. These are worth noticing.</p>
<ex>12 × 8 = 96<br>13 × 7 = 91<br>14 × 7 = 98<br>16 × 6 = 96<br>17 × 6 = 102<br>18 × 6 = 108<br>19 × 9 = 171<br>16 × 8 = 128 (power of 2)<br>16 × 4 = 64 (power of 2)</ex>
""")

    private val SQUARES_LESSON = Lesson("Squares 11–30", """
<p>Squares are the backbone of fast multiplication, and they give you square roots too.</p>
<h3>Products of close numbers</h3>
<p>Any product of two numbers close together becomes a subtraction: <b>a × b = m² − d²</b>, where m
is their midpoint and d is half their gap.</p>
<ex>23 × 27<br>m = 25, d = 2<br>25² − 2²<br>625 − 4 = <b>621</b></ex>
<ex>18 × 22<br>m = 20, d = 2<br>400 − 4 = <b>396</b></ex>
<h3>Square roots</h3>
<p>Two neighbouring squares bracket the root.</p>
<ex>√500<br>22² = 484<br>23² = 529<br>So √500 ≈ <b>22.4</b></ex>
<h3>Ends in 5</h3>
<p>Multiply the tens digit by the next integer, then append 25.</p>
<ex>25²<br>2 × 3 = 6<br>Append 25: <b>625</b></ex>
<h3>Teens</h3>
<p>Use (n + units) × 10 + units².</p>
<ex>17²<br>17 + 7 = 24<br>24 × 10 = 240<br>7² = 49<br>240 + 49 = <b>289</b></ex>
<h3>Round to a ten</h3>
<p>Use n² = (n − d)(n + d) + d², choosing d so one factor is a round ten.</p>
<ex>28², with d = 2<br>30 × 26 = 780<br>2² = 4<br>780 + 4 = <b>784</b></ex>
<tip>These three derivations are for falling back on while memorizing.</tip>
<h3>Worth knowing cold</h3>
<ex>16² = 256 (power of two)<br>32² = 1024 (power of two)<br>24² = 576<br>27² = 729 = 9³</ex>
<ex>29²<br>900 − 60 + 1 = <b>841</b><br>31²<br>900 + 60 + 1 = <b>961</b></ex>
""")

    private val CUBES_LESSON = Lesson("Cubes", """
<p>Cubes matter for volumes (a 12-inch cube is 1728 in³), for cube roots, and for recognising
numbers: 343, 729 or 1331 should make you feel the 7, 9 or 11 behind them.</p>
<h3>Square times base</h3>
<p>Each cube is the square times the base again.</p>
<ex>7³ = 7 × 49 = <b>343</b><br>12³ = 12 × 144 = <b>1728</b></ex>
<h3>Cube roots</h3>
<p>Bracket the number between two cubes.</p>
<ex>∛600<br>8³ = 512<br>9³ = 729<br>Between 8 and 9, roughly <b>8.4</b></ex>
<h3>Extra hooks</h3>
<ul>
<li>4³ = 64 and 8³ = 512 are powers of two (2⁶, 2⁹).</li>
<li>5³ = 125 = 1000/8, the reason 1/8 = 0.125.</li>
<li>9³ = 729 = 27² = 3⁶.</li>
<li>11³ = 1331 is row 3 of Pascal's triangle.</li>
<li>6³ = 216 = 3³ + 4³ + 5³.</li>
</ul>
<h3>Last digits</h3>
<p>Cubes of 1–9 end in 1, 8, 7, 4, 5, 6, 3, 2, 9. Every digit appears once, so the last digit of a
perfect cube tells you the last digit of its root.</p>
<ex>343 ends in 3, so its root ends in <b>7</b><br>512 ends in 2, so its root ends in <b>8</b></ex>
""")

    private val POW2_LESSON = Lesson("Powers of two", """
<p>Powers of two show up in doubling times and compound growth, computer memory, halving in binary
search, and the "rule of 70" problems later on.</p>
<h3>The key fact</h3>
<p><b>2¹⁰ = 1024 ≈ 10³</b> converts between doubling and powers of ten. It is also why
log<sub>10</sub> 2 ≈ 0.3: ten doublings ≈ three decades.</p>
<ex>2²⁰ ≈ 10⁶<br>2³⁰ ≈ 10⁹<br>2⁴⁰ ≈ 10¹²<br>2³⁴ = 2⁴ × 2³⁰ ≈ <b>16 × 10⁹</b></ex>
<h3>Up to 2¹⁰</h3>
<p>Just double.</p>
<ex>16, 32, 64, 128, 256, 512, <b>1024</b></ex>
<h3>Above 2¹⁰</h3>
<p>Split off 2¹⁰.</p>
<ex>2¹³ = 8 × 1024 = <b>8192</b><br>2¹⁶ = 64 × 1024 = <b>65 536</b></ex>
<tip>Rhythm helps. Say them as a chant: 1024, 2048, 4096, 8192, 16 384, 32 768, 65 536.</tip>
<h3>Negative powers</h3>
<p>These are just halvings of 1: 0.5, 0.25, 0.125, 0.0625, 0.03125. They also give you reciprocals.</p>
<ex>1/8 = 0.125<br>1/16 = 0.0625<br>1/32 = 0.03125</ex>
""")

    private val POWERS_LESSON = Lesson("Small-integer powers", """
<p>Powers of 3, 5, 6 and 7 come up in probability (6⁵ = 7776 dice outcomes), combinatorics and
compound growth. Recognising them lets you spot that 2401 is 7⁴ or 15 625 is 5⁶ = 125².</p>
<p>Build each from one you already know rather than memorizing in isolation.</p>
<h3>Square a square</h3>
<p>For an even exponent, square the power at half the exponent.</p>
<ex>3⁸ = 81² = <b>6561</b><br>6⁴ = 36² = <b>1296</b><br>7⁴ = 49² = <b>2401</b></ex>
<h3>One more factor</h3>
<p>Multiply the power below by the base.</p>
<ex>3⁷ = 3 × 729 = <b>2187</b><br>7⁵ = 7 × 2401 = <b>16 807</b></ex>
<h3>Powers of 5</h3>
<p>They are powers of 10 over powers of 2.</p>
<ex>5⁶ = 10⁶ / 64 = <b>15 625</b></ex>
<h3>Powers of 4 and 8</h3>
<p>They are powers of 2.</p>
<ex>4⁵ = 2¹⁰<br>8⁴ = 2¹²</ex>
<h3>Powers of 11</h3>
<p>They follow Pascal's triangle.</p>
<ex>121, 1331, 14 641</ex>
<h3>The 3-chain</h3>
<p>This one is worth chanting.</p>
<ex>9, 27, 81, 243, 729, 2187, 6561, 19 683, 59 049</ex>
<h3>Checking logarithms</h3>
<p>Powers also check logs.</p>
<ex>3¹⁰ = 59 049 ≈ 6 × 10⁴<br>10 × log 3 ≈ 4.77<br>So log 3 ≈ <b>0.477</b></ex>
""")

    private val RECIP_LESSON = Lesson("Reciprocals and common fractions", """
<p>Division is slow and multiplication is fast. Reciprocals turn one into the other: ÷ 8 is
× 0.125, ÷ 7 is × 0.1429, ÷ 16 is × 0.0625.</p>
<ex>350 ÷ 7 = 50 needs no trick<br>37 ÷ 7 ≈ 37 × 0.143 ≈ <b>5.29</b><br>91 ÷ 16 = 91 × 0.0625 ≈ <b>5.69</b></ex>
<p>Fractions also convert percentages and ratios in your head.</p>
<ex>5/8 = 0.625 = <b>62.5 %</b><br>3/7 ≈ <b>42.9 %</b></ex>
<tip>Type answers to 3 decimal places, or exactly for the ones that terminate.</tip>
<h3>Eighths and sixteenths</h3>
<p>These are multiples of 0.125 and 0.0625.</p>
<ex>3/8 = 3 × 0.125 = <b>0.375</b><br>5/16 = 5 × 0.0625 = <b>0.3125</b></ex>
<h3>Ninths and elevenths</h3>
<p>n/9 = 0.nnn…, and n/11 repeats 9n.</p>
<ex>4/9 = 0.444…<br>3/11 = 0.2727… (9 × 3 = 27)</ex>
<h3>Sevenths</h3>
<p>All use the cyclic digits 142857. Start at the right place by estimating.</p>
<ex>1/7 = 0.142857…<br>2/7 = 0.285714…<br>3/7 = 0.428571…</ex>
<tip>3/7 is a bit under 0.43, so start at the 4.</tip>
<h3>Halve to get the next</h3>
<p>Get 1/6 from 1/3, 1/12 from 1/6, 1/14 from 1/7.</p>
<ex>1/3 ≈ 0.333<br>1/6 ≈ 0.167<br>1/12 ≈ 0.083<br>1/7 ≈ 0.143<br>1/14 ≈ 0.071</ex>
<h3>Odd ones</h3>
<p>Go through a near multiple.</p>
<ex>13 × 77 = 1001, so 1/13 ≈ <b>0.077</b><br>17 × 6 = 102, so 1/17 ≈ <b>0.0588</b></ex>
""")

    private val LOG10_LESSON = Lesson("Common logarithms: the anchors", """
<p>With a handful of logs you can take the log of <i>any</i> number by factoring, and the antilog
of any exponent by reversing the table. That drives fast estimation of products, powers and roots:
multiply by adding logs.</p>
<h3>Four to know cold</h3>
<p><b>log 2 = 0.301, log 3 = 0.477, log 7 = 0.845</b>, and <b>log e = 0.434</b>. Everything else follows.</p>
<ex>log 4 = 2 × 0.301 = 0.602<br>log 8 = 3 × 0.301 = 0.903<br>log 5 = 1 − 0.301 = 0.699<br>log 6 = 0.301 + 0.477 = 0.778<br>log 9 = 2 × 0.477 = 0.954<br>log 1.5 = 0.477 − 0.301 = 0.176</ex>
<h3>Any number: factor it</h3>
<p>Pull out the power of ten, then factor what is left.</p>
<ex>log 4200 = 3 + log 4.2<br>log 4.2 = log 6 + log 7 − 1<br>3 + 0.778 + 0.845 − 1 = <b>3.623</b></ex>
<h3>Reverse cards</h3>
<p>These are the same facts read backwards.</p>
<ex>10<sup>0.3</sup> ≈ 2<br>10<sup>0.6</sup> ≈ 4<br>10<sup>0.7</sup> ≈ 5<br>10<sup>0.9</sup> ≈ 8<br>10<sup>0.5</sup> = √10 = 3.162<br>10<sup>0.1</sup> ≈ 1.26<br>So 10<sup>4.7</sup> ≈ <b>5 × 10⁴</b></ex>
<tip>Answer logs to 3 decimal places and antilogs to about 3 significant figures.</tip>
""")

    private val LN_LESSON = Lesson("Natural logarithms: the anchors", """
<p>Natural logs are the language of growth and decay: continuous interest, half-lives, doubling
times, entropy, anything with e<sup>x</sup>. Like common logs, a few anchors generate the rest by
factoring.</p>
<h3>Anchors</h3>
<p>Know cold: <b>ln 2 = 0.693, ln 3 = 1.099, ln 10 = 2.303</b>. Factor the rest.</p>
<ex>ln 5 = ln 10 − ln 2 = 1.609<br>ln 4 = 1.386<br>ln 8 = 2.079<br>ln 6 = 1.792<br>ln 9 = 2.197</ex>
<ex>ln 30 = ln 3 + ln 10<br>1.099 + 2.303 = 3.402<br>Rounded anchors land slightly high: ln 30 is <b>3.401</b></ex>
<h3>ln 7</h3>
<p>ln 7 ≈ 1.946. It is half of ln 49, a bit under half of ln 50.</p>
<ex>ln 7 = ½ × ln 49 ≈ <b>1.946</b></ex>
<h3>Small x</h3>
<p>For small x, <b>ln(1 + x) ≈ x − x²/2</b>.</p>
<ex>ln 1.1 ≈ 0.1 − 0.005 = <b>0.095</b><br>ln 1.05 ≈ 0.05 − 0.00125 ≈ <b>0.049</b></ex>
<h3>Converting bases</h3>
<p>One multiplier takes you each way.</p>
<ex>ln x = 2.303 × log x<br>log x = 0.434 × ln x</ex>
<h3>Doubling time</h3>
<p>A quantity growing 7 % a year doubles in about ln 2 / 0.07 years. This is the "rule of 70".</p>
<ex>0.693 ÷ 0.07 = 9.9<br>About <b>10</b> years</ex>
<tip>Answer to 3 decimal places.</tip>
""")

    private val EXP_LESSON = Lesson("Powers of e", """
<p>Exponentials are logs read backwards. They appear directly in compound growth, radioactive decay,
probability (the Poisson e<sup>−λ</sup>) and physics.</p>
<h3>Anchors</h3>
<p>The last two are the natural-log anchors reversed.</p>
<ex><b>e = 2.718</b><br>e² = 7.389<br>e³ ≈ 20.09<br>e⁵ ≈ 148<br>e<sup>0.693</sup> = 2<br>e<sup>2.303</sup> = 10</ex>
<h3>Negatives</h3>
<p>Negative powers are reciprocals.</p>
<ex>e<sup>−1</sup> = 0.368<br>e<sup>−2</sup> = 0.135<br>e<sup>−3</sup> ≈ 0.05</ex>
<h3>Any x: split it</h3>
<p>Split x into a piece you know plus a small remainder.</p>
<ex>e<sup>3.7</sup> = e<sup>3</sup> × e<sup>0.7</sup><br>0.7 ≈ ln 2 + 0.007, so e<sup>0.7</sup> ≈ 2.01<br>20.09 × 2.01 ≈ <b>40.4</b></ex>
<h3>Small x</h3>
<p>For small x, <b>e<sup>x</sup> ≈ 1 + x + x²/2</b>.</p>
<ex>e<sup>0.1</sup><br>1 + 0.1 + 0.005 = <b>1.105</b></ex>
<h3>Think in decades</h3>
<p>Convert to base 10: e<sup>x</sup> = 10<sup>0.434x</sup>.</p>
<ex>e<sup>10</sup> = 10<sup>4.34</sup> ≈ <b>2.2 × 10⁴</b></ex>
<tip>Answer to about 3 significant figures (within 1 %).</tip>
""")

    private val CONSTS_LESSON = Lesson("Constants and roots", """
<p>These numbers recur in geometry, physics, statistics and every trig value: sin 60° is √3/2, a
circle's area is πr², a normal curve's peak is 1/√(2π). At four significant figures, your estimates
are never limited by a fuzzy constant.</p>
<h3>Square roots</h3>
<p>Learn <b>√2 = 1.414, √3 = 1.732, √5 = 2.236</b>. Build others from them.</p>
<ex>√6 = √2√3 = 2.449<br>√8 = 2√2 = 2.828<br>√10 = 3.162</ex>
<h3>π</h3>
<p>Learn <b>π = 3.1416</b>, then its relatives.</p>
<ex>2π = 6.283<br>π/2 = 1.571<br>π² = 9.870<br>1/π = 0.318<br>√π = 1.772</ex>
<h3>e and φ</h3>
<p><b>e = 2.71828</b>, and 1/e = 0.368. <b>φ = 1.618</b> = (1 + √5)/2, and 1/φ = φ − 1.</p>
<ex>φ = (1 + √5)/2<br>1 + 2.236 = 3.236<br>3.236 ÷ 2 = <b>1.618</b><br>1/φ = 1.618 − 1 = <b>0.618</b></ex>
<h3>Cube roots</h3>
<ex>∛2 = 1.26<br>∛10 = 2.154</ex>
<h3>Memorize by checking</h3>
<p>Square your answer, or derive from a neighbour.</p>
<ex>1.732² ≈ 2.9998<br>1/√2 = √2/2<br>1.414 ÷ 2 = <b>0.707</b></ex>
<tip>Use the mnemonics in the card notes for π and e. Answer to 4 significant figures.</tip>
""")

    private val TRIG_LESSON = Lesson("Special angles and radians", """
<p>Every trig estimate starts from a nearby known angle. These values are your grid.</p>
<ex>sin 37° is a bit more than sin 30° = 0.5<br>tan 70° is less than tan 75° = 3.73</ex>
<p>All of them come from two triangles.</p>
<h3>45-45-90 triangle</h3>
<p>Sides 1, 1, √2.</p>
<ex>sin 45° = cos 45° = √2/2 = <b>0.7071</b></ex>
<h3>30-60-90 triangle</h3>
<p>Half an equilateral, with sides 1, √3, 2.</p>
<ex>sin 30° = ½<br>sin 60° = √3/2 = 0.866<br>tan 30° = 1/√3 = 0.577<br>tan 60° = √3</ex>
<h3>Complements</h3>
<p>Cosine is sine of the complement: cos θ = sin(90° − θ).</p>
<ex>cos 60° = sin 30° = <b>½</b></ex>
<h3>15° and 75°</h3>
<ex>sin 15° = (√6 − √2)/4 = 0.2588<br>cos 15° = 0.9659<br>tan 15° = 2 − √3 = 0.268<br>tan 75° = 2 + √3 = 3.732</ex>
<h3>18° and 36°</h3>
<p>These involve the golden ratio.</p>
<ex>sin 18° = 0.309<br>cos 36° = φ/2 = 0.809</ex>
<h3>Radians</h3>
<p>1 rad = 57.30° and 1° = 0.01745 rad. Multiply degrees by π/180.</p>
<ex>30° = 0.524<br>45° = 0.785<br>60° = 1.047<br>90° = 1.571</ex>
<h3>Small angles</h3>
<p>sin x ≈ tan x ≈ x, in radians.</p>
<ex>sin 3°<br>3 × 0.01745 ≈ <b>0.052</b></ex>
""")


    // ---------------------------------------------------------------- export

    override val DECKS: List<Deck> = listOf(
        Deck("d_times", "Times tables", "arithmetic", TIMES_LESSON, TIMES_CARDS, 2.0),
        Deck("d_teens", "Teens × one digit", "arithmetic", TEENS_LESSON, TEENS_CARDS, 3.0),
        Deck("d_squares", "Squares 11–30", "arithmetic", SQUARES_LESSON, SQUARES_CARDS, 2.5),
        Deck("d_cubes", "Cubes", "arithmetic", CUBES_LESSON, CUBES_CARDS, 3.0),
        Deck("d_pow2", "Powers of two", "logs", POW2_LESSON, POW2_CARDS, 3.0),
        Deck("d_powers", "Small-integer powers", "logs", POWERS_LESSON, POWERS_CARDS, 3.0),
        Deck("d_recip", "Reciprocals & fractions", "fractions", RECIP_LESSON, RECIP_CARDS, 3.0),
        Deck("d_log10", "Common logs", "logs", LOG10_LESSON, LOG10_CARDS, 3.0),
        Deck("d_ln", "Natural logs", "logs", LN_LESSON, LN_CARDS, 3.0),
        Deck("d_exp", "Powers of e", "logs", EXP_LESSON, EXP_CARDS, 3.0),
        Deck("d_consts", "Constants & roots", "trig", CONSTS_LESSON, CONSTS_CARDS, 3.0),
        Deck("d_trig", "Special angles", "trig", TRIG_LESSON, TRIG_CARDS, 3.0),
    )
}
