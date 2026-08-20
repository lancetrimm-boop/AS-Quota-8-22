package com.example.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase as StandardSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as EncryptedSQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Handles the secure transition of a legacy plaintext SQLite database to SQLCipher encryption.
 */
object LegacyDatabaseEncryptionMigrator {
    private const val TAG = "AURA_TRANSITION_FORENSIC"
    private const val DATABASE_NAME = "aura_intelligence.db"

    sealed class TransitionResult {
        object Success : TransitionResult()
        object AlreadyEncrypted : TransitionResult()
        object LegacyNotPresent : TransitionResult()
        data class Failure(val reason: String, val cause: Throwable? = null) : TransitionResult()
    }

    /**
     * Ensures the database is encrypted. If a plaintext database is found, it is transitioned.
     */
    fun ensureEncryption(context: Context): TransitionResult {
        val dbPath = context.getDatabasePath(DATABASE_NAME)
        val tempDbPath = File(dbPath.parent, "${DATABASE_NAME}.tmp")
        val backupDbPath = File(dbPath.parent, "${DATABASE_NAME}.legacy_bak")

        // Interrupted Swap Recovery: If main file is missing but backup exists, restore it to retry.
        if (!dbPath.exists() && backupDbPath.exists()) {
            Log.w(TAG, "Interrupted transition detected. Restoring legacy backup for retry.")
            if (!backupDbPath.renameTo(dbPath)) {
                return TransitionResult.Failure("Failed to restore legacy backup from interrupted swap.")
            }
        }

        if (!dbPath.exists()) {
            Log.i(TAG, "Legacy database not present. Proceeding normally.")
            return TransitionResult.LegacyNotPresent
        }

        // Check for zero-length files which can cause SQLite open crashes
        if (dbPath.length() == 0L) {
            Log.w(TAG, "Empty database file detected. Skipping transition.")
            return TransitionResult.LegacyNotPresent
        }

        if (isDatabaseEncrypted(dbPath)) {
            Log.i(TAG, "Database is already encrypted. No transition required.")
            return TransitionResult.AlreadyEncrypted
        }

        Log.i(TAG, "Plaintext legacy database detected. Starting transition...")
        
        // 0. Space Check
        val requiredSpace = dbPath.length() * 2
        val usableSpace = dbPath.parentFile?.usableSpace ?: 0L
        if (usableSpace < requiredSpace) {
            return TransitionResult.Failure("Insufficient disk space for transition. Required: $requiredSpace, Available: $usableSpace")
        }

        return try {
            val hexKey = PassphraseManager.getPassphraseAsHex(context)
            performTransition(context, dbPath, hexKey)
            TransitionResult.Success
        } catch (t: Throwable) {
            Log.e(TAG, "Transition failed: ${t.message}", t)
            TransitionResult.Failure(t.message ?: t.javaClass.simpleName, t)
        }
    }

    private fun isDatabaseEncrypted(dbPath: File): Boolean {
        if (!dbPath.exists() || dbPath.length() < 16L) {
            return false
        }
        return try {
            val header = ByteArray(16)
            java.io.FileInputStream(dbPath).use { fis ->
                val bytesRead = fis.read(header)
                if (bytesRead < 16) return false
            }
            val sqliteMagic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            !header.contentEquals(sqliteMagic)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to inspect database header bytes (assuming encrypted): ${e.message}")
            true
        }
    }

