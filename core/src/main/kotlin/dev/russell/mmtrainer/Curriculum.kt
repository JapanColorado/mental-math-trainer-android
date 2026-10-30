package dev.russell.mmtrainer

/*
 * Introduction order of skills and decks, and the fixed benchmark battery.
 *
 * The order interleaves tracks on purpose: memorized anchor facts arrive just before the
 * skills that build on them, and the upper-level tracks start early instead of only after
 * every arithmetic skill has been mastered.
 */
object Curriculum {
    val ORDER = listOf(
        "d_times",
        "s_add",
        "s_mul1",
        "d_squares",
        "s_sub",
        "d_pow2",
        "s_square",
        "d_recip",
        "s_frac",
        "s_pct",
        "d_log10",
        "s_log10",
        "s_mul2",
        "d_consts",
        "s_logint",
        "s_sci",
        "d_trig",
        "s_div",
        "s_antilog",
        "d_ln",
        "s_ln",
        "d_cubes",
        "s_root",
        "d_exp",
        "s_exp",
        "s_degrad",
        "s_trig",
        "s_fermi",
        "d_teens",
        "s_pow",
        "d_powers",
        "s_logb",
        "s_small",
        "s_recip",
        "s_growth",
        "s_invtrig",
    )

    // Direct prerequisites: a topic is only introduced by a quick session once each of these is
    // solid (see Store.prerequisiteMet). Every prerequisite comes earlier in ORDER.
    val PREREQUISITES: Map<String, List<String>> = mapOf(
        "s_mul1" to listOf("d_times", "s_add"),
        "d_squares" to listOf("d_times"),
        "s_sub" to listOf("s_add"),
        "d_pow2" to listOf("d_times"),
        "s_square" to listOf("d_squares", "s_mul1"),
        "d_recip" to listOf("d_times"),
        "s_frac" to listOf("d_recip"),
        "s_pct" to listOf("s_frac", "s_mul1"),
        "d_log10" to listOf("d_pow2"),
        "s_log10" to listOf("d_log10"),
        "s_mul2" to listOf("s_mul1", "d_squares"),
        "d_consts" to listOf("d_squares"),
        "s_logint" to listOf("d_pow2"),
        "s_sci" to listOf("s_mul1"),
        "d_trig" to listOf("d_consts"),
        "s_div" to listOf("s_mul1", "s_sub"),
        "s_antilog" to listOf("s_log10"),
        "d_ln" to listOf("d_log10"),
        "s_ln" to listOf("d_ln", "s_log10"),
        "d_cubes" to listOf("d_squares"),
        "s_root" to listOf("d_squares", "d_cubes", "s_log10"),
        "d_exp" to listOf("d_ln"),
        "s_exp" to listOf("d_exp"),
        "s_degrad" to listOf("d_consts"),
        "s_trig" to listOf("d_trig", "s_degrad"),
        "s_fermi" to listOf("s_sci", "s_mul2"),
        "d_teens" to listOf("d_times"),
        "s_pow" to listOf("s_square", "d_pow2", "s_antilog"),
        "d_powers" to listOf("d_pow2", "d_cubes"),
        "s_logb" to listOf("s_log10", "s_logint"),
        "s_small" to listOf("s_degrad"),
        "s_recip" to listOf("d_recip", "s_div"),
        "s_growth" to listOf("s_ln", "s_exp"),
        "s_invtrig" to listOf("s_trig"),
    )

    // (skill id, level index) — asked BENCHMARK_ROUNDS times each, at fixed difficulty, so scores
    // are comparable over months regardless of what has been unlocked. Several questions per skill
    // keep one lucky (or awkward) set of numbers from swinging the score.
    const val BENCHMARK_ROUNDS = 2
    val BENCHMARK = listOf(
        "s_add" to 1,
        "s_sub" to 2,
        "s_mul1" to 1,
        "s_mul2" to 1,
        "s_square" to 2,
        "s_div" to 2,
        "s_frac" to 1,
        "s_pct" to 1,
        "s_log10" to 2,
        "s_antilog" to 1,
        "s_ln" to 1,
        "s_exp" to 2,
        "s_root" to 0,
        "s_sci" to 1,
        "s_fermi" to 1,
        "s_trig" to 1,
    )
}
