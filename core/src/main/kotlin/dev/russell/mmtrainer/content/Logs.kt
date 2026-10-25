package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.gcd
import dev.russell.mmtrainer.py.ipow
import dev.russell.mmtrainer.py.isInteger
import dev.russell.mmtrainer.py.pyRound
import java.util.Collections
import java.util.WeakHashMap

/*
 * Logarithms: exact logs, common logs, powers of ten, natural logs, change of base.
 * Port of mmtrainer/content/logs.py.
 *
 * Everything is built from anchors memorized in other decks — log10 of 2…9, log10 e = 0.434,
 * ln 2/3/5/7/10 — plus factoring and a first-order correction:
 *
 *     log(a + Δ) ≈ log a + 0.434·Δ/a          ln(a + Δ) ≈ ln a + Δ/a
 *     10^(log a + d) ≈ a·(1 + d/0.434)         e^(ln a + d) ≈ a·(1 + d)
 *
 * The helpers below (anchors, LogEst, Pow10Est) are also used by Powers (hence `internal`).
 * Generators record the value their worked explanation arrives at via [withEstimate], so the
 * tests can check that the taught method actually lands inside the tolerance.
 */
object Logs : ContentModule {

    // ---------------------------------------------------------------- memorized anchors

    internal val LOG: Map<Int, Double> = linkedMapOf(
        1 to 0.0, 2 to 0.301, 3 to 0.477, 4 to 0.602, 5 to 0.699, 6 to 0.778, 7 to 0.845,
        8 to 0.903, 9 to 0.954, 10 to 1.0,
    )
    internal val LN: Map<Int, Double> = linkedMapOf(
        1 to 0.0, 2 to 0.693, 3 to 1.099, 4 to 1.386, 5 to 1.609, 6 to 1.792, 7 to 1.946,
        8 to 2.079, 9 to 2.197, 10 to 2.303,
    )
    internal const val LN10 = 2.303
    internal const val LOG_E = 0.434

    // ---------------------------------------------------------------- formatting helpers

    /** Unicode minus. */
    internal fun neg(s: String): String = s.replace("-", MINUS)
    internal fun neg(n: Int): String = neg(n.toString())
    internal fun neg(n: Long): String = neg(n.toString())

    /** Three decimals, unicode minus, no '−0.000'. */
    internal fun f3(x: Double): String {
        val r = pyRound(x, 3)
        return neg(fmtF(if (r != 0.0) r else 0.0, 3))
    }

    internal fun pm(x: Double): String = if (x >= 0) "+" else MINUS
    internal fun pm(x: Int): String = pm(x.toDouble())

    /** An exponent as rich text, e.g. sup(-2.5) -> '<sup>−2.5</sup>'. */
    internal fun sup(x: Double): String = "<sup>${fmtNum(pyRound(x, 6))}</sup>"
    internal fun sup(x: Int): String = sup(x.toDouble())

    internal fun p10(k: Int): String = "10${sup(k)}"

    /** A result, rounded: plain for moderate magnitudes, scientific otherwise. */
    internal fun fmtVal(x: Double, sig: Int = 3): String {
        if (x == 0.0) return "0"
        if (1e-4 <= Math.abs(x) && Math.abs(x) < 1e7) return fmtNum(x, sig = sig)
        return fmtSci(x, sig)
    }

    /** A number inside a prompt; wraps scientific notation in parentheses. */
    internal fun fmtArg(x: Double, sig: Int = 3): String {
        val s = fmtVal(x, sig)
        return if ("×" in s) "($s)" else s
    }

    private val ESTIMATES: MutableMap<Problem, Double> = Collections.synchronizedMap(WeakHashMap())

    /** Record the value the worked explanation arrives at (checked by the tests). */
    internal fun withEstimate(p: Problem, est: Double): Problem {
        synchronized(ESTIMATES) {
            ESTIMATES.remove(p)   // make this instance the (weak) key
            ESTIMATES[p] = est
        }
        return p
    }

    /** Python's `problem.mental_estimate` (null if the generator didn't record one). */
    internal fun mentalEstimate(p: Problem): Double? = ESTIMATES[p]

    /** A mantissa in [1, 10) with the given number of significant figures (no trailing 0). */
    internal fun randMantissa(rng: Rng, sigs: List<Int> = listOf(2, 3)): Double {
        val s = rng.choice(sigs)
        if (s == 1) return rng.randint(2, 9).toDouble()
        repeat(50) {
            val n = rng.randint(ipow(10, s - 1).toInt() + 1, ipow(10, s).toInt() - 1)
            if (n % 10 != 0) return n.toDouble() / ipow(10, s - 1).toDouble()
        }
        return 4.7
    }

    private fun short(v: Double): String = if (v.isInteger()) v.toLong().toString() else fmtF(v, 3)

    private fun sumText(terms: List<Pair<Int, Double>>): String {
        val out = StringBuilder()
        for ((i, t) in terms.withIndex()) {
            val (s, v) = t
            val txt = short(v)
            out.append(if (i == 0) (if (s > 0) txt else "−$txt") else " ${if (s > 0) "+" else "−"} $txt")
        }
        return out.toString()
    }

    // ---------------------------------------------------------------- anchors

