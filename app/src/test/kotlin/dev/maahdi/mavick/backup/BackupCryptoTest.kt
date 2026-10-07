package dev.maahdi.mavick.backup

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.security.SecureRandom
import org.junit.Test

/** Rounds are kept low here so the tests are quick; the real default is checked on its own. */
class BackupCryptoTest {
    private val rounds = 1_000
    private val password = "correct horse battery staple"
    private val data = "Call the bank at 17:00 \u2014 \u0985\u09AE\u09BF \uD83D\uDE00".toByteArray(Charsets.UTF_8)

    private fun seal(plain: ByteArray = data, pw: String = password, iterations: Int = rounds) =
        BackupCrypto.encrypt(plain, pw.toCharArray(), iterations)

    private fun problemOf(block: () -> Unit): BackupProblem = (runCatching(block).exceptionOrNull() as BackupException).problem

    // --- Round trip ---

    @Test
    fun `a backup opens with its password and gives back the same bytes`() {
        assertThat(BackupCrypto.decrypt(seal(), password.toCharArray())).isEqualTo(data)
    }

    @Test
    fun `an empty message and a large one both survive`() {
        assertThat(BackupCrypto.decrypt(seal(ByteArray(0)), password.toCharArray())).isEmpty()
        val large = ByteArray(5_000_000) { (it % 251).toByte() }
        assertThat(BackupCrypto.decrypt(seal(large), password.toCharArray())).isEqualTo(large)
    }

    @Test
    fun `a password in any alphabet works, exactly as typed`() {
        val sealed = seal(pw = "p\u00e4ssw\u00f6rd \u0985\u09AE\u09BF \uD83D\uDD11")

        assertThat(BackupCrypto.decrypt(sealed, "p\u00e4ssw\u00f6rd \u0985\u09AE\u09BF \uD83D\uDD11".toCharArray())).isEqualTo(data)
        assertThat(problemOf { BackupCrypto.decrypt(sealed, "passw\u00f6rd".toCharArray()) }).isEqualTo(BackupProblem.WRONG_PASSWORD_OR_DAMAGED)
    }

    // --- Passwords ---

    @Test
    fun `the wrong password is refused`() {
        assertThat(problemOf { BackupCrypto.decrypt(seal(), "Correct horse battery staple".toCharArray()) })
            .isEqualTo(BackupProblem.WRONG_PASSWORD_OR_DAMAGED)
    }

    @Test
    fun `an empty password is refused when making a backup and never opens one`() {
        val refused = runCatching { BackupCrypto.encrypt(data, CharArray(0), rounds) }.exceptionOrNull()
        assertThat(refused).isInstanceOf(IllegalArgumentException::class.java)

        assertThat(problemOf { BackupCrypto.decrypt(seal(), CharArray(0)) }).isEqualTo(BackupProblem.WRONG_PASSWORD_OR_DAMAGED)
    }

    // --- What the file shows ---

    @Test
    fun `the file does not contain the text it protects`() {
        val sealed = seal("Call the bank at five".toByteArray())

        assertThat(String(sealed, Charsets.ISO_8859_1)).doesNotContain("Call the bank")
        assertThat(String(sealed, Charsets.ISO_8859_1)).doesNotContain(password)
    }

    @Test
    fun `two backups of the same data differ, so a file says nothing about another`() {
        assertThat(seal()).isNotEqualTo(seal())
    }

    @Test
    fun `the salt and the nonce come from the random source given`() {
        val first = BackupCrypto.encrypt(data, password.toCharArray(), rounds, SecureRandom(byteArrayOf(1)))
        val other = BackupCrypto.encrypt(data, password.toCharArray(), rounds, SecureRandom())

        assertThat(first).isNotEqualTo(other)
    }

    // --- The layout is a contract ---

