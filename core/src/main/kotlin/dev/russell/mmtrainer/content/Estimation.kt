package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.floorDiv
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.isClose
import dev.russell.mmtrainer.py.isInteger
import dev.russell.mmtrainer.py.pyRound

/*
 * Estimation track: scientific notation, fast (Fermi-style) estimation, reciprocals.
 * Port of mmtrainer/content/estimation.py.
 *
 * Generators draw operands and hand them to pure builders, so the explanations depend only on
 * the numbers. Explanations always end with the mental estimate in bold, which the tests check
 * against the tolerance.
 */
object Estimation : ContentModule {
    private const val TRACK = "estimation"

    // Mantissas that are easy to multiply/divide with mentally (used for balanced rounding).
    private val FRIENDLY = listOf(1.0, 1.2, 1.25, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 6.0, 7.0, 7.5, 8.0, 9.0, 10.0)

    private const val HINT_ANY = "any form, e.g. 2.4e7"

    // ---------------------------------------------------------------- formatting helpers

    private fun neg(x: Int): String = x.toString().replace("-", "−")

    private fun p10(e: Int): String = "10<sup>${neg(e)}</sup>"

    private fun paren(e: Int): String = if (e < 0) "(${neg(e)})" else e.toString()

    private fun pow10(e: Int): Double = Math.pow(10.0, e.toDouble())

    private fun isExact(x: Double, sig: Int): Boolean = isClose(sigRound(x, sig), x, relTol = 1e-9, absTol = 0.0)

    /** Plain notation for moderate magnitudes, scientific otherwise. */
    private fun fmt(x: Double, sig: Int = 3): String {
        if (x == 0.0) return "0"
        if (1e-3 <= Math.abs(x) && Math.abs(x) < 1e6) return fmtNum(x, sig = sig)
        return fmtSci(x, sig)
    }

    /** "= 42" if x is exactly representable at [sig] figures, else "≈ 0.286". */
    private fun eq(x: Double, sig: Int = 3): String = (if (isExact(x, sig)) "= " else "≈ ") + fmt(x, sig)

    /** display_answer: the true value, prefixed with ≈ when rounded. */
    private fun approx(x: Double, sig: Int = 3): String = (if (isExact(x, sig)) "" else "≈ ") + fmt(x, sig)

    /** x -> (mantissa in [1, 10), exponent). */
    private fun split(x: Double): Pair<Double, Int> {
        var e = Math.floor(Math.log10(Math.abs(x))).toInt()
        var m = x / pow10(e)
        if (Math.abs(m) >= 10 - 1e-9) {
            m /= 10; e += 1
        } else if (Math.abs(m) < 1 - 1e-12) {
            m *= 10; e -= 1
        }
        return pyRound(m, 9) to e
    }

    /** "5 + (−3) − 8 = −6" (ops[0] ignored; "×" adds, "÷" subtracts). */
    private fun expSum(exps: List<Int>, ops: List<String>): Pair<String, Int> {
        var total = exps[0]
        val parts = mutableListOf(neg(exps[0]))
        for ((e, op) in exps.drop(1).zip(ops.drop(1))) {
            if (op == "×") {
                total += e
                parts.add("+ ${paren(e)}")
            } else {
                total -= e
                parts.add("− ${paren(e)}")
            }
        }
        return parts.joinToString(" ") + " = ${neg(total)}" to total
    }

    private fun apply(vals: List<Double>, ops: List<String>): Double {
        var acc = vals[0]
        for ((v, op) in vals.drop(1).zip(ops.drop(1))) {
            acc = if (op == "×") acc * v else acc / v
        }
        return acc
    }

    private fun pct(p: Double): String = (if (p >= 0) "+" else "−") + fmtF(Math.abs(p) * 100, 1) + "%"

    /** Python 3.12+ sum() of floats (Neumaier compensated summation). */
    private fun pySum(xs: List<Double>): Double {
        var f = 0.0
        var c = 0.0
        for (x in xs) {
            val t = f + x
            c += if (Math.abs(f) >= Math.abs(x)) (f - t) + x else (x - t) + f
            f = t
        }
        if (c != 0.0 && c.isFinite()) f += c
        return f
    }

