package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.ipow
import dev.russell.mmtrainer.py.pyRound

/**
 * Arithmetic track: addition, subtraction, multiplication, squaring, division.
 *
 * Every level's generator picks operands and hands them to a pure `*Problem` builder,
 * so explanations (and tests) depend only on the numbers, not on how they were drawn.
 */
object Arithmetic : ContentModule {
    const val TRACK = "arithmetic"

    // ------------------------------------------------------------------ helpers

    /** Format a number (int, float or Fraction) for display. */
    private fun N(x: Int): String = fmtNum(x)
    private fun N(x: Double): String = fmtNum(x)
    private fun N(x: Fraction): String = fmtNum(x.toDouble())

    /** Distance from n up to the next multiple of `unit` (0 if n already is one). */
    private fun roundUpGap(n: Int, unit: Int): Int = Math.floorMod(-n, unit)

    /** Random integer in [lo, hi] that isn't a multiple of 10. */
    private fun nonround(rng: Rng, lo: Int, hi: Int): Int {
        while (true) {
            val n = rng.randint(lo, hi)
            if (n % 10 != 0) return n
        }
    }

    /** Split n into place-value chunks, largest first, skipping zeros. */
    private fun placeChunks(n: Int, splitLastTwo: Boolean = false): List<Int> {
        val s = n.toString()
        val chunks = mutableListOf<Int>()
        for ((i, d) in s.withIndex()) {
            val place = s.length - 1 - i
            if (splitLastTwo && place == 1) {
                chunks.add(n % 100)
                break
            }
            chunks.add(d.digitToInt() * ipow(10, place).toInt())
        }
        return chunks.filter { it != 0 }
    }

    /** '586 + 200 = 786 → + 40 = 826 → + 7 = 833' (or with −). */
    private fun running(start: Int, steps: List<Int>, op: String): String {
        val parts = mutableListOf<String>()
        var total = start
        for ((i, s) in steps.withIndex()) {
            total = if (op == "+") total + s else total - s
            val lead = if (i == 0) "${N(start)} $op ${N(s)}" else "$op ${N(s)}"
            parts.add("$lead = ${N(total)}")
        }
        return parts.joinToString(" → ")
    }

    // ================================================================== addition

    fun addProblem(nums: List<Int>): Problem {
        val total = nums.sum()
        val prompt = nums.joinToString(" + ") { N(it) }
        val expl: String
        if (nums.size == 3) {
            val h = nums.sumOf { it / 100 }
            val t = nums.sumOf { it / 10 % 10 }
            val u = nums.sumOf { it % 10 }
            val hd = nums.joinToString(" + ") { (it / 100).toString() }
            val td = nums.joinToString(" + ") { (it / 10 % 10).toString() }
            val ud = nums.joinToString(" + ") { (it % 10).toString() }
            expl = "Column by column, left to right: hundreds $hd = $h → ${N(h * 100)}; " +
                "tens $td = $t → ${N(t * 10)}; units $ud = $u" +
                "<br>→ ${N(h * 100)} + ${N(t * 10)} + $u = ${N(total)}"
        } else {
            val (a, b) = nums
            val size = maxOf(a, b).toString().length
            val (unit, maxGap) = if (size <= 2) 10 to 2 else 100 to 4
            // near-round compensation: round the addend that is just below a round number
            val cands = mutableListOf<Triple<Int, Int, Int>>()
            for ((x, y) in listOf(a to b, b to a)) {
                val g = roundUpGap(y, unit)
                if (g in 1..maxGap) cands.add(Triple(g, x, y))
            }
            if (cands.isNotEmpty()) {
                val (g, x, y) = cands.minWithOrNull(
                    compareBy<Triple<Int, Int, Int>>({ it.first }, { it.second }, { it.third }))!!
                val r = y + g
                expl = "${N(y)} is just under ${N(r)}: add ${N(r)}, then take back $g" +
                    "<br>${N(x)} + ${N(r)} − $g = ${N(x + r)} − $g = ${N(total)}"
            } else {
                val steps = placeChunks(b, splitLastTwo = size >= 4)
                expl = "Left to right, adding ${N(b)} a piece at a time:<br>${running(a, steps, "+")}"
            }
        }
        return Problem(prompt, total.toDouble(), N(total), explanation = expl)
    }

    private fun addPair(rng: Rng, lo: Int, hi: Int, nearRoundUnit: Int): Problem {
        val a = rng.randint(lo, hi)
        val b = if (rng.random() < 0.2) {  // make the compensation trick show up regularly
            rng.randint(lo / nearRoundUnit + 1, hi / nearRoundUnit) * nearRoundUnit -
                rng.randint(1, if (nearRoundUnit == 10) 2 else 4)
        } else {
            rng.randint(lo, hi)
        }
        return addProblem(listOf(a, b))
    }

    private fun genAdd1(rng: Rng): Problem {
        while (true) {
            val a = rng.randint(12, 89)
            val b = rng.randint(12, 89)
            val carry = a % 10 + b % 10 >= 10
            if (a % 10 != 0 && b % 10 != 0 && (carry || rng.random() < 0.2)) return addProblem(listOf(a, b))
        }
    }

    private fun genAdd2(rng: Rng) = addPair(rng, 101, 999, 100)
    private fun genAdd3(rng: Rng) = addPair(rng, 1001, 9999, 100)
    private fun genAdd4(rng: Rng) = addProblem(List(3) { nonround(rng, 101, 999) })

