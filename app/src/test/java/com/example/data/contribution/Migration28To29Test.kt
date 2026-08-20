package com.example.data.contribution

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration28To29Test {

    private lateinit var context: Context
    private val testDbName = "test_migration_28_29.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(testDbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun testRoomMigration28To29_RemovesDuplicatesAndAddsUniqueIndex() {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create SQLite DB with version 28 schema
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(28) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `contribution_queue` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `eventType` TEXT NOT NULL, 
                            `schemaVersion` TEXT NOT NULL, 
                            `payloadJson` TEXT NOT NULL, 
                            `createdAt` INTEGER NOT NULL, 
                            `status` TEXT NOT NULL,
                            `idempotencyKey` TEXT NOT NULL DEFAULT '',
                            `retryCount` INTEGER NOT NULL DEFAULT 0,
                            `lastAttemptTimestamp` INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(config)
        val db = openHelper.writableDatabase

        // 2. Insert duplicate idempotencyKey records
        // Oldest (should keep)
        db.execSQL("""
            INSERT INTO contribution_queue (id, eventType, schemaVersion, payloadJson, createdAt, status, idempotencyKey)
            VALUES (1, 'TYPE_1', '1.0', '{}', 1000, 'PENDING', 'key_1')
        """)
        // Duplicate (should remove)
        db.execSQL("""
            INSERT INTO contribution_queue (id, eventType, schemaVersion, payloadJson, createdAt, status, idempotencyKey)
            VALUES (2, 'TYPE_1', '1.0', '{}', 2000, 'PENDING', 'key_1')
        """)
        // Unique record (should keep)
        db.execSQL("""
            INSERT INTO contribution_queue (id, eventType, schemaVersion, payloadJson, createdAt, status, idempotencyKey)
            VALUES (3, 'TYPE_2', '1.0', '{}', 3000, 'PENDING', 'key_2')
        """)

        // 3. Execute MIGRATION_28_29
        AuraDatabase.MIGRATION_28_29.migrate(db)

        // 4. Verify data
        val cursor = db.query("SELECT id, idempotencyKey FROM contribution_queue ORDER BY id ASC")
        
        // Should have record 1
        assertTrue(cursor.moveToNext())
        assertEquals(1L, cursor.getLong(0))
        assertEquals("key_1", cursor.getString(1))

        // Record 2 should be gone.
        // Should have record 3
        assertTrue(cursor.moveToNext())
        assertEquals(3L, cursor.getLong(0))
        assertEquals("key_2", cursor.getString(1))

        assertFalse(cursor.moveToNext())
        cursor.close()

        // 5. Verify index exists and is unique
        val indexCursor = db.query("PRAGMA index_list('contribution_queue')")
        var foundUniqueIndex = false
        while (indexCursor.moveToNext()) {
            val name = indexCursor.getString(indexCursor.getColumnIndexOrThrow("name"))
            val unique = indexCursor.getInt(indexCursor.getColumnIndexOrThrow("unique"))
            if (name == "index_contribution_queue_idempotencyKey" && unique == 1) {
                foundUniqueIndex = true
            }
        }
        indexCursor.close()
        assertTrue("Unique index must exist", foundUniqueIndex)

        // 6. Verify duplicate insertion fails at SQL level
        try {
            db.execSQL("INSERT INTO contribution_queue (eventType, schemaVersion, payloadJson, createdAt, status, idempotencyKey) VALUES ('T', '1', '{}', 0, 'P', 'key_1')")
            fail("Should have thrown SQLiteConstraintException")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Success
        }

        db.close()
    }
}
