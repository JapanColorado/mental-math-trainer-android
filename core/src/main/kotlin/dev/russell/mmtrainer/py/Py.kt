package dev.russell.mmtrainer.py

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/*
 * Python semantics the content code relies on, reproduced exactly: float formatting
 * (f"{x:.3f}", f"{x:g}", f"{x:,}", str(x)), round() (half-even on the exact binary value),
 * floor division / modulo, and statistics.median. Every user-facing number in the content
 * modules is formatted through these, so output never depends on the device locale.
 */

private fun isNegative(x: Double) = x < 0 || (x == 0.0 && 1.0 / x < 0)

private fun nonFinite(x: Double): String? = when {
    x.isNaN() -> "nan"
    x == Double.POSITIVE_INFINITY -> "inf"
    x == Double.NEGATIVE_INFINITY -> "-inf"
    else -> null
}

/** Insert ',' every three digits in the integer part of a plain decimal string. */
private fun group(s: String): String {
    val neg = s.startsWith("-")
    val body = if (neg) s.substring(1) else s
    val dot = body.indexOf('.')
    val intPart = if (dot >= 0) body.substring(0, dot) else body
    val rest = if (dot >= 0) body.substring(dot) else ""
    val sb = StringBuilder()
    for ((i, c) in intPart.withIndex()) {
        if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return (if (neg) "-" else "") + sb + rest
}

/** f"{x:.{decimals}f}" (or f"{x:,.{decimals}f}" with [grouping]). */
fun fmtF(x: Double, decimals: Int, grouping: Boolean = false): String {
    nonFinite(x)?.let { return it }
    var s = BigDecimal(x).setScale(decimals, RoundingMode.HALF_EVEN).toPlainString()
    if (isNegative(x) && !s.startsWith("-")) s = "-$s"
    return if (grouping) group(s) else s
}

/** f"{n:,}" for an integer. */
fun fmtInt(n: Long, grouping: Boolean = true): String = if (grouping) group(n.toString()) else n.toString()

fun fmtInt(n: Int, grouping: Boolean = true): String = fmtInt(n.toLong(), grouping)

/** f"{x:.{precision}g}" — Python's general format (also f"{x:g}" with precision 6). */
fun fmtG(x: Double, precision: Int = 6): String {
    nonFinite(x)?.let { return it }
    val p = if (precision == 0) 1 else precision
    if (x == 0.0) return if (isNegative(x)) "-0" else "0"
    val rounded = BigDecimal(x).round(MathContext(p, RoundingMode.HALF_EVEN))
    val exp = rounded.precision() - rounded.scale() - 1
    val sign = if (x < 0) "-" else ""
    val abs = rounded.abs()
    return if (exp >= -4 && exp < p) {
        val s = abs.setScale(maxOf(0, p - 1 - exp), RoundingMode.HALF_EVEN).toPlainString()
        sign + stripZeros(s)
    } else {
        val digits = abs.unscaledValue().toString().padEnd(p, '0').substring(0, p)
        val mant = stripZeros(if (p > 1) digits[0] + "." + digits.substring(1) else digits)
        sign + mant + "e" + (if (exp < 0) "-" else "+") + Math.abs(exp).toString().padStart(2, '0')
    }
}

private fun stripZeros(s: String): String =
    if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s

/**
 * f"{x:.{precision}e}" — scientific notation with a fixed number of mantissa decimals.
 */
fun fmtE(x: Double, precision: Int = 6): String {
    nonFinite(x)?.let { return it }
    if (x == 0.0) return (if (isNegative(x)) "-" else "") + "0" + (if (precision > 0) "." + "0".repeat(precision) else "") + "e+00"
    val rounded = BigDecimal(x).round(MathContext(precision + 1, RoundingMode.HALF_EVEN))
    val exp = rounded.precision() - rounded.scale() - 1
    val digits = rounded.abs().unscaledValue().toString().padEnd(precision + 1, '0').substring(0, precision + 1)
    val mant = if (precision > 0) digits[0] + "." + digits.substring(1) else digits
    return (if (x < 0) "-" else "") + mant + "e" + (if (exp < 0) "-" else "+") + Math.abs(exp).toString().padStart(2, '0')
}

/** str(x) / repr(x) for a Python float: shortest round-trip digits, e.g. 0.1, 3.0, 1e-05, 1e+16. */
fun pyStr(x: Double): String {
    nonFinite(x)?.let { return it }
    if (x == 0.0) return if (isNegative(x)) "-0.0" else "0.0"
    val exact = BigDecimal(x)
    var shortest = exact
    for (p in 1..17) {
        val r = exact.round(MathContext(p, RoundingMode.HALF_EVEN))
        if (r.toDouble() == x) { shortest = r; break }
    }
    val exp = shortest.precision() - shortest.scale() - 1
    val sign = if (x < 0) "-" else ""
    val abs = shortest.abs()
    return if (exp >= -4 && exp < 16) {
        val s = abs.stripTrailingZeros().toPlainString()
        sign + if (s.contains('.')) s else "$s.0"
    } else {
        val digits = abs.unscaledValue().toString().trimEnd('0').ifEmpty { "0" }
        val mant = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        sign + mant + "e" + (if (exp < 0) "-" else "+") + Math.abs(exp).toString().padStart(2, '0')
    }
}

/** str(n) for a Python int. */
fun pyStr(n: Long): String = n.toString()

fun pyStr(n: Int): String = n.toString()

/** round(x, ndigits) for a float: half-even on the exact binary value, like CPython. */
fun pyRound(x: Double, ndigits: Int): Double {
    if (!x.isFinite() || x == 0.0) return x
    val r = BigDecimal(x).setScale(ndigits, RoundingMode.HALF_EVEN).toDouble()
    return if (r == 0.0 && x < 0) -0.0 else r
}

/** round(x) with no ndigits: nearest integer, ties to even. */
fun pyRound(x: Double): Long = Math.rint(x).toLong()

/** float.is_integer() */
fun Double.isInteger(): Boolean = this.isFinite() && this == Math.floor(this)

/** Python a // b and a % b (floor semantics) for ints. */
fun floorDiv(a: Long, b: Long): Long = Math.floorDiv(a, b)
fun floorMod(a: Long, b: Long): Long = Math.floorMod(a, b)
fun floorDiv(a: Int, b: Int): Int = Math.floorDiv(a, b)
fun floorMod(a: Int, b: Int): Int = Math.floorMod(a, b)

/** Python a // b and a % b for floats. */
fun floorDiv(a: Double, b: Double): Double = Math.floor(a / b)
fun floorMod(a: Double, b: Double): Double {
    val m = a % b
    return if (m != 0.0 && (m < 0) != (b < 0)) m + b else m
}

/** Integer power with overflow checking (Python ints never overflow; we'd rather crash than wrap). */
fun ipow(base: Long, exp: Int): Long {
    require(exp >= 0)
    var result = 1L
    repeat(exp) { result = Math.multiplyExact(result, base) }
    return result
}

fun ipow(base: Int, exp: Int): Long = ipow(base.toLong(), exp)

/** math.isqrt */
fun isqrt(n: Long): Long {
    require(n >= 0)
    var x = Math.sqrt(n.toDouble()).toLong()
    while (x * x > n) x--
    while ((x + 1) * (x + 1) <= n) x++
    return x
}

/** math.gcd (non-negative result). */
tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) Math.abs(a) else gcd(b, a % b)

fun gcd(a: Int, b: Int): Int = gcd(a.toLong(), b.toLong()).toInt()

/** statistics.median */
fun median(values: Collection<Double>): Double {
    require(values.isNotEmpty()) { "no median for empty data" }
    val s = values.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
}

/** math.isclose with the default rel_tol=1e-09, abs_tol=0.0 */
fun isClose(a: Double, b: Double, relTol: Double = 1e-9, absTol: Double = 0.0): Boolean {
    if (a == b) return true
    if (a.isInfinite() || b.isInfinite()) return false
    val diff = Math.abs(b - a)
    return diff <= Math.abs(relTol * b) || diff <= Math.abs(relTol * a) || diff <= absTol
}