    // =============================================================== subtraction

    fun subProblem(a: Int, b: Int): Problem {
        val diff = a - b
        val prompt = "${N(a)} − ${N(b)}"
        val size = b.toString().length
        if ((a == 1000 || a == 10000) && b % 10 != 0 && size == a.toString().length - 1) {
            val digits = b.toString()
            val steps = digits.dropLast(1).map { d -> "9−$d = ${9 - d.digitToInt()}" }.toMutableList()
            steps.add("10−${digits.last()} = ${10 - digits.last().digitToInt()}")
            val expl = "Complement: every digit from 9, the last from 10<br>" +
                "${steps.joinToString(", ")} → <b>${N(diff)}</b>"
            return Problem(prompt, diff.toDouble(), N(diff), explanation = expl)
        }

        val (unit, maxGap) = if (size <= 2) 10 to 3 else 100 to 4
        val gap = roundUpGap(b, unit)
        if (gap in 1..maxGap) {
            val r = b + gap
            val expl = "${N(b)} is just under ${N(r)}: subtract ${N(r)}, then give back $gap" +
                "<br>${N(a)} − ${N(r)} + $gap = ${N(a - r)} + $gap = ${N(diff)}"
            return Problem(prompt, diff.toDouble(), N(diff), explanation = expl)
        }

        val upUnit = if (size == 3) 100 else 1000
        val nxt = b + roundUpGap(b, upUnit)
        val close = size == 4 || (size == 3 && a / 100 - b / 100 <= 2)
        val expl: String
        if (size >= 3 && close && b < nxt && nxt <= a) {
            val first = nxt - b
            val second = a - nxt
            val tail = if (second != 0) " → +${N(second)} → ${N(a)}" else ""
            expl = "Count up from ${N(b)}: +${N(first)} → ${N(nxt)}$tail" +
                "<br>difference = ${N(first)} + ${N(second)} = ${N(diff)}"
        } else {
            val steps = placeChunks(b, splitLastTwo = size >= 4)
            expl = "Left to right, taking ${N(b)} off a piece at a time:<br>${running(a, steps, "−")}"
        }
        return Problem(prompt, diff.toDouble(), N(diff), explanation = expl)
    }

    private fun genSub1(rng: Rng): Problem {
        while (true) {
            val a = rng.randint(31, 99)
            val b = rng.randint(12, 89)
            if (a - b >= 11 && a % 10 < b % 10) return subProblem(a, b)  // forces a borrow
        }
    }

    private fun genSub2(rng: Rng): Problem {
        if (rng.random() < 0.5) return subProblem(1000, nonround(rng, 101, 999))
        return subProblem(10000, nonround(rng, 1001, 9999))
    }

    private fun genSubN(rng: Rng, lo: Int, hi: Int): Problem {
        while (true) {
            var a = rng.randint(lo, hi)
            var b = rng.randint(lo, hi)
            if (rng.random() < 0.15) {  // near-round subtrahend
                b = rng.randint(lo / 100 + 1, hi / 100) * 100 - rng.randint(1, 4)
            }
            val mx = maxOf(a, b)
            val mn = minOf(a, b)
            a = mx
            b = mn
            val borrow = a.toString().drop(1).zip(b.toString().drop(1)).any { (x, y) -> x.digitToInt() < y.digitToInt() }
            if (a - b >= 50 && b % 10 != 0 && borrow) return subProblem(a, b)
        }
    }

    private fun genSub3(rng: Rng) = genSubN(rng, 101, 999)
    private fun genSub4(rng: Rng) = genSubN(rng, 1001, 9999)

    // ==================================================== multiply by one digit

    /** Mental working for an integer m times a single digit d (ends with '= m·d'). */
    private fun intTimesDigit(m: Int, d: Int): String {
        val p = m * d
        val size = m.toString().length
        val (unit, maxGap) = if (size <= 2) 10 to 2 else 100 to 3
        val gap = roundUpGap(m, unit)
        if (gap in 1..maxGap) {
            val r = m + gap
            return "${N(m)} = ${N(r)} − $gap: ${N(r)} × $d − $gap × $d = " +
                "${N(r * d)} − ${gap * d} = ${N(p)}"
        }
        val chunks = placeChunks(m, splitLastTwo = size >= 4)
        if (chunks.size == 1) return "${N(m)} × $d = ${N(p)}"
        if (size == 2) {
            val (t, u) = chunks
            return "${N(m)} × $d = $t×$d + $u×$d = ${t * d} + ${u * d} = ${N(p)}"
        }
        val parts = mutableListOf<String>()
        var total = 0
        for (c in chunks) {
            total += c * d
            parts.add("${N(c)}×$d = ${N(c * d)}" + (if (total != c * d) " → ${N(total)}" else ""))
        }
        return "${N(m)} × $d: " + parts.joinToString("; ")
    }

    /** (m / 10^decimals) × d. */
    fun mul1Problem(m: Int, d: Int, decimals: Int = 0): Problem {
        val x = Fraction.of(m.toLong(), ipow(10, decimals))
        val ans = x * d.toLong()
        val prompt = "${N(x)} × $d"
        val expl: String
        if (decimals == 0) {
            expl = intTimesDigit(m, d)
        } else {
            val est = sigRound(x.toDouble(), 1) * d
            val places = if (decimals == 1) "1 decimal place" else "$decimals decimal places"
            expl = "Ignore the decimal point: ${intTimesDigit(m, d)}<br>" +
                "${N(x)} has $places → <b>${N(ans)}</b> (check: ≈ ${N(sigRound(x.toDouble(), 1))}" +
                " × $d = ${N(sigRound(est, 3))})"
        }
        return Problem(prompt, ans.toDouble(), N(ans), explanation = expl)
    }

