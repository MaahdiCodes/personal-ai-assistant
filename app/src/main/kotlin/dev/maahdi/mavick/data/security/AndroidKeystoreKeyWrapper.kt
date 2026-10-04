package dev.maahdi.mavick.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps secrets with an AES-256-GCM key kept in the Android Keystore. That key is generated
 * inside the phone's secure hardware and can never be read out, only used.
 *
 * The key deliberately does not require the phone to be unlocked: Mavick must be able to save
 * incoming messages while the screen is locked (Phase 2).
 */
class AndroidKeystoreKeyWrapper(private val alias: String) : KeyWrapper {

    override fun wrap(plaintext: ByteArray): WrappedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKeyOrNull() ?: generateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return WrappedSecret(iv = cipher.iv, ciphertext = ciphertext)
    }

    override fun unwrap(wrapped: WrappedSecret): ByteArray {
        val key = existingKeyOrNull() ?: throw KeyStoreException("Keystore key '$alias' is missing.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, wrapped.iv))
        return cipher.doFinal(wrapped.ciphertext)
    }

    private fun existingKeyOrNull(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as SecretKey?
    }

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}