    // ---------------------------------------------------------------- scientific notation

    private fun randExp(rng: Rng, lo: Int = -12, hi: Int = 12): Int = rng.choice((lo..hi).filter { it != 0 })

    /** Mantissa in [1, 10) with exactly [sig] significant figures (no trailing zero). */
    private fun randMant(rng: Rng, sig: Int): Double {
        val scale = pow10i(sig - 1)
        val lo = if (sig == 1) 2 else scale + 1
        while (true) {
            val n = rng.randint(lo, pow10i(sig) - 1)
            if (sig == 1 || n % 10 != 0) return n.toDouble() / scale.toDouble()
        }
    }

    private fun pow10i(k: Int): Int {
        var r = 1
        repeat(k) { r *= 10 }
        return r
    }

    /** "3.7 × 6.2 = 22.2 + 0.74 ≈ 22.9" (distribute over b's integer and decimal parts). */
    private fun mantProductText(a: Double, b: Double): String {
        val bi = b.toInt()
        val bf = pyRound(b - bi, 6)
        val head = "${fmtNum(a)} × ${fmtNum(b)}"
        if (bf == 0.0 || bi == 0) return "$head ${eq(a * b)}"
        return "$head = ${fmtNum(a * bi, sig = 4)} + ${fmtNum(a * bf, sig = 3)} ${eq(a * b)}"
    }

    /** "3.7 ÷ 6.2 ≈ 0.597 (check: 6.2 × 0.6 = 3.72)". */
    private fun mantQuotientText(a: Double, b: Double, check: Boolean): String {
        val q = a / b
        var text = "${fmtNum(a)} ÷ ${fmtNum(b)} ${eq(q)}"
        if (check && !isExact(q, 3)) {
            val q1 = sigRound(q, 1)
            text += " (check: ${fmtNum(b)} × ${fmtNum(q1)} = ${fmtNum(b * q1, sig = 4)})"
        }
        return text
    }

    /** (m0 × 10^e0) op1 (m1 × 10^e1) [op2 (m2 × 10^e2)]. */
    fun sciProblem(mants: List<Double>, exps: List<Int>, ops: List<String>, tolRel: Double, sig: Int): Problem {
        val vals = mants.zip(exps).map { (m, e) -> m * pow10(e) }
        val terms = vals.map { "(${fmtSci(it, sig)})" }
        val prompt = terms[0] + ops.drop(1).zip(terms.drop(1)).joinToString("") { (op, t) -> " $op $t" }
        val answer = apply(vals, ops)

        val mtext: String
        if (mants.size == 2) {
            mtext = if (ops[1] == "×") {
                if (sig > 1) mantProductText(mants[0], mants[1])
                else "${fmtNum(mants[0])} × ${fmtNum(mants[1])} ${eq(mants[0] * mants[1])}"
            } else {
                mantQuotientText(mants[0], mants[1], check = sig > 1)
            }
        } else {
            val first = apply(mants.take(2), ops.take(2))
            mtext = "${fmtNum(mants[0])} ${ops[1]} ${fmtNum(mants[1])} ${eq(first)}; " +
                "${fmt(first)} ${ops[2]} ${fmtNum(mants[2])} ${eq(apply(mants, ops))}"
        }
        val bigM = sigRound(apply(mants, ops), 3)
        val (etext, bigE) = expSum(exps, ops)
        val final = bigM * pow10(bigE)
        val last = if (1 <= Math.abs(bigM) && Math.abs(bigM) < 10) "→ <b>${fmtSci(final, 3)}</b>"
        else "→ ${fmtNum(bigM)} × ${p10(bigE)} = <b>${fmtSci(final, 3)}</b>"
        val expl = "Mantissas: $mtext<br>Exponents: $etext<br>$last"
        val display = (if (isExact(answer, 3)) "" else "≈ ") + fmtSci(answer, 3)
        return Problem(prompt, answer, display, tolRel = tolRel, explanation = expl, hint = HINT_ANY)
    }

    private val MULDIV = listOf("×", "÷")

