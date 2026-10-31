package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.content.Logs.LN
import dev.russell.mmtrainer.content.Logs.LN10
import dev.russell.mmtrainer.content.Logs.anchorForLn
import dev.russell.mmtrainer.content.Logs.f3
import dev.russell.mmtrainer.content.Logs.fmtArg
import dev.russell.mmtrainer.content.Logs.fmtVal
import dev.russell.mmtrainer.content.Logs.logEst
import dev.russell.mmtrainer.content.Logs.neg
import dev.russell.mmtrainer.content.Logs.pm
import dev.russell.mmtrainer.content.Logs.pow10Est
import dev.russell.mmtrainer.content.Logs.randMantissa
import dev.russell.mmtrainer.content.Logs.sup
import dev.russell.mmtrainer.content.Logs.withEstimate
import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.floorDiv
import dev.russell.mmtrainer.py.floorMod
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.gcd
import dev.russell.mmtrainer.py.ipow
import dev.russell.mmtrainer.py.isqrt
import dev.russell.mmtrainer.py.pyRound
import dev.russell.mmtrainer.py.pyStr

/*
 * Exponentials, powers, roots and compound growth.
 *
 * Built on the same memorized anchors as Logs (log10 of digits, ln 2/3/5/7/10, e^1…e^5,
 * squares, cubes, powers of 2, √2 √3 √5 √10). As in Logs, every generator records the value
 * its worked explanation arrives at via Logs.withEstimate for the tests.
 */
object Powers : ContentModule {

    private val E_VAL: Map<Int, Double> =
        linkedMapOf(0 to 1.0, 1 to 2.718, 2 to 7.389, 3 to 20.09, 4 to 54.60, 5 to 148.4, 6 to 403.4)
    private val E_STR: Map<Int, String> =
        linkedMapOf(0 to "1", 1 to "2.718", 2 to "7.389", 3 to "20.09", 4 to "54.60", 5 to "148.4", 6 to "403.4")
    private const val SQRT_E = 1.649
    private val SQRT: Map<Int, Double> = linkedMapOf(2 to 1.414, 3 to 1.732, 5 to 2.236, 10 to 3.162)

    private fun pow10(k: Int): Double = Math.pow(10.0, k.toDouble())

    /** 'e', 'e<sup>2</sup>', 'e<sup>−0.2</sup>'. */
    private fun e(n: Double): String = if (n == 1.0) "e" else "e${sup(n)}"
    private fun e(n: Int): String = e(n.toDouble())

    /** `sig` significant figures, keeping trailing zeros below 10^(sig−1) (3.00, 11.0). */
    private fun sig(x: Double, sig: Int = 4): String {
        if (x == 0.0) return "0"
        val d = sig - 1 - Math.floor(Math.log10(Math.abs(x))).toInt()
        return if (d > 0) neg(fmtF(x, d)) else fmtNum(x, sig = sig)
    }

    // ---------------------------------------------------------------- shared: e^y via ln anchors

    /**
     * Mental e^y: memorized e^n if y is near an integer, else the nearest ln anchor (after
     * pulling out multiples of ln 10 = 2.303), corrected by e^d ≈ 1 + d.
     */
    internal fun expEst(y0: Double): Pair<Double, String> {
        val y = pyRound(y0, 3)
        val n = pyRound(y).toInt()
        if (n in 1..5 && Math.abs(y - n) <= 0.03) {
            val d = pyRound(y - n, 3)
            val v = E_VAL.getValue(n) * (1 + d)
            if (d == 0.0) return v to "${e(n)} ≈ ${E_STR.getValue(n)}"
            return v to ("${e(y)} = ${e(n)} × ${e(d)} ≈ ${E_STR.getValue(n)} × ${fmtNum(1 + d)} " +
                "≈ ${fmtVal(v)}")
        }
        val k = Math.floor(y / LN10).toInt()
        val r = pyRound(y - k * LN10, 3)
        val a = anchorForLn(r)
        val d = pyRound(r - a.ln(), 3)
        val mant = a.value * (1 + d)
        val v = mant * pow10(k)
        val pre = if (k != 0) "${fmtNum(y)} = ${neg(k)} × 2.303 + ${f3(r)}; " else ""
        val anchor = "ln ${a.name} = ${f3(a.ln())}" + (if (a.simple) "" else " (${a.recipe()})")
        val core = if (d == 0.0) {
            "${f3(r)} = $anchor → ${e(r)} ≈ ${a.name}"
        } else {
            "${f3(r)} = ln ${a.name} ${pm(d)} ${f3(Math.abs(d))} [$anchor] → ${e(r)} ≈ " +
                "${a.name} × (1 ${pm(d)} ${f3(Math.abs(d))}) ≈ ${sig(mant, 3)}"
        }
        val post = if (k != 0) " → × 10${sup(k)} ≈ ${fmtVal(v)}" else ""
        return v to (pre + core + post)
    }

    // ---------------------------------------------------------------- s_exp

    /** e^h for h a non-negative multiple of 0.5, from memorized e^n and √e. */
    internal fun eHalf(h: Double): Pair<Double, String> {
        val n = h.toInt()
        if (h - n < 0.25) {
            if (n == 6) return E_VAL.getValue(6) to "e<sup>6</sup> = (e<sup>3</sup>)<sup>2</sup> ≈ 403.4"
            return E_VAL.getValue(n) to "${e(n)} ≈ ${E_STR.getValue(n)}"
        }
        if (n == 0) return SQRT_E to "e<sup>0.5</sup> = √e ≈ 1.649"
        val v = E_VAL.getValue(n) * SQRT_E
        return v to "${e(h)} = ${e(n)} × √e ≈ ${E_STR.getValue(n)} × 1.649 = ${sig(v)}"
    }

