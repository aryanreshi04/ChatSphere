package com.jos.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import com.jos.myapplication.data.Message
import com.jos.myapplication.data.SyncState
import com.jos.myapplication.database.DatabaseHelper
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CacheMigrationTest {
    @Test fun versionOneUsersSurviveAndOutboxIsAccountScopedAndDurable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test-${UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use {
                it.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL)")
                it.execSQL("INSERT INTO users VALUES (42,'Existing Student','student@example.test')")
                it.version = 1
            }
            DatabaseHelper(context, name).use { helper ->
                helper.readableDatabase.rawQuery("SELECT id,name FROM users", null).use {
                    assertTrue(it.moveToFirst()); assertEquals(42, it.getInt(0)); assertEquals("Existing Student", it.getString(1))
                }
                helper.saveMessages("alice", "alice_bob", listOf(Message("stable-id", "alice", "Offline draft", 123)))
                assertTrue(helper.messages("bob", "alice_bob").isEmpty())
                assertEquals(1, helper.outbox("alice").size)
            }
            DatabaseHelper(context, name).use { helper ->
                val message = helper.messages("alice", "alice_bob").single()
                assertEquals("stable-id", message.id); assertEquals(SyncState.QUEUED, message.state)
                helper.saveMessages("alice", "alice_bob", listOf(message.copy(state = SyncState.SENT)))
                assertTrue(helper.outbox("alice").isEmpty()); assertEquals(1, helper.messages("alice", "alice_bob").size)
            }
        } finally { context.deleteDatabase(name) } // Only this test's unique database is removed.
    }
}