    private fun sciL1(rng: Rng): Problem {
        val mants = listOf(randMant(rng, 1), randMant(rng, 1))
        val exps = listOf(randExp(rng), randExp(rng))
        return sciProblem(mants, exps, listOf("×", rng.choice(MULDIV)), 0.01, 1)
    }

    private fun sciL2(rng: Rng): Problem {
        val mants = listOf(randMant(rng, 2), randMant(rng, 2))
        val exps = listOf(randExp(rng, -15, 15), randExp(rng, -15, 15))
        return sciProblem(mants, exps, listOf("×", rng.choice(MULDIV)), 0.03, 2)
    }

    private fun sciL3(rng: Rng): Problem {
        // A × B ÷ C: unambiguous however it is read.
        val mants = MutableList(3) { randMant(rng, rng.choice(listOf(1, 2, 2))) }
        if (mants.all { it == it.toInt().toDouble() }) mants[0] = randMant(rng, 2)
        val exps = List(3) { randExp(rng, -15, 15) }
        return sciProblem(mants, exps, listOf("×", "×", "÷"), 0.05, 2)
    }

    // ---------------------------------------------------------------- fast estimation

    /** A "messy" number like 347, 0.0219 or 6 140 (not already a friendly value). */
    private fun messy(rng: Rng, lo: Int, hi: Int, sigs: List<Int> = listOf(2, 3, 3)): Double {
        while (true) {
            val sig = rng.choice(sigs)
            val n = rng.randint(pow10i(sig - 1) + 1, pow10i(sig) - 1)
            if (n % 10 == 0) continue
            val m = n.toDouble() / pow10i(sig - 1).toDouble()
            if (FRIENDLY.any { Math.abs(m - it) < 1e-9 }) continue
            return sigRound(m * pow10(rng.randint(lo, hi)), sig)
        }
    }

    /** The friendly (mantissa, exponent) just below and just above v. */
    private fun friendlyOptions(v: Double): List<Pair<Double, Int>> {
        val (m, e) = split(v)
        val lo = FRIENDLY.filter { it <= m + 1e-12 }.max()
        val hi = FRIENDLY.filter { it >= m - 1e-12 }.min()
        val opts = mutableListOf(lo to e)
        if (hi != lo) opts.add(if (hi == 10.0) 1.0 to e + 1 else hi to e)
        return opts
    }

    /** Pick friendly roundings whose errors cancel, without rounding any one number too far. */
    private fun balancedRounding(vals: List<Double>, ops: List<String>): List<Pair<Double, Int>> {
        val truth = apply(vals, ops)
        var best: List<Pair<Double, Int>>? = null
        var bestErr = Double.POSITIVE_INFINITY
        val options = vals.map { friendlyOptions(it) }

        fun rec(i: Int, chosen: List<Pair<Double, Int>>) {
            if (i == vals.size) {
                val rvals = chosen.map { (m, e) -> m * pow10(e) }
                val est = apply(rvals, ops)
                // net error matters most, but prefer close roundings (a human rounds to the nearer one)
                val err = Math.abs(Math.log(est / truth)) +
                    0.3 * pySum(rvals.zip(vals).map { (r, v) -> Math.abs(Math.log(r / v)) })
                if (err < bestErr - 1e-12) {
                    best = chosen.toList()
                    bestErr = err
                }
                return
            }
            for (opt in options[i]) rec(i + 1, chosen + listOf(opt))
        }

        rec(0, emptyList())
        return best!!
    }