    private fun genMul1N(rng: Rng, lo: Int, hi: Int): Problem {
        val m = if (rng.random() < 0.2) {  // near-round multiplicand
            val unit = if (hi < 100) 10 else 100
            rng.randint(lo / unit + 1, hi / unit) * unit - rng.randint(1, 2)
        } else {
            nonround(rng, lo, hi)
        }
        return mul1Problem(m, rng.randint(3, 9))
    }

    private fun genMul11(rng: Rng) = genMul1N(rng, 12, 99)
    private fun genMul12(rng: Rng) = genMul1N(rng, 101, 999)
    private fun genMul13(rng: Rng) = genMul1N(rng, 1001, 9999)

    private fun genMul14(rng: Rng): Problem {
        val m = nonround(rng, 12, 99)
        val decimals = rng.choices(listOf(1, 2, 3), weights = listOf(5.0, 4.0, 1.0))
        return mul1Problem(m, rng.randint(3, 9), decimals)
    }

    // =================================================== multiply 2-digit numbers

    private data class NearCand(val gap: Int, val tens: Boolean, val x: Int, val y: Int, val r: Int)

    /** Pick the best trick for a × b and describe it (always ends with the product). */
    private fun mul2Explain(a: Int, b: Int): String {
        val p = a * b
        val lo = minOf(a, b)
        val hi = maxOf(a, b)

        // × 11: add the digits and put the sum in the middle
        if (lo == 11 && hi in 10..99) {
            val x = hi / 10
            val y = hi % 10
            val s = x + y
            if (s < 10) {
                return "× 11: spread ${x}_$y apart and put $x+$y = $s in the middle → <b>$p</b>"
            }
            return "× 11: spread ${x}_$y apart and put $x+$y = $s in the middle; " +
                "carry the 1 → ${x + 1}|${s - 10}|$y = <b>${N(p)}</b>"
        }

        // teen × teen: (a + units of b) × 10 + units × units
        if (11 <= lo && hi <= 19) {
            val u1 = a % 10
            val u2 = b % 10
            return "Teens: $a + $u2 = ${a + u2} → ${(a + u2) * 10}; $u1 × $u2 = ${u1 * u2}" +
                "<br>${(a + u2) * 10} + ${u1 * u2} = <b>$p</b>"
        }

        // both just under 100
        if (88 <= lo && hi <= 99) {
            val da = 100 - a
            val db = 100 - b
            return "Near 100: $a is $da short, $b is $db short. $a − $db = ${a - db} → " +
                "${N((a - db) * 100)}; $da × $db = ${da * db}<br>" +
                "${N((a - db) * 100)} + ${da * db} = <b>${N(p)}</b>"
        }

        // difference of squares: symmetric around a round number
        if (lo != hi && (lo + hi) % 2 == 0) {
            val m = (lo + hi) / 2
            val d = (hi - lo) / 2
            if ((m % 10 == 0 && d <= 9) || (m % 5 == 0 && d <= 5)) {
                return "$lo and $hi are $d either side of $m: ($m − $d)($m + $d) = " +
                    "$m² − $d²<br>= ${N(m * m)} − ${d * d} = <b>${N(p)}</b>"
            }
        }

        // factoring: × 25 / × 75 / × 125
        for ((f, num, den) in listOf(Triple(25, 100, 4), Triple(75, 300, 4), Triple(125, 1000, 8))) {
            if (a == f || b == f) {
                val o = if (a == f) b else a
                if (o % den == 0) {
                    return "× $f = × $num ÷ $den: $o ÷ $den = ${o / den}, " +
                        "× $num = <b>${N(p)}</b>"
                }
                return "× $f = × $num ÷ $den: $o × $num = ${N(o * num)}, " +
                    "÷ $den = <b>${N(p)}</b>"
            }
        }

        // halve & double: something ending in 5 times something even
        for ((five, ev) in listOf(a to b, b to a)) {
            if (five % 10 == 5 && five < 100 && ev % 2 == 0) {
                val d2 = five * 2
                val h = ev / 2
                return "Halve & double: $five × $ev = $d2 × $h = ${d2 / 10} × $h × 10" +
                    " = ${N(d2 / 10 * h)} × 10 = <b>${N(p)}</b>"
            }
        }

        // near-round: one factor is within 2 of a multiple of 10 (or a 3-digit within 3 of 100s)
        val cands = mutableListOf<NearCand>()
        for ((x, y) in listOf(a to b, b to a)) {
            val options = if (x >= 100) listOf(100 to 3, 10 to 1) else listOf(10 to 2)
            for ((unit, maxGap) in options) {
                val r = pyRound(x.toDouble() / unit).toInt() * unit
                if (Math.abs(x - r) in 1..maxGap && r != 0) {
                    cands.add(NearCand(Math.abs(x - r), unit == 10, x, y, r))
                }
            }
        }
        if (cands.isNotEmpty()) {
            val best = cands.minWithOrNull(
                compareBy<NearCand>({ it.gap }, { it.tens }, { it.x }, { it.y }, { it.r }))!!
            val x = best.x
            val y = best.y
            val r = best.r
            val k = Math.abs(x - r)
            val op = if (x < r) "−" else "+"
            val corr = y * k
            val corrTxt = if (k == 1) N(y) else "${N(y)} × $k = ${N(corr)}"
            return "${N(x)} = ${N(r)} $op $k: ${N(y)} × ${N(r)} = ${N(y * r)}, $op $corrTxt" +
                "<br>${N(y * r)} $op ${N(corr)} = <b>${N(p)}</b>"
        }

        // split one factor into tens + units (prefer splitting the one with the smaller units digit)
        val big: Int
        val small: Int
        if (a >= 100 || b >= 100) {
            if (a >= 100) { big = a; small = b } else { big = b; small = a }
        } else {
            if (a % 10 <= b % 10) { small = a; big = b } else { small = b; big = a }
        }
        val t = small - small % 10
        val u = small % 10
        return "Split $small = $t + $u: ${N(big)} × $t = ${N(big * t)}, ${N(big)} × $u = " +
            "${N(big * u)}<br>${N(big * t)} + ${N(big * u)} = <b>${N(p)}</b>"
    }

