package org.nooklin.files.net

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the Nextcloud password with a key that lives in the Android Keystore and cannot be read out of it. */
object SecretBox {
    const val ALIAS = "nooklin_files_nextcloud"
    /** The key for the passwords of the FTP and SMB servers. Separate, so disconnecting Nextcloud does not lose them. */
    const val ALIAS_SERVERS = "nooklin_files_servers"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(alias: String): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Text safe to store: the random start value followed by the encrypted bytes, as base64. */
    fun encrypt(plain: String, alias: String = ALIAS): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /** The original text, or null when it cannot be decrypted (for example after the key was removed). */
    fun decrypt(stored: String, alias: String = ALIAS): String? =
        try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }

    fun deleteKey(alias: String = ALIAS) {
        try {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(alias)
        } catch (e: Exception) {
            // Nothing to delete
        }
    }
}
