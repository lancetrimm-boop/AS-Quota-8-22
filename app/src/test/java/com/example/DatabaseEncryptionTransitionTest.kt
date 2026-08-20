package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.LegacyDatabaseEncryptionMigrator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseEncryptionTransitionTest {

    private lateinit var context: Context
    private val dbName = "aura_intelligence.db"
    private val passphrase = "test_passphrase_32_bytes_long_total".toByteArray().take(32).toByteArray()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()
        File(dbFile.parent, "${dbName}.legacy_bak").delete()
        File(dbFile.parent, "${dbName}.tmp").delete()
    }

    @Test
    fun testTransition_LegacyNotPresent() {
        val result = LegacyDatabaseEncryptionMigrator.ensureEncryption(context)
        assertEquals(LegacyDatabaseEncryptionMigrator.TransitionResult.LegacyNotPresent, result)
    }

    @Test
    fun testTransition_AlreadyEncrypted() {
        // This test is limited in unit test environment because SQLCipher 
        // native libs are typically not available on JVM.
        // We simulate by creating a file that Standard SQLite cannot open.
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("THIS IS NOT A SQLITE DATABASE")
        
        val result = LegacyDatabaseEncryptionMigrator.ensureEncryption(context)
        // Since standard SQLite fails to open it, it is detected as AlreadyEncrypted
        assertEquals(LegacyDatabaseEncryptionMigrator.TransitionResult.AlreadyEncrypted, result)
    }

    @Test
    fun testIsDatabaseEncrypted_Plaintext() {
        val dbFile = context.getDatabasePath("test_plain.db")
        dbFile.parentFile?.mkdirs()
        
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE test (id INTEGER PRIMARY KEY)")
        }
        
        // Use reflection to access private method for testing
        val method = LegacyDatabaseEncryptionMigrator::class.java.getDeclaredMethod("isDatabaseEncrypted", File::class.java)
        method.isAccessible = true
        val isEncrypted = method.invoke(LegacyDatabaseEncryptionMigrator, dbFile) as Boolean
        
        assertFalse("Should be detected as plaintext", isEncrypted)
        dbFile.delete()
    }

    @Test
    fun testEnsureEncryption_FreshInstall() {
        val dbFile = context.getDatabasePath(dbName)
        assertFalse(dbFile.exists())
        
        val result = LegacyDatabaseEncryptionMigrator.ensureEncryption(context)
        assertEquals(LegacyDatabaseEncryptionMigrator.TransitionResult.LegacyNotPresent, result)
        assertFalse(dbFile.exists())
    }
}
