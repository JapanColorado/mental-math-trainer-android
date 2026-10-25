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
 *
 * Port of mmtrainer/content/facts.py.
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

    private fun intCard(cid: String, prompt: String, value: Long, note: String) =
        Card(cid, prompt, value.toDouble(), intStr(value), note = note)

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
        TIMES_FACTORS.drop(i).map { b -> intCard("times:${a}x$b", "$a × $b", (a * b).toLong(), timesNote(a, b)) }
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
        (2 until 10).map { m -> intCard("teens:${n}x$m", "$n × $m", (n * m).toLong(), teensNote(n, m)) }
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
<p>Every multi-digit product you will ever do mentally is a stack of single-digit products.
If 7 × 8 costs you a second of hesitation, 47 × 68 costs you four of them plus the strain of
holding intermediate results. The goal here is <b>instant recall</b> — under a second, no
counting up, no rederiving.</p>
<p>There are only 55 facts once you notice a × b = b × a and drop the trivial ×1 and ×10.
Most of them collapse to a few patterns:</p>
<ul>
<li><b>×2, ×4, ×8</b>: double once, twice, three times.</li>
<li><b>×5</b>: half of ×10. <b>×9</b>: ×10 minus one copy (the digits sum to 9).</li>
<li><b>×11</b>: repeat the digit. <b>×12</b>: ×10 plus ×2.</li>
<li><b>Neighbours of squares</b>: (n−1)(n+1) = n² − 1, so 6 × 8 = 49 − 1 = 48.</li>
</ul>
<p>The genuinely hard ones are the "middle" facts: 6 × 7 = 42, 6 × 8 = 48, 7 × 7 = 49,
7 × 8 = 56 (5-6-7-8), 7 × 9 = 63, 8 × 8 = 64. Use the derivation when you miss, but the aim is
to stop needing it: say the fact aloud, then retype it from memory.</p>
""")

    private val TEENS_LESSON = Lesson("Teens × one digit", """
<p>Products like 17 × 6 = 102 or 14 × 8 = 112 turn up constantly: in percentages, in unit
conversions, inside every two-digit multiplication. They are just past the times table, so it
pays to know them by heart rather than compute them each time.</p>
<p>The derivation is always the same: <b>split off the ten</b>.
17 × 6 = 10 × 6 + 7 × 6 = 60 + 42 = 102. Some are faster another way:</p>
<ul>
<li><b>19 × m</b> = 20 × m − m: 19 × 7 = 140 − 7 = 133.</li>
<li><b>15 × m</b> = 10m plus half of 10m: 15 × 8 = 80 + 40 = 120.</li>
<li><b>18 × m</b> can also be 20m − 2m: 18 × 7 = 140 − 14 = 126.</li>
</ul>
<p>Practise until the answer arrives before the derivation does. The landmarks worth noticing:
12 × 8 = 96, 13 × 7 = 91, 14 × 7 = 98, 16 × 6 = 96, 17 × 6 = 102, 18 × 6 = 108,
19 × 9 = 171, and 16 × 8 = 128 and 16 × 4 = 64 (powers of 2).</p>
""")

    private val SQUARES_LESSON = Lesson("Squares 11–30", """
<p>Squares are the backbone of fast multiplication. With them, any product of two numbers that
are close together becomes a subtraction: <b>a × b = m² − d²</b>, where m is their midpoint
and d half their gap. So 23 × 27 = 25² − 2² = 625 − 4 = 621, and 18 × 22 = 400 − 4 = 396.
They also give you square roots for free: √500 ≈ 22.4 because 22² = 484 and 23² = 529.</p>
<p>Three derivations to fall back on while memorizing:</p>
<ul>
<li><b>Ends in 5</b>: multiply the tens digit by the next integer, append 25.
25² = 2 × 3 | 25 = 625.</li>
<li><b>Teens</b>: (n + units) × 10 + units². 17² = 240 + 49 = 289.</li>
<li><b>Round to a ten</b>: n² = (n − d)(n + d) + d². 28² = 30 × 26 + 4 = 784.</li>
</ul>
<p>Worth knowing cold: 16² = 256 and 32² = 1024 (powers of two), 24² = 576, 27² = 729 = 9³,
and 29² = 900 − 60 + 1 = 841 (so 31² = 900 + 60 + 1 = 961).</p>
""")

    private val CUBES_LESSON = Lesson("Cubes", """
