package dev.maahdi.mavick.data.security

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.testing.containsSequence
import java.io.File
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseKeyRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val wrapper = FakeKeyWrapper()
    private lateinit var keyFile: File

    @Before
    fun setUp() {
        keyFile = File(folder.root, "database.key")
    }

    private fun repository(file: File = keyFile) = DatabaseKeyRepository(file, wrapper)

    @Test
    fun `first use creates a key and stores it only in wrapped form`() {
        val passphrase = repository().getOrCreatePassphrase(databaseExists = false)

        assertThat(keyFile.exists()).isTrue()
        assertThat(wrapper.wrapCalls).isEqualTo(1)
        assertThat(keyFile.readBytes().containsSequence(rawKeyOf(passphrase))).isFalse()
    }

    @Test
    fun `passphrase is in SQLCipher raw-key form, which skips slow key stretching`() {
        val passphrase = String(repository().getOrCreatePassphrase(databaseExists = false), Charsets.US_ASCII)

        assertThat(passphrase).matches("x'[0-9A-F]{64}'")
    }

    @Test
    fun `later calls return the same key without wrapping it again`() {
        val first = repository().getOrCreatePassphrase(databaseExists = false)
        val second = repository().getOrCreatePassphrase(databaseExists = true)

        assertThat(second).isEqualTo(first)
        assertThat(wrapper.wrapCalls).isEqualTo(1)
    }

    @Test
    fun `an existing key is reused even when the database file is missing`() {
        val first = repository().getOrCreatePassphrase(databaseExists = false)
        val again = repository().getOrCreatePassphrase(databaseExists = false)

        assertThat(again).isEqualTo(first)
    }

    @Test
    fun `separate installs get different keys`() {
        val one = repository(File(folder.newFolder(), "database.key")).getOrCreatePassphrase(databaseExists = false)
        val two = repository(File(folder.newFolder(), "database.key")).getOrCreatePassphrase(databaseExists = false)

        assertThat(one).isNotEqualTo(two)
    }

    @Test
    fun `a missing key for an existing database is reported, never silently replaced`() {
        assertThrows(DatabaseKeyException::class.java) {
            repository().getOrCreatePassphrase(databaseExists = true)
        }
        assertThat(keyFile.exists()).isFalse()
    }

    @Test
    fun `a damaged key file is reported`() {
        keyFile.writeBytes(byteArrayOf(9, 9, 9))

        assertThrows(DatabaseKeyException::class.java) {
            repository().getOrCreatePassphrase(databaseExists = true)
        }
    }

    @Test
    fun `an empty key file is reported`() {
        keyFile.writeBytes(ByteArray(0))

        assertThrows(DatabaseKeyException::class.java) {
            repository().getOrCreatePassphrase(databaseExists = true)
        }
    }

    @Test
    fun `a key that fails to decrypt is reported`() {
        repository().getOrCreatePassphrase(databaseExists = false)
        wrapper.unwrapFailure = AEADBadTagException("tampered")

        val error = assertThrows(DatabaseKeyException::class.java) {
            repository().getOrCreatePassphrase(databaseExists = true)
        }
        assertThat(error).hasCauseThat().isInstanceOf(AEADBadTagException::class.java)
    }

    @Test
    fun `a keystore hardware failure is reported`() {
        repository().getOrCreatePassphrase(databaseExists = false)
        wrapper.unwrapFailure = ProviderException("keystore unavailable")

        assertThrows(DatabaseKeyException::class.java) {
            repository().getOrCreatePassphrase(databaseExists = true)
        }
    }

    @Test
    fun `a key of the wrong length is reported`() {
        repository().getOrCreatePassphrase(databaseExists = false)
        wrapper.unwrapResultOverride = ByteArray(16)

        assertThrows(DatabaseKeyException::class.java) {
            repository().getOrCreatePassphrase(databaseExists = true)
        }
    }

    @Test
    fun `no temporary file is left behind`() {
        repository().getOrCreatePassphrase(databaseExists = false)

        assertThat(folder.root.list()).asList().containsExactly("database.key")
    }

    @Test
    fun `missing parent folders are created`() {
        val nestedKeyFile = File(folder.root, "a/b/database.key")

        repository(nestedKeyFile).getOrCreatePassphrase(databaseExists = false)

        assertThat(nestedKeyFile.exists()).isTrue()
    }

    @Test
    fun `raw-key passphrase encodes every byte as two upper-case hex digits`() {
        val key = ByteArray(DatabaseKeyRepository.KEY_SIZE_BYTES) { index -> (index * 8).toByte() }

        val passphrase = String(DatabaseKeyRepository.toRawKeyPassphrase(key), Charsets.US_ASCII)

        assertThat(passphrase).isEqualTo(
            "x'" + key.joinToString("") { "%02X".format(it.toInt() and 0xFF) } + "'",
        )
    }

    /** Recovers the 32 key bytes from an `x'<hex>'` passphrase. */
    private fun rawKeyOf(passphrase: ByteArray): ByteArray {
        val hex = String(passphrase, Charsets.US_ASCII).removePrefix("x'").removeSuffix("'")
        return ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
}
