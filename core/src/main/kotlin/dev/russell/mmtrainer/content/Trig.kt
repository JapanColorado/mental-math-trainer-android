package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.Fraction
import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.floorMod
import dev.russell.mmtrainer.py.isClose
import dev.russell.mmtrainer.py.pyRound

/*
 * Trigonometry track: degree/radian conversion, trig values, small angles, inverse trig.
 *
 * Explanations lean on the memorized anchors (special angles, 15°/75°, 1 rad = 57.3°,
 * 0.01745 rad per degree) and end with the mental estimate in bold.
 */
object Trig : ContentModule {
    private const val TRACK = "trig"

    val RAD_PER_DEG = Math.PI / 180   // ≈ 0.01745
    val DEG_PER_RAD = 180 / Math.PI   // ≈ 57.3

    private val SIN_EXACT = mapOf(0 to "0", 15 to "(√6 − √2)/4", 30 to "1/2", 45 to "√2/2", 60 to "√3/2",
        75 to "(√6 + √2)/4", 90 to "1")
    private val TAN_EXACT = mapOf(0 to "0", 15 to "2 − √3", 30 to "√3/3", 45 to "1", 60 to "√3", 75 to "2 + √3")

    private fun func(fn: String, x: Double): Double = when (fn) {
        "sin" -> Math.sin(x)
        "cos" -> Math.cos(x)
        "tan" -> Math.tan(x)
        else -> error("unknown function $fn")
    }

    private fun inv(fn: String, x: Double): Double = when (fn) {
        "arcsin" -> Math.asin(x)
        "arccos" -> Math.acos(x)
        "arctan" -> Math.atan(x)
        else -> error("unknown function $fn")
    }

    // ------------------------------------------------------------ formatting helpers

    private fun neg(s: String): String = s.replace("-", MINUS)
    private fun neg(n: Int): String = neg(n.toString())

    /** Decimal with up to [places] places, trailing zeros stripped, unicode minus. */
    private fun d(x: Double, places: Int = 3): String {
        var s = fmtF(x, places).trimEnd('0').trimEnd('.')
        if (s == "-0" || s == "") s = "0"
        return neg(s)
    }

    private fun d(x: Int, places: Int = 3): String = d(x.toDouble(), places)

    /** Four significant figures: 0.01745, 0.6458, 4.206. */
    private fun s4(x: Double): String = neg(fmtNum(x, sig = 4))

    private fun deg(x: Double): String = "${neg(fmtNum(x))}°"

    /** 'sin 37°' or 'cos(−30°)'. */
    private fun arg(fn: String, inner: String, negative: Boolean): String =
        if (negative) "$fn($inner)" else "$fn $inner"

    private fun signed(exact: String, negative: Boolean): String {
        if (!negative || exact == "0") return exact
        return if (" " in exact) "−($exact)" else "−$exact"
    }

    private fun pm(x: Double): String = if (x >= 0) "+" else "−"
    private fun pm(x: Int): String = if (x >= 0) "+" else "−"

    /** Fraction(3, 4) -> '3π/4'. */
    private fun piFrac(f: Fraction): String {
        val num = if (f.num == 1L) "π" else "${f.num}π"
        return if (f.den == 1L) num else "$num/${f.den}"
    }

    // ------------------------------------------------------------ degrees <-> radians

    private val PI_FRAC_DEC = mapOf(1L to 3.1416, 2L to 1.5708, 3L to 1.0472, 4L to 0.7854, 6L to 0.5236, 12L to 0.2618)

    fun degToRadSpecial(deg: Int): Problem {
        val f = Fraction.of(deg, 180)
        val unit = PI_FRAC_DEC.getValue(f.den)
        val lead = "$deg° = $deg/180 · π = ${piFrac(f)}"
        val expl = if (f.num == 1L) {
            "$lead ≈ <b>${d(unit)}</b>"
        } else if (f.den > 1) {
            "$lead<br>π/${f.den} ≈ ${d(unit, 4)} → ${f.num} × ${d(unit, 4)} ≈ " +
                "<b>${d(f.num * unit)}</b>"
        } else {
            "$lead ≈ ${f.num} × 3.1416 ≈ <b>${d(f.num * unit)}</b>"
        }
        val ans = deg * RAD_PER_DEG
        return Problem("$deg° → rad", ans, "≈ ${d(ans)}", tolRel = 0.01, explanation = expl,
            hint = "radians, decimal")
    }

    fun radToDegSpecial(f: Fraction): Problem {
        val per = 180 / f.den
        val ans = f.toDouble() * 180
        val expl = if (f.num == 1L) {
            "π = 180° → ${piFrac(f)} = 180°/${f.den} = <b>${deg(ans)}</b>"
        } else if (f.den > 1) {
            "π/${f.den} = 180°/${f.den} = $per° → ${piFrac(f)} = " +
                "${f.num} × $per° = <b>${deg(ans)}</b>"
        } else {
            "π = 180° → ${piFrac(f)} = ${f.num} × 180° = <b>${deg(ans)}</b>"
        }
        return Problem("${piFrac(f)} → °", ans, deg(ans), tolRel = 0.01, explanation = expl,
            hint = "degrees")
    }

    private fun degradL1(rng: Rng): Problem {
        if (rng.random() < 0.5) return degToRadSpecial(15 * rng.randint(1, 24))
        while (true) {
            val den = rng.choice(listOf(1, 2, 3, 4, 6, 12))
            val num = rng.randint(1, 2 * den)
            val f = Fraction.of(num, den)
            if (f.den == den.toLong()) return radToDegSpecial(f)
        }
    }