    /** A mantissa in [1, 10] that factors into memorized numbers: prod(num)/prod(den)·10^shift. */
    internal data class Anchor(
        val value: Double,
        val num: List<Int> = emptyList(),
        val den: List<Int> = emptyList(),
        val shift: Int = 0,
    ) {
        /** A memorized single digit (incl. 1 and 10). */
        val simple: Boolean get() = den.isEmpty() && shift == 0 && num.size == 1

        val name: String get() = fmtNum(value)

        fun terms(kind: String = "log"): List<Pair<Int, Double>> {
            val table = if (kind == "log") LOG else LN
            val ten = if (kind == "log") 1.0 else LN10
            val terms = num.map { 1 to table.getValue(it) } + den.map { -1 to table.getValue(it) }
            return if (shift != 0) terms + ((if (shift > 0) 1 else -1) to Math.abs(shift) * ten) else terms
        }

        private fun total(kind: String): Double {
            var s = 0.0
            for ((sign, v) in terms(kind)) s += sign * v
            return pyRound(s, 3)
        }

        fun log(): Double = total("log")

        fun ln(): Double = total("ln")

        /** '3 × 4 / 10' — how the mantissa factors. */
        fun recipe(): String {
            val top = num.map { it.toString() } + List(maxOf(shift, 0)) { "10" }
            val bottom = den.map { it.toString() } + List(maxOf(-shift, 0)) { "10" }
            val s = top.joinToString(" × ").ifEmpty { "1" }
            return s + bottom.joinToString("") { " / $it" }
        }

        /** e.g. 'log 1.2 = log(3 × 4 / 10) = 0.477 + 0.602 − 1 = 0.079'. */
        fun derive(kind: String = "log"): String {
            val v = if (kind == "log") log() else ln()
            if (simple) return "$kind $name = ${f3(v)}"
            return "$kind $name = $kind(${recipe()}) = ${sumText(terms(kind))} = ${f3(v)}"
        }
    }

    internal val ANCHORS: List<Anchor>
    internal val DIGITS: List<Anchor>

    init {
        val found = LinkedHashMap<Double, Anchor>()
        fun add(value: Double, num: List<Int> = emptyList(), den: List<Int> = emptyList(), shift: Int = 0) {
            val key = pyRound(value, 6)
            if (key !in found) found[key] = Anchor(key, num, den, shift)
        }
        for (d in 1..10) add(d.toDouble(), num = listOf(d))
        for (a in 2 until 10) {                     // products of two digits: 1.2 = 3 × 4 / 10
            for (b in a until 10) {
                if (a * b >= 10) add((a * b).toDouble() / 10, num = listOf(a, b), shift = -1)
            }
        }
        for (b in listOf(2, 4, 5, 8)) {             // terminating quotients: 1.75 = 7 / 4
            for (a in 1 until 10) {
                if (gcd(a, b) == 1) {
                    val shift = if (a > b) 0 else 1
                    add(a.toDouble() / b * ipow(10, shift).toDouble(),
                        num = if (a > 1) listOf(a) else emptyList(), den = listOf(b), shift = shift)
                }
            }
        }
        ANCHORS = found.values.sortedBy { it.value }
        DIGITS = (1..10).map { found.getValue(it.toDouble()) }
    }

    /** Best anchor for a mantissa m in [1, 10]: a digit if within ~3 %, else the nearest. */
    internal fun anchorFor(m: Double): Anchor {
        val d = DIGITS.minBy { Math.abs(Math.log(m / it.value)) }
        if (Math.abs(Math.log(m / d.value)) <= 0.03) return d
        return ANCHORS.minBy { Math.abs(Math.log(m / it.value)) }
    }

    /** Best anchor whose log10 is near f in [0, 1]. */
    internal fun anchorForLog(f: Double): Anchor {
        val d = DIGITS.minBy { Math.abs(f - it.log()) }
        if (Math.abs(f - d.log()) <= 0.013) return d
        return ANCHORS.minBy { Math.abs(f - it.log()) }
    }

    /** Best anchor whose ln is near r in [0, ln 10]. */
    internal fun anchorForLn(r: Double): Anchor {
        val d = DIGITS.minBy { Math.abs(r - it.ln()) }
        if (Math.abs(r - d.ln()) <= 0.03) return d
        return ANCHORS.minBy { Math.abs(r - it.ln()) }
    }

    /** x = m × 10^k with m in [1, 10). */
    internal fun split10(x: Double): Pair<Double, Int> {
        var k = Math.floor(Math.log10(Math.abs(x))).toInt()
        var m = pyRound(Math.abs(x) / Math.pow(10.0, k.toDouble()), 9)
        if (m >= 10) {
            m /= 10; k += 1
        } else if (m < 1) {
            m *= 10; k -= 1
        }
        return m to k
    }

    // ---------------------------------------------------------------- mental log / antilog

    /** Mental log10 (or ln) of a mantissa m in [1, 10): nearest anchor + linear correction. */
    internal data class LogEst(
        val m: Double,
        val kind: String,
        val anchor: Anchor,
        val delta: Double,
        val corr: Double,
        val value: Double,
    ) {
        val exact: Boolean get() = Math.abs(delta) < 1e-9

        val base: Double get() = if (kind == "log") anchor.log() else anchor.ln()

        private fun factor(): String {
            val k = if (kind == "log") "0.434 × " else ""
            return "$k${fmtNum(Math.abs(delta), sig = 3)}/${anchor.name}"
        }

        fun lines(): List<String> {
            val a = anchor
            val out = if (a.simple) mutableListOf() else mutableListOf(a.derive(kind))
            if (exact) return if (out.isEmpty()) listOf(a.derive(kind)) else out
            val s = pm(delta)
            out.add("$kind ${fmtNum(m, sig = 4)} ≈ $kind ${a.name} $s ${factor()} " +
                "≈ ${f3(base)} $s ${f3(Math.abs(corr))} = ${f3(value)}")
            return out
        }

        /** One line, e.g. 'log 47 ≈ 1 + 0.699 − 0.434 × 0.3/5 = 1.673'. */
        fun compact(xLabel: String, k: Int = 0): String {
            val kpart = if (k != 0) "${neg(k)} + " else ""
            var body = f3(base)
            if (!exact) body += " ${pm(delta)} ${factor()}"
            val total = f3((if (kind == "log") k.toDouble() else k * LN10) + value)
            var s = if (exact && k == 0) {
                "$kind $xLabel = $total"
            } else {
                "$kind $xLabel ${if (exact) "=" else "≈"} $kpart$body = $total"
            }
            if (!anchor.simple) s += " (using ${anchor.name} = ${anchor.recipe()})"
            return s
        }
    }

