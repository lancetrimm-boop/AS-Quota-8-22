package com.example.data.db

import android.content.Context
import android.util.Log

/**
 * Manages the initialization of SQLCipher native libraries.
 * Ensures that libraries are loaded exactly once and before any database operation.
 */
object SQLCipherInitializer {
    private const val TAG = "SQLCipherInitializer"
    
    @Volatile
    private var isInitialized = false

    /**
     * Loads SQLCipher native libraries. This call is thread-safe and idempotent.
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        synchronized(this) {
            if (isInitialized) return
            
            try {
                Log.d(TAG, "Loading SQLCipher native libraries...")
                System.loadLibrary("sqlcipher")
                isInitialized = true
                Log.i(TAG, "SQLCipher libraries loaded successfully.")
            } catch (t: Throwable) {
                Log.e(TAG, "CRITICAL: Failed to load SQLCipher native libraries", t)
                throw RuntimeException("SQLCipher initialization failed", t)
            }
        }
    }
}