    fun mul2Problem(a: Int, b: Int): Problem {
        val p = a * b
        return Problem("${N(a)} × ${N(b)}", p.toDouble(), N(p), explanation = mul2Explain(a, b))
    }

    private fun genMul21(rng: Rng): Problem {
        if (rng.random() < 0.5) {
            var a: Int
            while (true) {
                a = rng.randint(12, 99)
                if (a % 10 != 0 && a % 11 != 0) break
            }
            return if (rng.random() < 0.5) mul2Problem(a, 11) else mul2Problem(11, a)
        }
        val x = rng.randint(11, 19)
        return mul2Problem(x, rng.randint(12, 19))
    }

    private fun genMul22(rng: Rng): Problem {
        val r = rng.random()
        var a: Int
        var b: Int
        if (r < 0.15) {  // symmetric pair around a round number
            val m = rng.randint(2, 9) * 10
            val d = rng.randint(1, 9)
            a = m - d
            b = m + d
        } else if (r < 0.25) {  // a nice factor
            a = rng.choice(listOf(25, 75, 15, 35, 45, 55, 65, 85))
            b = rng.randint(6, 49) * 2
            if (b % 10 == 0) b += 2
        } else {
            while (true) {
                a = nonround(rng, 21, 99)
                b = nonround(rng, 13, 99)
                if (a != 11 && b != 11 && !(a < 20 && b < 20)) break
            }
        }
        return if (rng.random() < 0.5) mul2Problem(a, b) else mul2Problem(b, a)
    }

    private fun genMul23(rng: Rng): Problem {
        while (true) {
            val a = nonround(rng, 101, 999)
            val b = nonround(rng, 12, 99)
            if (b != 11) return mul2Problem(a, b)
        }
    }

    // ================================================================== squaring

    fun squareProblem(n: Int): Problem {
        val sq = n * n
        val expl: String
        if (n % 10 == 5) {
            val k = n / 10
            expl = "Ends in 5: $k × ${k + 1} = ${N(k * (k + 1))}, then append 25" +
                " → <b>${N(sq)}</b>"
        } else if (n in 41..59 && n != 50) {
            val d = n - 50
            val sign = if (d > 0) "+" else "−"
            val ad = Math.abs(d)
            expl = "$n = 50 $sign $ad: 25 $sign $ad = ${25 + d} → ${N((25 + d) * 100)};" +
                " $ad² = ${d * d}<br>${N((25 + d) * 100)} + ${d * d} = <b>${N(sq)}</b>"
        } else if (n in 88..112 && n != 100) {
            val d = n - 100
            val sign = if (d > 0) "+" else "−"
            val ad = Math.abs(d)
            expl = "$n = 100 $sign $ad: $n $sign $ad = ${n + d} → ${N((n + d) * 100)};" +
                " $ad² = ${d * d}<br>${N((n + d) * 100)} + ${d * d} = <b>${N(sq)}</b>"
        } else {
            val unit = if (n < 100) 10 else 100
            val r = pyRound(n.toDouble() / unit).toInt() * unit
            val d = Math.abs(n - r)
            val other = 2 * n - r
            expl = "Round to ${N(r)}: $n² = ($n − $d)($n + $d) + $d² = " +
                "${N(minOf(r, other))} × ${N(maxOf(r, other))} + $d²<br>" +
                "= ${N(r * other)} + ${N(d * d)} = <b>${N(sq)}</b>"
        }
        return Problem("${N(n)}<sup>2</sup>", sq.toDouble(), N(sq), explanation = expl)
    }

    private fun genSq1(rng: Rng) = squareProblem(rng.randint(1, 9) * 10 + 5)

    private fun genSq2(rng: Rng): Problem {
        val base = rng.choice(listOf(50, 100))
        val sign = rng.choice(listOf(-1, 1))
        return squareProblem(base + sign * rng.randint(1, 9))
    }

    private fun genSq3(rng: Rng) = squareProblem(nonround(rng, 11, 99))