<p>Cubes matter for volumes (a 12-inch cube is 1728 in³), for cube roots, and for
recognising numbers: if you see 343 or 729 or 1331 you should feel the 7, 9 or 11 behind it.
Knowing them also makes cube roots fast: ∛600 is between 8 (512) and 9 (729), roughly 8.4.</p>
<p>Each cube is the square times the base again: 7³ = 7 × 49 = 343, 12³ = 12 × 144 = 1728.
A few have extra hooks:</p>
<ul>
<li>4³ = 64 and 8³ = 512 are powers of two (2⁶, 2⁹).</li>
<li>5³ = 125 = 1000/8, the reason 1/8 = 0.125.</li>
<li>9³ = 729 = 27² = 3⁶.</li>
<li>11³ = 1331 is row 3 of Pascal's triangle.</li>
<li>6³ = 216 = 3³ + 4³ + 5³.</li>
</ul>
<p>Also notice the last digits: cubes of 1–9 end in 1, 8, 7, 4, 5, 6, 3, 2, 9 — every digit
appears once, so the last digit of a perfect cube tells you the last digit of its root.</p>
""")

    private val POW2_LESSON = Lesson("Powers of two", """
<p>The single most useful fact here is <b>2¹⁰ = 1024 ≈ 10³</b>. It converts between doubling
and powers of ten: 2²⁰ ≈ 10⁶, 2³⁰ ≈ 10⁹, 2⁴⁰ ≈ 10¹². So 2³⁴ = 2⁴ × 2³⁰ ≈ 16 × 10⁹. It is
also why log<sub>10</sub> 2 ≈ 0.3 (ten doublings ≈ three decades).</p>
<p>Powers of two show up everywhere: doubling times and compound growth, computer memory,
halving in binary search, and the "rule of 70" problems later on. They also give you
reciprocals: 1/8 = 0.125, 1/16 = 0.0625, 1/32 = 0.03125.</p>
<p>How to learn them:</p>
<ul>
<li>Up to 2¹⁰ = 1024, just double: 16, 32, 64, 128, 256, 512, 1024.</li>
<li>Above that, split off 2¹⁰: 2¹³ = 8 × 1024 = 8192; 2¹⁶ = 64 × 1024 = 65 536.</li>
<li>Rhythm helps: 1024, 2048, 4096, 8192, 16 384, 32 768, 65 536 — say them as a chant.</li>
</ul>
<p>Negative powers are just halvings of 1: 0.5, 0.25, 0.125, 0.0625, 0.03125.</p>
""")

    private val POWERS_LESSON = Lesson("Small-integer powers", """
<p>Powers of 3, 5, 6 and 7 come up in probability (6⁵ = 7776 dice outcomes), combinatorics,
compound growth, and in checking logarithms: 3¹⁰ = 59 049 ≈ 6 × 10⁴ tells you
10 × log 3 ≈ 4.77, i.e. log 3 ≈ 0.477. Recognising them also lets you spot that 2401 is 7⁴
or 15 625 is 5⁶ = 125².</p>
<p>Build each from one you already know rather than memorizing in isolation:</p>
<ul>
<li><b>Square a square</b>: 3⁸ = 81² = 6561, 6⁴ = 36² = 1296, 7⁴ = 49² = 2401.</li>
<li><b>One more factor</b>: 3⁷ = 3 × 729 = 2187, 7⁵ = 7 × 2401 = 16 807.</li>
<li><b>Powers of 5 are powers of 10 over powers of 2</b>: 5⁶ = 10⁶ / 64 = 15 625.</li>
<li><b>Powers of 4 and 8 are powers of 2</b>: 4⁵ = 2¹⁰, 8⁴ = 2¹².</li>
<li><b>Powers of 11</b> follow Pascal's triangle: 121, 1331, 14 641.</li>
</ul>
<p>The 3-chain is worth chanting: 9, 27, 81, 243, 729, 2187, 6561, 19 683, 59 049.</p>
""")

    private val RECIP_LESSON = Lesson("Reciprocals and common fractions", """