    private val DEG_ANCHORS = linkedMapOf(0 to "0", 30 to "π/6", 45 to "π/4", 60 to "π/3", 90 to "π/2",
        120 to "2π/3", 135 to "3π/4", 150 to "5π/6", 180 to "π", 210 to "7π/6", 225 to "5π/4", 240 to "4π/3",
        270 to "3π/2", 300 to "5π/3", 315 to "7π/4", 330 to "11π/6", 360 to "2π")

    fun degToRad(deg: Int): Problem {
        val a = DEG_ANCHORS.keys.minBy { Math.abs(deg - it) }  // first of equals
        val dd = deg - a
        val ans = deg * RAD_PER_DEG
        val dr = Math.abs(dd) * RAD_PER_DEG
        val expl = if (a == 0) {
            "$deg° × 0.01745 ≈ <b>${s4(ans)}</b> rad<br>" +
                "(or $deg ÷ 57.3; or $deg × 0.0175 then shave 0.3%)"
        } else {
            val ar = a * RAD_PER_DEG
            "$deg° = $a° ${pm(dd)} ${Math.abs(dd)}°: ${DEG_ANCHORS.getValue(a)} ≈ ${d(ar)}, " +
                "${Math.abs(dd)} × 0.01745 ≈ ${s4(dr)}<br>" +
                "→ ${d(ar)} ${pm(dd)} ${s4(dr)} ≈ <b>${s4(ar + Math.copySign(dr, dd.toDouble()))}</b> rad"
        }
        return Problem("$deg° → rad", ans, "≈ ${s4(ans)}", tolRel = 0.01, explanation = expl,
            hint = "radians, decimal")
    }

    fun radToDeg(r: Double): Problem {
        val ans = r * DEG_PER_RAD
        val whole = r.toInt()
        val frac = pyRound(r - whole, 6)
        val expl = if (whole == 0 || frac == 0.0) {
            "${d(r, 2)} × 57.3 ≈ <b>${d(r * 57.3, 1)}°</b>"
        } else {
            "${d(r, 2)} × 57.3 = $whole × 57.3 + ${d(frac, 2)} × 57.3 = " +
                "${d(whole * 57.3, 1)} + ${d(frac * 57.3, 1)} ≈ <b>${d(r * 57.3, 1)}°</b>"
        }
        return Problem("${d(r, 2)} rad → °", ans, "≈ ${d(ans, 1)}°", tolRel = 0.01,
            explanation = expl, hint = "degrees")
    }

    private fun degradL2(rng: Rng): Problem {
        if (rng.random() < 0.5) {
            while (true) {
                val deg = rng.randint(1, 359)
                if (deg % 15 != 0) return degToRad(deg)
            }
        }
        if (rng.random() < 0.7) return radToDeg(rng.randint(1, 62) / 10.0)
        return radToDeg(rng.randint(2, 124) * 5 / 100.0)
    }

    // ------------------------------------------------------------ trig values

    private val QUADRANT = mapOf(1 to "I", 2 to "II", 3 to "III", 4 to "IV")
    private val POSITIVE = mapOf(1 to "all three are positive", 2 to "only sin is positive",
        3 to "only tan is positive", 4 to "only cos is positive")
    private val AXIS = mapOf(0 to Triple("the +x axis", 1, 0), 90 to Triple("the +y axis", 0, 1),
        180 to Triple("the −x axis", -1, 0), 270 to Triple("the −y axis", 0, -1))

    /** sin/cos/tan of a multiple of 15° (tan only where defined). */
    fun specialTrig(fn: String, angle: Int): Problem {
        var value = func(fn, angle * RAD_PER_DEG)
        if (Math.abs(value) < 1e-12) value = 0.0
        val a = floorMod(angle, 360)
        val prompt = arg(fn, "${neg(angle)}°", angle < 0)
        val same = if (a != angle) "${neg(angle)}° ≡ $a°: " else "$a°: "
        val expl: String
        val display: String
        if (a % 90 == 0) {
            val (where, x, y) = AXIS.getValue(a)
            val exact = d(value)
            expl = "${same}point on $where, (cos, sin) = (${neg(x)}, ${neg(y)})<br>" +
                "→ $prompt = <b>$exact</b>"
            display = exact
        } else {
            val q = a / 90 + 1
            val ref = when (q) { 1 -> a; 2 -> 180 - a; 3 -> a - 180; else -> 360 - a }
            val form = when (q) { 1 -> "$a°"; 2 -> "180° − $ref°"; 3 -> "180° + $ref°"; else -> "360° − $ref°" }
            val base = when (fn) {
                "sin" -> SIN_EXACT.getValue(ref)
                "cos" -> SIN_EXACT.getValue(90 - ref)
                else -> TAN_EXACT.getValue(ref)
            }
            val exact = signed(base, value < 0)
            val refPart = if (q > 1) "reference $ref°, " else ""
            val one = exact == "1" || exact == "−1"
            val valuePart = if (one) "<b>$exact</b>" else "$exact ≈ <b>${d(value)}</b>"
            expl = "$same$form (quadrant ${QUADRANT.getValue(q)}): $refPart${POSITIVE.getValue(q)}<br>$prompt = $valuePart"
            display = if (one) exact else "$exact ≈ ${d(value)}"
        }
        return Problem(prompt, value, display, tolAbs = 0.005, explanation = expl,
            hint = "decimal (±0.005)")
    }

    private val TRIG_FNS = listOf("sin", "cos", "tan")