    /** Product/quotient chain: v0 op1 v1 op2 v2 ... (at most one trailing ÷). */
    fun fermiProblem(vals: List<Double>, ops: List<String>, tolRel: Double): Problem {
        val prompt = fmtNum(vals[0]) + ops.drop(1).zip(vals.drop(1)).joinToString("") { (op, v) -> " $op ${fmtNum(v)}" }
        val answer = apply(vals, ops)
        val rounded = balancedRounding(vals, ops)
        val rvals = rounded.map { (m, e) -> m * pow10(e) }
        val mants = rounded.map { it.first }
        val bigM = apply(mants, ops)
        val (etext, _) = expSum(rounded.map { it.second }, ops)
        val est = apply(rvals, ops)
        val rexpr = fmtNum(sigRound(rvals[0], 3)) +
            ops.drop(1).zip(rvals.drop(1)).joinToString("") { (op, r) -> " $op ${fmtNum(sigRound(r, 3))}" }
        val mexpr = fmtNum(mants[0]) + ops.drop(1).zip(mants.drop(1)).joinToString("") { (op, m) -> " $op ${fmtNum(m)}" }
        val line1 = "≈ $rexpr: mantissas $mexpr ${eq(bigM)}, exponents $etext → ${fmt(est)}"

        // first-order error bookkeeping: each rounding's effect on the result
        val effects = mutableListOf<Double>()
        val parts = mutableListOf<String>()
        for (i in vals.indices) {
            val v = vals[i]
            val r = rvals[i]
            val op = ops[i]
            var p = r / v - 1
            if (op == "÷") p = -p
            effects.add(p)
            if (Math.abs(p) > 1e-12) {
                parts.add("${if (op == "÷") op else ""}${fmtNum(v)}→${fmtNum(sigRound(r, 3))} ${pct(p)}")
            }
        }
        val net = pySum(effects)
        val line2: String
        val line3: String
        if (Math.abs(net) < 0.015) {
            line2 = "Errors: ${parts.joinToString(", ").ifEmpty { "none" }} → net ${pct(net)}, they cancel"
            line3 = "→ ≈ <b>${fmt(est)}</b>"
        } else {
            val corrected = est * (1 - net)
            line2 = "Errors: ${parts.joinToString(", ")} → estimate ${pct(net)} ${if (net > 0) "high" else "low"}"
            line3 = "${fmt(est)} ${if (net > 0) "−" else "+"} ${fmtF(Math.abs(net) * 100, 1)}% ≈ <b>${fmt(corrected)}</b>"
        }
        return Problem(prompt, answer, approx(answer), tolRel = tolRel,
            explanation = "$line1<br>$line2<br>$line3", hint = HINT_ANY)
    }

    private fun fermiL1(rng: Rng): Problem {
        val vals = mutableListOf(messy(rng, -3, 3), messy(rng, -2, 3))
        rng.shuffle(vals)
        return fermiProblem(vals, listOf("×", "×"), 0.05)
    }

    private fun fermiL2(rng: Rng): Problem {
        val n = rng.choice(listOf(3, 4))
        val divide = rng.random() < 0.7
        val vals = MutableList(n) { messy(rng, -3, 3) }
        if (vals.all { it >= 1 }) {  // make sure a decimal appears
            // Python evaluates the right-hand side before the subscript.
            val v = messy(rng, -3, -1)
            vals[rng.randrange(n)] = v
        }
        val ops = MutableList(n) { "×" }
        if (divide) ops[n - 1] = "÷"
        return fermiProblem(vals, ops, 0.10)
    }

    private data class HalfRoot(val est: Double, val a: Double, val text: String)

    /** √M via the nearest integer/half-integer anchor a: √M ≈ a + (M − a²)/(2a). */
    private fun halfRoot(bigM: Double): HalfRoot {
        val a = maxOf(1.0, pyRound(2 * Math.sqrt(bigM)).toDouble() / 2)
        val d = bigM - a * a
        val est = a + d / (2 * a)
        val sign = if (d >= 0) "+" else "−"
        val text = "√${fmtNum(bigM)} ≈ ${fmtNum(a)} $sign ${fmtNum(Math.abs(d), sig = 3)}/${fmtNum(2 * a)} " +
            "≈ ${fmtNum(est, sig = 3)} (${fmtNum(a)}² = ${fmtNum(a * a)})"
        return HalfRoot(est, a, text)
    }

    private val SQRT_EXPS = listOf(-5, -4, -3, 3, 4, 5, 6, 7, 8, 9)

    /** A radicand m × 10^k whose even-exponent mantissa isn't a perfect (half-)square. */
    private fun sqrtRadicand(rng: Rng): Pair<Double, String> {
        while (true) {
            val m = randMant(rng, 2)
            val k = rng.choice(SQRT_EXPS)
            val bigM = if (k % 2 == 0) m else m * 10
            val r2 = 2 * Math.sqrt(bigM)
            if (Math.abs(r2 - pyRound(r2)) > 1e-9) {
                val x = sigRound(m * pow10(k), 2)
                val shown = if (Math.abs(k) >= 5) fmtSci(x, 2) else fmtNum(x)
                return x to shown
            }
        }
    }