    private fun expL1(rng: Rng): Problem {
        val x = rng.choice(listOf(-3.0, -2.5, -2.0, -1.5, -1.0, -0.5, 0.5, 1.5, 2.5, 3.5, 4.5))
        val (v, text) = eHalf(Math.abs(x))
        val est = if (x > 0) v else 1 / v
        val lines = mutableListOf(text)
        if (x < 0) lines.add("${e(x)} = 1/${sig(v)} ≈ ${sig(est, 3)}")
        val answer = Math.exp(x)
        val p = Problem(e(x), answer, "≈ ${sig(answer, 3)}", tolRel = 0.02,
            explanation = lines.joinToString("<br>"))
        return withEstimate(p, est)
    }

    private fun expL2(rng: Rng): Problem {
        val x = rng.choice(listOf(-1, 1)) * rng.randint(1, 30) / 100.0
        val terms = listOf(x, x * x / 2) + (if (Math.abs(x) >= 0.15) listOf(Math.pow(x, 3.0) / 6) else emptyList())
        val est = 1 + terms.sum()
        val form = "1 + x + x²/2" + (if (terms.size == 3) " + x³/6" else "")
        val nums = "1" + terms.joinToString("") { " ${pm(it)} ${fmtNum(Math.abs(it), sig = 3)}" }
        val expl = "${e(x)} ≈ $form = $nums ≈ ${fmtF(est, 4)}"
        val answer = Math.exp(x)
        val p = Problem(e(x), answer, "≈ ${sig(answer)}", tolRel = 0.005, explanation = expl,
            hint = "4 significant figures")
        return withEstimate(p, est)
    }

    private fun smallE(f: Double): Pair<Double, String> {
        val v = 1 + f + f * f / 2
        return v to "${e(f)} ≈ 1 ${pm(f)} ${fmtNum(Math.abs(f))} + ${fmtNum(f * f / 2)} = ${fmtNum(v)}"
    }

    private fun expL3(rng: Rng): Problem {
        var x = 0.0
        while (x == 0.0 || floorMod(x * 10, 5.0) == 0.0) x = rng.randint(-30, 60) / 10.0
        val ax = Math.abs(x)
        val h = pyRound(ax * 2).toDouble() / 2
        val f = pyRound(ax - h, 1)
        val (ef, ftext) = smallE(f)
        val lines = mutableListOf<String>()
        if (x < 0) lines.add("${e(x)} = 1/${e(ax)}")
        val v: Double
        if (h == 0.0) {
            v = ef
            lines.add(ftext)
        } else {
            val (eh, htext) = eHalf(h)
            v = eh * ef
            lines.add("${e(ax)} = ${e(h)} × ${e(f)}")
            lines.add("$htext; $ftext")
            lines.add("→ ${sig(eh)} × ${fmtNum(ef)} ≈ ${sig(v, 3)}")
        }
        var est = v
        if (x < 0) {
            est = 1 / v
            lines.add("→ ${e(x)} ≈ 1/${sig(v, 3)} ≈ ${sig(est, 3)}")
        }
        val answer = Math.exp(x)
        val p = Problem(e(x), answer, "≈ ${sig(answer, 3)}", tolRel = 0.03,
            explanation = lines.joinToString("<br>"))
        return withEstimate(p, est)
    }

    // ---------------------------------------------------------------- s_pow

    /** a^n via a memorized smaller power: square of the half power, or one more factor. */
    internal fun splitExpl(a: Int, n: Int): String {
        if (n % 2 == 0 && n >= 4) {
            val half = ipow(a, n / 2)
            return "$a${sup(n)} = ($a${sup(n / 2)})${sup(2)} = ${fmtNum(half)}${sup(2)} " +
                "= ${fmtNum(ipow(a, n))}"
        }
        val prev = ipow(a, n - 1)
        var mult = "${fmtNum(prev)} × $a"
        if (a >= 11) {
            val t = a / 10 * 10
            val o = a % 10
            mult += if (o != 0) " = ${fmtNum(prev * t)} + ${fmtNum(prev * o)}" else ""
        }
        return "$a${sup(n)} = $a${sup(n - 1)} × $a = $mult = ${fmtNum(ipow(a, n))}"
    }

    internal fun squareExpl(a: Int): String {
        var d = a % 10
        if (d > 5) d -= 10
        val lo = a - d
        val hi = a + d
        return "$a${sup(2)} = $lo × $hi + ${d * d} = ${fmtNum(lo * hi)} + ${d * d} " +
            "= ${fmtNum(a * a)}  (a² = (a − d)(a + d) + d²)"
    }

