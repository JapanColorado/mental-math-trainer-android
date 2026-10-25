package dev.russell.mmtrainer.py

/** The subset of Python's fractions.Fraction the content uses. Always normalized, den > 0. */
class Fraction private constructor(val num: Long, val den: Long) : Comparable<Fraction> {

    operator fun plus(o: Fraction) = of(Math.addExact(Math.multiplyExact(num, o.den), Math.multiplyExact(o.num, den)),
        Math.multiplyExact(den, o.den))
    operator fun minus(o: Fraction) = this + (-o)
    operator fun times(o: Fraction) = of(Math.multiplyExact(num, o.num), Math.multiplyExact(den, o.den))
    operator fun div(o: Fraction) = of(Math.multiplyExact(num, o.den), Math.multiplyExact(den, o.num))
    operator fun unaryMinus() = Fraction(-num, den)
    operator fun plus(o: Long) = this + of(o)
    operator fun minus(o: Long) = this - of(o)
    operator fun times(o: Long) = this * of(o)
    operator fun div(o: Long) = this / of(o)

    /** Fraction ** int (negative exponents invert). */
    fun pow(n: Int): Fraction =
        if (n >= 0) of(ipow(num, n), ipow(den, n)) else of(ipow(den, -n), ipow(num, -n))

    /** float(Fraction): correctly rounded, like Python's int true division. */
    fun toDouble(): Double =
        if (Math.abs(num) < EXACT && den < EXACT) num.toDouble() / den.toDouble()
        else java.math.BigDecimal(num).divide(java.math.BigDecimal(den), java.math.MathContext(40)).toDouble()

    val isInteger get() = den == 1L

    override fun compareTo(other: Fraction): Int =
        (Math.multiplyExact(num, other.den)).compareTo(Math.multiplyExact(other.num, den))

    override fun equals(other: Any?) = other is Fraction && other.num == num && other.den == den
    override fun hashCode() = 31 * num.hashCode() + den.hashCode()

    /** str(Fraction): "3/4", or "3" for whole numbers. */
    override fun toString() = if (den == 1L) "$num" else "$num/$den"

    companion object {
        private const val EXACT = 1L shl 53

        fun of(n: Long, d: Long = 1L): Fraction {
            require(d != 0L) { "Fraction($n, 0)" }
            val g = gcd(n, d).coerceAtLeast(1)
            val s = if (d < 0) -1 else 1
            return Fraction(s * n / g, s * d / g)
        }

        fun of(n: Int, d: Int = 1): Fraction = of(n.toLong(), d.toLong())
    }
}
