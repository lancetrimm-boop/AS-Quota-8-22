package com.example.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecureStorageStateTest {

    private lateinit var context: Context
    private val dbName = "aura_intelligence.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()
        
        val prefs = context.getSharedPreferences("aura_security_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testGetPassphrase_FreshInstall() {
        // STATE 1: No DB, no prefs
        val passphrase = PassphraseManager.getPassphrase(context)
        assertNotNull(passphrase)
        assertEquals(32, passphrase.size)
        
        val prefs = context.getSharedPreferences("aura_security_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.contains("encrypted_db_passphrase"))
        assertTrue(prefs.contains("db_passphrase_iv"))
    }

    @Test
    fun testGetPassphrase_PlaintextMigration() {
        // STATE 5: Plaintext DB exists, no prefs
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE test (id INTEGER PRIMARY KEY)")
        }
        
        val passphrase = PassphraseManager.getPassphrase(context)
        assertNotNull(passphrase)
        
        val prefs = context.getSharedPreferences("aura_security_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.contains("encrypted_db_passphrase"))
    }

    @Test(expected = SecureStorageException.InconsistentStateException::class)
    fun testGetPassphrase_InconsistentState() {
        // STATE 6: Non-plaintext file exists, no prefs
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("THIS IS NOT SQLITE")
        
        PassphraseManager.getPassphrase(context)
    }
}