    private fun trigL1(rng: Rng): Problem {
        while (true) {
            val k = rng.randint(-24, 24)
            val angle = 15 * k
            if (angle % 30 != 0 && angle % 45 != 0 && rng.random() < 0.6) continue  // 15°-family angles appear, but less often
            val fn = rng.choice(TRIG_FNS)
            if (fn == "tan" && floorMod(angle, 180) == 90) continue
            if (floorMod(angle, 360) == 0 && rng.random() < 0.8) continue  // rarely ask about 0°
            return specialTrig(fn, angle)
        }
    }

    private class Slope(val value: Double, val slope: Double, val text: String)

    /** (value at anchor, derivative per radian, text) for fn at anchor a degrees. */
    private fun slopeText(fn: String, a: Int): Slope {
        val ar = a * RAD_PER_DEG
        if (fn == "sin") return Slope(Math.sin(ar), Math.cos(ar), "slope cos $a° ≈ ${d(Math.cos(ar))}")
        if (fn == "cos") return Slope(Math.cos(ar), -Math.sin(ar), "slope −sin $a° ≈ ${d(-Math.sin(ar))}")
        val t = Math.tan(ar)
        return Slope(t, 1 + t * t, "slope 1 + tan² $a° ≈ ${d(1 + t * t)}")
    }

    /** fn(deg°) via nearest 15° anchor + derivative. Returns the problem and the estimate. */
    fun linearTrig(fn: String, deg: Int): Pair<Problem, Double> {
        var a = (15 * pyRound(deg / 15.0)).toInt()
        if (fn == "tan" && a == 90) a = 75
        val dd = deg - a
        val st = slopeText(fn, a)
        val base = st.value
        val slope = st.slope
        val dr = dd * RAD_PER_DEG
        val est: Double
        val expl: String
        if (Math.abs(slope) < 1e-9) {
            // flat at the anchor (cos near 0°, sin near 90°): use the curvature, 1 − θ²/2
            est = 1 - dr * dr / 2
            expl = "$fn is flat at $a°, so use 1 − θ²/2 with θ = ${Math.abs(dd)}° × 0.01745 ≈ " +
                "${d(Math.abs(dr))} rad<br>→ $fn $deg° ≈ 1 − ${d(dr * dr / 2, 4)} ≈ <b>${d(est)}</b>"
        } else {
            est = base + slope * dr
            val term = slope * dr
            expl = "Anchor $fn $a° ≈ ${d(base)}; ${st.text} per radian<br>" +
                "${neg(dd)}° = ${neg(dd)} × 0.01745 ≈ ${d(dr)} rad → $fn $deg° ≈ ${d(base)} " +
                "${pm(term)} ${d(Math.abs(slope))} × ${d(Math.abs(dr))} ≈ <b>${d(est)}</b>"
        }
        val ans = func(fn, deg * RAD_PER_DEG)
        return Problem("$fn $deg°", ans, "≈ ${d(ans)}", tolAbs = 0.02, explanation = expl,
            hint = "decimal (±0.02)") to est
    }

    private fun trigL2(rng: Rng): Problem {
        while (true) {
            val fn = rng.choice(TRIG_FNS)
            val deg = rng.randint(1, if (fn == "tan") 70 else 89)
            if (deg % 15 == 0) continue
            val (p, est) = linearTrig(fn, deg)
            if (Math.abs(est - p.answer) <= 0.012) return p  // only angles where the taught method really works
        }
    }

    private class RadAnchor(val rad: Double, val label: String, val deg: Int)

    private val RAD_ANCHORS = listOf(RadAnchor(0.0, "0", 0), RadAnchor(Math.PI / 12, "π/12", 15),
        RadAnchor(Math.PI / 6, "π/6", 30), RadAnchor(Math.PI / 4, "π/4", 45), RadAnchor(Math.PI / 3, "π/3", 60),
        RadAnchor(5 * Math.PI / 12, "5π/12", 75), RadAnchor(Math.PI / 2, "π/2", 90))

    private fun taylor(fn: String, y: Double): Pair<Double, String> {
        val ys = d(y, 3)
        if (fn == "sin") {
            val t = Math.pow(y, 3.0) / 6
            return (y - t) to "$fn $ys ≈ $ys − ${ys}³/6 = $ys − ${d(t, 4)}"
        }
        if (fn == "tan") {
            val t = Math.pow(y, 3.0) / 3
            return (y + t) to "$fn $ys ≈ $ys + ${ys}³/3 = $ys + ${d(t, 4)}"
        }
        val t2 = y * y / 2
        val t4 = Math.pow(y, 4.0) / 24
        if (t4 > 0.004) {
            return (1 - t2 + t4) to "cos $ys ≈ 1 − ${ys}²/2 + ${ys}⁴/24 = 1 − ${d(t2, 4)} + ${d(t4, 4)}"
        }
        return (1 - t2) to "cos $ys ≈ 1 − ${ys}²/2 = 1 − ${d(t2, 4)}"
    }

    private fun anchorRad(fn: String, y: Double): Pair<Double, String> {
        val anchors = if (fn == "tan") RAD_ANCHORS.dropLast(1) else RAD_ANCHORS
        val anc = anchors.minBy { Math.abs(y - it.rad) }
        val ar = anc.rad
        val st = slopeText(fn, anc.deg)
        val base = st.value
        val slope = st.slope
        val dd = y - ar
        val est = base + slope * dd
        val term = slope * dd
        val text = "Anchor ${anc.label} ≈ ${d(ar)} (${anc.deg}°): $fn = ${d(base)}, ${st.text}<br>" +
            "d = ${d(y, 3)} − ${d(ar)} = ${d(dd)} → $fn ${d(y, 3)} ≈ ${d(base)} ${pm(term)} " +
            "${d(Math.abs(slope))} × ${d(Math.abs(dd))}"
        return est to text
    }

