package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.gcd
import dev.russell.mmtrainer.py.ipow
import dev.russell.mmtrainer.py.pyRound

/*
 * Fractions & percents track: fraction → decimal conversion and percentages.
 *
 * As in Arithmetic.kt, generators only pick the numbers; pure *Problem builders turn them
 * into Problems so explanations and tests depend only on the operands.
 */
object Fractions : ContentModule {
    private const val TRACK = "fractions"

    /** Format an int, float or Fraction for display. */
    private fun N(x: Fraction): String = fmtNum(x.toDouble())
    private fun N(x: Double): String = fmtNum(x)
    private fun N(x: Long): String = fmtNum(x)
    private fun N(x: Int): String = fmtNum(x)

    /** '+15' / '−7.5' (zero gets no sign). */
    private fun signed(x: Fraction): String = N(x).let { if (x <= Fraction.of(0)) it else "+$it" }
    private fun signed(x: Double): String = N(x).let { if (x <= 0) it else "+$it" }
    private fun signed(x: Int): String = N(x).let { if (x <= 0) it else "+$it" }

    private fun frac(n: Int, d: Int = 1) = Fraction.of(n, d)

    /** int(Fraction): truncate toward zero. */
    private fun truncInt(f: Fraction): Long = f.num / f.den

    /** round(Fraction): nearest integer, ties to even. */
    private fun roundHalfEven(f: Fraction): Long {
        val fl = Math.floorDiv(f.num, f.den)
        val twice = 2 * (f.num - fl * f.den)  // 2 × fractional part, in units of 1/den
        return when {
            twice < f.den -> fl
            twice > f.den -> fl + 1
            else -> if (fl % 2 == 0L) fl else fl + 1
        }
    }

    // ===================================================== fractions → decimals

    /** First n decimal digits of num/den (0 <= num < den) by long division. */
    private fun digits(num0: Int, den: Int, n: Int): String {
        var num = num0
        val out = StringBuilder()
        repeat(n) {
            num *= 10
            out.append(num / den)
            num %= den
        }
        return out.toString()
    }

    private fun terminates(den0: Int): Boolean {
        var den = den0
        while (den % 2 == 0) den /= 2
        while (den % 5 == 0) den /= 5
        return den == 1
    }

    /** Mental route for the proper fraction r/den (0 < r < den), ending in its decimal value. */
    private fun properExpl(r: Int, den: Int): String {
        val v = r.toDouble() / den
        if (terminates(den)) {
            var m = 0
            while (ipow(10, m) % den != 0L) m += 1
            val k = ipow(10, m) / den
            val exact = N(frac(r, den))
            var text = "Scale to a power of 10: $r/$den = ($r × $k)/($den × $k) = " +
                "${N(r * k)}/${N(ipow(10, m))} = $exact"
            if (den == 8 && r > 1) {
                text += "  (1/8 = 0.125, so $r × 0.125)"
            } else if (den == 16) {
                text += "  (= half of $r/8 = ${N(frac(r, 8))})"
            }
            return text
        }
        if (den == 3) return if (r == 1) "1/3 = 0.3333…" else "1/3 = 0.333…, so 2/3 = 0.6666…"
        if (den == 9) return "n/9 repeats n: $r/9 = 0.${r.toString().repeat(4)}…"
        if (den == 11) {
            val pair = (9 * r).toString().padStart(2, '0')
            return "n/11 repeats 9n: $r × 9 = ${9 * r} → $r/11 = 0.${pair.repeat(2)}…"
        }
        if (den == 7) {
            return "1/7 = 0.142857… (a repeating cycle); $r/7 runs through the same cycle from another " +
                "digit: $r/7 = 0.${digits(r, 7, 6)}…"
        }
        // reciprocal route, using the complement when r is more than half of den
        val note = mapOf(14 to " (half of 1/7)", 18 to " (half of 1/9)", 12 to " (1/3 ÷ 4)", 6 to " (half of 1/3)",
            15 to " (1/3 ÷ 5)")[den] ?: ""
        val inv = fmtF(1.0 / den, 5)
        val recip = "1/$den ≈ $inv$note"
        if (r == 1) return recip
        if (2 * r <= den) return "$recip<br>$r/$den = $r × 1/$den ≈ $r × $inv ≈ ${fmtF(v, 4)}"
        val c = den - r
        val times = if (c > 1) " ≈ 1 − $c × $inv" else ""
        return "$recip<br>$r/$den = 1 − $c/$den$times ≈ 1 − ${fmtF(c.toDouble() / den, 4)} = ${fmtF(v, 4)}"
    }

