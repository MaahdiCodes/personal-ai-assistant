package dev.maahdi.mavick.data.security

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.SecureRandom

/**
 * Provides the key for Mavick's encrypted (SQLCipher) database.
 *
 * - The key is 32 random bytes, generated once.
 * - On disk it is stored only in wrapped (encrypted) form. The wrapping key lives in the Android
 *   Keystore and never leaves secure hardware.
 * - SQLCipher receives it in raw-key form (`x'<64 hex digits>'`). Because the key is already
 *   random, SQLCipher skips its deliberately slow password-stretching step, so opening the
 *   database costs almost no CPU.
 */
class DatabaseKeyRepository(
    private val keyFile: File,
    private val keyWrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Returns the SQLCipher passphrase, creating the key on first use.
     *
     * @param databaseExists whether the encrypted database file already exists. A missing key is
     *   only created when there is no database it could belong to; otherwise that database would
     *   become unreadable without anyone noticing.
     * @throws DatabaseKeyException if the key exists but can't be read, or is missing while the
     *   database exists.
     */
    @Synchronized
    fun getOrCreatePassphrase(databaseExists: Boolean): ByteArray {
        val key = when {
            keyFile.exists() -> readKey()
            databaseExists -> throw DatabaseKeyException(
                "The database exists but its key is missing, so it can't be opened.",
            )
            else -> createKey()
        }
        try {
            return toRawKeyPassphrase(key)
        } finally {
            key.fill(0)
        }
    }

    private fun createKey(): ByteArray {
        val key = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)
        writeAtomically(WrappedSecretCodec.encode(keyWrapper.wrap(key)))
        return key
    }

    private fun readKey(): ByteArray {
        val wrapped = try {
            WrappedSecretCodec.decode(keyFile.readBytes())
        } catch (e: IllegalArgumentException) {
            throw DatabaseKeyException("The database key file is damaged.", e)
        }
        val key = try {
            keyWrapper.unwrap(wrapped)
        } catch (e: GeneralSecurityException) {
            throw DatabaseKeyException("The database key could not be decrypted.", e)
        } catch (e: ProviderException) {
            // The Android Keystore reports some hardware failures this way.
            throw DatabaseKeyException("The database key could not be decrypted.", e)
        }
        if (key.size != KEY_SIZE_BYTES) {
            key.fill(0)
            throw DatabaseKeyException("The database key has the wrong length.")
        }
        return key
    }

    /** Writes to a temporary file first, so a crash mid-write can never leave a half-written key. */
    private fun writeAtomically(bytes: ByteArray) {
        keyFile.parentFile?.mkdirs()
        val temporary = File(keyFile.parentFile, "${keyFile.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            keyFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    companion object {
        const val KEY_SIZE_BYTES = 32

        private val HEX_DIGITS = "0123456789ABCDEF".toByteArray(Charsets.US_ASCII)

        /**
         * Builds `x'<hex>'` directly as bytes, without creating a String that would keep the key
         * in memory where it can't be wiped.
         */
        fun toRawKeyPassphrase(key: ByteArray): ByteArray {
            val passphrase = ByteArray(key.size * 2 + 3)
            passphrase[0] = 'x'.code.toByte()
            passphrase[1] = '\''.code.toByte()
            key.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xFF
                passphrase[2 + index * 2] = HEX_DIGITS[value ushr 4]
                passphrase[3 + index * 2] = HEX_DIGITS[value and 0x0F]
            }
            passphrase[passphrase.size - 1] = '\''.code.toByte()
            return passphrase
        }
    }
}

class DatabaseKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * File format of the wrapped key: `[version: 1 byte][iv length: 1 byte][iv][ciphertext]`.
 * The version byte lets a future format be told apart from damage.
 */
internal object WrappedSecretCodec {
    private const val VERSION: Byte = 1
    private const val MAX_IV_LENGTH = 32

    fun encode(wrapped: WrappedSecret): ByteArray {
        require(wrapped.iv.size in 1..MAX_IV_LENGTH) { "Unexpected IV length ${wrapped.iv.size}." }
        return byteArrayOf(VERSION, wrapped.iv.size.toByte()) + wrapped.iv + wrapped.ciphertext
    }

    /** @throws IllegalArgumentException if [bytes] is not a valid wrapped secret. */
    fun decode(bytes: ByteArray): WrappedSecret {
        require(bytes.size >= 2) { "Too short." }
        require(bytes[0] == VERSION) { "Unsupported format version ${bytes[0]}." }
        val ivLength = bytes[1].toInt() and 0xFF
        require(ivLength in 1..MAX_IV_LENGTH) { "Unexpected IV length $ivLength." }
        require(bytes.size > 2 + ivLength) { "No ciphertext." }
        return WrappedSecret(
            iv = bytes.copyOfRange(2, 2 + ivLength),
            ciphertext = bytes.copyOfRange(2 + ivLength, bytes.size),
        )
    }
}