    private data class Steps(val est: Double, val exp: Int, val text: String)

    /** √x = √M × 10^(E/2) with E even. Returns (mantissa estimate, exponent, text). */
    private fun sqrtSteps(x: Double): Steps {
        val (m, k) = split(x)
        val (bigM, bigE) = if (k % 2 == 0) m to k else pyRound(m * 10, 6) to k - 1
        val hr = halfRoot(bigM)
        val shown = if (bigE == 0) fmtNum(bigM) else "${fmtNum(bigM)} × ${p10(bigE)}"
        val half = floorDiv(bigE, 2)
        return Steps(hr.est, half, "√($shown) = √${fmtNum(bigM)} × ${p10(half)}; ${hr.text}")
    }

    /** "× 0.035: 6.48 × 3.5 ≈ 22.7, exponents 3 + (−2) = 1 → ≈ <b>227</b>". */
    private fun combineText(am: Double, ae: Int, op: String, y: Double): Pair<String, Double> {
        val (ym, ye) = split(y)
        val r = if (op == "×") am * ym else am / ym
        val (etext, bigE) = expSum(listOf(ae, ye), listOf("×", op))
        val final = r * pow10(bigE)
        val text = "$op ${fmtNum(y)}: ${fmtNum(am, sig = 3)} $op ${fmtNum(ym)} ${eq(r)}, exponents $etext " +
            "→ ≈ <b>${fmt(final)}</b>"
        return text to final
    }

    /** x² = m² × 10^(2e) with the mantissa square shown via a memorized square or (a+b)². */
    private fun squareText(x: Double): Steps {
        val (m, e) = split(x)
        val sq = m * m
        val r = pyRound(m * 10)
        val how = if (r <= 30) {
            "$r² = ${r * r}"
        } else {
            val a = m.toInt()
            val b = pyRound(m - a, 6)
            "${a * a} + 2·$a·${fmtNum(b)} + ${fmtNum(b * b)}"
        }
        return Steps(sq, 2 * e,
            "${fmtNum(x)}² = ${fmtNum(m)}² × ${p10(2 * e)}; ${fmtNum(m)}² = ${fmtNum(sq, sig = 4)} ($how)")
    }

    private val L3_KINDS = listOf("sqrt_mul", "sq_div", "div_sqrt")

    private fun fermiL3(rng: Rng): Problem {
        val kind = rng.choice(L3_KINDS)
        val prompt: String
        val answer: Double
        val expl: String
        if (kind == "sqrt_mul") {
            val (x, shown) = sqrtRadicand(rng)
            val y = messy(rng, -3, 3)
            prompt = "√($shown) × ${fmtNum(y)}"
            answer = Math.sqrt(x) * y
            val s = sqrtSteps(x)
            val (ctext, _) = combineText(s.est, s.exp, "×", y)
            expl = "${s.text}<br>$ctext"
        } else if (kind == "sq_div") {
            val x = messy(rng, -3, 4, sigs = listOf(2))
            val y = messy(rng, -2, 3)
            prompt = "${fmtNum(x)}<sup>2</sup> ÷ ${fmtNum(y)}"
            answer = x * x / y
            val s = squareText(x)
            val (ctext, _) = combineText(s.est, s.exp, "÷", y)
            expl = "${s.text}<br>$ctext"
        } else {
            val (y, shown) = sqrtRadicand(rng)
            val x = messy(rng, -2, 4)
            prompt = "${fmtNum(x)} ÷ √($shown)"
            answer = x / Math.sqrt(y)
            val s = sqrtSteps(y)
            val (xm, xe) = split(x)
            val r = xm / s.est
            val (etext, bigE) = expSum(listOf(xe, s.exp), listOf("×", "÷"))
            val final = r * pow10(bigE)
            val ctext = "${fmtNum(xm)} ÷ ${fmtNum(s.est, sig = 3)} ${eq(r)}, exponents $etext " +
                "→ ≈ <b>${fmt(final)}</b>"
            expl = "${s.text}<br>$ctext"
        }
        return Problem(prompt, answer, approx(answer), tolRel = 0.10, explanation = expl, hint = HINT_ANY)
    }