    internal fun logEst(m: Double, kind: String = "log"): LogEst {
        val a = anchorFor(m)
        val base = if (kind == "log") a.log() else a.ln()
        val delta = m - a.value
        if (Math.abs(delta) < 1e-9) return LogEst(m, kind, a, 0.0, 0.0, base)
        val corr = pyRound((if (kind == "log") LOG_E else 1.0) * delta / a.value, 3)
        return LogEst(m, kind, a, delta, corr, pyRound(base + corr, 3))
    }

    /** Mental 10^y: split off the integer part, find the mantissa from the nearest anchor. */
    internal data class Pow10Est(
        val y: Double,
        val k: Int,
        val f: Double,
        val anchor: Anchor,
        val d: Double,
        val mant: Double,
    ) {
        val value: Double get() = mant * Math.pow(10.0, k.toDouble())

        fun lines(): List<String> {
            val a = anchor
            val out = mutableListOf<String>()
            if (k != 0) out.add("10${sup(y)} = 10${sup(f)} × ${p10(k)}")
            if (d == 0.0) {
                out.add("${a.derive("log")} → 10${sup(f)} ≈ ${a.name}")
            } else {
                val inc = a.value * Math.abs(d) / LOG_E
                out.add("${a.derive("log")}, so ${f3(f)} is ${pm(d)}${f3(Math.abs(d))} past it")
                out.add("10${sup(f)} ≈ ${a.name} ${pm(d)} ${a.name} × ${f3(Math.abs(d))}/0.434 " +
                    "≈ ${a.name} ${pm(d)} ${fmtNum(inc, sig = 2)} = ${fmtNum(mant, sig = 3)}")
            }
            if (k != 0) out.add("→ ${fmtNum(mant, sig = 3)} × ${p10(k)} ≈ ${fmtVal(value)}")
            return out
        }

        /** One line, e.g. '10^1.366 = 10^0.366 × 10: 0.366 = log 2.4 − 0.014 → … ≈ 23.2'. */
        fun compact(): String {
            val a = anchor
            var core = if (d == 0.0) {
                "${f3(f)} = log ${a.name} → 10${sup(f)} ≈ ${a.name}"
            } else {
                "${f3(f)} = log ${a.name} ${pm(d)} ${f3(Math.abs(d))} → ${a.name} × " +
                    "(1 ${pm(d)} ${f3(Math.abs(d))}/0.434) ≈ ${fmtNum(mant, sig = 3)}"
            }
            if (!a.simple) core += " (log ${a.name} = ${f3(a.log())})"
            if (k != 0) {
                return "10${sup(y)} = 10${sup(f)} × ${p10(k)}: $core → ${fmtVal(value)}"
            }
            return "10${sup(y)}: $core"
        }
    }

    internal fun pow10Est(y0: Double): Pow10Est {
        val y = pyRound(y0, 3)
        var k = Math.floor(y).toInt()
        var f = pyRound(y - k, 3)
        if (f >= 1) {
            f = pyRound(f - 1, 3); k += 1
        }
        val a = anchorForLog(f)
        val d = pyRound(f - a.log(), 3)
        return Pow10Est(y, k, f, a, d, a.value * (1 + d / LOG_E))
    }

    // ---------------------------------------------------------------- s_logint

    private fun fracText(q: Fraction): String = if (q.den != 1L) neg("${q.num}/${q.den}") else neg(q.num)

    private fun terminates(q: Fraction): Boolean {
        var d = q.den
        for (p in listOf(2L, 5L)) {
            while (d % p == 0L) d /= p
        }
        return d == 1L
    }

    // base: (lowest, highest) integer exponent
    private val INT_LOG: Map<Int, Pair<Int, Int>> = linkedMapOf(
        2 to (-6 to 10), 3 to (-4 to 6), 4 to (-3 to 5), 5 to (-3 to 4), 6 to (-2 to 3), 7 to (-2 to 3),
        8 to (-2 to 3), 9 to (-2 to 3), 10 to (-5 to 6), 11 to (2 to 2), 12 to (2 to 2), 16 to (-1 to 3),
        25 to (-1 to 3),
    )
    private val AS_POWER: Map<Int, Pair<Int, Int>> = linkedMapOf(
        4 to (2 to 2), 8 to (2 to 3), 16 to (2 to 4), 9 to (3 to 2), 25 to (5 to 2),
    )

    private fun logintL1(rng: Rng): Problem {
        val b = rng.choice(INT_LOG.keys.toList())
        val (lo, hi) = INT_LOG.getValue(b)
        val k = rng.choice((lo..hi).filter { it != 0 && it != 1 })
        val n = ipow(b, Math.abs(k))
        val arg = if (k > 0) {
            fmtNum(n)
        } else if (b == 10 || (b in listOf(2, 4, 5, 8, 16, 25) && n <= 10_000 && rng.random() < 0.5)) {
            fmtNum(1.0 / n)
        } else {
            "(1/${fmtNum(n)})"
        }
        var why = "${arg.trim('(', ')')} = "
        if (k < 0 && b != 10 && !arg.startsWith("(")) why += "1/${fmtNum(n)} = "
        if (b in AS_POWER && Math.abs(k) > 1) {
            val (r, p) = AS_POWER.getValue(b)
            why += "$r${sup(p * k)} = ($r${sup(p)})${sup(k)} = "
        }
        why += "$b${sup(k)} → log<sub>$b</sub> = ${neg(k)}"
        return Problem("log<sub>$b</sub> $arg", k.toDouble(), neg(k), explanation = why, hint = "exact")
    }