    /** fn(x rad); returns (null, NaN) if neither taught method is accurate enough. */
    fun radianTrig(fn: String, x: Double): Pair<Problem?, Double> {
        val ans = func(fn, x)
        var pre = ""
        var y = x
        var sign = 1
        if (x > Math.PI / 2) {  // fold into the first quadrant
            y = Math.PI - x
            if (fn == "sin") {
                pre = "${d(x, 2)} > π/2: sin ${d(x, 2)} = sin(π − ${d(x, 2)}) = sin ${d(y)}<br>"
            } else {
                sign = -1
                pre = "${d(x, 2)} > π/2: cos ${d(x, 2)} = −cos(π − ${d(x, 2)}) = −cos ${d(y)}<br>"
            }
            y = pyRound(y, 3)
        }
        var (est, text) = taylor(fn, y)
        if (y > (if (fn == "cos") 0.8 else 0.6) || Math.abs(sign * est - ans) > 0.01) {
            val r = anchorRad(fn, y)
            est = r.first
            text = r.second
            if (Math.abs(sign * est - ans) > 0.012) return null to Double.NaN
        }
        val tail = if (sign == 1) " ≈ <b>${d(est)}</b>" else " ≈ ${d(est)} → <b>${d(-est)}</b>"
        val expl = pre + text + tail
        val hint = "decimal (±0.02); argument in radians"
        return Problem("$fn ${d(x, 2)} rad", ans, "≈ ${d(ans)}", tolAbs = 0.02, explanation = expl,
            hint = hint) to sign * est
    }

    private fun trigL3(rng: Rng): Problem {
        while (true) {
            val fn = rng.choice(TRIG_FNS)
            val hi = if (fn == "tan") 12 else 31
            val x = if (rng.random() < 0.7) rng.randint(1, hi) / 10.0 else rng.randint(1, 2 * hi) * 5 / 100.0
            if (x == 0.0) continue
            val (p, _) = radianTrig(fn, x)
            if (p != null) return p
        }
    }

    // ------------------------------------------------------------ small angles

    fun smallAngleDeg(fn: String, x: Double): Problem {
        val th = x * RAD_PER_DEG
        val xs = d(x, 1)
        val lead = "θ = $xs × 0.01745 ≈ ${fmtNum(th, sig = 3)} rad (= $xs ÷ 57.3)"
        val ans: Double
        val prompt: String
        val second: String
        if (fn == "1−cos") {
            ans = 1 - Math.cos(th)
            val est = th * th / 2
            prompt = "1 − cos $xs°"
            second = "1 − cos θ ≈ θ²/2 = ${fmtNum(th * th, sig = 3)}/2 ≈ <b>${fmtNum(est, sig = 3)}</b>"
        } else {
            ans = func(fn, th)
            val k = if (fn == "sin") 6 else 3
            val rel = th * th / k
            prompt = "$fn $xs°"
            second = if (rel < 0.003) {
                "$fn θ ≈ θ (relative error θ²/$k ≈ ${fmtF(rel * 100, 2)}%) → " +
                    "<b>${fmtNum(th, sig = 3)}</b>"
            } else {
                val est = if (fn == "sin") th * (1 - rel) else th * (1 + rel)
                "$fn θ ≈ θ·(1 ${if (fn == "sin") "−" else "+"} θ²/$k) = ${fmtNum(th, sig = 3)} × " +
                    "${d(if (fn == "sin") 1 - rel else 1 + rel, 4)} ≈ <b>${fmtNum(est, sig = 3)}</b>"
            }
        }
        return Problem(prompt, ans, "≈ ${fmtNum(ans, sig = 3)}", tolRel = 0.02,
            explanation = "$lead<br>$second", hint = "decimal or e-notation, e.g. 4.36e-2")
    }

    fun smallAngleRad(fn: String, y: Double): Problem {
        val ys = d(y, 3)
        val ans: Double
        val prompt: String
        val expl: String
        if (fn == "1−cos") {
            ans = 1 - Math.cos(y)
            prompt = "1 − cos $ys rad"
            expl = "1 − cos θ ≈ θ²/2 = ${fmtNum(y * y, sig = 3)}/2 ≈ <b>${fmtNum(y * y / 2, sig = 3)}</b>"
        } else {
            ans = func(fn, y)
            prompt = "$fn $ys rad"
            val k = if (fn == "sin") 6 else 3
            val rel = y * y / k
            expl = if (rel < 0.003) {
                "Small θ in radians: $fn θ ≈ θ (off by only θ²/$k ≈ ${fmtF(rel * 100, 2)}%) → <b>$ys</b>"
            } else {
                val factor = if (fn == "sin") 1 - rel else 1 + rel
                "Small θ in radians: $fn θ ≈ θ, but θ²/$k ≈ ${fmtF(rel * 100, 1)}% so refine:<br>" +
                    "$fn θ ≈ θ·(1 ${if (fn == "sin") "−" else "+"} θ²/$k) = $ys × ${d(factor, 4)} " +
                    "≈ <b>${fmtNum(y * factor, sig = 3)}</b>"
            }
        }
        return Problem(prompt, ans, "≈ ${fmtNum(ans, sig = 3)}", tolRel = 0.02, explanation = expl,
            hint = "decimal or e-notation, e.g. 4.36e-2")
    }

