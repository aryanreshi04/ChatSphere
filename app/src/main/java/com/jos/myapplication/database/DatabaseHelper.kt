package com.jos.myapplication.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jos.myapplication.data.Message
import kotlinx.coroutines.flow.MutableStateFlow

/** Firebase is authoritative. Account-scoped cache and durable outbox only. Call on Dispatchers.IO. */
class DatabaseHelper(context: Context, databaseName: String = "ChatSphere.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
    val changes = MutableStateFlow(0L)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL)")
        createCache(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createCache(db) // Preserve all version 1 users and IDs.
    }
    private fun createCache(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cache_records (owner TEXT NOT NULL, namespace TEXT NOT NULL, key TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(owner, namespace, key))")
        db.execSQL("CREATE TABLE IF NOT EXISTS cached_messages (owner TEXT NOT NULL, conversation TEXT NOT NULL, id TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(owner, conversation, id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS message_order ON cached_messages(owner, conversation, timestamp)")
    }
    fun addUser(name: String, email: String) {
        writableDatabase.insert("users", null, ContentValues().apply { put("name", name); put("email", email) })
    }
    fun putRecord(owner: String, namespace: String, key: String, payload: String) {
        writableDatabase.insertWithOnConflict("cache_records", null, ContentValues().apply {
            put("owner", owner); put("namespace", namespace); put("key", key); put("payload", payload)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        if (namespace == "media" || namespace == "notified") {
            val limit = if (namespace == "media") 50 else 1000
            writableDatabase.execSQL("DELETE FROM cache_records WHERE owner=? AND namespace=? AND rowid NOT IN (SELECT rowid FROM cache_records WHERE owner=? AND namespace=? ORDER BY rowid DESC LIMIT $limit)", arrayOf(owner, namespace, owner, namespace))
        }
    }
    fun record(owner: String, namespace: String, key: String): String? =
        readableDatabase.query("cache_records", arrayOf("payload"), "owner=? AND namespace=? AND key=?",
            arrayOf(owner, namespace, key), null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }

    @Synchronized fun saveMessages(owner: String, conversation: String, messages: List<Message>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            messages.forEach { message ->
                db.insertWithOnConflict("cached_messages", null, ContentValues().apply {
                    put("owner", owner); put("conversation", conversation); put("id", message.id)
                    put("payload", message.json()); put("state", message.state.name); put("timestamp", message.timestamp)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.execSQL("DELETE FROM cached_messages WHERE owner=? AND conversation=? AND state IN ('SENT','CACHED') AND id NOT IN (SELECT id FROM cached_messages WHERE owner=? AND conversation=? ORDER BY timestamp DESC, id DESC LIMIT 300)", arrayOf(owner, conversation, owner, conversation))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        changes.value += 1
    }
    fun messages(owner: String, conversation: String): List<Message> =
        readableDatabase.query("cached_messages", arrayOf("payload"), "owner=? AND conversation=?",
            arrayOf(owner, conversation), null, null, "timestamp ASC, id ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) runCatching { Message.fromJson(cursor.getString(0)) }.getOrNull()?.let { add(it) } }
        }
    fun outbox(owner: String): List<Pair<String, Message>> =
        readableDatabase.query("cached_messages", arrayOf("conversation", "payload"),
            "owner=? AND state IN ('QUEUED','SENDING')", arrayOf(owner), null, null, "timestamp ASC, id ASC").use { cursor ->
            buildList { while (cursor.moveToNext()) runCatching { Message.fromJson(cursor.getString(1)) }.getOrNull()?.let { add(cursor.getString(0) to it) } }
        }
}
