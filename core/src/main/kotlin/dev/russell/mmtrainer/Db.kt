package dev.russell.mmtrainer

/**
 * The minimal SQLite surface the Store needs, so the core stays platform-free: the app
 * backs it with android.database.sqlite, the JVM tests with sqlite-jdbc.
 *
 * Arguments may be null, Boolean, Int, Long, Double or String. Query rows map column name
 * to value, normalized to Long (INTEGER), Double (REAL), String (TEXT) or null.
 */
interface Db {
    /** Run one statement (or several, separated by ';', when [args] is empty). */
    fun exec(sql: String, args: List<Any?> = emptyList())

    /** Run an INSERT and return the new rowid. */
    fun insert(sql: String, args: List<Any?> = emptyList()): Long

    fun query(sql: String, args: List<Any?> = emptyList()): List<Row>

    /** Run [block] in one transaction (much faster for batches of writes). */
    fun <T> transaction(block: () -> T): T
}

class Row(private val values: Map<String, Any?>) {
    operator fun get(column: String): Any? {
        require(column in values) { "no column $column" }
        return values[column]
    }

    fun long(column: String): Long = (get(column) as Number).toLong()
    fun int(column: String): Int = long(column).toInt()
    fun double(column: String): Double = (get(column) as Number).toDouble()
    fun doubleOrNull(column: String): Double? = (get(column) as Number?)?.toDouble()
    fun string(column: String): String = get(column) as String
    fun stringOrNull(column: String): String? = get(column) as String?
    fun bool(column: String): Boolean = long(column) != 0L

    /** First column, for SELECT COUNT(*) and the like. */
    fun first(): Any? = values.values.first()

    override fun toString() = values.toString()
}

/** Split a script of ';'-separated statements (no ';' inside literals or comments). */
fun splitStatements(script: String): List<String> =
    script.split(';').map { it.trim() }.filter { it.isNotEmpty() }