    private fun smallL1(rng: Rng): Problem {
        val r = rng.random()
        if (r < 0.45) {
            val fn = rng.choice(listOf("sin", "tan"))
            return smallAngleDeg(fn, rng.randint(1, 20) / 2.0)
        }
        if (r < 0.75) return smallAngleDeg("1−cos", rng.randint(2, 20) / 2.0)
        val fn = rng.choice(listOf("sin", "tan", "1−cos", "1−cos"))
        return smallAngleRad(fn, rng.randint(3, 25) / 100.0)
    }

    private val UNITS = mapOf("cm" to 0.01, "m" to 1.0, "km" to 1000.0)

    private fun q(x: Double, unit: String): String = "${fmtNum(x)} $unit"

    fun arcAngle(s: Double, us: String, dist: Double, ud: String): Problem {
        val sm = s * UNITS.getValue(us)
        val dm = dist * UNITS.getValue(ud)
        val ans = 2 * Math.toDegrees(Math.atan(sm / (2 * dm)))
        val r = sm / dm
        val same = us == ud
        val ratio = if (same) "${q(s, us)} / ${q(dist, ud)}"
        else "${q(s, us)} / ${fmtNum(dm / UNITS.getValue(us))} $us"
        val expl = "θ ≈ size/distance = $ratio = ${fmtNum(r, sig = 3)} rad<br>" +
            "× 57.3 → ≈ <b>${fmtNum(r * 57.3, sig = 3)}°</b>"
        return Problem("${q(s, us)} seen at ${q(dist, ud)}", ans, "≈ ${fmtNum(ans, sig = 3)}°",
            tolRel = 0.02, explanation = expl, hint = "angle subtended, degrees")
    }

    fun arcSize(theta: Double, dist: Double, ud: String): Problem {
        val ans = 2 * dist * Math.tan(Math.toRadians(theta) / 2)
        val th = theta * RAD_PER_DEG
        val expl = "θ = ${fmtNum(theta)} × 0.01745 ≈ ${fmtNum(th, sig = 3)} rad<br>" +
            "size ≈ θ · distance = ${fmtNum(th, sig = 3)} × ${fmtNum(dist)} ≈ " +
            "<b>${fmtNum(th * dist, sig = 3)} $ud</b>"
        return Problem("${fmtNum(theta)}° at ${q(dist, ud)}: size?", ans, "≈ ${fmtNum(ans, sig = 3)} $ud",
            tolRel = 0.02, explanation = expl, hint = "size in $ud")
    }

    fun arcDistance(s: Double, us: String, theta: Double): Problem {
        val ans = s / (2 * Math.tan(Math.toRadians(theta) / 2))
        val expl = "1° ↔ size/distance = 1/57.3, so distance ≈ 57.3 × size / θ°<br>" +
            "= 57.3 × ${fmtNum(s)} / ${fmtNum(theta)} ≈ <b>${fmtNum(57.3 * s / theta, sig = 3)} $us</b>"
        return Problem("${q(s, us)} spans ${fmtNum(theta)}°: distance?", ans,
            "≈ ${fmtNum(ans, sig = 3)} $us", tolRel = 0.02, explanation = expl,
            hint = "distance in $us")
    }

    private fun twoSig(rng: Rng, loExp: Int, hiExp: Int): Double {
        val m = rng.randint(11, 99) / 10.0
        return sigRound(m * Math.pow(10.0, rng.randint(loExp, hiExp).toDouble()), 2)
    }

    private val ARC_KINDS = listOf("angle", "angle", "size", "distance")
    private val ARC_UNITS = listOf("m" to "m", "m" to "km", "cm" to "m", "km" to "km")

    private fun smallL2(rng: Rng): Problem {
        val kind = rng.choice(ARC_KINDS)
        if (kind == "angle") {
            val (us, ud) = rng.choice(ARC_UNITS)
            val s = twoSig(rng, 0, 1)
            val r = Math.pow(10.0, rng.uniform(Math.log10(0.002), Math.log10(0.07)))
            val dist = sigRound(s * UNITS.getValue(us) / r / UNITS.getValue(ud), 2)
            return arcAngle(s, us, dist, ud)
        }
        val theta = twoSig(rng, -1, 0)
        if (kind == "size") {
            val ud = rng.choice(listOf("m", "km"))
            return arcSize(theta, twoSig(rng, 1, 3), ud)
        }
        return arcDistance(twoSig(rng, 0, 1), "m", theta)
    }

    // ------------------------------------------------------------ inverse trig

    // angle θ -> (sin θ as shown symbolically or null, decimal shown)
    private val SIN_SHOWN = mapOf(15 to (null to "0.259"), 30 to ("1/2" to "0.5"), 45 to ("√2/2" to "0.707"),
        60 to ("√3/2" to "0.866"), 75 to (null to "0.966"), 90 to ("1" to "1"))
    private val TAN_SHOWN = mapOf(15 to ("2 − √3" to "0.268"), 30 to ("1/√3" to "0.577"), 45 to ("1" to "1"),
        60 to ("√3" to "1.732"), 75 to ("2 + √3" to "3.732"))