    private fun genSq4(rng: Rng): Problem {
        val kind = rng.randrange(3)
        if (kind == 0) return squareProblem(rng.randint(10, 99) * 10 + 5)
        if (kind == 1) {
            val h = rng.randint(2, 9) * 100
            val sign = rng.choice(listOf(-1, 1))
            return squareProblem(h + sign * rng.randint(1, 9))
        }
        while (true) {
            val n = nonround(rng, 101, 999)
            if (n % 10 != 5) return squareProblem(n)
        }
    }

    // ================================================================== division

    /** Exact division a ÷ b (b divides a). */
    fun divProblem(a: Int, b: Int): Problem {
        val q = a / b
        val prompt = "${N(a)} ÷ $b"
        val expl: String
        if (b == 5) {
            expl = "÷ 5 = × 2 ÷ 10: ${N(a)} × 2 = ${N(2 * a)} → <b>$q</b>"
        } else if (b == 25) {
            expl = "÷ 25 = × 4 ÷ 100: ${N(a)} × 4 = ${N(4 * a)} → <b>$q</b>"
        } else if (b < 10) {
            val lead = q - q % 10
            val rest = q - lead
            expl = "Chunk it: ${N(a)} = ${N(lead * b)} + ${rest * b}<br>" +
                "${N(lead * b)} ÷ $b = $lead, ${rest * b} ÷ $b = $rest → <b>$q</b>"
        } else {
            // simplify by halving both while possible; worth it if the divisor gets small
            var x = a
            var y = b
            val chain = mutableListOf<String>()
            while (x % 2 == 0 && y % 2 == 0 && y > 12) {
                x /= 2
                y /= 2
                chain.add("${N(x)} ÷ $y")
            }
            if (chain.isNotEmpty() && y <= 12) {
                expl = "Halve both: ${N(a)} ÷ $b = " + chain.joinToString(" = ") + " = <b>$q</b>"
            } else {
                val qt = q - q % 10
                val qu = q % 10
                expl = "Estimate, then chunk: $b × $qt = ${N(b * qt)}; " +
                    "${N(a)} − ${N(b * qt)} = ${N(a - b * qt)} = $b × $qu<br>" +
                    "→ $qt + $qu = <b>$q</b>"
            }
        }
        return Problem(prompt, q.toDouble(), N(q), explanation = expl)
    }

    /** a ÷ b to 3 significant figures. */
    fun divApproxProblem(a: Int, b: Int): Problem {
        val v = a.toDouble() / b
        val shown = sigRound(v, 3)
        var s = 1
        while (a * s < b) s *= 10
        val q = a * s / b
        val r = a * s % b
        val frac = r.toDouble() / b
        val scaled = if (s > 1) "${N(a * s)} ÷ $b" else "${N(a)} ÷ $b"
        val back = if (s > 1) " → ÷ ${N(s)}" else ""
        val expl = "$scaled: $b × ${N(q)} = ${N(b * q)}, remainder $r; $r/$b ≈ ${fmtF(frac, 3)}" +
            "<br>→ ${N(q)} + ${fmtF(frac, 3)} = ${N(pyRound(q + frac, 3))}$back ≈ <b>${N(shown)}</b>"
        return Problem("${N(a)} ÷ $b", v, "≈ " + N(shown), tolRel = 0.005, explanation = expl,
            hint = "3 significant figures")
    }

    private fun genDiv1(rng: Rng): Problem {
        val b = rng.randint(3, 9)
        while (true) {
            val q = rng.randint(12, 999 / b)
            if (q % 10 != 0) return divProblem(q * b, b)
        }
    }

    private fun genDiv2(rng: Rng): Problem {
        val b = nonround(rng, 12, 99)
        while (true) {
            val q = rng.randint(12, 99)
            if (q % 10 != 0) return divProblem(q * b, b)
        }
    }

    private fun genDiv3(rng: Rng): Problem {
        while (true) {
            val c1 = nonround(rng, 6, 99)
            val c2 = rng.randint(6, 9)
            val b = rng.choice(listOf(c1, c2))
            val digits = rng.choice(listOf(2, 3, 3, 4))
            val a = rng.randint(ipow(10, digits - 1).toInt(), ipow(10, digits).toInt() - 1)
            if (a % b != 0 && a != b) return divApproxProblem(a, b)
        }
    }

    // ================================================================== lessons

    private val ADD_LESSON = Lesson("Addition",
        "\n" +
        "<h3>Add left to right</h3>\n" +
        "<p>Keep the first number whole and add the second a place at a time. The big part of the\n" +
        "answer comes first, which is what you'd say aloud, and you never have to reverse\n" +
        "digits.</p>\n" +
        "<ex>586 + 247<br>586 + 200 = 786<br>786 + 40 = 826<br>826 + 7 = <b>833</b></ex>\n" +
        "<tip>Say the running total to yourself after each step.</tip>\n" +
        "<p>For 4-digit numbers, add the top two digits as one chunk, then the last two.</p>\n" +
        "<ex>4 735 + 2 869<br>4 735 + 2 800 = 7 535<br>7 535 + 69 = <b>7 604</b></ex>\n" +
        "<h3>Round and compensate</h3>\n" +
        "<p>When an addend sits just under a round number, add the round number and take back the\n" +
        "difference.</p>\n" +
        "<ex>586 + 298<br>586 + 300 = 886<br>886 − 2 = <b>884</b></ex>\n" +
        "<ex>47 + 39<br>47 + 40 = 87<br>87 − 1 = <b>86</b></ex>\n" +
        "<h3>Three numbers: column sums</h3>\n" +
        "<p>Add all the hundreds, all the tens and all the units, then combine.</p>\n" +
        "<ex>347 + 586 + 219<br>Hundreds: 3 + 5 + 2 = 10, so 1 000<br>" +
        "Tens: 4 + 8 + 1 = 13, so 130<br>Units: 7 + 6 + 9 = 22<br>" +
        "1 000 + 130 + 22 = <b>1 152</b></ex>\n" +
        "<tip>Practise until adding one digit to a running total, like 826 + 7, is automatic.\n" +
        "That's where the time goes.</tip>\n")

