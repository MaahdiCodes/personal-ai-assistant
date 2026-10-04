package dev.maahdi.mavick.data.security

/** Encrypts and decrypts small secrets (such as the database key) with a protected key. */
interface KeyWrapper {
    fun wrap(plaintext: ByteArray): WrappedSecret

    /** @throws java.security.GeneralSecurityException if the secret can't be decrypted. */
    fun unwrap(wrapped: WrappedSecret): ByteArray
}

/** An encrypted secret plus the initialization vector needed to decrypt it. */
class WrappedSecret(val iv: ByteArray, val ciphertext: ByteArray)