    @Test
    fun `the file starts with the name, the format and the rounds`() {
        val sealed = seal()

        assertThat(String(sealed.copyOfRange(0, 8), Charsets.US_ASCII)).isEqualTo("MAVICKBK")
        assertThat(sealed[8].toInt()).isEqualTo(1)
        assertThat(ByteBuffer.wrap(sealed, 9, 4).int).isEqualTo(rounds)
        // 8 + 1 + 4 + 16 (salt) + 12 (nonce) = 41, then the data and its 16-byte tag.
        assertThat(sealed.size).isEqualTo(41 + data.size + 16)
    }

    @Test
    fun `the number of rounds for new backups is high enough to slow down guessing`() {
        assertThat(BackupCrypto.DEFAULT_ITERATIONS).isAtLeast(600_000)
        assertThat(BackupCrypto.FORMAT).isEqualTo(1)
    }

    // --- Damage and mistakes ---

    @Test
    fun `changing any single byte is caught, never giving back data`() {
        val sealed = seal()
        for (position in sealed.indices) {
            val changed = sealed.copyOf().also { it[position] = (it[position].toInt() xor 0x01).toByte() }

            val result = runCatching { BackupCrypto.decrypt(changed, password.toCharArray()) }

            assertThat(result.exceptionOrNull()).isInstanceOf(BackupException::class.java)
        }
    }

    @Test
    fun `a file cut short is refused at every length`() {
        val sealed = seal()
        for (length in 0 until sealed.size) {
            val result = runCatching { BackupCrypto.decrypt(sealed.copyOf(length), password.toCharArray()) }

            assertThat(result.exceptionOrNull()).isInstanceOf(BackupException::class.java)
        }
    }

    @Test
    fun `something that is not a backup is said to be so`() {
        assertThat(problemOf { BackupCrypto.decrypt(ByteArray(0), password.toCharArray()) }).isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problemOf { BackupCrypto.decrypt("just some text, long enough to have a header and a tag".toByteArray(), password.toCharArray()) })
            .isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problemOf { BackupCrypto.decrypt(ByteArray(500) { 7 }, password.toCharArray()) }).isEqualTo(BackupProblem.NOT_A_BACKUP)
    }

    @Test
    fun `a backup from a newer Mavick says to update, before asking for the password`() {
        val newer = seal().also { it[8] = 2 }

        assertThat(problemOf { BackupCrypto.decrypt(newer, password.toCharArray()) }).isEqualTo(BackupProblem.NEWER_FORMAT)
        assertThat(problemOf { BackupCrypto.decrypt(newer, "wrong".toCharArray()) }).isEqualTo(BackupProblem.NEWER_FORMAT)
    }

    @Test
    fun `a format of zero is not a backup`() {
        assertThat(problemOf { BackupCrypto.decrypt(seal().also { it[8] = 0 }, password.toCharArray()) }).isEqualTo(BackupProblem.NOT_A_BACKUP)
    }

    @Test
    fun `rounds out of range are refused, so a crafted file can't hang the phone`() {
        assertThat(runCatching { BackupCrypto.encrypt(data, password.toCharArray(), 999) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)

        val huge = seal().also { ByteBuffer.wrap(it, 9, 4).putInt(2_000_000_000) }
        assertThat(problemOf { BackupCrypto.decrypt(huge, password.toCharArray()) }).isEqualTo(BackupProblem.NOT_A_BACKUP)
        val tiny = seal().also { ByteBuffer.wrap(it, 9, 4).putInt(1) }
        assertThat(problemOf { BackupCrypto.decrypt(tiny, password.toCharArray()) }).isEqualTo(BackupProblem.NOT_A_BACKUP)
    }

    @Test
    fun `an error carries only the name of the problem`() {
        val error = runCatching { BackupCrypto.decrypt(seal(), "wrong".toCharArray()) }.exceptionOrNull() as BackupException

        assertThat(error.message).isEqualTo("WRONG_PASSWORD_OR_DAMAGED")
    }
}
