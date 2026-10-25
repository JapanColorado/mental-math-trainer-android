package dev.russell.mmtrainer.content

/** One content module: a track's skills and/or memorization decks. */
interface ContentModule {
    val SKILLS: List<Skill> get() = emptyList()
    val DECKS: List<Deck> get() = emptyList()
}

/** Registry of every skill, deck and card. */
object Content {
    private val MODULES: List<ContentModule> = listOf(Facts, Arithmetic, Fractions, Logs, Powers, Estimation, Trig)

    val SKILLS: Map<String, Skill> = MODULES.flatMap { it.SKILLS }.associateByTo(LinkedHashMap()) { it.id }
    val DECKS: Map<String, Deck> = MODULES.flatMap { it.DECKS }.associateByTo(LinkedHashMap()) { it.id }
    val CARDS: Map<String, Pair<Deck, Card>> =
        DECKS.values.flatMap { d -> d.cards.map { c -> c.id to (d to c) } }.toMap(LinkedHashMap())

    fun get(itemId: String): Topic = SKILLS[itemId] ?: DECKS.getValue(itemId)

    fun nameOf(itemId: String): String = get(itemId).name

    fun isSkill(itemId: String) = itemId in SKILLS
    fun isDeck(itemId: String) = itemId in DECKS
    fun exists(itemId: String) = itemId in SKILLS || itemId in DECKS
}