    private val SUB_LESSON = Lesson("Subtraction",
        "\n" +
        "<p>Pick the method that makes every step a single easy move. With practice you'll see it\n" +
        "instantly.</p>\n" +
        "<h3>Left to right</h3>\n" +
        "<p>Take the second number off a piece at a time, biggest piece first.</p>\n" +
        "<ex>834 − 267<br>834 − 200 = 634<br>634 − 60 = 574<br>574 − 7 = <b>567</b></ex>\n" +
        "<h3>Compensation</h3>\n" +
        "<p>If you're subtracting something just under a round number, subtract the round number\n" +
        "and give the difference back.</p>\n" +
        "<ex>724 − 298<br>724 − 300 = 424<br>424 + 2 = <b>426</b></ex>\n" +
        "<ex>83 − 47<br>83 − 50 = 33<br>33 + 3 = <b>36</b></ex>\n" +
        "<h3>Count up (the cashier's method)</h3>\n" +
        "<p>When the numbers are close or straddle a round number, find the gap by adding up from\n" +
        "the smaller one.</p>\n" +
        "<ex>612 − 487<br>487 → 500 is 13<br>500 → 612 is 112<br>13 + 112 = <b>125</b></ex>\n" +
        "<p>For 4-digit numbers, hop to the next thousand.</p>\n" +
        "<ex>7 352 − 2 868<br>2 868 → 3 000 is 132<br>3 000 → 7 352 is 4 352<br>" +
        "132 + 4 352 = <b>4 484</b></ex>\n" +
        "<h3>Complements from 1 000 and 10 000</h3>\n" +
        "<p>\"All from 9, the last from 10\": take each digit from 9, and the last non-zero digit from 10.\n" +
        "Trailing zeros stay zero.\n" +
        "This is how you make change.</p>\n" +
        "<ex>1 000 − 387<br>9 − 3 = 6<br>9 − 8 = 1<br>10 − 7 = 3<br>Answer: <b>613</b></ex>\n" +
        "<ex>10 000 − 4 736<br>9 − 4 = 5<br>9 − 7 = 2<br>9 − 3 = 6<br>10 − 6 = 4<br>" +
        "Answer: <b>5 264</b></ex>\n" +
        "<tip>This is the engine behind counting up: 2 868 → 3 000 is 1 000 − 868 = 132.</tip>\n")

    private val MUL1_LESSON = Lesson("Multiplying by one digit",
        "\n" +
        "<h3>Left-to-right partial products</h3>\n" +
        "<p>Multiply the biggest place first and keep a running total. You hold one number in\n" +
        "your head instead of a column of carries.</p>\n" +
        "<ex>347 × 6<br>300 × 6 = 1 800<br>40 × 6 = 240<br>1 800 + 240 = 2 040<br>7 × 6 = 42<br>" +
        "2 040 + 42 = <b>2 082</b></ex>\n" +
        "<p>For 4-digit numbers, treat the last two digits as one chunk, since you know 2-digit ×\n" +
        "1-digit.</p>\n" +
        "<ex>4 736 × 7<br>4 000 × 7 = 28 000<br>700 × 7 = 4 900<br>28 000 + 4 900 = 32 900<br>" +
        "36 × 7 = 252<br>32 900 + 252 = <b>33 152</b></ex>\n" +
        "<tip>The whole skill rests on instant single-digit times tables and quick additions like\n" +
        "2 040 + 42. If a step feels slow, drill that step.</tip>\n" +
        "<h3>Near-round numbers: overshoot and subtract</h3>\n" +
        "<p>Multiply by the round number, then subtract the excess.</p>\n" +
        "<ex>49 × 7<br>50 × 7 = 350<br>350 − 7 = <b>343</b></ex>\n" +
        "<ex>398 × 6<br>400 × 6 = 2 400<br>2 × 6 = 12<br>2 400 − 12 = <b>2 388</b></ex>\n" +
        "<h3>Decimals</h3>\n" +
        "<p>Drop the decimal point and multiply the integers. Then put back as many decimal\n" +
        "places as the factor had.</p>\n" +
        "<ex>4.7 × 8<br>47 × 8 = 376<br>One decimal place: <b>37.6</b></ex>\n" +
        "<ex>0.36 × 7<br>36 × 7 = 252<br>Two decimal places: <b>2.52</b></ex>\n" +
        "<tip>Always sanity-check the size: 4.7 × 8 ≈ 5 × 8 = 40.</tip>\n")