    /** num/den as a decimal. exact=true demands the exact (terminating) decimal. */
    fun fracProblem(num: Int, den: Int, exact: Boolean = false): Problem {
        val v = num.toDouble() / den
        val whole = num / den
        val r = num % den
        val disp: String
        val tol: Double
        val hint: String
        if (exact) {
            disp = N(frac(num, den)); tol = 0.0; hint = "exact decimal"
        } else {
            tol = 0.0006; hint = "3 decimal places"
            val terminatingShort = terminates(den) && pyRound(v, 4) == v
            disp = if (terminatingShort) N(frac(num, den)) else "≈ ${fmtF(v, 3)}"
        }
        val final = if (disp.startsWith("≈")) disp else "= $disp"
        val expl = if (whole != 0) {
            "Split off the whole part: $num/$den = $whole + $r/$den<br>" +
                "${properExpl(r, den)}<br>→ $whole + ${fmtF(r.toDouble() / den, 4)} $final"
        } else {
            properExpl(r, den) + (if (exact) "" else " → <b>${disp.trimStart { it == '≈' || it == ' ' }}</b>")
        }
        return Problem("$num/$den", v, disp, tolAbs = tol, explanation = expl, hint = hint)
    }

    private fun coprimeNumerator(rng: Rng, den: Int, lo: Int, hi: Int): Int {
        while (true) {
            val n = rng.randint(lo, hi)
            if (gcd(n, den) == 1 && n % den != 0) return n
        }
    }

    private fun genFracLevel(dens: List<Int>, exact: Boolean): (Rng) -> Problem = { rng ->
        val den = rng.choice(dens)
        fracProblem(coprimeNumerator(rng, den, 1, den - 1), den, exact)
    }

    private fun genFrac4(rng: Rng): Problem {
        val den = rng.choice(listOf(3, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 19))
        return fracProblem(coprimeNumerator(rng, den, den + 1, 9 * den), den)
    }

    private val FRAC_LESSON = Lesson("Fractions → decimals",
        "\n" +
        "<p>Fast conversion is mostly <b>memory plus one multiplication</b>. Learn these reciprocals:</p>\n" +
        "<table>\n" +
        "<tr><td>1/8 = 0.125</td><td>1/16 = 0.0625</td><td>1/3 = 0.333…</td><td>1/6 = 0.1667</td></tr>\n" +
        "<tr><td>1/7 = 0.142857…</td><td>1/9 = 0.111…</td><td>1/11 = 0.0909…</td><td>1/12 = 0.0833</td></tr>\n" +
        "<tr><td>1/13 ≈ 0.0769</td><td>1/17 ≈ 0.0588</td><td>1/19 ≈ 0.0526</td><td>1/15 = 0.0667</td></tr>\n" +
        "</table>\n" +
        "<h3>Terminating denominators</h3>\n" +
        "<p>For denominators 2, 4, 5, 8, 16, 20, 25 and 40, scale to a power of 10.</p>\n" +
        "<ex>13/40 = 325/1 000 = <b>0.325</b><br>7/25 = 28/100 = <b>0.28</b><br>" +
        "5/8 = 5 × 0.125 = <b>0.625</b></ex>\n" +
        "<h3>Repeating patterns</h3>\n" +
        "<p><b>n/9</b> repeats n. <b>n/11</b> repeats 9n.</p>\n" +
        "<ex>7/9 = 0.777… ≈ <b>0.778</b><br>7 × 9 = 63, so 7/11 = <b>0.6363…</b></ex>\n" +
        "<p><b>n/7</b> is the cycle 142857 started at a different digit.</p>\n" +
        "<ex>1/7 = 0.142857…<br>2/7 = 0.285714…<br>3/7 = 0.428571…<br>4/7 = 0.571428…<br>5/7 = 0.714285…<br>" +
        "6/7 = 0.857142…</ex>\n" +
        "<h3>Everything else</h3>\n" +
        "<p><b>a/b = a × (1/b).</b> Take the reciprocal, then multiply.</p>\n" +
        "<ex>5/17 ≈ 5 × 0.0588 = <b>0.294</b></ex>\n" +
        "<p>If a is more than half of b, go from 1.</p>\n" +
        "<ex>15/17 = 1 − 2/17<br>≈ 1 − 0.118 = <b>0.882</b></ex>\n" +
        "<h3>Improper fractions</h3>\n" +
        "<p>Split off the whole part first.</p>\n" +
        "<ex>23/7 = 3 + 2/7<br>≈ 3 + 0.286 = <b>3.286</b></ex>\n" +
        "<tip>Answers are to 3 decimal places. Round, don't truncate: 2/3 ≈ 0.667.</tip>\n")

