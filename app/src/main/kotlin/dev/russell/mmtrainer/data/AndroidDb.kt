package dev.russell.mmtrainer.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteProgram
import dev.russell.mmtrainer.Db
import dev.russell.mmtrainer.Row
import dev.russell.mmtrainer.splitStatements

/**
 * The core's Db over android.database.sqlite. Arguments are bound with their real types
 * (rawQuery would bind everything as text), so REAL/INTEGER comparisons behave exactly as
 * they do in the JVM tests.
 */
class AndroidDb(context: Context, name: String = "progress.db") : Db {
    // The Store creates its own schema (CREATE TABLE IF NOT EXISTS), so the helper has nothing to do.
    private val helper = object : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {}
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
    private val db: SQLiteDatabase get() = helper.writableDatabase

    private fun bind(p: SQLiteProgram, args: List<Any?>) {
        args.forEachIndexed { i, a ->
            val idx = i + 1
            when (a) {
                null -> p.bindNull(idx)
                is Boolean -> p.bindLong(idx, if (a) 1 else 0)
                is Int -> p.bindLong(idx, a.toLong())
                is Long -> p.bindLong(idx, a)
                is Double -> p.bindDouble(idx, a)
                is String -> p.bindString(idx, a)
                else -> error("unsupported arg ${a::class}")
            }
        }
    }

    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) {
            splitStatements(sql).forEach { db.execSQL(it) }
        } else {
            db.compileStatement(sql).use { st ->
                bind(st, args)
                st.execute()
            }
        }
    }

    override fun insert(sql: String, args: List<Any?>): Long =
        db.compileStatement(sql).use { st ->
            bind(st, args)
            st.executeInsert()
        }

    override fun query(sql: String, args: List<Any?>): List<Row> {
        val cursor = db.rawQueryWithFactory({ _, driver, table, query ->
            bind(query, args)
            SQLiteCursor(driver, table, query)
        }, sql, null, "")
        return cursor.use { c ->
            val out = ArrayList<Row>(c.count)
            while (c.moveToNext()) {
                val values = LinkedHashMap<String, Any?>()
                for (i in 0 until c.columnCount) {
                    values[c.getColumnName(i)] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        else -> c.getString(i)
                    }
                }
                out.add(Row(values))
            }
            out
        }
    }

    override fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try {
            val r = block()
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }
}
