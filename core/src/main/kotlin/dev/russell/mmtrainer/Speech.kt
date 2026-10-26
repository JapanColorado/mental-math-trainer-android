package dev.russell.mmtrainer

/**
 * Rich-text prompts and answers as plain English for text-to-speech (audio practice; Android only).
 *
 * "(8 × 10<sup>−11</sup>) ÷ √(2π)" -> "8 times 10 to the minus 11, divided by the square root of 2 pi".
 * Numbers stay as digits (thin-space grouping removed) so the TTS engine reads them naturally.
 */
object Speech {
    private val ORDINALS = mapOf("4" to "fourth", "5" to "fifth", "6" to "sixth", "7" to "seventh",
        "8" to "eighth", "9" to "ninth", "10" to "tenth")
    private val FUNCTIONS = mapOf("arcsin" to "arc sine", "arccos" to "arc cosine", "arctan" to "arc tangent",
        "sin" to "sine", "cos" to "cosine", "tan" to "tangent", "ln" to "natural log")
    private val UNITS = mapOf("km" to "kilometers", "cm" to "centimeters", "m" to "meters")

    private val ROOT_INDEX = Regex("""<sup>(\d+)</sup>√""")
    private val LOG_BASE = Regex("""log<sub>(.+?)</sub>\s*""")
    private val SUP = Regex("""<sup>(.+?)</sup>""")
    private val TAG = Regex("""<[^>]+>""")
    private val FUNCTION = Regex("""\b(arcsin|arccos|arctan|sin|cos|tan|ln)\b\s*""")
    private val UNIT = Regex("""(\d) (km|cm|m)\b""")
    private val DOLLARS = Regex("""\$([\d.]+)""")
    private val CLOSE = Regex("""\)\s*(?=\S)""")

    fun speakable(rich: String): String {
        var s = rich.replace(Regex("""(\d)[\u2009\u202f\u00a0](?=\d{3}\b)"""), "$1")  // "7 000 000" -> "7000000"
        s = s.replace("φ (golden ratio)", "phi, the golden ratio")
        s = ROOT_INDEX.replace(s) { m -> " the ${ORDINALS[m.groupValues[1]] ?: m.groupValues[1] + "th"} root of " }
        s = LOG_BASE.replace(s) { m -> " log base ${m.groupValues[1]} of " }
        s = SUP.replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "2" -> " squared"
                "3" -> " cubed"
                else -> if ("/" in e) " to the power $e," else " to the $e"
            }
        }
        s = s.replace("²", " squared").replace("³", " cubed")
        s = s.replace("<br>", ". ")
        s = TAG.replace(s, "")
        s = s.replace("√", " the square root of ").replace("∛", " the cube root of ")
        s = FUNCTION.replace(s) { m -> " ${FUNCTIONS.getValue(m.groupValues[1])} of " }
        s = s.replace("→ °", " in degrees").replace("→ rad", " in radians").replace("→", " to ")
        s = Regex("""\b1°""").replace(s, "1 degree").replace("°", " degrees")
        s = Regex("""\b1 rad\b""").replace(s, "1 radian").replace(Regex("""\brad\b"""), "radians")
        s = s.replace("%/yr", " percent per year").replace("%", " percent")
        s = DOLLARS.replace(s) { m -> m.groupValues[1] + " dollars" }
        s = UNIT.replace(s) { m -> m.groupValues[1] + " " + UNITS.getValue(m.groupValues[2]) }
        s = s.replace(" ≈ ", ", about ").replace("≈", " about ").replace("=", " equals ").replace("…", "")
        s = s.replace("−(", " minus the quantity ")
        s = s.replace("×", " times ").replace("÷", " divided by ").replace("/", " over ")
        s = s.replace("−", " minus ").replace(Regex("""-(?=\d)"""), " minus ").replace("+", " plus ")
        s = s.replace("π", " pi ").replace("φ", " phi ").replace(Regex("""(\d)e\b"""), "$1 e")
        s = CLOSE.replace(s, ", ").replace(")", "").replace("(", " ")
        s = s.replace(":", ",")
        s = s.replace(Regex("""\s+"""), " ").replace(Regex(""" ([,.?])"""), "$1")
        s = s.replace(Regex(""",([,.?])"""), "$1")
        return s.trim().trimEnd(',')
    }
}