<p>Division is slow; multiplication is fast. Knowing reciprocals lets you turn one into the
other: ÷ 8 is × 0.125, ÷ 7 is × 0.1429, ÷ 16 is × 0.0625. So 350 ÷ 7 is easy anyway, but
37 ÷ 7 ≈ 37 × 0.143 ≈ 5.29, and 91 ÷ 16 = 91 × 0.0625 ≈ 5.69. Fractions are also how you
convert percentages and ratios in your head: 5/8 = 62.5 %, 3/7 ≈ 42.9 %.</p>
<p>Type answers to <b>3 decimal places</b> (or exactly, for the ones that terminate).</p>
<ul>
<li><b>Eighths and sixteenths</b> are multiples of 0.125 and 0.0625.</li>
<li><b>Ninths</b>: n/9 = 0.nnn…. <b>Elevenths</b>: n/11 repeats 9n (3/11 = 0.2727…).</li>
<li><b>Sevenths</b> all use the cyclic digits 142857: 1/7 = 0.142857…, 2/7 = 0.285714…,
3/7 = 0.428571…. Start at the right place by estimating (3/7 is a bit under 0.43).</li>
<li><b>Halve to get the next</b>: 1/6 from 1/3, 1/12 from 1/6, 1/14 from 1/7.</li>
<li><b>Odd ones</b> via a near multiple: 13 × 77 = 1001 so 1/13 ≈ 0.077; 17 × 6 = 102 so
1/17 ≈ 0.0588.</li>
</ul>
""")

    private val LOG10_LESSON = Lesson("Common logarithms: the anchors", """
<p>With a handful of logs you can take the log of <i>any</i> number by factoring, and the
antilog of any exponent by reversing the table. That is the engine behind fast estimation of
products, powers and roots: multiply by adding logs.</p>
<p>The four to know cold: <b>log 2 = 0.301, log 3 = 0.477, log 7 = 0.845</b>, and
<b>log e = 0.434</b>. Everything else follows:</p>
<ul>
<li>log 4 = 2 × 0.301 = 0.602, log 8 = 0.903, log 5 = 1 − 0.301 = 0.699.</li>
<li>log 6 = 0.301 + 0.477 = 0.778, log 9 = 0.954, log 1.5 = 0.477 − 0.301 = 0.176.</li>
</ul>
<p>Example: log 4200 = 3 + log 4.2 = 3 + log 6 + log 7 − 1 = 3 + 0.778 + 0.845 − 1 = 3.623.</p>
<p>The reverse cards are the same facts read backwards: 10<sup>0.3</sup> ≈ 2,
10<sup>0.6</sup> ≈ 4, 10<sup>0.7</sup> ≈ 5, 10<sup>0.9</sup> ≈ 8, plus 10<sup>0.5</sup> =
√10 = 3.162 and 10<sup>0.1</sup> ≈ 1.26. So 10<sup>4.7</sup> ≈ 5 × 10⁴ instantly.</p>
<p>Answer logs to 3 decimal places and antilogs to about 3 significant figures.</p>
""")

    private val LN_LESSON = Lesson("Natural logarithms: the anchors", """
<p>Natural logs are the language of growth and decay: continuous interest, half-lives,
doubling times, entropy, anything with e<sup>x</sup>. Like common logs, a few anchors
generate the rest by factoring.</p>
<p>Know cold: <b>ln 2 = 0.693, ln 3 = 1.099, ln 10 = 2.303</b>. Then:</p>
<ul>
<li>ln 5 = ln 10 − ln 2 = 1.609; ln 4 = 1.386; ln 8 = 2.079; ln 6 = 1.792; ln 9 = 2.197.</li>
<li>ln 7 ≈ 1.946 (half of ln 49, a bit under half of ln 50).</li>
<li>Small x: <b>ln(1 + x) ≈ x − x²/2</b>, so ln 1.1 ≈ 0.095, ln 1.05 ≈ 0.049.</li>
<li>Conversions: ln x = 2.303 × log x, and log x = 0.434 × ln x.</li>
</ul>
<p>Example: a quantity growing 7 % a year doubles in about ln 2 / 0.07 ≈ 0.693 / 0.07 ≈ 10
years — the "rule of 70". And ln 30 = ln 3 + ln 10 = 1.099 + 2.303 = 3.401.</p>
<p>Answer to 3 decimal places.</p>
""")

    private val EXP_LESSON = Lesson("Powers of e", """
