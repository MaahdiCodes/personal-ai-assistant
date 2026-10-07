package dev.maahdi.mavick.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Why a backup file could not be made or opened. */
enum class BackupProblem {
    /** Not a Mavick backup at all (or cut short). */
    NOT_A_BACKUP,

    /** Made by a newer Mavick than this one: update the app, then open it. */
    NEWER_FORMAT,

    /** The password is wrong, or the file was changed or damaged. They can't be told apart, by design. */
    WRONG_PASSWORD_OR_DAMAGED,
}

/** The message is only the problem's name: nothing of the backup ever goes into an error. */
class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/**
 * The backup file's protection (docs/PLAN.md §5.7 A): AES-256-GCM with a key made from a password
 * the user chooses, by PBKDF2-HMAC-SHA256. Mavick never keeps the password; without it the file
 * can't be opened, by design.
 *
 * The file is: `MAVICKBK` (8 bytes), the format (1 byte), the PBKDF2 rounds (4 bytes), the salt
 * (16), the nonce (12), then the encrypted data with its authentication tag. Everything before the
 * encrypted data is authenticated too, so none of it can be changed without the password. This layout
 * is a contract: old backups must always open (extend it by raising the format byte, never change it).
 */
object BackupCrypto {
    /** PBKDF2 rounds for new backups (OWASP's advice for PBKDF2-HMAC-SHA256). Stored in each file. */
    const val DEFAULT_ITERATIONS = 600_000

    /** The format this version of Mavick writes, and the newest it can read. */
    const val FORMAT = 1

    /** Rounds outside this range in a file are refused: too few is no protection, too many could hang the phone. */
    val ITERATION_RANGE = 1_000..5_000_000

    private val MAGIC = "MAVICKBK".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private val HEADER_BYTES = MAGIC.size + 1 + Int.SIZE_BYTES + SALT_BYTES + NONCE_BYTES

    /** @throws IllegalArgumentException for an empty password or rounds outside [ITERATION_RANGE]. */
    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(password.isNotEmpty()) { "A backup needs a password." }
        require(iterations in ITERATION_RANGE) { "Rounds out of range." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(FORMAT.toByte()).putInt(iterations).put(salt).put(nonce)
            .array()
        val encrypted = cipher(Cipher.ENCRYPT_MODE, password, salt, iterations, nonce, header).doFinal(plain)
        return header + encrypted
    }

    /** @throws BackupException [BackupProblem.NOT_A_BACKUP], [BackupProblem.NEWER_FORMAT] or [BackupProblem.WRONG_PASSWORD_OR_DAMAGED]. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        // The shortest possible file: the header and an empty message's authentication tag.
        if (file.size < HEADER_BYTES + TAG_BITS / 8) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val buffer = ByteBuffer.wrap(file)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val format = buffer.get().toInt()
        if (format > FORMAT) throw BackupException(BackupProblem.NEWER_FORMAT)
        if (format < 1) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val iterations = buffer.getInt()
        if (iterations !in ITERATION_RANGE) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        if (password.isEmpty()) throw BackupException(BackupProblem.WRONG_PASSWORD_OR_DAMAGED)
        return try {
            cipher(Cipher.DECRYPT_MODE, password, salt, iterations, nonce, file.copyOfRange(0, HEADER_BYTES))
                .doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupProblem.WRONG_PASSWORD_OR_DAMAGED, e)
        }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iterations: Int, nonce: ByteArray, header: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(header)
            }
        } finally {
            keyBytes.fill(0)
        }
    }
}