    private val FRAC_ROOTS: Map<Int, Int> = linkedMapOf(2 to 5, 3 to 4, 5 to 3, 10 to 3, 6 to 2, 7 to 2)   // root r: highest power used as a base

    private fun logintL2(rng: Rng): Problem {
        var r = 2
        var p = 3
        var q = 2
        var form = "pow"
        var found = false
        for (i in 0 until 200) {
            r = rng.choice(FRAC_ROOTS.keys.toList())
            p = rng.randint(1, FRAC_ROOTS.getValue(r))
            form = rng.choice(listOf("pow", "pow", "inv", "sqrt"))
            // Python builds a dict literal here, so all three draws happen whatever the form
            val qPow = rng.randint(1, 8)
            val qInv = -rng.randint(1, 4)
            val qSqrt = rng.choice(listOf(1, 3, 5, 7))
            q = when (form) { "pow" -> qPow; "inv" -> qInv; else -> qSqrt }
            val ans = Fraction.of(q, if (form == "sqrt") 2 * p else p)
            val limit = when (form) { "pow" -> 1_000_000L; "inv" -> 10_000L; else -> 100_000L }
            if (ans.den != 1L && ipow(r, Math.abs(q)) <= limit) {
                found = true
                break
            }
        }
        if (!found) {
            r = 2; p = 3; q = 2; form = "pow"
        }
        val ans = Fraction.of(q, if (form == "sqrt") 2 * p else p)
        val base = ipow(r, p)
        val mag = ipow(r, Math.abs(q))
        val arg: String
        val argPlain: String
        val qexp: Fraction
        if (form == "pow") {
            arg = fmtNum(mag); argPlain = fmtNum(mag); qexp = Fraction.of(q)
        } else if (form == "inv") {
            qexp = Fraction.of(q)
            if (r == 10 || (r in listOf(2, 5) && rng.random() < 0.5)) {
                arg = fmtNum(1.0 / mag); argPlain = arg
            } else {
                arg = "(1/${fmtNum(mag)})"; argPlain = "1/${fmtNum(mag)}"
            }
        } else {
            arg = "√${fmtNum(mag)}"; argPlain = arg
            qexp = Fraction.of(q, 2)
        }
        val parts = if (p == 1) mutableListOf() else mutableListOf("$base = $r${sup(p)}")
        parts.add("$argPlain = $r<sup>${fracText(qexp)}</sup>")
        var expl = parts.joinToString(", ") + "<br>→ log<sub>$base</sub> $argPlain = "
        val qtxt = if (qexp.den == 1L && q > 0) fracText(qexp) else "(${fracText(qexp)})"
        expl += if (p != 1) "$qtxt / $p = ${fracText(ans)}" else fracText(ans)
        val value = ans.toDouble()
        if (terminates(ans)) {
            return Problem("log<sub>$base</sub> $arg", value, "${fracText(ans)} = ${fmtNum(value)}",
                explanation = expl, hint = "fraction or decimal")
        }
        return Problem("log<sub>$base</sub> $arg", value, "${fracText(ans)} ≈ ${f3(value)}",
            tolAbs = 0.01, explanation = expl, hint = "decimal ok, e.g. 0.667")
    }

    // ---------------------------------------------------------------- s_log10

    private fun pow10(k: Int): Double = Math.pow(10.0, k.toDouble())

    private fun splitLine(m: Double, k: Int): String =
        "${fmtNum(m * pow10(k), sig = 4)} = ${fmtNum(m, sig = 4)} × ${p10(k)}"

    private fun log10Problem(m: Double, k: Int, mant: Double, lines0: List<String>, tol: Double, hint: String): Problem {
        val est = pyRound(k + mant, 3)
        var lines = lines0
        if (k != 0) lines = listOf(splitLine(m, k)) + lines + listOf("→ ${neg(k)} + ${f3(mant)} = ${f3(est)}")
        val answer = Math.log10(m) + k
        val p = Problem("log<sub>10</sub>(${fmtNum(m * pow10(k), sig = 4)})", answer, "≈ ${f3(answer)}",
            tolAbs = tol, explanation = lines.joinToString("<br>"), hint = hint)
        return withEstimate(p, est)
    }

    private fun log10L1(rng: Rng): Problem {
        val a = rng.randint(2, 9)
        val k = rng.choice((-4 until 8).filter { it != 0 })
        val la = LOG.getValue(a)
        return log10Problem(a.toDouble(), k, la, listOf("log $a = ${f3(la)}"), 0.005, "2–3 decimals")
    }

    private val FACTORABLE: List<Anchor> = ANCHORS.filter { !it.simple && pyRound(it.value, 2) == it.value }

    private fun log10L2(rng: Rng): Problem {
        val a = rng.choice(FACTORABLE)
        val k = rng.randint(-3, 6)
        return log10Problem(a.value, k, a.log(), listOf(a.derive("log")), 0.006, "3 decimals")
    }

    private fun log10L3(rng: Rng): Problem {
        var m = randMantissa(rng)
        for (i in 0 until 20) {
            if (!ANCHORS.any { Math.abs(m - it.value) < 1e-9 }) break
            m = randMantissa(rng)
        }
        val k = rng.randint(-3, 6)
        val est = logEst(m)
        return log10Problem(m, k, est.value, est.lines(), 0.01, "2–3 decimals")
    }

    // ---------------------------------------------------------------- s_antilog

    private const val ANTI_HINT = "any form, e.g. 2.4e7"

