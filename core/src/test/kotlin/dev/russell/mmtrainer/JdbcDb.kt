package dev.russell.mmtrainer

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/** Db over sqlite-jdbc, for JVM tests. */
class JdbcDb(url: String = "jdbc:sqlite::memory:") : Db {
    private val conn: Connection = DriverManager.getConnection(url)

    private fun prepare(sql: String, args: List<Any?>): PreparedStatement {
        val st = conn.prepareStatement(sql)
        args.forEachIndexed { i, a ->
            when (a) {
                null -> st.setNull(i + 1, Types.NULL)
                is Boolean -> st.setLong(i + 1, if (a) 1 else 0)
                is Int -> st.setLong(i + 1, a.toLong())
                is Long -> st.setLong(i + 1, a)
                is Double -> st.setDouble(i + 1, a)
                is String -> st.setString(i + 1, a)
                else -> error("unsupported arg ${a::class}")
            }
        }
        return st
    }

    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) {
            conn.createStatement().use { st -> splitStatements(sql).forEach { st.executeUpdate(it) } }
        } else {
            prepare(sql, args).use { it.executeUpdate() }
        }
    }

    override fun insert(sql: String, args: List<Any?>): Long {
        prepare(sql, args).use { it.executeUpdate() }
        return conn.createStatement().use { st -> st.executeQuery("SELECT last_insert_rowid()").use { it.next(); it.getLong(1) } }
    }

    override fun query(sql: String, args: List<Any?>): List<Row> = prepare(sql, args).use { st ->
        st.executeQuery().use { rs ->
            val meta = rs.metaData
            val out = mutableListOf<Row>()
            while (rs.next()) {
                val values = LinkedHashMap<String, Any?>()
                for (c in 1..meta.columnCount) {
                    values[meta.getColumnLabel(c)] = when (val v = rs.getObject(c)) {
                        is Int -> v.toLong()
                        is Float -> v.toDouble()
                        else -> v
                    }
                }
                out.add(Row(values))
            }
            out
        }
    }

    override fun <T> transaction(block: () -> T): T {
        conn.autoCommit = false
        try {
            val r = block()
            conn.commit()
            return r
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }
}