    // ---------------------------------------------------------------- reciprocals

    /** Integers 2..100 whose reciprocal follows directly from memorized 1/2 … 1/16. */
    private fun niceReciprocals(): Map<Int, String> {
        val nice = LinkedHashMap<Int, String>()
        for (n in 2..100) {
            val inv = 1.0 / n
            val v = fmtNum(inv, sig = 3)
            if (n <= 16) {
                nice[n] = if (!isExact(inv, 4)) "1/$n ≈ $v" else "1/$n = ${fmtNum(inv)}"
            } else if (n % 10 == 0) {
                val k = n / 10
                nice[n] = "1/$n = 1/$k ÷ 10 ${eq(inv)}"
            } else {
                for (p in 2..16) {
                    val q = n / p
                    val r = n % p
                    if (r == 0 && p <= q && q <= 16) {
                        nice[n] = "1/$n = 1/$q ÷ $p = ${fmtNum(1.0 / q, sig = 3)} ÷ $p ${eq(inv)}"
                        break
                    }
                }
            }
        }
        return nice
    }

    val NICE: Map<Int, String> = niceReciprocals()

    /** Python str.rsplit(" ", 2)[0]. */
    private fun rsplit2Head(s: String): String {
        val i1 = s.lastIndexOf(' ')
        if (i1 < 0) return s
        val i2 = s.lastIndexOf(' ', i1 - 1)
        return if (i2 < 0) s.substring(0, i1) else s.substring(0, i2)
    }

    /**
     * Rich-text steps for 1/y (10 ≤ y < 100) via a nice anchor, direct or with the first-order
     * correction. [tail] renders the end of the last line from the estimate of 1/y.
     */
    private fun recipSteps(y: Double, tail: (Double) -> String): String {
        if (y.isInteger() && y.toInt() in NICE) {
            val n = y.toInt()
            val head = if (n > 16) rsplit2Head(NICE.getValue(n)) else "1/$n"
            return "$head ${tail(1 / y)}"
        }
        // min(NICE, key=lambda n: (abs(y - n) / n, n % 10 != 0)): first minimum wins
        var a = -1
        var bestD = 0.0
        var bestFlag = false
        for (n in NICE.keys) {
            val dk = Math.abs(y - n) / n
            val flag = n % 10 != 0
            if (a < 0 || dk < bestD || (dk == bestD && !flag && bestFlag)) {
                a = n; bestD = dk; bestFlag = flag
            }
        }
        val d = y - a
        val base = 1.0 / a
        val corr = base * Math.abs(d) / a
        val est = if (d > 0) base - corr else base + corr
        val sign = if (d > 0) "+" else "−"
        val inner = if (d > 0) "−" else "+"
        return "Anchor ${NICE.getValue(a)}<br>${fmtNum(y)} = $a $sign ${fmtNum(Math.abs(d))} → 1/${fmtNum(y)} ≈ " +
            "${fmtNum(base, sig = 3)} × (1 $inner ${fmtNum(Math.abs(d))}/$a) ≈ ${fmtNum(base, sig = 3)} " +
            "$inner ${fmtNum(corr, sig = 2)} ${tail(est)}"
    }

    fun recipProblem(x: Double, tolRel: Double): Problem {
        val (m, k) = split(x)
        val y = pyRound(m * 10, 6)  // 10 ≤ y < 100
        val shift = 1 - k           // 1/x = (1/y) × 10^shift
        val prompt = if (k in -4..4) "1/${fmtNum(x)}" else "1/(${fmtSci(x, 3)})"
        val answer = 1 / x
        val expl = if (shift == 0) {
            recipSteps(y) { est -> "≈ <b>${fmt(est)}</b>" }
        } else {
            val xs = if (k in -4..4) fmtNum(x) else fmtSci(x, 3)
            val lead = "$xs = ${fmtNum(y)} × ${p10(k - 1)} → 1/x = (1/${fmtNum(y)}) × ${p10(shift)}"
            val steps = recipSteps(y) { est ->
                "≈ ${fmtNum(est, sig = 3)} → × ${p10(shift)} ≈ <b>${fmt(est * pow10(shift))}</b>"
            }
            "$lead<br>$steps"
        }
        val hint = if (shift == 0) "decimal, e.g. 0.0435" else "any form, e.g. 2.4e-6"
        return Problem(prompt, answer, approx(answer), tolRel = tolRel, explanation = expl, hint = hint)
    }