    private fun powL1(rng: Rng): Problem {
        val kind = rng.choices(listOf("pow2", "pow3", "pow5", "cube", "fourth", "square", "decimal", "neg"),
            listOf(2.0, 2.0, 2.0, 3.0, 2.0, 3.0, 3.0, 1.5))
        val base: String
        val n: Int
        val value: Fraction
        val expl: String
        when (kind) {
            "pow2" -> {
                val (b, p) = rng.choice(listOf(4 to 2, 8 to 3, 16 to 4, 32 to 5))
                n = rng.choice((2 until 11).filter { p * it in 7..20 })
                base = b.toString(); value = Fraction.of(b).pow(n)
                expl = "$b${sup(n)} = (2${sup(p)})${sup(n)} = 2${sup(p * n)} = ${fmtNum(ipow(b, n))}"
            }
            "pow3" -> {
                val (b, nn) = rng.choice((4 until 10).map { 3 to it } + listOf(9 to 2, 9 to 3, 9 to 4, 27 to 2))
                n = nn
                base = b.toString(); value = Fraction.of(b).pow(n); expl = splitExpl(b, n)
            }
            "pow5" -> {
                val (b, nn) = rng.choice((3 until 9).map { 5 to it } + listOf(25 to 2, 25 to 3, 25 to 4))
                n = nn
                val (ten, two) = if (b == 5) 10 to 2 else 100 to 4
                base = b.toString(); value = Fraction.of(b).pow(n)
                expl = "$b${sup(n)} = $ten${sup(n)} / $two${sup(n)} = ${fmtNum(ipow(ten, n))} / " +
                    "${fmtNum(ipow(two, n))} = ${fmtNum(ipow(b, n))}"
            }
            "cube" -> {
                val b = rng.choice(listOf(13, 14, 15, 16, 17, 18, 19, 21, 25))
                n = 3
                base = b.toString(); value = Fraction.of(b).pow(3); expl = splitExpl(b, 3)
            }
            "fourth" -> {
                val b = rng.choice(listOf(3, 4, 5, 6, 7, 8, 9, 11, 12))
                n = 4
                base = b.toString(); value = Fraction.of(b).pow(4); expl = splitExpl(b, 4)
            }
            "square" -> {
                val b = rng.choice((31 until 100).filter { it % 10 != 0 })
                n = 2
                base = b.toString(); value = Fraction.of(b).pow(2); expl = squareExpl(b)
            }
            "decimal" -> {
                val (bs, nn) = rng.choice(
                    (2 until 7).map { "0.5" to it } + (2 until 5).map { "0.2" to it } +
                        (2 until 5).map { "0.3" to it } + (2 until 5).map { "1.1" to it } +
                        listOf("1.2" to 2, "1.2" to 3, "1.5" to 2, "1.5" to 3, "1.5" to 4,
                            "0.4" to 2, "0.4" to 3, "2.5" to 2, "2.5" to 3, "0.9" to 2, "0.9" to 3),
                )
                base = bs; n = nn
                val digits = bs.replace(".", "").trimStart('0').toInt()
                val decimals = bs.split(".")[1].length
                val places = decimals * n
                value = Fraction.of(bs.replace(".", "").toLong(), ipow(10, decimals)).pow(n)
                expl = "$digits${sup(n)} = ${fmtNum(ipow(digits, n))}, then shift the point $places places " +
                    "→ ${fmtNum(value.toDouble())}"
            }
            else -> {
                val (b, m) = rng.choice((1 until 7).map { 2 to it } +
                    listOf(4 to 2, 4 to 3, 5 to 2, 5 to 3, 8 to 2, 20 to 2, 25 to 2))
                n = -m
                base = b.toString(); value = Fraction.of(1L, ipow(b, m))
                expl = "$b${sup(n)} = 1/$b${sup(m)} = 1/${fmtNum(ipow(b, m))} = ${fmtNum(value.toDouble())}"
            }
        }
        val answer = value.toDouble()
        return Problem("$base${sup(n)}", answer, fmtNum(answer), explanation = expl, hint = "exact")
    }

    private val ROOT_BASES: Map<Int, Int> = linkedMapOf(2 to 5, 3 to 4, 4 to 3, 5 to 3, 10 to 3, 6 to 2, 7 to 2)   // r: highest root index q

    private fun powL2(rng: Rng): Problem {
        if (rng.random() < 0.6) {
            var r = 2
            var q = 3
            var p = 2
            var found = false
            for (i in 0 until 200) {
                r = rng.choice(ROOT_BASES.keys.toList())
                q = rng.randint(2, ROOT_BASES.getValue(r))
                val qq = q
                p = rng.choice((-3 until 8).filter { it != 0 && gcd(it, qq) == 1 })
                val v = Math.pow(r.toDouble(), p.toDouble())
                if (1e-3 <= v && v <= 1e5) {
                    found = true
                    break
                }
            }
            if (!found) {
                r = 2; q = 3; p = 2
            }
            val a = ipow(r, q)
            val frac = Fraction.of(p, q)
            val exp = if (q in listOf(2, 4, 5) && rng.random() < 0.6) fmtNum(p.toDouble() / q) else neg("$p/$q")
            val value = Fraction.of(r).pow(p)
            val answer = value.toDouble()
            val shown: String
            val display: String
            if (r in listOf(2, 4, 5, 10) || p > 0) {    // terminating decimal
                shown = fmtNum(answer); display = shown
            } else {
                shown = "1/${value.den} ≈ ${sig(answer, 3)}"; display = "≈ ${sig(answer, 3)}"
            }
            val root = if (q == 2) "√" else (if (q == 3) "∛" else "<sup>$q</sup>√")
            val power = if (frac.num == 1L) "" else "<sup>${neg(frac.num)}</sup>"
            val expl = "${fmtNum(a)} = $r${sup(q)} → ${fmtNum(a)}<sup>$exp</sup> = " +
                "($root${fmtNum(a)})$power = " + (if (p != 1) "$r${sup(p)} = $shown" else shown)
            val pr = Problem("${fmtNum(a)}<sup>$exp</sup>", answer, display, tolRel = 0.02, explanation = expl)
            return withEstimate(pr, answer)
        }
        val r = rng.choice(listOf(2, 3, 5, 10))
        val n = rng.randint(1, mapOf(2 to 12, 3 to 7, 5 to 5, 10 to 5).getValue(r))
        val sq = SQRT.getValue(r)
        val est = ipow(r, n) * sq
        val expl = "$r${sup(n + 0.5)} = $r${if (n > 1) sup(n) else ""} × √$r ≈ ${fmtNum(ipow(r, n))} × ${pyStr(sq)} " +
            "≈ ${fmtVal(est)}"
        val answer = Math.pow(r.toDouble(), n + 0.5)
        val pr = Problem("$r${sup(n + 0.5)}", answer, "≈ ${fmtVal(answer)}", tolRel = 0.02, explanation = expl)
        return withEstimate(pr, est)
    }

    private fun powL3(rng: Rng): Problem {
        var a = 3.7
        var b = 2.4
        var found = false
        for (i in 0 until 200) {
            a = rng.randint(12, 99) / 10.0
            b = rng.randint(11, 60) / 10.0
            val y = b * Math.log10(a)
            if (floorMod(a, 1.0) != 0.0 && floorMod(b, 1.0) != 0.0 && 0.3 <= y && y <= 5.3) {
                found = true
                break
            }
        }
        if (!found) {
            a = 3.7; b = 2.4
        }
        val e = logEst(a)
        val y = pyRound(b * e.value, 3)
        val pe = pow10Est(y)
        val expl = "${e.compact(fmtNum(a))}; × ${fmtNum(b)} = ${f3(y)}<br>${pe.compact()}"
        val answer = Math.pow(a, b)
        val p = Problem("${fmtNum(a)}${sup(b)}", answer, "≈ ${fmtVal(answer)}", tolRel = 0.05, explanation = expl)
        return withEstimate(p, pe.value)
    }