    private val S_FRAC = Skill("s_frac", "Fractions → decimals", TRACK, FRAC_LESSON, listOf(
        Level("denominators 2, 4, 5, 8, 16, 20, 25, 40", 3.0,
            genFracLevel(listOf(2, 4, 5, 8, 16, 20, 25, 40), exact = true)),
        Level("denominators 3, 6, 9, 11, 12", 5.0, genFracLevel(listOf(3, 6, 9, 11, 12), exact = false)),
        Level("denominators 7, 13–19", 7.0,
            genFracLevel(listOf(7, 13, 14, 15, 16, 17, 18, 19), exact = false)),
        Level("improper fractions", 8.0, ::genFrac4),
    ))

    // =============================================================== percentages

    private fun pct(p: Fraction): String = N(p) + "%"
    private fun pct(p: Int): String = N(p) + "%"

    private val FRIENDLY: Map<Fraction, String> = linkedMapOf(
        frac(5) to "10% = {t}, so 5% = half of that = {ans}",
        frac(10) to "10%: move the decimal point one place left → {ans}",
        frac(15) to "10% = {t}, 5% = {h} → 15% = {t} + {h} = {ans}",
        frac(20) to "10% = {t}, doubled → {ans}",
        frac(25) to "25% = ¼: {y} ÷ 4 = {ans}",
        frac(50) to "50% = ½: {y} ÷ 2 = {ans}",
        frac(75) to "25% = {y} ÷ 4 = {q}, so 75% = {y} − {q} = {ans}",
        frac(25, 2) to "12.5% = ⅛: halve three times, {y} → {h2} → {h4} → {ans}",
    )