    private val MUL2_LESSON = Lesson("Multiplying 2-digit numbers",
        "\n" +
        "<p>No single method is best. The skill is spotting which trick the numbers want, so\n" +
        "check them in this order.</p>\n" +
        "<h3>1. Difference of squares</h3>\n" +
        "<p>Use it when the numbers are symmetric around a round number.</p>\n" +
        "<ex>47 × 53<br>(50 − 3)(50 + 3)<br>50² = 2 500<br>3² = 9<br>" +
        "2 500 − 9 = <b>2 491</b></ex>\n" +
        "<tip>Memorize squares up to 30 or so. They make this instant.</tip>\n" +
        "<h3>2. Factor it</h3>\n" +
        "<p>Look for a nice factor. × 25 is the same as × 100 ÷ 4.</p>\n" +
        "<ex>48 × 25<br>48 ÷ 4 = 12<br>12 × 100 = <b>1 200</b></ex>\n" +
        "<p>For a number ending in 5 × an even number, double the first and halve the second.</p>\n" +
        "<ex>35 × 48<br>Double and halve: 70 × 24<br>70 × 24 = <b>1 680</b></ex>\n" +
        "<h3>3. Overshoot and correct</h3>\n" +
        "<p>Use it when a factor is close to a round number.</p>\n" +
        "<ex>47 × 59<br>47 × 60 = 2 820<br>2 820 − 47 = <b>2 773</b></ex>\n" +
        "<p>Near 100, take each number's gap from 100. Subtract one gap from the other number and\n" +
        "multiply by 100, then add the product of the gaps.</p>\n" +
        "<ex>96 × 93 (gaps 4 and 7)<br>96 − 7 = 89<br>89 × 100 = 8 900<br>4 × 7 = 28<br>" +
        "8 900 + 28 = <b>8 928</b></ex>\n" +
        "<h3>4. Split one factor</h3>\n" +
        "<p>Otherwise, split the factor with the smaller units digit.</p>\n" +
        "<ex>47 × 63<br>47 × 60 = 2 820<br>47 × 3 = 141<br>2 820 + 141 = <b>2 961</b></ex>\n" +
        "<tip>Criss-cross (tens × tens, cross terms, units × units) is the same idea written as a\n" +
        "column.</tip>\n" +
        "<p>For 3-digit × 2-digit, split the 2-digit factor.</p>\n" +
        "<ex>347 × 26<br>347 × 20 = 6 940<br>347 × 6 = 2 082<br>6 940 + 2 082 = <b>9 022</b></ex>\n" +
        "<h3>Special: × 11</h3>\n" +
        "<p>Spread the digits and drop their sum in the middle, carrying if the sum has two\n" +
        "digits.</p>\n" +
        "<ex>47 × 11<br>4 + 7 = 11<br>4 (11) 7<br>Carry the 1: <b>517</b></ex>\n" +
        "<h3>Special: teen × teen</h3>\n" +
        "<p>Add one number to the other's units and multiply by 10. Then add the product of the\n" +
        "units.</p>\n" +
        "<ex>13 × 17<br>13 + 7 = 20<br>20 × 10 = 200<br>3 × 7 = 21<br>200 + 21 = <b>221</b></ex>\n")

    private val SQUARE_LESSON = Lesson("Squaring",
        "\n" +
        "<h3>Ending in 5</h3>\n" +
        "<p>n5² = n × (n + 1), followed by 25.</p>\n" +
        "<ex>65²<br>6 × 7 = 42<br>Follow with 25: <b>4 225</b></ex>\n" +
        "<ex>345²<br>34 × 35 = 1 190<br>Follow with 25: <b>119 025</b></ex>\n" +
        "<h3>Near 50</h3>\n" +
        "<p>(50 + d)² = (25 + d) hundreds + d².</p>\n" +
        "<ex>47² (d is −3)<br>25 − 3 = 22<br>2 200 + 9 = <b>2 209</b></ex>\n" +
        "<ex>56² (d is 6)<br>25 + 6 = 31<br>3 100 + 36 = <b>3 136</b></ex>\n" +
        "<h3>Near 100</h3>\n" +
        "<p>(100 + d)² = (100 + 2d) hundreds + d². Since 100 + 2d is the number plus d, adjust\n" +
        "the number itself.</p>\n" +
        "<ex>97² (d is −3)<br>97 − 3 = 94<br>9 400 + 9 = <b>9 409</b></ex>\n" +
        "<ex>104² (d is 4)<br>104 + 4 = 108<br>10 800 + 16 = <b>10 816</b></ex>\n" +
        "<h3>Anything else: up and down</h3>\n" +
        "<p>Go up and down by the same amount. Because a² = (a − d)(a + d) + d², pick d so one\n" +
        "factor is round.</p>\n" +
        "<ex>67² (d is 3)<br>70 × 64 = 4 480<br>3² = 9<br>4 480 + 9 = <b>4 489</b></ex>\n" +
        "<p>For 3-digit numbers, round to the nearest hundred.</p>\n" +
        "<ex>304² (d is 4)<br>300 × 308 = 92 400<br>4² = 16<br>92 400 + 16 = <b>92 416</b></ex>\n" +
        "<ex>497² (d is 3)<br>500 × 494 = 247 000<br>3² = 9<br>247 000 + 9 = <b>247 009</b></ex>\n" +
        "<ex>347² (d is 47)<br>300 × 394 = 118 200<br>47² = 2 209<br>" +
        "118 200 + 2 209 = <b>120 409</b></ex>\n" +
        "<tip>The 3-digit case needs a 2-digit square as a sub-step. Get 2-digit squares fast\n" +
        "first, and memorize 1²–25².</tip>\n" +
        "<tip>Hold the big product in your head while you work out d².</tip>\n")