    // ---------------------------------------------------------------- s_root

    private fun nearestRoot(x: Double, p: Int): Int {
        val n = maxOf(1L, pyRound(Math.pow(x, 1.0 / p))).toInt()
        return listOf(n - 1, n, n + 1).filter { it >= 1 }.minBy { Math.abs(x - ipow(it, p).toDouble()) }
    }

    /** √m ≈ n + d/2n with n² the nearest square. */
    internal fun sqrtParts(m: Double): Pair<Double, String> {
        val n = nearestRoot(m, 2)
        val d = pyRound(m - n * n, 6)
        val est = n + d / (2 * n)
        if (d == 0.0) return n.toDouble() to "√${fmtNum(m)} = $n ($n² = ${fmtNum(n * n)})"
        return est to ("√${fmtNum(m)}: $n${sup(2)} = ${fmtNum(n * n)}, d = ${neg(fmtNum(d))} → " +
            "$n ${pm(d)} ${fmtNum(Math.abs(d))}/${2 * n} ≈ ${sig(est)}")
    }

    /** ∛m ≈ n + d/3n² with n³ the nearest cube. */
    internal fun cbrtParts(m: Double): Pair<Double, String> {
        val n = nearestRoot(m, 3)
        val d = pyRound(m - ipow(n, 3), 6)
        val est = n + d / (3 * n * n)
        if (d == 0.0) return n.toDouble() to "∛${fmtNum(m)} = $n"
        return est to ("∛${fmtNum(m)}: $n${sup(3)} = ${fmtNum(ipow(n, 3))}, d = ${neg(fmtNum(d))} → " +
            "$n ${pm(d)} ${fmtNum(Math.abs(d))}/${3 * n * n} ≈ ${sig(est)}")
    }

    private fun rootL1(rng: Rng): Problem {
        var m = 52
        var res: Pair<Double, String>? = null
        for (i in 0 until 100) {
            m = rng.randint(30, 999)
            if (isqrt(m.toLong()) * isqrt(m.toLong()) == m.toLong()) continue
            val r = sqrtParts(m.toDouble())
            if (Math.abs(r.first / Math.sqrt(m.toDouble()) - 1) <= 0.0035) {
                res = r
                break
            }
        }
        if (res == null) {
            m = 52
            res = sqrtParts(m.toDouble())
        }
        val (est, text) = res
        val answer = Math.sqrt(m.toDouble())
        val p = Problem("√$m", answer, "≈ ${sig(answer)}", tolRel = 0.005, explanation = text,
            hint = "3–4 significant figures")
        return withEstimate(p, est)
    }

    private fun rootL2(rng: Rng): Problem {
        var m = 0.0
        var k = 0
        var big = 0.0
        var j = 0
        var root = 0.0
        var text = ""
        for (i in 0 until 100) {
            m = randMantissa(rng, listOf(1, 2, 2, 3))
            k = rng.choice((-6 until 0).toList() + (3 until 11).toList())
            val odd = floorMod(k, 2) != 0
            big = pyRound(m * (if (odd) 10 else 100), 6)        // pair the exponent: M ∈ [10, 1000)
            j = if (odd) floorDiv(k - 1, 2) else floorDiv(k - 2, 2)
            val r = sqrtParts(big)
            root = r.first; text = r.second
            if (Math.abs(root / Math.sqrt(big) - 1) <= 0.006) break
        }
        val x = m * pow10(k)
        val est = root * pow10(j)
        val lines = listOf(
            "${fmtVal(x)} = ${fmtNum(big)} × 10${sup(2 * j)} → √${fmtNum(big)} × 10${sup(j)}",
            text, "→ ${sig(root)} × 10${sup(j)} ≈ ${fmtVal(est, 4)}",
        )
        val answer = Math.sqrt(m) * Math.pow(10.0, k / 2.0)
        val p = Problem("√${fmtArg(x)}", answer, "≈ ${fmtVal(answer)}", tolRel = 0.01,
            explanation = lines.joinToString("<br>"), hint = "3 significant figures")
        return withEstimate(p, est)
    }

    private fun rootL3(rng: Rng): Problem {
        var big = 50
        var res: Pair<Double, String>? = null
        for (i in 0 until 100) {
            val n = rng.randint(10, 99)
            val e = rng.choice(listOf(0, 0, 1, 2))
            big = n * ipow(10, e).toInt()
            if (big >= 1900 || n % 10 == 0 || ipow(pyRound(Math.pow(big.toDouble(), 1.0 / 3)), 3) == big.toLong()) continue
            val r = cbrtParts(big.toDouble())
            if (Math.abs(r.first / Math.pow(big.toDouble(), 1.0 / 3) - 1) <= 0.009) {
                res = r
                break
            }
        }
        if (res == null) {
            big = 50
            res = cbrtParts(big.toDouble())
        }
        val (root, text) = res
        val j = rng.randint(-2, 1)
        val x = big * pow10(3 * j)
        val est = root * pow10(j)
        val lines = mutableListOf(text)
        if (j != 0) {
            lines.add(0, "${fmtVal(x)} = ${fmtNum(big)} × 10${sup(3 * j)} → " +
                "∛${fmtNum(big)} × 10${sup(j)}")
            lines.add("→ ${sig(root)} × 10${sup(j)} ≈ ${fmtVal(est, 4)}")
        }
        val answer = Math.pow(big.toDouble(), 1.0 / 3) * pow10(j)
        val p = Problem("∛${fmtArg(x)}", answer, "≈ ${fmtVal(answer)}", tolRel = 0.015,
            explanation = lines.joinToString("<br>"), hint = "3 significant figures")
        return withEstimate(p, est)
    }

