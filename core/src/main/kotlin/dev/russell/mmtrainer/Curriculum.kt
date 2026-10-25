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
