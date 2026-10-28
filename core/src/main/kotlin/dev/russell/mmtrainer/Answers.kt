package dev.russell.mmtrainer

import dev.russell.mmtrainer.py.pyStr
import java.math.BigDecimal

/** Parsing of typed answers into numbers. */
object Answers {
    private const val NUM = """(?:\d+\.?\d*|\.\d+)"""
    private val SCI = Regex("""^([+-]?$NUM)(?:(?:x|\*|×|·)10\^|e)([+-]?\d+)$""")
    private val POW10 = Regex("""^10\^([+-]?$NUM)$""")
    private val FRAC = Regex("""^([+-]?$NUM)/($NUM)$""")
    private val MIXED = Regex("""^(\d+)\s+(\d+)/(\d+)$""")
    private val FLOAT = Regex("""^[+-]?(?:\d+\.?\d*(?:e[+-]?\d+)?|\.\d+(?:e[+-]?\d+)?)$""")
    private val PLAIN = Regex("""^-?$NUM$""")

    /**
     * Parse a typed answer. Returns null if it isn't a recognisable number.
     *
     * Accepts: 1234, 1,234, 1 234, -0.5, .5, −3 (unicode minus), 3/8, 2 3/8,
     * 3.2e5, 3.2e-5, 3.2x10^5, 3.2*10^-5, 3.2×10^5, 10^3.5, 45%, 30°.
     */
    fun parse(text: String): Double? {
        var s = text.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.replace("−", "-").replace("–", "-").replace(" ", " ").replace(" ", " ")
        s = s.trimEnd('%', '°').trim()
        MIXED.matchEntire(s)?.let { m ->
            val (whole, num, den) = m.destructured
            val d = den.toLong()
            return if (d != 0L) whole.toLong() + num.toLong().toDouble() / d else null
        }
        s = s.replace(",", "").replace("_", "").replace(" ", "")
        if (FLOAT.matches(s)) {
            val v = s.toDoubleOrNull()
            if (v != null) return if (v.isFinite()) v else null
        }
        SCI.matchEntire(s)?.let { m ->
            return m.groupValues[1].toDouble() * Math.pow(10.0, m.groupValues[2].toDouble())
        }
        POW10.matchEntire(s)?.let { m -> return Math.pow(10.0, m.groupValues[1].toDouble()) }
        FRAC.matchEntire(s)?.let { m ->
            val den = m.groupValues[2].toDouble()
            return if (den != 0.0) m.groupValues[1].toDouble() / den else null
        }
        return null
    }

    /**
     * Whether [text] is a plain decimal (no fraction, exponent or %) as long as [answer] written out
     * in full, e.g. "−42" or "0.125": typed this far, it is finished and can be graded.
     */
    fun fullLength(text: String, answer: Double): Boolean {
        val s = text.trim().replace("−", "-").replace("–", "-").replace(",", "").replace(" ", "")
        if (!PLAIN.matches(s) || !answer.isFinite()) return false
        return s.length == BigDecimal(pyStr(answer)).stripTrailingZeros().toPlainString().length
    }
}