    /** p% of y. */
    fun pctOfProblem(p: Fraction, y: Int): Problem {
        val ans = p * y.toLong() / 100L
        val one = frac(y, 100)
        val template = FRIENDLY[p]
        val expl: String
        if (template != null) {
            val fields = linkedMapOf(
                "t" to N(one * 10L), "h" to N(one * 5L), "y" to N(y), "q" to N(frac(y, 4)),
                "h2" to N(frac(y, 2)), "h4" to N(frac(y, 4)), "ans" to N(ans),
            )
            expl = Regex("""\{(\w+)\}""").replace(template) { fields.getValue(it.groupValues[1]) }
        } else if (y in listOf(20, 25, 50) && p.den == 1L) {
            expl = "x% of y = y% of x: ${pct(p)} of $y = $y% of ${N(p)} = " +
                "${N(p)} × ${N(frac(y, 100))} = ${N(ans)}"
        } else if (p >= frac(85) && p.den == 1L) {
            val rest = frac(100) - p
            expl = "${pct(p)} = 100% − ${pct(rest)}: 1% = ${N(one)} → ${pct(rest)} = " +
                "${N(rest * one)}<br>${N(y)} − ${N(rest * one)} = ${N(ans)}"
        } else {
            val tens = Math.floorDiv(p.num, p.den * 10) * 10   // (p // 10) * 10
            val units = Math.floorDiv(p.num, p.den) - tens
            val half = p - tens - units  // 0 or 1/2
            val parts = mutableListOf<Fraction>()
            val steps = mutableListOf("1% = ${N(one)}")
            if (tens != 0L) {
                steps.add("10% = ${N(one * 10L)}" + (if (tens > 10) " → ${N(tens)}% = ${N(one * tens)}" else ""))
                parts.add(one * tens)
            }
            if (units != 0L) {
                steps.add("${N(units)}% = $units × ${N(one)} = ${N(one * units)}")
                parts.add(one * units)
            }
            if (half != frac(0)) {
                steps.add("0.5% = ${N(one / 2L)}")
                parts.add(one / 2L)
            }
            var e = "Build from 10% and 1%: " + steps.joinToString("; ")
            e += if (parts.size > 1) {
                "<br>→ " + parts.joinToString(" + ") { N(it) } + " = ${N(ans)}"
            } else {
                " → ${N(ans)}"
            }
            expl = e
        }
        return Problem("${pct(p)} of ${N(y)}", ans.toDouble(), N(ans), explanation = expl)
    }

    private val L1_PCTS: Map<Fraction, Int> = linkedMapOf(
        frac(5) to 20, frac(10) to 10, frac(15) to 20, frac(20) to 5,
        frac(25) to 4, frac(50) to 2, frac(75) to 4, frac(25, 2) to 8,
    )

    private fun genPct1(rng: Rng): Problem {
        val p = rng.choice(L1_PCTS.keys.toList())
        val step = L1_PCTS.getValue(p)
        while (true) {
            val y = step * rng.randint(maxOf(1, 20 / step), 1000 / step)
            if (y % 100 != 0 || rng.random() < 0.3) return pctOfProblem(p, y)
        }
    }

    private fun genPct2(rng: Rng): Problem {
        var p: Fraction
        if (rng.random() < 0.6) {
            while (true) {
                p = frac(rng.randint(2, 99))
                if (p.num % 10 != 0L && p !in L1_PCTS) break
            }
        } else {
            p = frac(2 * rng.randint(1, 19) + 1, 2)  // 1.5% … 19.5%
        }
        val r = rng.random()
        val y = if (r < 0.12 && p.den == 1L) {
            rng.choice(listOf(20, 25, 50))
        } else if (r < 0.75) {
            nonroundTens(rng)
        } else {
            rng.randint(21, 999)
        }
        return pctOfProblem(p, y)
    }

    private fun nonroundTens(rng: Rng): Int {
        while (true) {
            val y = 10 * rng.randint(3, 99)
            if (y % 100 != 0) return y
        }
    }

    fun pctChangeProblem(a: Int, b: Int): Problem {
        val diff = b - a
        val pctv = frac(diff * 100, a)
        val exact = (pctv * 10L).den == 1L
        val absDiff = Math.abs(diff)
        val absPct = if (pctv < frac(0)) -pctv else pctv
        val expl: String
        val disp: String
        val tol: Double
        if (exact) {
            val f = frac(absDiff, a)
            val fr = "$absDiff/$a" + (if (f.den != a.toLong()) " = ${f.num}/${f.den}" else "")
            expl = "Change = ${N(b)} − ${N(a)} = ${signed(diff)}. Divide by the <b>start</b>: " +
                "$fr = ${N(absPct / 100L)}<br>→ ${signed(pctv)}%"
            disp = signed(pctv) + "%"
            tol = 0.0
        } else {
            val one = frac(a, 100)
            val k = roundHalfEven(absPct)
            val r1 = pyRound(pctv.toDouble(), 1)
            expl = "Change = ${N(b)} − ${N(a)} = ${signed(diff)}. Divide by the <b>start</b>: " +
                "$absDiff/$a ≈ ${fmtF(Math.abs(pctv.toDouble()) / 100, 3)}<br>" +
                "(1% of $a = ${N(one)}, and $absDiff ≈ $k × ${N(one)}) " +
                "→ ≈ ${signed(r1)}%"
            disp = "≈ ${signed(r1)}%"
            tol = 0.5
        }
        return Problem("${N(a)} → ${N(b)}<br>% change?", pctv.toDouble(), disp, tolAbs = tol,
            explanation = expl, hint = "percent, e.g. 15 or −7.5")
    }

