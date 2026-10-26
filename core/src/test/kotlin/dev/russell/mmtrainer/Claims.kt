package dev.russell.mmtrainer

import org.junit.Assert.assertTrue

/*
 * Helpers shared by content tests: parse numbers out of rich text and verify the simple
 * arithmetic claims ("47 × 8 = 376", "3² = 9", "196/560 = 0.35") in explanations.
 */
object Claims {
    private const val NUM = """\d{1,3}(?: \d{3})*(?:\.\d+)?|\d+(?:\.\d+)?"""
    // a claim starts at the beginning of a line/clause, after "= " / "→ ", or after a word
    private const val START = """(?:^|(?<=: )|(?<=; )|(?<=, )|(?<=\n)|(?<==\s)|(?<=→ )|(?<=[a-z] ))"""
    private const val END = """(?![\d …]|\.\d|\s?[×+−÷/])"""
    private const val TERM = """ ?[+−] ?(?:$NUM)"""
    private val ADDITIVE = Regex("""$START($NUM)((?:$TERM)+) = ([−+]?(?:$NUM))$END""", RegexOption.MULTILINE)
    private val PRODUCT = Regex("""$START($NUM) ?([×÷/]) ?($NUM) = ([−+]?(?:$NUM))$END""", RegexOption.MULTILINE)
    private val SQUARE = Regex("""(?<![\d ])($NUM)² = ($NUM)$END""")
    private val TAG = Regex("<[^>]+>")

    /** Strip tags (<br> → newline). */
    fun plain(text: String): String = text.replace("<br>", "\n").replace(TAG, "")

    /** plain() with thin spaces turned into normal spaces, for substring assertions. */
    fun readable(text: String): String = plain(text).replace(" ", " ")

    fun toFloat(s: String): Double = s.replace(" ", "").replace("−", "-").toDouble()

    /** All unsigned numbers in a rich-text prompt (tags become separators). */
    fun numbers(prompt: String): List<Double> {
        val s = prompt.replace(TAG, " ").replace(" ", "")
        return Regex("""\d+(?:\.\d+)?""").findAll(s).map { it.value.toDouble() }.toList()
    }

    private fun close(got: Double, want: Double) = Math.abs(got - want) <= 1e-9 * maxOf(1.0, Math.abs(got))

    /** Assert every recognised 'a op b = c' / 'a² = c' claim holds; return how many were checked. */
    fun checkClaims(explanation: String): Int {
        val text = plain(explanation)
        var count = 0
        for (m in ADDITIVE.findAll(text)) {
            val (a, terms, c) = m.destructured
            var got = toFloat(a)
            for (t in Regex("""([+−]) ?($NUM)""").findAll(terms)) {
                val b = toFloat(t.groupValues[2])
                got = if (t.groupValues[1] == "+") got + b else got - b
            }
            assertTrue("bad claim $a$terms = $c in: $text", close(got, toFloat(c)))
            count++
        }
        for (m in PRODUCT.findAll(text)) {
            val (a, op, b, c) = m.destructured
            val x = toFloat(a)
            val y = toFloat(b)
            val got = if (op == "×") x * y else x / y
            assertTrue("bad claim $a $op $b = $c in: $text", close(got, toFloat(c)))
            count++
        }
        for (m in SQUARE.findAll(text)) {
            val (a, c) = m.destructured
            assertTrue("bad claim $a² = $c in: $text", toFloat(a) * toFloat(a) == toFloat(c))
            count++
        }
        return count
    }
}