    private fun performTransition(context: Context, originalDbPath: File, hexKey: String) {
        val tempDbPath = File(originalDbPath.parent, "${DATABASE_NAME}.tmp")
        val backupDbPath = File(originalDbPath.parent, "${DATABASE_NAME}.legacy_bak")

        // Cleanup any stale temporary files
        if (tempDbPath.exists()) tempDbPath.delete()
        File(tempDbPath.path + "-wal").delete()
        File(tempDbPath.path + "-shm").delete()
        File(tempDbPath.path + "-journal").delete()

        // 0. Capture baseline metrics and version from plaintext
        val baselineMetrics = mutableMapOf<String, Int>()
        val criticalTables = listOf("media_items", "user_preferences", "pairwise_outcomes", "collections")
        var legacyVersion = 0
        
        StandardSQLiteDatabase.openDatabase(originalDbPath.absolutePath, null, StandardSQLiteDatabase.OPEN_READONLY).use { db ->
            legacyVersion = db.version
            criticalTables.forEach { table ->
                try {
                    db.rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            baselineMetrics[table] = cursor.getInt(0)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Table $table not found in legacy schema. Skipping baseline.")
                }
            }
        }

        // 1. Pre-initialize the encrypted destination (Approach B)
        // This ensures SQLCipher initializes headers and confirms the key is working.
        EncryptedSQLiteDatabase.openOrCreateDatabase(tempDbPath, hexKey.toByteArray(), null, null).use { db ->
            db.version = legacyVersion // Preserve the version pragma for Room migration logic
        }

        // 2. Open the plaintext database using SQLCipher with an empty key
        val plaintextDb = EncryptedSQLiteDatabase.openDatabase(originalDbPath.absolutePath, "".toByteArray(), null, EncryptedSQLiteDatabase.OPEN_READWRITE, null)
        try {
            // 3. Attach the new encrypted database
            // Note: Wrap the key in double quotes for SQL compatibility
            plaintextDb.execSQL("ATTACH DATABASE '${tempDbPath.absolutePath}' AS encrypted KEY \"$hexKey\"")
            
            try {
                // 4. Export data
                Log.i(TAG, "Exporting data to encrypted container...")
                plaintextDb.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { cursor ->
                    cursor.moveToFirst()
                }
            } finally {
                // 5. Detach
                plaintextDb.execSQL("DETACH DATABASE encrypted")
            }
        } finally {
            plaintextDb.close()
        }

        // 6. Comprehensive Verification
        Log.i(TAG, "Verifying encrypted database integrity...")
        // Use raw hex key for verification open
        val encryptedDb = EncryptedSQLiteDatabase.openDatabase(tempDbPath.absolutePath, hexKey.toByteArray(), null, EncryptedSQLiteDatabase.OPEN_READONLY, null)
        try {
            // A. Check SQLCipher is actually active (Standard SQLite should fail on this file)
            if (!isDatabaseEncrypted(tempDbPath)) {
                throw IllegalStateException("Verification failed: Target database is not encrypted")
            }

            // B. Verify Table and Row Continuity
            baselineMetrics.forEach { (table, expectedCount) ->
                encryptedDb.rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val actualCount = cursor.getInt(0)
                        if (actualCount != expectedCount) {
                            throw IllegalStateException("Data loss detected in $table: Expected $expectedCount, found $actualCount")
                        }
                        Log.d(TAG, "Verified $table: $actualCount rows preserved.")
                    } else {
                        throw IllegalStateException("Verification failed: Could not read $table")
                    }
                }
            }

            // C. Verify Schema Integrity (Indexes)
            val expectedIndexes = listOf("index_media_items_uriPath", "index_media_items_contentHash")
            expectedIndexes.forEach { idx ->
                encryptedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='$idx'", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        Log.d(TAG, "Verified index preservation: $idx")
                    } else {
                        Log.w(TAG, "Index $idx not found in migrated database (legacy schema?)")
                    }
                }
            }

            // D. PRAGMA integrity_check
            encryptedDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    if (result != "ok") throw IllegalStateException("SQLite integrity check failed: $result")
                }
            }

        } finally {
            encryptedDb.close()
        }

        // 7. Atomic Swap
        Log.i(TAG, "Transition verified. Performing atomic swap...")
        if (backupDbPath.exists()) backupDbPath.delete()
        if (!originalDbPath.renameTo(backupDbPath)) throw IllegalStateException("Failed to backup legacy database")
        if (!tempDbPath.renameTo(originalDbPath)) {
            // Rollback backup if swap fails
            backupDbPath.renameTo(originalDbPath)
            throw IllegalStateException("Failed to move encrypted database into place")
        }
        
        Log.i(TAG, "Transition complete. legacy_bak retained for safety.")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789ABCDEF".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Cleans up legacy backups after a retention period or successful use of the new database.
     */
    fun cleanupLegacyBackups(context: Context) {
        val backupDbPath = File(context.getDatabasePath(DATABASE_NAME).parent, "${DATABASE_NAME}.legacy_bak")
        if (!backupDbPath.exists()) return

        val prefs = context.getSharedPreferences("aura_transition_prefs", Context.MODE_PRIVATE)
        val launchCount = prefs.getInt("successful_launches_post_transition", 0) + 1
        
        val lastModified = backupDbPath.lastModified()
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        val isExpired = (System.currentTimeMillis() - lastModified) > sevenDaysMs

        if (launchCount >= 5 || isExpired) {
            Log.i(TAG, "Legacy backup retention period expired ($launchCount launches). Deleting backup.")
            backupDbPath.delete()
        } else {
            prefs.edit().putInt("successful_launches_post_transition", launchCount).apply()
            Log.d(TAG, "Legacy backup retained (Launch count: $launchCount/5)")
        }
    }
}
