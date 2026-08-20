package com.example.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

/**
 * Manages database encryption passphrases using Android KeyStore.
 * Securely stores a persistent random passphrase encrypted by a hardware-backed key.
 */
object PassphraseManager {
    private const val KEY_ALIAS = "aura_db_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "aura_security_prefs"
    private const val ENCRYPTED_PASSPHRASE_KEY = "encrypted_db_passphrase"
    private const val IV_KEY = "db_passphrase_iv"

    /**
     * Retrieves or generates a persistent passphrase for database encryption.
     */
    fun getPassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(ENCRYPTED_PASSPHRASE_KEY, null)
        val ivBase64 = prefs.getString(IV_KEY, null)

        val masterKey = getOrCreateMasterKey()

        return if (encryptedBase64 != null && ivBase64 != null) {
            // Decrypt existing passphrase
            val encrypted = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } else {
            // Generate new random passphrase
            val passphrase = ByteArray(32)
            SecureRandom().nextBytes(passphrase)

            // Encrypt and store it
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val encrypted = cipher.doFinal(passphrase)
            val iv = cipher.iv

            prefs.edit()
                .putString(ENCRYPTED_PASSPHRASE_KEY, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
                .putString(IV_KEY, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                .apply()

            passphrase
        }
    }

    /**
     * Returns the passphrase as a hex string formatted for SQLCipher raw binary key usage (x'HEX').
     */
    fun getPassphraseAsHex(context: Context): String {
        val bytes = getPassphrase(context)
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789ABCDEF".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return "x'${String(hexChars)}'"
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, 
                ANDROID_KEYSTORE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false) // Required for background database access
                .build()
            )
            keyGenerator.generateKey()
        }
    }
}