    private fun rootL4(rng: Rng): Problem {
        var n = 0
        var m = 0.0
        var k = 0
        var answer = 0.0
        for (i in 0 until 100) {
            n = rng.choice(listOf(4, 5, 6, 7, 8, 10))
            m = randMantissa(rng, listOf(1, 2))
            k = rng.randint(-3, 8)
            answer = Math.pow(10.0, (Math.log10(m) + k) / n)
            if (Math.abs(answer - pyRound(answer).toDouble()) > 0.01 && Math.abs(answer - 1) > 0.05) break
        }
        val x = m * pow10(k)
        val e = logEst(m)
        val lx = pyRound(k + e.value, 3)
        val y = pyRound(lx / n, 3)
        val pe = pow10Est(y)
        val xs = fmtArg(x)
        val expl = "${e.compact(xs, k)}; ÷ $n = ${f3(y)}<br>${pe.compact()}"
        val p = Problem("<sup>$n</sup>√${fmtArg(x)}", answer, "≈ ${fmtVal(answer)}", tolRel = 0.03,
            explanation = expl)
        return withEstimate(p, pe.value)
    }

    // ---------------------------------------------------------------- s_growth

    /** ln(1 + r) ≈ r − r²/2 (+ r³/3 when it matters over n periods). */
    private fun ln1pText(r: Double, n: Int = 1): Pair<Double, String> {
        val terms = mutableListOf(r, -r * r / 2)
        if (n * Math.pow(r, 3.0) / 3 >= 0.002) terms.add(Math.pow(r, 3.0) / 3)
        val g = pyRound(terms.sum(), 5)
        val form = "r − r²/2" + (if (terms.size == 3) " + r³/3" else "")
        val nums = fmtNum(r) + terms.drop(1).joinToString("") { " ${pm(it)} ${fmtNum(Math.abs(it), sig = 2)}" }
        return g to "ln ${fmtNum(1 + r)} ≈ $form = $nums = ${fmtNum(g, sig = 3)}"
    }