    /** fn(±value) where value is sin θ (arcsin, arccos) or tan θ (arctan). */
    fun exactInverse(fn: String, theta: Int, negative: Boolean, symbolic: Boolean): Problem {
        val table: Map<Int, Pair<String?, String>> = if (fn == "arctan") TAN_SHOWN else SIN_SHOWN
        val (sym, dec) = table.getValue(theta)
        val shown = if (symbolic && sym != null) sym else dec
        val base = if (fn == "arctan") Math.tan(Math.toRadians(theta.toDouble()))
        else Math.sin(Math.toRadians(theta.toDouble()))
        val mag = if (shown == sym) base else dec.toDouble()
        val x = if (negative) -mag else mag
        val ans = Math.toDegrees(inv(fn, x))
        val shownSigned = signed(shown, negative)
        val prompt = if (negative || " " in shown || "/" in shown) "$fn($shownSigned)" else "$fn $shown"
        val fact: String
        val exact: Int
        val tail: String
        if (fn == "arccos") {
            val phi = 90 - theta
            fact = "cos $phi° " + (if (sym != null) "= $sym" + (if (sym != dec) " ≈ $dec" else "") else "≈ $dec")
            exact = if (negative) 180 - phi else phi
            tail = if (negative) "arccos(−x) = 180° − arccos x = 180° − $phi° = <b>$exact°</b>"
            else "→ <b>$exact°</b>"
        } else {
            val f = if (fn == "arctan") "tan" else "sin"
            fact = "$f $theta° " + (if (sym != null) "= $sym" + (if (sym != dec) " ≈ $dec" else "") else "≈ $dec")
            exact = if (negative) -theta else theta
            tail = if (negative) "$fn is odd: $fn(−x) = −$fn x → <b>${neg(exact)}°</b>"
            else "→ <b>$exact°</b>"
        }
        val display = if (isClose(ans, exact.toDouble(), absTol = 1e-9)) "${neg(exact)}°" else "≈ ${neg(exact)}°"
        return Problem(prompt, ans, display, tolAbs = 1.0, explanation = "$fact<br>$tail",
            hint = "degrees")
    }

    private val INV_FNS = listOf("arcsin", "arccos", "arctan")
    private val INV_THETAS = listOf(15, 30, 30, 30, 45, 45, 45, 60, 60, 60, 75, 90)

    private fun invL1(rng: Rng): Problem {
        while (true) {
            val fn = rng.choice(INV_FNS)
            val theta = rng.choice(INV_THETAS)
            if (fn == "arctan" && theta == 90) continue
            if (fn == "arccos" && theta == 90 && rng.random() < 0.7) continue  // arccos 1 = 0° is a bit trivial
            val negative = rng.random() < 0.4
            val symbolic = rng.random() < 0.5
            return exactInverse(fn, theta, negative, symbolic)
        }
    }

    private class InvAnchor(val x: Double, val deg: Double, val label: String)

    private val SIN_ANCHORS = listOf(InvAnchor(0.0, 0.0, "small angle: arcsin x ≈ x rad"),
        InvAnchor(Math.sin(Math.toRadians(15.0)), 15.0, "sin 15° ≈ 0.259"),
        InvAnchor(0.5, 30.0, "sin 30° = 0.5"), InvAnchor(0.6, 36.87, "sin 36.87° = 0.6 (3-4-5)"),
        InvAnchor(Math.sqrt(0.5), 45.0, "sin 45° ≈ 0.707"), InvAnchor(0.8, 53.13, "sin 53.13° = 0.8 (3-4-5)"),
        InvAnchor(Math.sqrt(3.0) / 2, 60.0, "sin 60° ≈ 0.866"),
        InvAnchor(Math.sin(Math.toRadians(75.0)), 75.0, "sin 75° ≈ 0.966"))
    private val TAN_ANCHORS = listOf(InvAnchor(0.0, 0.0, "small angle: arctan x ≈ x rad"),
        InvAnchor(2 - Math.sqrt(3.0), 15.0, "tan 15° ≈ 0.268"), InvAnchor(0.5, 26.57, "tan 26.57° = 0.5"),
        InvAnchor(1 / Math.sqrt(3.0), 30.0, "tan 30° ≈ 0.577"), InvAnchor(0.75, 36.87, "tan 36.87° = 0.75 (3-4-5)"),
        InvAnchor(1.0, 45.0, "tan 45° = 1"), InvAnchor(4.0 / 3, 53.13, "tan 53.13° = 4/3 (3-4-5)"),
        InvAnchor(Math.sqrt(3.0), 60.0, "tan 60° ≈ 1.732"), InvAnchor(2.0, 63.43, "tan 63.43° = 2"),
        InvAnchor(2 + Math.sqrt(3.0), 75.0, "tan 75° ≈ 3.732"))

    /** arcsin/arctan of x ≥ 0 by nearest anchor + inverse derivative. Returns (deg, text). */
    private fun invLinear(f: String, x: Double): Pair<Double, String> {
        val anchors = if (f == "arctan") TAN_ANCHORS else SIN_ANCHORS
        val anc = anchors.minBy { Math.abs(x - it.x) }
        val a = anc.x
        val adeg = anc.deg
        val xs = d(x, 2)
        if (a == 0.0) {
            val est = x * 57.3
            return est to "${anc.label}: $f $xs ≈ $xs × 57.3° ≈ ${d(est, 1)}°"
        }
        val slope: Double
        val stext: String
        if (f == "arctan") {
            slope = DEG_PER_RAD / (1 + a * a)
            stext = "slope 1/(1 + ${d(a)}²) rad ≈ ${d(slope, 1)}° per unit"
        } else {
            slope = DEG_PER_RAD / Math.sqrt(1 - a * a)
            stext = "slope 1/cos ${d(adeg, 2)}° rad ≈ ${d(slope, 1)}° per unit"
        }
        val dd = x - a
        val est = adeg + slope * dd
        val text = "Anchor ${anc.label}; $stext<br>$f $xs ≈ ${d(adeg, 2)}° ${pm(dd)} ${d(Math.abs(dd))} × " +
            "${d(slope, 1)}° ≈ ${d(est, 1)}°"
        return est to text
    }