    private fun antilogProblem(y: Double, est: Double, lines: List<String>, tol: Double): Problem {
        val answer = Math.pow(10.0, y)
        val p = Problem("10${sup(y)}", answer, "≈ ${fmtVal(answer)}", tolRel = tol,
            explanation = lines.joinToString("<br>"), hint = ANTI_HINT)
        return withEstimate(p, est)
    }

    private fun antilogL1(rng: Rng): Problem {
        val k = rng.randint(-3, 6)
        val y: Double
        val lines: MutableList<String>
        val mant: Double
        if (rng.random() < 0.1) {   // √10
            y = k + 0.5
            lines = mutableListOf("10${sup(y)} = 10<sup>0.5</sup> × ${p10(k)} = √10 × ${p10(k)}")
            mant = 3.162
        } else {
            val a = rng.randint(2, 9)
            val la = LOG.getValue(a)
            y = pyRound(k + la, 3)
            lines = mutableListOf(
                if (k != 0) "10${sup(y)} = 10${sup(la)} × ${p10(k)}" else "10${sup(y)}",
                "${f3(la)} = log $a → 10${sup(la)} ≈ $a",
            )
            mant = a.toDouble()
        }
        val est = mant * pow10(k)
        lines.add(if (k != 0) "→ ${fmtNum(mant)} × ${p10(k)} = ${fmtVal(est, 4)}" else "→ ${fmtNum(mant)}")
        return antilogProblem(y, est, lines, 0.01)
    }

    private fun antilogL2(rng: Rng): Problem {
        val k = rng.randint(-3, 6)
        val y = pyRound(k + rng.randint(1, 99) / 100.0, 2)
        val e = pow10Est(y)
        return antilogProblem(y, e.value, e.lines(), 0.03)
    }

    // ---------------------------------------------------------------- s_ln

    private val LN_KEYS: Map<String, Double> =
        linkedMapOf("2" to 0.693, "3" to 1.099, "5" to 1.609, "7" to 1.946, "10" to 2.303)

    /** Exponents over 10, 2, 3, 5, 7 (tens pulled out first). Keys are "10", "2", …, "e". */
    private fun factor(n0: Int): LinkedHashMap<String, Int> {
        var n = n0
        val f = LinkedHashMap<String, Int>()
        while (n % 10 == 0) {
            n /= 10
            f["10"] = (f["10"] ?: 0) + 1
        }
        for (p in listOf(2, 3, 5, 7)) {
            while (n % p == 0) {
                n /= p
                f["$p"] = (f["$p"] ?: 0) + 1
            }
        }
        return f
    }

    private fun smooth(n0: Int): Boolean {
        var n = n0
        for (p in listOf(2, 3, 5, 7)) {
            while (n % p == 0) n /= p
        }
        return n == 1
    }

    private val COMPOSITES: List<Int> = (4 until 101).filter {
        smooth(it) && it != 10 && factor(it).values.sum() + (factor(it)["10"] ?: 0) >= 2
    }

    private val ORDER = listOf("2", "3", "5", "7", "10", "e")

    private fun powerText(key: String, e: Int): String = if (e != 1) "$key${sup(e)}" else key

    private fun factorText(f: Map<String, Int>): String {
        val pos = ORDER.filter { (f[it] ?: 0) > 0 && it != "e" }.map { powerText(it, f.getValue(it)) }
        val negs = ORDER.filter { (f[it] ?: 0) < 0 && it != "e" }.map { powerText(it, -f.getValue(it)) }
        var s = pos.joinToString(" × ").ifEmpty { "1" }
        if (negs.isNotEmpty()) {
            val den = negs.joinToString(" × ")
            s += if (negs.size > 1) " / ($den)" else " / $den"
        }
        return s
    }

    /** ('2 ln 2 + ln 3', '2 × 0.693 + 1.099', estimate). */
    private fun lnTerms(f: Map<String, Int>): Triple<String, String, Double> {
        val sym = mutableListOf<Pair<String, String>>()
        val num = mutableListOf<Pair<String, String>>()
        var total = 0.0
        for (key in ORDER) {
            val c = f[key] ?: 0
            if (c == 0) continue
            val s = if (c > 0) "+" else "−"
            if (key == "e") {
                sym.add(s to Math.abs(c).toString())
                num.add(s to Math.abs(c).toString())
                total += c
                continue
            }
            val v = LN_KEYS.getValue(key)
            sym.add(s to if (Math.abs(c) == 1) "ln $key" else "${Math.abs(c)} ln $key")
            num.add(s to if (Math.abs(c) == 1) fmtF(v, 3) else "${Math.abs(c)} × ${fmtF(v, 3)}")
            total += c * v
        }
        fun join(parts: List<Pair<String, String>>): String {
            val out = StringBuilder()
            for ((i, pt) in parts.withIndex()) {
                val (s, t) = pt
                out.append(if (i == 0) (if (s == "+") t else "−$t") else " $s $t")
            }
            return out.toString()
        }
        return Triple(join(sym), join(num), pyRound(total, 3))
    }