    private fun growthL1(rng: Rng): Problem {
        val r = rng.choice(listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 12.0, 15.0, 18.0,
            20.0, 24.0, 25.0, 30.0))
        val answer = Math.log(2.0) / Math.log1p(r / 100)
        val rule = 72 / r
        val est: Double
        var expl: String
        if (Math.abs(rule / answer - 1) <= 0.03) {
            est = rule
            expl = "Rule of 72: 72 / ${fmtNum(r)} = ${sig(rule, 3)}"
        } else {
            est = 69.3 / r + 0.35
            val side = if (r < 4) "low" else "high"
            expl = "At $side rates the rule of 72 drifts; use 69.3/r + 0.35:<br>" +
                "69.3 / ${fmtNum(r)} + 0.35 = ${sig(69.3 / r, 3)} + 0.35 = ${sig(est, 3)}"
        }
        expl += "<br>(exact: ln 2 / ln ${fmtNum(1 + r / 100)} = ${sig(answer, 3)})"
        val p = Problem("Doubling time at ${fmtNum(r)}% per period", answer, "≈ ${sig(answer, 3)}",
            tolRel = 0.05, explanation = expl, hint = "periods")
        return withEstimate(p, est)
    }

    private fun growthL2(rng: Rng): Problem {
        var rp = 6.0
        var n = 12
        var found = false
        for (i in 0 until 100) {
            rp = rng.choice(listOf(1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 12.0))
            n = rng.randint(5, 40)
            val y = n * Math.log1p(rp / 100)
            if (0.25 <= y && y <= 3.5) {
                found = true
                break
            }
        }
        if (!found) {
            rp = 6.0; n = 12
        }
        val r = rp / 100
        val (g, gtext) = ln1pText(r, n)
        val y = pyRound(n * g, 3)
        val (factor, etext) = expEst(y)
        var answer = Math.pow(1 + r, n.toDouble())
        val lines = mutableListOf(gtext, "× $n = ${f3(y)} → growth factor e${sup(y)}", etext)
        val prompt: String
        val est: Double
        val hint: String
        val shown: String
        if (rng.random() < 0.5) {
            prompt = "${fmtNum(1 + r)}${sup(n)}"
            est = factor; hint = "growth factor"; shown = "≈ ${sig(answer, 3)}"
        } else {
            val principal = rng.choice(listOf(500, 1000, 1000, 2000, 5000, 10000, 20000))
            prompt = "\$${fmtNum(principal)} at ${fmtNum(rp)}% for $n years"
            est = principal * factor
            answer *= principal
            hint = "dollars"; shown = "≈ \$${sig(answer, 3)}"
            lines.add("→ \$${fmtNum(principal)} × ${sig(factor, 3)} ≈ \$${sig(est, 3)}")
        }
        val p = Problem(prompt, answer, shown, tolRel = 0.03, explanation = lines.joinToString("<br>"), hint = hint)
        return withEstimate(p, est)
    }

    private val VERBS: Map<Int, String> =
        linkedMapOf(2 to "double", 3 to "triple", 4 to "quadruple", 5 to "grow 5×", 10 to "grow 10×")

    private fun growthL3(rng: Rng): Problem {
        val kind = rng.choice(listOf("half", "rate", "cont", "years"))
        val answer: Double
        val est: Double
        val expl: String
        val prompt: String
        val hint: String
        if (kind == "half") {
            val pc = rng.choice(listOf(2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30))
            val q = pc / 100.0
            val terms = listOf(q, q * q / 2) + (if (q >= 0.1) listOf(Math.pow(q, 3.0) / 3) else emptyList())
            val g = terms.sum()
            est = 0.693 / g
            answer = Math.log(2.0) / -Math.log1p(-q)
            val nums = terms.joinToString(" + ") { fmtNum(it, sig = 2) }
            expl = "−ln(1 − ${fmtNum(q)}) ≈ q + q²/2${if (terms.size == 3) " + q³/3" else ""} = " +
                "$nums = ${fmtNum(g, sig = 3)}<br>half-life = 0.693 / ${fmtNum(g, sig = 3)} ≈ " +
                "${sig(est, 3)}  (shortcut: 69.3/$pc − 0.35 = ${sig(69.3 / pc - 0.35, 3)})"
            prompt = "Half-life if $pc% decays per period"; hint = "periods"
        } else if (kind == "rate") {
            var bigF = 3
            var bigN = 20
            var found = false
            for (i in 0 until 100) {
                bigF = rng.choice(VERBS.keys.toList())
                bigN = rng.randint(3, 40)
                val rate = Math.pow(bigF.toDouble(), 1.0 / bigN) - 1
                if (0.02 <= rate && rate <= 0.30) {
                    found = true
                    break
                }
            }
            if (!found) {
                bigF = 3; bigN = 20
            }
            val rate = Math.pow(bigF.toDouble(), 1.0 / bigN) - 1
            val lnF = LN.getValue(bigF)
            val g = lnF / bigN
            val terms = listOf(g, g * g / 2) + (if (g >= 0.1) listOf(Math.pow(g, 3.0) / 6) else emptyList())
            est = 100 * terms.sum()
            answer = 100 * rate
            val nums = terms.joinToString(" + ") { fmtNum(it, sig = 3) }
            expl = "continuous rate = ln $bigF / $bigN = ${f3(lnF)}/$bigN = ${fmtNum(g, sig = 3)}<br>" +
                "annual = e<sup>g</sup> − 1 ≈ g + g²/2${if (terms.size == 3) " + g³/6" else ""} = " +
                "$nums ≈ ${sig(est / 100, 3)} → ${sig(est, 3)}%"
            prompt = "Annual rate to ${VERBS.getValue(bigF)} in $bigN years?"; hint = "% per year"
        } else if (kind == "cont") {
            val rp = rng.randint(1, 12)
            if (rng.random() < 0.3) {
                answer = 100 * Math.log(2.0) / rp
                est = 69.3 / rp
                expl = "Continuous growth: e<sup>rT</sup> = 2 → T = ln 2 / r = 69.3 / $rp " +
                    "= ${sig(est, 3)}"
                prompt = "Doubling time at $rp% continuous growth"; hint = "periods"
            } else {
                var t = 20
                var found = false
                for (i in 0 until 100) {
                    t = rng.randint(3, 40)
                    val v = (rp * t) / 100.0
                    if (0.3 <= v && v <= 3.5) {
                        found = true
                        break
                    }
                }
                if (!found) t = 20
                val y = pyRound((rp * t) / 100.0, 3)
                val (ee, etext) = expEst(y)
                est = ee
                answer = Math.exp((rp * t) / 100.0)
                expl = "factor = e<sup>rt</sup> = e<sup>${fmtNum(rp / 100.0)} × $t</sup> = ${e(y)}<br>$etext"
                prompt = "Growth factor: $rp% continuous for $t years"; hint = "growth factor"
            }
        } else {
            val bigF = rng.choice(listOf(3, 5, 10))
            val rp = rng.randint(3, 15)
            val r = rp / 100.0
            val (g, gtext) = ln1pText(r, 40)
            val lnF = LN.getValue(bigF)
            est = lnF / g
            answer = Math.log(bigF.toDouble()) / Math.log1p(r)
            expl = "$gtext<br>years = ln $bigF / ${fmtNum(g, sig = 3)} = ${f3(lnF)} / ${fmtNum(g, sig = 3)} ≈ ${sig(est, 3)}"
            prompt = "Years to grow $bigF× at $rp%/yr?"; hint = "years"
        }
        val p = Problem(prompt, answer, "≈ ${sig(answer, 3)}", tolRel = 0.05, explanation = expl, hint = hint)
        return withEstimate(p, est)
    }

    // ---------------------------------------------------------------- lessons

    private val L_EXP = Lesson(
        "Exponentials",
        "\n" +
        "<p>Every value here comes from a few anchors.</p>\n" +
        "<h3>Anchors</h3>\n" +
        "<p>Know these by heart.</p>\n" +
        "<ex>e ≈ 2.718<br>" +
        "e<sup>2</sup> ≈ 7.389<br>" +
        "e<sup>3</sup> ≈ 20.09<br>" +
        "e<sup>4</sup> ≈ 54.6<br>" +
        "e<sup>5</sup> ≈ 148.4<br>" +
        "<b>√e = e<sup>0.5</sup> ≈ 1.649</b></ex>\n" +
        "<h3>Adding exponents</h3>\n" +
        "<p>Exponents add, so multiply anchors. Negative exponents are reciprocals.</p>\n" +
        "<ex>e<sup>3.5</sup> = e<sup>3</sup> × √e<br>" +
        "20.09 × 1.65 ≈ <b>33.1</b></ex>\n" +
        "<ex>e<sup>−2</sup> = 1/7.389 ≈ <b>0.135</b></ex>\n" +
        "<h3>Small x</h3>\n" +
        "<p>e<sup>x</sup> ≈ 1 + x + x²/2. Add x³/6 once |x| passes about 0.15.</p>\n" +
        "<ex>e<sup>0.12</sup><br>" +
        "1 + 0.12 + 0.0072 = <b>1.1272</b></ex>\n" +
        "<ex>e<sup>−0.25</sup><br>" +
        "1 − 0.25 + 0.03125 − 0.0026 ≈ <b>0.779</b></ex>\n" +
        "<tip>Use it when x is close to 0.</tip>\n" +
        "<h3>Any other x</h3>\n" +
        "<p>Split x into the nearest half-integer plus a small remainder f, with |f| ≤ 0.25. Then multiply: e<sup>n</sup> × e<sup>f</sup>.</p>\n" +
        "<ex>e<sup>2.3</sup> = e<sup>2.5</sup> × e<sup>−0.2</sup><br>" +
        "e<sup>2.5</sup> = 7.389 × 1.649 ≈ 12.18<br>" +
        "e<sup>−0.2</sup> ≈ 1 − 0.2 + 0.02 = 0.82<br>" +
        "12.18 × 0.82 ≈ <b>9.99</b> (true 9.97)</ex>\n" +
        "<tip>Use it when x is neither an anchor nor small.</tip>\n" +
        "<h3>Convert to base 10</h3>\n" +
        "<p>An alternative: e<sup>x</sup> = 10<sup>0.434x</sup>. Convert, then use your powers-of-ten skill.</p>\n" +
        "<ex>e<sup>3</sup> = 10<sup>0.434 × 3</sup><br>" +
        "0.434 × 3 = 1.302<br>" +
        "10<sup>1.302</sup> = 10 × 10<sup>0.302</sup><br>" +
        "10 × 2 ≈ <b>20</b> (true 20.09)</ex>\n" +
        "<tip>Also handy: e<sup>0.693</sup> ≈ 2, e<sup>1.099</sup> ≈ 3, e<sup>2.303</sup> ≈ 10.</tip>\n",
    )

    private val L_POW = Lesson(
        "Powers",
        "\n" +
        "<p>Break a power into pieces you know.</p>\n" +
        "<h3>Change the base</h3>\n" +
        "<p>Rewrite the base as a power of 2, or write 5 as 10 over 2.</p>\n" +
        "<ex>8<sup>5</sup> = 2<sup>15</sup> = <b>32 768</b></ex>\n" +
        "<ex>5<sup>7</sup> = 10<sup>7</sup> ÷ 2<sup>7</sup><br>" +
        "10 000 000 ÷ 128 = <b>78 125</b></ex>\n" +
        "<tip>Use it when the base is a power of 2, or is 5.</tip>\n" +
        "<h3>Square the half power</h3>\n" +
        "<p>For an even exponent, find the power at half the exponent and square it.</p>\n" +
        "<ex>7<sup>4</sup> = 49<sup>2</sup> = <b>2 401</b><br>" +
        "3<sup>6</sup> = 27<sup>2</sup> = <b>729</b></ex>\n" +
        "<h3>Squares near a round number</h3>\n" +
        "<p>a<sup>2</sup> = (a − d)(a + d) + d<sup>2</sup>. Pick d so that one factor is round.</p>\n" +
        "<ex>47<sup>2</sup> with d of 3<br>" +
        "47<sup>2</sup> = 50 × 44 + 9<br>" +
        "50 × 44 = 2 200<br>" +
        "2 200 + 9 = <b>2 209</b></ex>\n" +
        "<tip>Use it for two-digit squares.</tip>\n" +
        "<h3>Decimals</h3>\n" +
        "<p>Do the digits, then place the point.</p>\n" +
        "<ex>1.1<sup>3</sup><br>" +
        "11<sup>3</sup> = 1 331<br>" +
        "Three decimal places → <b>1.331</b></ex>\n" +
        "<h3>Fractional exponents</h3>\n" +
        "<p>Root first, then power: a<sup>p/q</sup> = (<sup>q</sup>√a)<sup>p</sup>.</p>\n" +
        "<ex>27<sup>4/3</sup> = (∛27)<sup>4</sup><br>" +
        "∛27 = 3<br>" +
        "3<sup>4</sup> = <b>81</b></ex>\n" +
        "<ex>16<sup>0.75</sup> = 16<sup>3/4</sup> = (<sup>4</sup>√16)<sup>3</sup><br>" +
        "<sup>4</sup>√16 = 2<br>" +
        "2<sup>3</sup> = <b>8</b></ex>\n" +
        "<h3>Half-integer exponents</h3>\n" +
        "<p>Split off one √ and multiply.</p>\n" +
        "<ex>2<sup>6.5</sup> = 2<sup>6</sup> × √2<br>" +
        "64 × 1.414 ≈ <b>90.5</b></ex>\n" +
        "<ex>10<sup>2.5</sup> = 10<sup>2</sup> × √10<br>" +
        "100 × 3.162 ≈ <b>316</b></ex>\n" +
        "<h3>Any a<sup>b</sup>: go through logs</h3>\n" +
        "<p>a<sup>b</sup> = 10<sup>b·log a</sup>. Find the log, multiply by b, then turn the result back into a number.</p>\n" +
        "<ex>3.7<sup>2.4</sup>, with log 4 ≈ 0.602<br>" +
        "log 3.7 ≈ 0.602 − 0.434 × 0.3/4 ≈ 0.569<br>" +
        "0.569 × 2.4 ≈ 1.366<br>" +
        "10<sup>0.366</sup>: log 2.4 ≈ 0.380, which is 0.014 higher<br>" +
        "2.4 × (1 − 0.014/0.434) ≈ 2.32<br>" +
        "2.32 × 10 = <b>23.2</b> (true 23.1)</ex>\n" +
        "<tip>Keep three decimals in the log: the multiplication by b magnifies its error.</tip>\n",
    )

    private val L_ROOT = Lesson(
        "Roots",
        "\n" +
        "<h3>Square roots</h3>\n" +
        "<p>√(n² + d) ≈ n + d/2n. Find the nearest square, then divide the leftover by twice its root.</p>\n" +
        "<ex>√52 = √(49 + 3)<br>" +
        "7 + 3/14 ≈ <b>7.21</b> (true 7.211)</ex>\n" +
        "<p>Going down works too.</p>\n" +
        "<ex>√60 = √(64 − 4)<br>" +
        "8 − 4/16 = <b>7.75</b> (true 7.746)</ex>\n" +
        "<tip>It's the tangent line of √x, so it always slightly overshoots.</tip>\n" +
        "<h3>Big or small numbers</h3>\n" +
        "<p>Pair the exponent first. Write x = M × 10<sup>even</sup> with M between 10 and 1000, then √x = √M × 10<sup>half</sup>.</p>\n" +
        "<ex>√52 000 = √520 × 10<sup>1</sup><br>" +
        "√520 ≈ 23 − 9/46 ≈ 22.8<br>" +
        "22.8 × 10 = <b>228</b></ex>\n" +
        "<ex>√0.003 = √30 × 10<sup>−2</sup><br>" +
        "√30 ≈ 5.48<br>" +
        "5.48 × 0.01 = <b>0.0548</b></ex>\n" +
        "<h3>Cube roots</h3>\n" +
        "<p>∛(n³ + d) ≈ n + d/3n². Group the exponent in threes.</p>\n" +
        "<ex>∛70 = ∛(64 + 6)<br>" +
        "4 + 6/48 = <b>4.125</b> (true 4.121)</ex>\n" +
        "<ex>∛0.07 = ∛70 × 10<sup>−1</sup> ≈ <b>0.412</b></ex>\n" +
        "<h3>Higher roots</h3>\n" +
        "<p>Use logs: <sup>n</sup>√x = 10<sup>(log x)/n</sup>.</p>\n" +
        "<ex><sup>4</sup>√50<br>" +
        "log 50 ≈ 1.699<br>" +
        "1.699 ÷ 4 ≈ 0.425<br>" +
        "10<sup>0.425</sup>: log 2.7 ≈ 0.431, a hair above<br>" +
        "So <b>≈ 2.66</b> (true 2.659)</ex>\n" +
        "<tip>Use it for fourth roots and above.</tip>\n",
    )

    private val L_GROWTH = Lesson(
        "Growth & rule of 72",
        "\n" +
        "<h3>Why 72?</h3>\n" +
        "<p>Growing r per period doubles when n · ln(1 + r) = ln 2 ≈ 0.693.</p>\n" +
        "<p>For small r, ln(1 + r) ≈ r, giving n ≈ 69.3/r, with r in percent. But ln(1 + r) is a bit less than r, so the true number is a bit larger.</p>\n" +
        "<p>72 corrects for that around 8%, and it divides nicely by 2, 3, 4, 6, 8, 9 and 12.</p>\n" +
        "<ex>6%: 72/6 = <b>12</b> periods (true 11.9)</ex>\n" +
        "<tip>Reach for 72 when r divides it nicely.</tip>\n" +
        "<h3>Best all-round</h3>\n" +
        "<p>Use <b>69.3/r + 0.35</b>, with r in percent.</p>\n" +
        "<ex>1%: 69.3 + 0.35 ≈ <b>69.7</b></ex>\n" +
        "<ex>25%: 69.3/25 ≈ 2.77<br>" +
        "2.77 + 0.35 = <b>3.12</b></ex>\n" +
        "<h3>Growth factors</h3>\n" +
        "<p>(1 + r)<sup>n</sup> = e<sup>n·ln(1 + r)</sup>, with ln(1 + r) ≈ r − r²/2. Add r³/3 for long horizons.</p>\n" +
        "<p>Then find e<sup>y</sup> from the ln anchors: ln 2 ≈ 0.693, ln 3 ≈ 1.099, ln 5 ≈ 1.609, ln 10 ≈ 2.303, and e<sup>1</sup> = e.</p>\n" +
        "<ex>1.06<sup>12</sup><br>" +
        "ln 1.06 ≈ 0.06 − 0.0018 = 0.0582<br>" +
        "0.0582 × 12 ≈ 0.698<br>" +
        "0.698 ≈ ln 2 + 0.005<br>" +
        "2 × 1.005 ≈ <b>2.01</b> (true 2.012)</ex>\n" +
        "<h3>Decay</h3>\n" +
        "<p>−ln(1 − q) ≈ q + q²/2, so half-life ≈ 69.3/q − 0.35, with q in percent.</p>\n" +
        "<ex>10% per period<br>" +
        "69.3 ÷ 10 = 6.93<br>" +
        "6.93 − 0.35 = 6.58<br>" +
        "Half-life <b>≈ 6.6</b></ex>\n" +
        "<h3>Required rate</h3>\n" +
        "<p>To grow F× in N years, the continuous rate is g = ln F / N. The annual rate is e<sup>g</sup> − 1 ≈ g + g²/2.</p>\n" +
        "<ex>Triple in 20 years, with ln 3 ≈ 1.099<br>" +
        "g = 1.099 ÷ 20 ≈ 0.055<br>" +
        "g²/2 ≈ 0.0015<br>" +
        "0.055 + 0.0015 = 0.0565<br>" +
        "Annual rate <b>≈ 5.65%</b></ex>\n",
    )

    override val SKILLS: List<Skill> = listOf(
        Skill("s_exp", "Exponentials", "logs", L_EXP, listOf(
            Level("e^x for half-integers −3…5", 4.0, ::expL1),
            Level("small x: 1 + x + x²/2", 5.0, ::expL2),
            Level("any x: e^n × e^f", 9.0, ::expL3),
        )),
        Skill("s_pow", "Powers", "logs", L_POW, listOf(
            Level("exact integer powers", 6.0, ::powL1),
            Level("fractional exponents: 8^(2/3), 2^6.5", 8.0, ::powL2),
            Level("arbitrary aᵇ via logs", 14.0, ::powL3),
        )),
        Skill("s_root", "Roots", "logs", L_ROOT, listOf(
            Level("√n up to 1000: n + d/2n", 6.0, ::rootL1),
            Level("√ of large/small numbers", 9.0, ::rootL2),
            Level("cube roots", 11.0, ::rootL3),
            Level("nth roots via logs", 14.0, ::rootL4),
        )),
        Skill("s_growth", "Growth & rule of 72", "logs", L_GROWTH, listOf(
            Level("doubling time", 5.0, ::growthL1),
            Level("growth factor (1 + r)ⁿ", 10.0, ::growthL2),
            Level("half-life, required rate, continuous", 14.0, ::growthL3),
        )),
    )
}