    private fun recipL1(rng: Rng): Problem {
        while (true) {
            val n = rng.randint(17, 99)
            if (n % 10 != 0 && n !in listOf(25, 50, 75)) return recipProblem(n.toDouble(), 0.01)
        }
    }

    private val RECIP_EXPS = (-6..6).filter { it != 0 && it != 1 }

    private fun recipL2(rng: Rng): Problem {
        val sig = rng.choice(listOf(2, 3))
        val m = randMant(rng, sig)
        val k = rng.choice(RECIP_EXPS)
        return recipProblem(sigRound(m * pow10(k), sig), 0.015)
    }

    // ---------------------------------------------------------------- lessons

    private val SCI_LESSON = Lesson(
        "Scientific notation",
        "\n" +
            "<p>Split every number into <b>mantissa × power of ten</b> and handle the two parts separately:</p>\n" +
            "<ul>\n" +
            "<li><b>Multiply:</b> multiply the mantissas, <b>add</b> the exponents.</li>\n" +
            "<li><b>Divide:</b> divide the mantissas, <b>subtract</b> the exponents.</li>\n" +
            "<li><b>Renormalize:</b> if the mantissa lands outside 1–10, shift one place and fix the exponent\n" +
            "(42 × 10<sup>2</sup> = 4.2 × 10<sup>3</sup>; 0.286 × 10<sup>11</sup> = 2.86 × 10<sup>10</sup>).</li>\n" +
            "</ul>\n" +
            "<p><b>Example:</b> (6 × 10<sup>5</sup>) × (7 × 10<sup>−3</sup>): 6 × 7 = 42, 5 + (−3) = 2 →\n" +
            "42 × 10<sup>2</sup> = <b>4.2 × 10<sup>3</sup></b>.</p>\n" +
            "<p><b>Example:</b> (3.7 × 10<sup>8</sup>) ÷ (6.2 × 10<sup>−4</sup>): 3.7 ÷ 6.2 ≈ 0.6\n" +
            "(check 6.2 × 0.6 = 3.72), so ≈ 0.597; 8 − (−4) = 12 → <b>5.97 × 10<sup>11</sup></b>.</p>\n" +
            "<p>Tips: say the exponent bookkeeping out loud — sign errors with negative exponents are the\n" +
            "#1 mistake. Use your reciprocal anchors for division (÷ 8 = × 0.125, ÷ 7 ≈ × 0.143). For\n" +
            "chains, do all the exponents in one pass at the end. Type answers as e.g. <i>5.97e11</i>.</p>\n",
    )

    private val FERMI_LESSON = Lesson(
        "Fast estimation",
        "\n" +
            "<p>Goal: a few-percent answer in seconds. Three habits:</p>\n" +
            "<ul>\n" +
            "<li><b>Round to friendly numbers — balanced.</b> Friendly mantissas: 1, 1.2, 1.25, 1.5, 2, 2.5,\n" +
            "3, 3.5, 4, 5, 6, 7, 7.5, 8, 9. In a product, round one factor <i>up</i> and another <i>down</i>; in a\n" +
            "quotient, round top and bottom the <i>same</i> way.</li>\n" +
            "<li><b>Track the exponent separately.</b> Write each number as mantissa × 10<sup>n</sup>, multiply the\n" +
            "small mantissas, add up the exponents at the end.</li>\n" +
            "<li><b>Quantify the rounding.</b> Each rounding changes the result by about its own percentage\n" +
            "(÷ flips the sign). Add them up and correct: small errors add, to first order.</li>\n" +
            "</ul>\n" +
            "<p><b>Example:</b> 347 × 58 ≈ 350 × 60 = 21 000. Errors: +0.9% and +3.4% → ≈ 4.3% high →\n" +
            "21 000 − 4.3% ≈ <b>20 100</b> (true 20 126).</p>\n" +
            "<p><b>Example:</b> 347 × 0.0219 × 58 ÷ 7.3: 3.5 × 2 × 6 ÷ 7 = 6, exponents 2 − 2 + 1 − 0 = 1\n" +
            "→ 60. Errors +0.9%, −8.7%, +3.4%, ÷ 7.3→7 +4.3% → net ≈ 0 → <b>≈ 60</b> (true 60.4).</p>\n" +
            "<p><b>Roots and powers:</b> make the exponent even, then √M ≈ a + (M − a²)/(2a) using the\n" +
            "nearest whole or half square ((n + ½)² = n(n+1) + ¼, e.g. 6.5² = 42.25):\n" +
            "√(4.2 × 10<sup>7</sup>) = √42 × 10<sup>3</sup> ≈ 6.48 × 10<sup>3</sup>. Squares: 3 600² = 3.6² × 10<sup>6</sup> = 12.96 × 10<sup>6</sup>.</p>\n",
    )