    private val CHANGE_PCTS: List<Fraction> = listOf(
        frac(5), frac(10), frac(15), frac(20), frac(25), frac(30), frac(40), frac(50), frac(60), frac(75),
        frac(25, 2), frac(5, 2), frac(15, 2), frac(35), frac(4), frac(8),
    )

    private fun genPct3(rng: Rng): Problem {
        if (rng.random() < 0.6) {
            val p = rng.choice(CHANGE_PCTS)
            val sign = if (p >= frac(60) || rng.random() < 0.55) 1L else -1L
            val step = (p / 100L).den.toInt()
            val a = step * rng.randint(maxOf(1, 20 / step), maxOf(2, 600 / step))
            val b = truncInt((frac(1) + p * sign / 100L) * a.toLong())
            return pctChangeProblem(a, b.toInt())
        }
        while (true) {
            val a = rng.randint(20, 500)
            val b = pyRound(a * (1 + rng.uniform(-0.4, 0.6)))
            if (b != a.toLong() && b > 0) return pctChangeProblem(a, b.toInt())
        }
    }

    fun successiveProblem(p1: Int, p2: Int): Problem {
        val m1 = frac(1) + frac(p1, 100)
        val m2 = frac(1) + frac(p2, 100)
        val net = (m1 * m2 - 1L) * 100L
        val cross = frac(p1 * p2, 100)
        val sign = if (cross >= frac(0)) "+" else "−"
        val absCross = if (cross < frac(0)) -cross else cross
        val expl = "Multiply the factors: × ${N(m1)} × ${N(m2)} = × ${N(m1 * m2)} → ${signed(net)}%<br>" +
            "(shortcut: a + b + ab/100 = ${N(p1)} ${if (p2 > 0) "+" else "−"} ${Math.abs(p2)} $sign " +
            "${N(absCross)} = ${signed(net)})"
        return Problem("${signed(p1)}% then ${signed(p2)}%<br>net % change?", net.toDouble(),
            signed(net) + "%", explanation = expl, hint = "net % change, e.g. 2 or −9.25")
    }

    fun reverseProblem(p: Fraction, up: Boolean, new: Int): Problem {
        val sp = if (up) p else -p
        val m = frac(1) + sp / 100L
        val orig = Fraction.of(new.toLong()) / m
        val naive = (frac(1) - sp / 100L) * new.toLong()
        val word = if (up) "increase" else "decrease"
        val expl = "new = original × ${N(m)}, so original = ${N(new)} ÷ ${N(m)} = ${N(new)} × " +
            "${m.den}/${m.num} = <b>${N(orig)}</b>  (check: ${N(orig)} × ${N(m)} = ${N(new)})<br>" +
            "(Not ${N(new)} ${if (up) "−" else "+"} ${pct(p)} = ${N(naive)} — the ${pct(p)} " +
            "$word was of the original, not of ${N(new)}.)"
        return Problem("After ${if (up) "+" else "−"}${pct(p)}: ${N(new)}<br>original value?",
            orig.toDouble(), N(orig), explanation = expl)
    }

