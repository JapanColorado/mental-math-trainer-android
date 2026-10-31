package dev.russell.mmtrainer.content

import dev.russell.mmtrainer.py.Rng
import dev.russell.mmtrainer.py.fmtF
import dev.russell.mmtrainer.py.fmtG
import dev.russell.mmtrainer.py.fmtInt
import dev.russell.mmtrainer.py.isInteger
import dev.russell.mmtrainer.py.pyRound

/*
 * Core content types shared by every skill / deck module.
 *
 * Content modules (Arithmetic.kt, Logs.kt, ...) are objects exposing:
 *
 *     val SKILLS: List<Skill>   // procedural skills, drilled with freshly generated problems
 *     val DECKS:  List<Deck>    // memorization decks, reviewed with spaced repetition
 *
 * All user-facing strings (prompts, explanations, lessons, notes) are rich text: a small HTML
 * subset (<b>, <i>, <sup>, <sub>, <br>, <p>, <ul>/<li>, <h3>, <table>) plus unicode symbols
 * (× ÷ − √ ∛ π ° ≈ ·), rendered by the app's RichText composable. No CSS or images.
 * Lessons also use <ex> (a worked example, one step per <br>-separated line) and <tip> (a short
 * "when to use it" note). LessonsTest checks every "a + b = c" written in a lesson.
 */

data class Problem(
    val prompt: String,                // the question, rich text, e.g. "log<sub>10</sub>(4 700)"
    val answer: Double,                // numeric value used to grade the user's answer
    val displayAnswer: String,         // how the correct answer is shown, e.g. "≈ 3.672"
    val tolAbs: Double = 0.0,          // accepted absolute error
    val tolRel: Double = 0.0,          // accepted relative error (0.02 == ±2 %)
    val explanation: String = "",      // worked method for THIS problem, 1-3 short lines of rich text
    val hint: String = "",             // answer-format hint shown under the input; auto-derived if empty
    val mentalEstimate: Double? = null, // value the worked explanation arrives at (logs/powers; tests check it)
) {
    fun tolerance(): Double =
        maxOf(tolAbs, tolRel * Math.abs(answer), 1e-9 * maxOf(1.0, Math.abs(answer)))

    fun isCorrect(value: Double): Boolean = Math.abs(value - answer) <= tolerance()

    fun formatHint(): String = when {
        hint.isNotEmpty() -> hint
        tolRel > 0 -> "within ±${fmtG(tolRel * 100)}%"
        tolAbs > 0 -> "within ±${fmtG(tolAbs)}"
        else -> "exact"
    }
}

class Level(
    val description: String,                   // e.g. "2-digit × 1-digit"
    val targetSeconds: Double,                 // fluent response time for this level
    val generate: (Rng) -> Problem,            // must be pure w.r.t. the rng passed in
)

data class Lesson(
    val title: String,
    val body: String,                          // rich text: the technique, why it works, 1-2 worked examples
)

/** A skill or a deck: anything that can be introduced, has a lesson and lives in a track. */
sealed interface Topic {
    val id: String
    val name: String
    val track: String
    val lesson: Lesson
}

class Skill(
    override val id: String,                   // stable id, e.g. "s_log10" (stored in the database!)
    override val name: String,                 // short display name, e.g. "Common logarithms"
    override val track: String,                // one of TRACKS
    override val lesson: Lesson,
    val levels: List<Level>,
) : Topic

data class Card(
    val id: String,                            // stable id, unique across ALL decks, e.g. "pow2:13"
    val prompt: String,                        // rich text question, e.g. "2<sup>13</sup>"
    val answer: Double,
    val displayAnswer: String,                 // e.g. "8192"
    val tolAbs: Double = 0.0,
    val tolRel: Double = 0.0,
    val note: String = "",                     // mnemonic / derivation shown when studying or after a miss
    val hint: String = "",
    val altPrompt: String? = null,             // same fact asked the other way round, e.g. "6 × 2" for "2 × 6"
) {
    fun asProblem(alt: Boolean = false) =
        Problem(if (alt && altPrompt != null) altPrompt else prompt, answer, displayAnswer, tolAbs, tolRel, note, hint)
}

class Deck(
    override val id: String,                   // stable id, e.g. "d_pow2"
    override val name: String,
    override val track: String,                // one of TRACKS
    override val lesson: Lesson,               // shown once when the deck is introduced
    val cards: List<Card>,
    val targetSeconds: Double = 3.0,
) : Topic

val TRACKS: Map<String, String> = linkedMapOf(
    "arithmetic" to "Arithmetic",
    "fractions" to "Fractions & Percents",
    "logs" to "Logs & Exponents",
    "estimation" to "Estimation",
    "trig" to "Trigonometry & Constants",
)

// ---------------------------------------------------------------- helpers

const val THIN = " "   // thousands separator in displayed numbers
const val MINUS = "−"  // typographic minus sign

/** Round x to [sig] significant figures. */
fun sigRound(x: Double, sig: Int = 3): Double {
    if (x == 0.0 || !x.isFinite()) return x
    return pyRound(x, sig - 1 - Math.floor(Math.log10(Math.abs(x))).toInt())
}

/**
 * Human-friendly number: thin-space thousands grouping, no trailing zeros.
 *
 * fmtNum(1234567.0) -> "1 234 567"; fmtNum(0.0004567, sig = 3) -> "0.000457"
 */
fun fmtNum(x: Double, sig: Int? = null, decimals: Int? = null): String {
    val v = if (sig != null) sigRound(x, sig) else x
    val s = when {
        decimals != null -> fmtF(v, decimals, grouping = true)
        v.isInteger() && Math.abs(v) < 1e15 -> fmtInt(v.toLong())
        else -> fmtF(v, 10, grouping = true).trimEnd('0').trimEnd('.')
    }
    return s.replace(",", THIN).replace("-", MINUS)
}

fun fmtNum(x: Int, sig: Int? = null, decimals: Int? = null) = fmtNum(x.toDouble(), sig, decimals)
fun fmtNum(x: Long, sig: Int? = null, decimals: Int? = null) = fmtNum(x.toDouble(), sig, decimals)

/** Scientific notation as rich text: "3.2 × 10<sup>5</sup>". */
fun fmtSci(x: Double, sig: Int = 3): String {
    if (x == 0.0) return "0"
    var exp = Math.floor(Math.log10(Math.abs(x))).toInt()
    var mant = sigRound(x / Math.pow(10.0, exp.toDouble()), sig)
    if (Math.abs(mant) >= 10) {  // rounding pushed us up a decade
        mant /= 10
        exp += 1
    }
    var m = if (sig > 1) fmtF(mant, sig - 1).trimEnd('0').trimEnd('.') else fmtF(mant, 0)
    m = m.replace("-", MINUS)
    if (exp == 0) return m
    return "$m × 10<sup>${exp.toString().replace("-", MINUS)}</sup>"
}