    private val DIV_LESSON = Lesson("Division",
        "\n" +
        "<h3>Chunking</h3>\n" +
        "<p>Peel off a big, easy multiple of the divisor, then deal with what's left.</p>\n" +
        "<ex>432 ÷ 6<br>420 ÷ 6 = 70<br>12 ÷ 6 = 2<br>70 + 2 = <b>72</b></ex>\n" +
        "<p>For 2-digit divisors, estimate the tens of the answer first.</p>\n" +
        "<ex>1 035 ÷ 23<br>23 × 40 = 920<br>1 035 − 920 = 115<br>23 × 5 = 115<br>" +
        "40 + 5 = <b>45</b></ex>\n" +
        "<h3>Simplify first</h3>\n" +
        "<p>Divide both numbers by a common factor. The answer doesn't change.</p>\n" +
        "<ex>1 344 ÷ 48<br>Halve both: 672 ÷ 24<br>Halve both: 336 ÷ 12<br>" +
        "336 ÷ 12 = <b>28</b></ex>\n" +
        "<h3>Friendly divisors</h3>\n" +
        "<ul>\n" +
        "<li>÷ 5: × 2, then ÷ 10</li>\n" +
        "<li>÷ 25: × 4, then ÷ 100</li>\n" +
        "<li>÷ 50: × 2, then ÷ 100</li>\n" +
        "</ul>\n" +
        "<ex>435 ÷ 5<br>435 × 2 = 870<br>870 ÷ 10 = <b>87</b></ex>\n" +
        "<h3>Non-exact answers</h3>\n" +
        "<p>Get the whole part by chunking, then turn the remainder into a decimal.</p>\n" +
        "<ex>347 ÷ 23<br>23 × 15 = 345, remainder 2<br>2/23 ≈ 0.087<br>15 + 0.087 = 15.087<br>" +
        "Rounded: <b>15.1</b></ex>\n" +
        "<tip>Give 3 significant figures, rounded, not truncated.</tip>\n" +
        "<p>Knowing reciprocals helps a lot here.</p>\n" +
        "<ex>1/7 ≈ 0.1429<br>1/8 = 0.125<br>1/9 ≈ 0.111<br>1/11 ≈ 0.0909<br>1/12 ≈ 0.0833<br>" +
        "1/13 ≈ 0.0769<br>1/17 ≈ 0.0588<br>1/19 ≈ 0.0526</ex>\n" +
        "<ex>5/17 ≈ 5 × 0.0588 = <b>0.294</b></ex>\n" +
        "<h3>Dividend smaller than divisor</h3>\n" +
        "<p>Shift it: multiply the dividend by 10, divide, then divide the result by 10.</p>\n" +
        "<ex>17 ÷ 43<br>170 ÷ 43 ≈ 3.95<br>3.95 ÷ 10 = <b>0.395</b></ex>\n")

    // ================================================================== skills

    val S_ADD = Skill("s_add", "Addition", TRACK, ADD_LESSON, listOf(
        Level("2-digit + 2-digit", 3.0, ::genAdd1),
        Level("3-digit + 3-digit", 5.0, ::genAdd2),
        Level("4-digit + 4-digit", 8.0, ::genAdd3),
        Level("three 3-digit numbers", 10.0, ::genAdd4),
    ))

    val S_SUB = Skill("s_sub", "Subtraction", TRACK, SUB_LESSON, listOf(
        Level("2-digit − 2-digit (borrowing)", 3.5, ::genSub1),
        Level("complements: 1 000 − xyz, 10 000 − wxyz", 4.0, ::genSub2),
        Level("3-digit − 3-digit", 6.0, ::genSub3),
        Level("4-digit − 4-digit", 9.0, ::genSub4),
    ))

    val S_MUL1 = Skill("s_mul1", "Multiply by 1 digit", TRACK, MUL1_LESSON, listOf(
        Level("2-digit × 1-digit", 4.0, ::genMul11),
        Level("3-digit × 1-digit", 6.0, ::genMul12),
        Level("4-digit × 1-digit", 9.0, ::genMul13),
        Level("decimal × 1-digit", 5.0, ::genMul14),
    ))

    val S_MUL2 = Skill("s_mul2", "Multiply 2 digits", TRACK, MUL2_LESSON, listOf(
        Level("× 11 and teen × teen", 6.0, ::genMul21),
        Level("2-digit × 2-digit", 12.0, ::genMul22),
        Level("3-digit × 2-digit", 20.0, ::genMul23),
    ))

    val S_SQUARE = Skill("s_square", "Squaring", TRACK, SQUARE_LESSON, listOf(
        Level("2-digit numbers ending in 5", 3.0, ::genSq1),
        Level("near 50 and near 100", 5.0, ::genSq2),
        Level("any 2-digit number", 7.0, ::genSq3),
        Level("3-digit numbers", 14.0, ::genSq4),
    ))

    val S_DIV = Skill("s_div", "Division", TRACK, DIV_LESSON, listOf(
        Level("exact, 2–3 digits ÷ 1 digit", 4.0, ::genDiv1),
        Level("exact, 3–4 digits ÷ 2 digits", 9.0, ::genDiv2),
        Level("non-exact, to 3 significant figures", 12.0, ::genDiv3),
    ))

    override val SKILLS: List<Skill> = listOf(S_ADD, S_SUB, S_MUL1, S_MUL2, S_SQUARE, S_DIV)
}