    private fun genPct4(rng: Rng): Problem {
        if (rng.random() < 0.5) {
            val choices = listOf(5, 10, 15, 20, 25, 30, 40, 50)
            val p1 = rng.choice(choices) * rng.choice(listOf(1, -1))
            val p2 = rng.choice(choices) * rng.choice(listOf(1, -1))
            return successiveProblem(p1, p2)
        }
        val up = rng.random() < 0.6
        val pool = listOf(frac(5), frac(10), frac(25, 2), frac(15), frac(20), frac(25), frac(30), frac(40), frac(50)) +
            (if (up) listOf(frac(60), frac(75)) else emptyList())
        val p = rng.choice(pool)
        val m = frac(1) + (if (up) p else -p) / 100L
        val md = m.den.toInt()
        val orig = md * rng.randint(maxOf(1, 20 / md), maxOf(2, 800 / md))
        return reverseProblem(p, up, truncInt(m * orig.toLong()).toInt())
    }

    private val PCT_LESSON = Lesson("Percentages",
        "\n" +
        "<h3>Building blocks</h3>\n" +
        "<p>10% = move the decimal point. 1% = move it twice. Build everything else from these.</p>\n" +
        "<ex>17% of 340<br>10% = 34<br>1% = 3.4<br>7% = 7 × 3.4 = 23.8<br>34 + 23.8 = <b>57.8</b></ex>\n" +
        "<ul>\n" +
        "<li>5% = half of 10%</li>\n" +
        "<li>15% = 10% + 5%</li>\n" +
        "<li>25% = ÷ 4</li>\n" +
        "<li>12.5% = ÷ 8</li>\n" +
        "<li>92% = 100% − 8%</li>\n" +
        "</ul>\n" +
        "<h3>Swap trick</h3>\n" +
        "<p><b>x% of y = y% of x.</b> Pick the easier order.</p>\n" +
        "<ex>18% of 50 = 50% of 18 = <b>9</b><br>4% of 75 = 75% of 4 = <b>3</b></ex>\n" +
        "<h3>Percent change</h3>\n" +
        "<p>Change ÷ starting value. Always divide by where you <i>started</i>.</p>\n" +
        "<ex>80 → 92<br>92 − 80 = 12<br>12/80 = 0.15 → <b>+15%</b></ex>\n" +
        "<tip>Quick estimate: find 1% of the start (0.8), then ask how many of those fit in the change: 12 ÷ 0.8 = 15.</tip>\n" +
        "<h3>Successive changes</h3>\n" +
        "<p>Changes multiply. They don't add.</p>\n" +
        "<ex>+20% then −15%<br>1.2 × 0.85 = 1.02<br>→ <b>+2%</b>, not +5%</ex>\n" +
        "<p>Shortcut: a + b + ab/100.</p>\n" +
        "<ex>a = 20, b = −15, so ab/100 = −3<br>20 − 15 − 3 = <b>2</b></ex>\n" +
        "<tip>+10% then −10% is −1%, not 0.</tip>\n" +
        "<h3>Reverse percent</h3>\n" +
        "<p>To undo a change, divide by the factor.</p>\n" +
        "<ex>\"After a 25% increase it's 150\"<br>150 = 1.25 × x<br>x = 150 ÷ 1.25 = 150 × 4/5<br>" +
        "x = <b>120</b></ex>\n" +
        "<tip>The classic mistake is subtracting 25% of 150, giving 112.5.</tip>\n" +
        "<p>Know the fraction forms:</p>\n" +
        "<ex>1.25 = 5/4, 1.2 = 6/5<br>0.8 = 4/5, 0.75 = 3/4<br>1.125 = 9/8, 0.875 = 7/8</ex>\n")

    private val S_PCT = Skill("s_pct", "Percentages", TRACK, PCT_LESSON, listOf(
        Level("friendly % of friendly numbers", 4.0, ::genPct1),
        Level("any % of a number", 7.0, ::genPct2),
        Level("percent change", 8.0, ::genPct3),
        Level("successive changes & reverse percent", 10.0, ::genPct4),
    ))

    override val SKILLS: List<Skill> = listOf(S_FRAC, S_PCT)
}