    private val RECIP_LESSON = Lesson(
        "Reciprocals",
        "\n" +
            "<p>Every reciprocal comes from your memorized 1/2 … 1/16 by one of two moves.</p>\n" +
            "<p><b>1. Factor.</b> If x splits into two anchors, divide one reciprocal by the other factor:\n" +
            "1/24 = 1/12 ÷ 2 = 0.0833 ÷ 2 = <b>0.0417</b>; 1/42 = 1/7 ÷ 6 = 0.143 ÷ 6 = 0.0238;\n" +
            "1/70 = 1/7 ÷ 10 = 0.0143.</p>\n" +
            "<p><b>2. Anchor + correction.</b> Otherwise pick the nearest \"nice\" number a and use</p>\n" +
            "<p>&nbsp;&nbsp;1/(a + d) ≈ (1/a)·(1 − d/a)</p>\n" +
            "<p>— a number <i>bigger</i> than the anchor has a slightly <i>smaller</i> reciprocal, by the same\n" +
            "percentage. The error is only (d/a)², so with d/a ≈ 3% you're good to 0.1%.<br>\n" +
            "1/37: anchor 36 = 4 × 9, 1/36 = 0.111 ÷ 4 = 0.0278; 37 is 1/36 ≈ 2.8% bigger →\n" +
            "0.0278 − 0.0008 = <b>0.0270</b> (true 0.02703).<br>\n" +
            "1/59: 1/60 = 0.01667, 59 is 1/60 smaller → 0.01667 + 0.00028 = 0.01695.</p>\n" +
            "<p><b>Decimals and exponents:</b> slide the decimal point first, then do a 2-digit reciprocal:\n" +
            "1/0.0042 = 10<sup>4</sup>/42 ≈ 0.0238 × 10<sup>4</sup> = <b>238</b>;\n" +
            "1/(3.7 × 10<sup>5</sup>) = (1/37) × 10<sup>−4</sup> ≈ 2.70 × 10<sup>−6</sup>.</p>\n",
    )

    // ---------------------------------------------------------------- skills

    override val SKILLS: List<Skill> = listOf(
        Skill("s_sci", "Scientific notation", TRACK, SCI_LESSON, listOf(
            Level("1-digit mantissas, × or ÷", 5.0, ::sciL1),
            Level("2-figure mantissas, × or ÷", 8.0, ::sciL2),
            Level("3-term chains (A × B ÷ C)", 12.0, ::sciL3),
        )),
        Skill("s_fermi", "Fast estimation", TRACK, FERMI_LESSON, listOf(
            Level("Product of two messy numbers (±5%)", 6.0, ::fermiL1),
            Level("3–4 term products/quotients (±10%)", 12.0, ::fermiL2),
            Level("With squares and square roots (±10%)", 16.0, ::fermiL3),
        )),
        Skill("s_recip", "Reciprocals", TRACK, RECIP_LESSON, listOf(
            Level("1/x for 2-digit x (±1%)", 6.0, ::recipL1),
            Level("1/x with decimals and exponents (±1.5%)", 8.0, ::recipL2),
        )),
    )
}