    private fun lnL1(rng: Rng): Problem {
        val mode = rng.choices(listOf("int", "big", "small", "e"), listOf(4.0, 2.0, 3.0, 2.0))
        val f: LinkedHashMap<String, Int>
        val shown: String
        if (mode == "int") {
            val n = rng.choice(COMPOSITES)
            f = factor(n); shown = fmtNum(n)
        } else if (mode == "big") {
            val p = rng.choice(listOf(2, 3, 4, 5, 6, 7, 8, 9, 12, 15))
            val t = rng.randint(1, 3)
            f = factor(p)
            f["10"] = (f["10"] ?: 0) + t
            shown = fmtNum(p * ipow(10, t))
        } else if (mode == "small" && rng.random() < 0.5) {   // reciprocals
            val n = rng.choice(listOf(3, 4, 6, 7, 8, 9, 12, 16, 20, 25, 40, 50))
            f = LinkedHashMap<String, Int>().also { m -> factor(n).forEach { (k, v) -> m[k] = -v } }
            val terminating = smooth(n) && n % 3 != 0 && n % 7 != 0
            shown = if (terminating) fmtNum(1.0 / n) else "(1/$n)"
        } else if (mode == "small") {                         // p / 10^t
            val p = rng.choice(listOf(2, 3, 4, 5, 6, 7, 8, 9, 12, 15, 35, 49))
            val t = rng.randint(if (p > 9 || p == 5) 2 else 1, 3)   // (0.5 is better done as 1/2)
            f = factor(p)
            f["10"] = (f["10"] ?: 0) - t
            shown = fmtNum(p.toDouble() / ipow(10, t).toDouble())
        } else {
            val p = rng.choice(listOf(2, 3, 5, 7, 10))
            val k = rng.choice(listOf(-2, -1, 1, 2, 3))
            f = factor(p)
            f["e"] = k
            shown = if (k > 0) "(${p}e${if (k != 1) sup(k) else ""})"
            else "($p/e${if (k != -1) sup(-k) else ""})"
        }
        var answer = 0.0
        for ((key, c) in f) answer += c * (if (key == "e") 1.0 else Math.log(key.toDouble()))
        val (sym, num, est) = lnTerms(f)
        val lines = mutableListOf<String>()
        if (mode != "e") lines.add("${shown.trim('(', ')')} = ${factorText(f)}")
        lines.add("ln $shown = $sym = $num = ${f3(est)}")
        val p = Problem("ln $shown", answer, "≈ ${f3(answer)}", tolAbs = 0.02,
            explanation = lines.joinToString("<br>"), hint = "2–3 decimals")
        return withEstimate(p, est)
    }

    private fun lnL2(rng: Rng): Problem {
        val m = randMantissa(rng)
        val k = rng.randint(-3, 5)
        val e = logEst(m, "ln")
        val tens = pyRound(k * LN10, 3)
        val est = pyRound(e.value + tens, 3)
        var lines = e.lines()
        if (k != 0) {
            lines = listOf("${splitLine(m, k)} → ln ${fmtNum(m, sig = 4)} ${pm(k)} ${Math.abs(k)} × 2.303") +
                lines + listOf("→ ${f3(e.value)} ${pm(k)} ${f3(Math.abs(tens))} = ${f3(est)}")
        }
        val answer = Math.log(m) + k * Math.log(10.0)
        val p = Problem("ln ${fmtArg(m * pow10(k), 4)}", answer, "≈ ${f3(answer)}", tolAbs = 0.03,
            explanation = lines.joinToString("<br>"), hint = "2 decimals")
        return withEstimate(p, est)
    }

    // ---------------------------------------------------------------- s_logb

    private val LOG2: Map<Int, Double> = (2 until 10).associateWith { pyRound(LOG.getValue(it) / LOG.getValue(2), 3) }
    private const val LOG2_10 = 3.322

    /** math.log2: exact for powers of two. */
    private fun log2(x: Double): Double {
        val e = Math.getExponent(x)
        if (x > 0 && x == Math.scalb(1.0, e)) return e.toDouble()
        return Math.log(x) / Math.log(2.0)
    }