    /** fn(x) in degrees with the anchor method; returns the problem and the mental estimate. */
    fun inverseEstimate(fn: String, x: Double): Pair<Problem, Double> {
        val ans = Math.toDegrees(inv(fn, x))
        val xs = d(x, 2)
        val ax = d(Math.abs(x), 2)
        val prompt = if (x < 0) "$fn($xs)" else "$fn $xs"
        val f = if (fn == "arctan") "arctan" else "arcsin"
        val (core, text) = invLinear(f, Math.abs(x))
        val est: Double
        val expl: String
        if (fn == "arccos") {
            val pre: String
            val tail: String
            if (x < 0) {
                pre = "arccos($xs) = 90° + arcsin $ax"
                est = 90 + core
                tail = " → 90° + ${d(core, 1)}° ≈ <b>${d(est, 1)}°</b>"
            } else {
                pre = "arccos $xs = 90° − arcsin $xs"
                est = 90 - core
                tail = " → 90° − ${d(core, 1)}° ≈ <b>${d(est, 1)}°</b>"
            }
            expl = "$pre<br>$text$tail"
        } else if (x < 0) {
            est = -core
            expl = "$fn is odd: $fn($xs) = −$fn $ax<br>$text → <b>${d(est, 1)}°</b>"
        } else {
            est = core
            val i = text.lastIndexOf("≈ ")
            val head = if (i >= 0) text.substring(0, i) else ""
            val last = if (i >= 0) text.substring(i + 2) else text
            expl = "$head≈ <b>$last</b>"
        }
        return Problem(prompt, ans, "≈ ${d(ans, 1)}°", tolAbs = 2.0, explanation = expl,
            hint = "degrees") to est
    }

    private val INV_EXACT = setOf(0.5, 0.6, 0.8, 0.75, 1.0, 2.0)

    private fun invL2(rng: Rng): Problem {
        while (true) {
            val fn = rng.choice(INV_FNS)
            var x = if (fn == "arctan") {
                if (rng.random() < 0.6) rng.randint(10, 99) / 100.0 else rng.randint(11, 40) / 10.0
            } else {
                rng.randint(5, 97) / 100.0
            }
            if (x in INV_EXACT) continue
            if (rng.random() < 0.2) x = -x
            val (p, est) = inverseEstimate(fn, x)
            if (Math.abs(est - p.answer) <= 1.4) return p  // keep only cases the anchor method handles well
        }
    }

    // ------------------------------------------------------------ lessons

    private val DEGRAD_LESSON = Lesson(
        "Degrees ↔ radians",
        "\n" +
        "<p><b>π rad = 180°</b>, so 1 rad ≈ <b>57.3°</b> and 1° ≈ <b>0.01745 rad</b>.</p>\n" +
        "<ul>\n" +
        "<li><b>Nice angles:</b> reduce the fraction of 180°. 135° = 135/180 · π = 3π/4 ≈ 3 × 0.785 =\n" +
        "2.356. Know π/12 ≈ 0.262, π/6 ≈ 0.524, π/4 ≈ 0.785, π/3 ≈ 1.047, π/2 ≈ 1.571.<br>\n" +
        "Back the other way, π/n = 180°/n: 5π/6 = 5 × 30° = 150°.</li>\n" +
        "<li><b>Any angle → rad:</b> start from the nearest nice angle and add 0.01745 per extra degree.\n" +
        "37° = 30° + 7° ≈ 0.524 + 0.122 = <b>0.646</b>. Or ÷ 57.3, or × 0.0175 (= 7/400) and shave 0.3%.</li>\n" +
        "<li><b>rad → °:</b> × 57.3, split into whole and fractional parts:\n" +
        "2.3 rad = 2 × 57.3 + 0.3 × 57.3 = 114.6 + 17.2 ≈ <b>131.8°</b>.</li>\n" +
        "</ul>\n" +
        "<p>Sanity anchors: 1 rad is a bit under 60°; 3 rad is just under 180°; 6.28 rad is a full turn.</p>\n",
    )

    private val TRIG_LESSON = Lesson(
        "Trig values",
        "\n" +
        "<p><b>1. Unit circle symmetry.</b> Any angle reduces to a <i>reference angle</i> in 0–90°; only the\n" +
        "sign changes. Signs by quadrant: <b>ASTC</b> — <i>All</i> positive in I, <i>Sin</i> in II,\n" +
        "<i>Tan</i> in III, <i>Cos</i> in IV. Negative angles: add 360° (cos is even, sin and tan are odd).<br>\n" +
        "225° = 180° + 45° (III) → sin 225° = −√2/2 ≈ −0.707; cos(−30°) = cos 30° ≈ 0.866.</p>\n" +
        "<p><b>2. In-between angles: anchor + slope.</b> Go from the nearest memorized angle (0, 15, 30, 45,\n" +
        "60, 75, 90°) using the derivative, with the step in radians (1° = 0.01745):</p>\n" +
        "<ul><li>sin(a + d) ≈ sin a + cos a · d</li><li>cos(a + d) ≈ cos a − sin a · d</li>\n" +
        "<li>tan(a + d) ≈ tan a + (1 + tan² a) · d</li></ul>\n" +
        "<p>sin 37°: 7° ≈ 0.122 rad → 0.5 + 0.866 × 0.122 ≈ <b>0.606</b> (true 0.602; the curve bends\n" +
        "down, so a linear step slightly overshoots).</p>\n" +
        "<p><b>3. Radian arguments.</b> Small x: Taylor — sin x ≈ x − x³/6, cos x ≈ 1 − x²/2,\n" +
        "tan x ≈ x + x³/3 (sin 0.4 ≈ 0.4 − 0.011 = 0.389). Larger x: use radian anchors π/6 ≈ 0.524,\n" +
        "π/4 ≈ 0.785, π/3 ≈ 1.047, 5π/12 ≈ 1.309 with the same slope rule — no degree conversion needed:\n" +
        "cos 1.2 ≈ 0.5 − 0.866 × 0.153 ≈ 0.368. Past π/2, fold: sin x = sin(π − x), cos x = −cos(π − x).</p>\n",
    )