<p>Exponentials are logs read backwards, and they appear directly in compound growth,
radioactive decay, probability (the Poisson e<sup>−λ</sup>), and physics. These anchors let
you evaluate e<sup>x</sup> for any x: split x into a piece you know plus a small remainder.</p>
<ul>
<li><b>e = 2.718, e² = 7.389, e³ ≈ 20.09, e⁵ ≈ 148</b>.</li>
<li><b>e<sup>0.693</sup> = 2, e<sup>2.303</sup> = 10</b> (the natural-log anchors reversed).</li>
<li>Negatives are reciprocals: e<sup>−1</sup> = 0.368, e<sup>−2</sup> = 0.135,
e<sup>−3</sup> ≈ 0.05.</li>
<li>Small x: <b>e<sup>x</sup> ≈ 1 + x + x²/2</b>, so e<sup>0.1</sup> ≈ 1.105.</li>
</ul>
<p>Example: e<sup>3.7</sup> = e<sup>3</sup> × e<sup>0.7</sup> ≈ 20.09 × 2.01 ≈ 40.4
(0.7 ≈ ln 2 + 0.007). Or think in decades: e<sup>x</sup> = 10<sup>0.434x</sup>, so
e<sup>10</sup> = 10<sup>4.34</sup> ≈ 2.2 × 10⁴.</p>
<p>Answer to about 3 significant figures (within 1 %).</p>
""")

    private val CONSTS_LESSON = Lesson("Constants and roots", """
<p>These numbers recur in geometry, physics and statistics, and in every trig value: sin 60°
is √3/2, a circle's area is πr², a normal curve's peak is 1/√(2π). Knowing them to four
significant figures means your estimates are never limited by a fuzzy constant.</p>
<ul>
<li><b>√2 = 1.414, √3 = 1.732, √5 = 2.236</b>. Then √6 = √2√3 = 2.449, √8 = 2√2 = 2.828,
√10 = 3.162.</li>
<li><b>π = 3.1416</b>, so 2π = 6.283, π/2 = 1.571, π² = 9.870, 1/π = 0.318, √π = 1.772.</li>
<li><b>e = 2.71828</b>, 1/e = 0.368. <b>φ = 1.618</b> = (1 + √5)/2, and 1/φ = φ − 1.</li>
<li>Cube roots: ∛2 = 1.26, ∛10 = 2.154.</li>
</ul>
<p>Memorize by checking: square your answer (1.732² = 2.9998), or derive from a neighbour
(1/√2 = √2/2). Use the mnemonics in the card notes for π and e.</p>
<p>Answer to 4 significant figures.</p>
""")

    private val TRIG_LESSON = Lesson("Special angles and radians", """
<p>Every trig estimate starts from a nearby known angle: sin 37° is "a bit more than
sin 30° = 0.5", tan 70° is "a bit less than tan 75° = 3.73". These values are your grid.</p>
<p>All of them come from two triangles. The <b>45-45-90</b> triangle (sides 1, 1, √2) gives
sin 45° = cos 45° = √2/2 = 0.7071. The <b>30-60-90</b> triangle (half an equilateral, sides
1, √3, 2) gives sin 30° = ½, sin 60° = √3/2 = 0.866, tan 30° = 1/√3 = 0.577, tan 60° = √3.
Cosine is sine of the complement: cos θ = sin(90° − θ).</p>
<ul>
<li><b>15° and 75°</b>: sin 15° = (√6 − √2)/4 = 0.2588, cos 15° = 0.9659,
tan 15° = 2 − √3 = 0.268, tan 75° = 2 + √3 = 3.732.</li>
<li><b>18° and 36°</b> involve the golden ratio: sin 18° = 0.309, cos 36° = φ/2 = 0.809.</li>
</ul>
<p><b>Radians</b>: 1 rad = 57.30°, 1° = 0.01745 rad. Multiply degrees by π/180:
30° = 0.524, 45° = 0.785, 60° = 1.047, 90° = 1.571. For small angles sin x ≈ tan x ≈ x
(in radians): sin 3° ≈ 3 × 0.01745 = 0.052.</p>
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