    private fun logbL1(rng: Rng): Problem {
        val a = rng.choice(listOf(1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        val k = if (a == 1) {
            rng.choice((-3 until 10).filter { it != 0 && it != 1 })
        } else {
            rng.choice((-3 until 7).filter { it != 0 })
        }
        val x = a * pow10(k)
        val shown = if (1e-3 <= x && x < 1e7) fmtNum(x, sig = 3) else (if (a == 1) p10(k) else fmtArg(x))
        val tens = pyRound(k * LOG2_10, 3)
        val la = if (a > 1) LOG2.getValue(a) else 0.0
        val est = pyRound(la + tens, 3)
        val lines = mutableListOf("log<sub>2</sub> 10 = 1/log 2 = 1/0.301 ≈ 3.322 (2<sup>10</sup> ≈ 10<sup>3</sup>, " +
            "so a bit over 10/3)")
        if (a > 1) {
            if (a in listOf(2, 4, 8)) {
                lines.add("log<sub>2</sub> $a = ${la.toLong()}")
            } else {
                lines.add("log<sub>2</sub> $a = log $a / log 2 = ${f3(LOG.getValue(a))}/0.301 ≈ ${f3(la)}")
            }
            lines.add("→ log<sub>2</sub> $shown = ${if (la.isInteger()) short(la) else f3(la)} " +
                "${pm(k)} ${Math.abs(k)} × 3.322 = ${f3(est)}")
        } else {
            lines.add("→ log<sub>2</sub> $shown = ${neg(k)} × 3.322 = ${f3(est)}")
        }
        val answer = log2(a.toDouble()) + k * log2(10.0)
        val p = Problem("log<sub>2</sub> $shown", answer, "≈ ${fmtF(answer, 2)}".replace("-", MINUS),
            tolAbs = 0.05, explanation = lines.joinToString("<br>"), hint = "2 decimals")
        return withEstimate(p, est)
    }

    private fun logbL2(rng: Rng): Problem {
        var b = 0
        var m = 0.0
        var k = 0
        var answer = 0.0
        for (i in 0 until 100) {
            b = rng.choice((2 until 21).filter { it != 10 })
            m = randMantissa(rng, listOf(1, 2))
            k = rng.randint(-2, 3)
            answer = (Math.log10(m) + k) / Math.log10(b.toDouble())
            if (Math.abs(answer - pyRound(answer).toDouble()) > 0.02 && Math.abs(answer) > 0.2) break
        }
        val x = m * pow10(k)
        val ex = logEst(m)
        val lx = pyRound(k + ex.value, 3)
        val (mb, kb) = split10(b.toDouble())
        val eb = logEst(mb)
        val lb = pyRound(kb + eb.value, 3)
        val est = lx / lb
        val xs = fmtNum(x, sig = 3)
        val lines = listOf(
            "log<sub>$b</sub> $xs = log $xs ÷ log $b",
            ex.compact(xs, k), eb.compact(b.toString(), kb),
            "→ ${f3(lx)} ÷ ${f3(lb)} ≈ ${fmtF(est, 2)}".replace("-", MINUS),
        )
        val p = Problem("log<sub>$b</sub> $xs", answer, "≈ ${fmtF(answer, 2)}".replace("-", MINUS),
            tolAbs = 0.05, explanation = lines.joinToString("<br>"), hint = "2 decimals")
        return withEstimate(p, est)
    }

    // ---------------------------------------------------------------- lessons

    private val L_LOGINT = Lesson(
        "Exact logarithms",
        "\n" +
        "<p><b>log<sub>b</sub> x asks: \"b to what power gives x?\"</b> When x is a power of b, just count.</p>\n" +
        "<ul>\n" +
        "<li>log<sub>2</sub> 64 = 6 because 2<sup>6</sup> = 64. log<sub>5</sub> 125 = 3.</li>\n" +
        "<li>Reciprocals give negatives: log<sub>2</sub>(1/8) = −3, log<sub>10</sub> 0.001 = −3.</li>\n" +
        "<li>Roots give fractions: √x = x<sup>1/2</sup>, so log<sub>2</sub> √32 = 5/2.</li>\n" +
        "</ul>\n" +
        "<p><b>Fractional answers — rewrite both numbers as powers of a common root.</b> If\n" +
        "b = r<sup>p</sup> and x = r<sup>q</sup>, then log<sub>b</sub> x = q/p.</p>\n" +
        "<p>Example: log<sub>8</sub> 4. 8 = 2<sup>3</sup>, 4 = 2<sup>2</sup> → <b>2/3</b>.\n" +
        "Check: 8<sup>2/3</sup> = (∛8)<sup>2</sup> = 2<sup>2</sup> = 4. ✓</p>\n" +
        "<p>Example: log<sub>9</sub>(1/3). 9 = 3<sup>2</sup>, 1/3 = 3<sup>−1</sup> → <b>−1/2</b>.</p>\n" +
        "<p>Your powers-of-2 table (to 2<sup>20</sup>) and cubes do most of the work: spot the root\n" +
        "first (2, 3, 5, 10), then read off the two exponents. Type answers as fractions (2/3) or\n" +
        "decimals (0.667).</p>\n",
    )

    private val L_LOG10 = Lesson(
        "Common logarithms",
        "\n" +
        "<p><b>Step 1 — scientific notation.</b> log(a × 10<sup>k</sup>) = k + log a, so only the\n" +
        "mantissa a (between 1 and 10) needs thought: log 3 000 = 3 + 0.477 = 3.477;\n" +
        "log 0.0007 = −4 + 0.845 = −3.155.</p>\n" +
        "<p><b>Step 2 — factor the mantissa into digits.</b> Logs turn × into + and ÷ into −:</p>\n" +
        "<ul>\n" +
        "<li>4.8 = 6 × 8 / 10 → 0.778 + 0.903 − 1 = 0.681</li>\n" +
        "<li>2.5 = 10 / 4 → 1 − 0.602 = 0.398; 1.75 = 7 / 4 → 0.845 − 0.602 = 0.243</li>\n" +
        "</ul>\n" +
        "<p><b>Step 3 — anything else: nearest anchor + linear correction.</b>\n" +
        "log(a + Δ) ≈ log a + <b>0.434 · Δ/a</b> (0.434 = log e, the slope of ln converted to log).\n" +
        "Use the closest number you can log exactly; keep Δ/a under ~5 % and the error is tiny.</p>\n" +
        "<p>Example: log 6 130 = 3 + log 6.13. Nearest anchor 6 (0.778); Δ/a = 0.13/6 ≈ 0.022;\n" +
        "× 0.434 ≈ 0.009 → 3.787 (true 3.7875).</p>\n" +
        "<p>Example: log 1.3 — the digit 1 is too far, but 1.25 = 10/8 has log 1 − 0.903 = 0.097;\n" +
        "0.434 × 0.05/1.25 ≈ 0.017 → 0.114 (true 0.1139).</p>\n" +
        "<p>Useful extra anchors: 1.2 (0.079), 1.25 (0.097), 1.4 (0.146), 1.5 (0.176),\n" +
        "1.6 (0.204), 1.8 (0.255).</p>\n",
    )

    private val L_ANTILOG = Lesson(
        "Powers of ten",
        "\n" +
        "<p>Reverse a log: <b>10<sup>x</sup> = 10<sup>fraction</sup> × 10<sup>integer</sup></b>. Take the\n" +
        "integer part <i>down</i> (floor), so the fractional part is always between 0 and 1:\n" +
        "10<sup>−2.523</sup> = 10<sup>0.477</sup> × 10<sup>−3</sup>.</p>\n" +
        "<p><b>Read the mantissa off your log table.</b> 0.477 = log 3, so 10<sup>3.477</sup> ≈ 3 000 and\n" +
        "10<sup>−2.523</sup> ≈ 0.003. 0.5 → √10 ≈ 3.16.</p>\n" +
        "<p><b>In between: nearest anchor, then correct linearly.</b> If the fraction is d above log a,\n" +
        "10<sup>fraction</sup> ≈ a·(1 + d/0.434) — i.e. add a × d/0.434 (each 0.01 of log is about 2.3 %).</p>\n" +
        "<p>Example: 10<sup>4.63</sup>. 0.63 is 0.028 above log 4 = 0.602 →\n" +
        "4 + 4 × 0.028/0.434 ≈ 4 + 0.26 = 4.26 → <b>42 600</b> (true 42 658).</p>\n" +
        "<p>Example: 10<sup>0.18</sup>: no digit is close, but log 1.5 = 0.477 − 0.301 = 0.176, so\n" +
        "≈ 1.5 × (1 + 0.004/0.434) ≈ 1.51.</p>\n" +
        "<p>Low fractions need the factored anchors: 0.079 → 1.2, 0.097 → 1.25, 0.146 → 1.4,\n" +
        "0.176 → 1.5, 0.204 → 1.6, 0.255 → 1.8. Answer in any form: 42600, 4.26e4, 4.26x10^4.</p>\n",
    )

    private val L_LN = Lesson(
        "Natural logarithms",
        "\n" +
        "<p><b>Factor into 2, 3, 5, 7, 10 and e</b> and add the memorized logs\n" +
        "(ln 2 = 0.693, ln 3 = 1.099, ln 5 = 1.609, ln 7 = 1.946, ln 10 = 2.303, ln e = 1):</p>\n" +
        "<ul>\n" +
        "<li>ln 12 = 2 ln 2 + ln 3 = 1.386 + 1.099 = 2.485</li>\n" +
        "<li>ln 700 = ln 7 + 2 ln 10 = 1.946 + 4.606 = 6.552</li>\n" +
        "<li>ln 0.25 = −2 ln 2 = −1.386; ln(3e<sup>2</sup>) = 1.099 + 2 = 3.099</li>\n" +
        "</ul>\n" +
        "<p><b>Anything else: anchor + Δ/a.</b> The slope of ln at a is 1/a, so\n" +
        "ln(a + Δ) ≈ ln a + Δ/a. Pull out powers of ten first (each adds 2.303), then pick the nearest\n" +
        "number you can factor.</p>\n" +
        "<p>Example: ln 3 700 = ln 3.7 + 3 × 2.303. ln 3.7 ≈ ln 3.5 + 0.2/3.5, with\n" +
        "ln 3.5 = ln 7 − ln 2 = 1.253 → 1.253 + 0.057 = 1.310; + 6.909 = <b>8.219</b> (true 8.216).</p>\n" +
        "<p><b>Alternative:</b> ln x = 2.303 × log x, handy if you already know log x\n" +
        "(ln 50 = 2.303 × 1.699 ≈ 3.91).</p>\n",
    )

    private val L_LOGB = Lesson(
        "Change of base",
        "\n" +
        "<p><b>log<sub>b</sub> x = log x / log b</b> — any base, using common logs you can already do.</p>\n" +
        "<p>Example: log<sub>3</sub> 50 = 1.699 / 0.477 ≈ 3.56. Sanity check: 3<sup>3</sup> = 27,\n" +
        "3<sup>4</sup> = 81, and 50 sits between. ✓</p>\n" +
        "<p><b>Base 2 shortcut.</b> 2<sup>10</sup> = 1 024 ≈ 10<sup>3</sup>, so\n" +
        "<b>log<sub>2</sub> 10 ≈ 3.32</b> (exactly 1/0.301 = 3.322). Then</p>\n" +
        "<ul>\n" +
        "<li>log<sub>2</sub> 10<sup>6</sup> = 6 × 3.322 = 19.93 (2<sup>20</sup> ≈ a million ✓)</li>\n" +
        "<li>log<sub>2</sub> 5 000 = log<sub>2</sub> 5 + 3 × 3.322 = 2.322 + 9.966 = 12.29</li>\n" +
        "<li>log<sub>2</sub> 0.01 = −2 × 3.322 = −6.64</li>\n" +
        "</ul>\n" +
        "<p>Worth knowing: log<sub>2</sub> 3 = 1.585, log<sub>2</sub> 5 = 2.322, log<sub>2</sub> 7 = 2.807\n" +
        "(each = log d / 0.301).</p>\n" +
        "<p>For bases that aren't digits, log the base like any other number: log 13 = 1 + log 1.3 ≈\n" +
        "1.114. Keep three decimals in both logs before dividing — the division magnifies errors when\n" +
        "log b is small.</p>\n",
    )

    override val SKILLS: List<Skill> = listOf(
        Skill("s_logint", "Exact logarithms", "logs", L_LOGINT, listOf(
            Level("integer results: log₂ 64, log₁₀ 0.001", 4.0, ::logintL1),
            Level("fractional results: log₈ 4, log₉ (1/3)", 7.0, ::logintL2),
        )),
        Skill("s_log10", "Common logarithms", "logs", L_LOG10, listOf(
            Level("a × 10ᵏ with a a digit", 4.0, ::log10L1),
            Level("factorable mantissas: 4.8, 1.25, 7.2", 6.0, ::log10L2),
            Level("any 2–3 significant figures", 9.0, ::log10L3),
        )),
        Skill("s_antilog", "Powers of ten", "logs", L_ANTILOG, listOf(
            Level("memorized mantissas: 10^3.477", 4.0, ::antilogL1),
            Level("any exponent: 10^4.63", 8.0, ::antilogL2),
        )),
        Skill("s_ln", "Natural logarithms", "logs", L_LN, listOf(
            Level("built from 2, 3, 5, 7, 10, e", 6.0, ::lnL1),
            Level("any number", 10.0, ::lnL2),
        )),
        Skill("s_logb", "Change of base", "logs", L_LOGB, listOf(
            Level("log₂ of round numbers", 8.0, ::logbL1),
            Level("any base 2–20", 12.0, ::logbL2),
        )),
    )
}