    private val SMALL_LESSON = Lesson(
        "Small-angle approximations",
        "\n" +
        "<p>For a small angle θ <b>in radians</b>:</p>\n" +
        "<ul><li><b>sin θ ≈ tan θ ≈ θ</b> (relative errors θ²/6 and θ²/3)</li>\n" +
        "<li><b>cos θ ≈ 1 − θ²/2</b>, i.e. 1 − cos θ ≈ θ²/2</li></ul>\n" +
        "<p>Even at 10° (θ ≈ 0.175) sin θ ≈ θ is off by only 0.5% and tan θ ≈ θ by 1%; refine with\n" +
        "θ(1 − θ²/6) or θ(1 + θ²/3). Degrees first: θ = x° × 0.01745 (or x ÷ 57.3).</p>\n" +
        "<p><b>Example:</b> sin 2.5° ≈ 2.5 × 0.01745 ≈ <b>0.0436</b>.\n" +
        "1 − cos 4°: θ ≈ 0.0698, θ² ≈ 0.00487 → <b>0.00244</b>.</p>\n" +
        "<p><b>Arc length:</b> an object of size s at distance d (s ≪ d) subtends θ ≈ s/d radians\n" +
        "= 57.3 · s/d degrees. Equivalently, something 1 unit wide at 57.3 units away spans 1°.</p>\n" +
        "<ul><li>1.8 m person at 2.5 km: 1.8/2 500 = 0.00072 rad × 57.3 ≈ 0.041°</li>\n" +
        "<li>The Moon: 3 474 km / 384 400 km ≈ 0.0090 rad ≈ 0.52°; a thumb at arm's length ≈ 2°.</li>\n" +
        "<li>Reverse: size ≈ θ<sub>rad</sub> · d; distance ≈ 57.3 · s / θ°. Watch the units (m vs km)!</li></ul>\n",
    )

    private val INV_LESSON = Lesson(
        "Inverse trig",
        "\n" +
        "<p><b>Exact values</b> are your special-angle table read backwards; give the answer in the\n" +
        "principal range: arcsin and arctan in −90°…90° (both <i>odd</i>: arcsin(−x) = −arcsin x),\n" +
        "arccos in 0°…180° with <b>arccos(−x) = 180° − arccos x</b>.<br>\n" +
        "arccos(−0.866) = 180° − 30° = 150°; arctan √3 = 60°; arcsin(−1/2) = −30°.</p>\n" +
        "<p><b>Extra anchors worth memorizing</b> (3-4-5 triangle and friends):</p>\n" +
        "<ul><li>arcsin 0.6 = arctan 0.75 = <b>36.87°</b>; arcsin 0.8 = arctan 4/3 = <b>53.13°</b></li>\n" +
        "<li>arctan 0.5 = <b>26.57°</b>; arctan 2 = <b>63.43°</b></li></ul>\n" +
        "<p><b>In between: anchor + inverse slope.</b> d(arcsin x)/dx = 1/cos θ and\n" +
        "d(arctan x)/dx = 1/(1 + x²), in radians — multiply by 57.3 for degrees.<br>\n" +
        "arctan 0.3: anchor tan 15° = 0.268, slope 1/(1 + 0.268²) ≈ 0.933 rad ≈ 53.5°/unit →\n" +
        "15° + 0.032 × 53.5° ≈ <b>16.7°</b>.<br>\n" +
        "arcsin 0.55: 30° + 0.05 × (57.3/0.866) ≈ 30° + 3.3° = <b>33.3°</b>.</p>\n" +
        "<p><b>Small x:</b> arcsin x ≈ arctan x ≈ x rad = 57.3·x degrees (arctan 0.15 ≈ 8.6°).\n" +
        "<b>arccos x = 90° − arcsin x</b>: arccos 0.2 ≈ 90° − 11.5° = 78.5°.</p>\n",
    )

    // ------------------------------------------------------------ skills

    override val SKILLS: List<Skill> = listOf(
        Skill("s_degrad", "Degrees ↔ radians", TRACK, DEGRAD_LESSON, listOf(
            Level("Multiples of 15° and fractions of π", 5.0, ::degradL1),
            Level("Any angle, both directions", 7.0, ::degradL2),
        )),
        Skill("s_trig", "Trig values", TRACK, TRIG_LESSON, listOf(
            Level("Special angles in all quadrants", 5.0, ::trigL1),
            Level("Any angle 0–90° (±0.02)", 9.0, ::trigL2),
            Level("Radian arguments (±0.02)", 10.0, ::trigL3),
        )),
        Skill("s_small", "Small-angle approximations", TRACK, SMALL_LESSON, listOf(
            Level("sin, tan, 1 − cos of small angles", 6.0, ::smallL1),
            Level("Angular size: size, distance, angle", 8.0, ::smallL2),
        )),
        Skill("s_invtrig", "Inverse trig", TRACK, INV_LESSON, listOf(
            Level("Exact values (±1°)", 5.0, ::invL1),
            Level("Any value (±2°)", 10.0, ::invL2),
        )),
    )
}
