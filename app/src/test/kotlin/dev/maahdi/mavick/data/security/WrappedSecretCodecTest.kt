package dev.maahdi.mavick.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class WrappedSecretCodecTest {
    @Test
    fun `encode then decode returns the same iv and ciphertext`() {
        val original = WrappedSecret(iv = byteArrayOf(1, 2, 3), ciphertext = byteArrayOf(4, 5, 6, 7))

        val decoded = WrappedSecretCodec.decode(WrappedSecretCodec.encode(original))

        assertThat(decoded.iv).isEqualTo(original.iv)
        assertThat(decoded.ciphertext).isEqualTo(original.ciphertext)
    }

    @Test
    fun `unknown format version is rejected`() {
        val bytes = WrappedSecretCodec.encode(WrappedSecret(byteArrayOf(1), byteArrayOf(2)))
        bytes[0] = 2

        assertThrows(IllegalArgumentException::class.java) { WrappedSecretCodec.decode(bytes) }
    }

    @Test
    fun `data without ciphertext is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WrappedSecretCodec.decode(byteArrayOf(1, 2, 9, 9))
        }
    }

    @Test
    fun `zero-length iv is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WrappedSecretCodec.decode(byteArrayOf(1, 0, 9))
        }
    }

    @Test
    fun `iv longer than allowed is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WrappedSecretCodec.decode(byteArrayOf(1, 33) + ByteArray(40))
        }
    }

    @Test
    fun `too-short data is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { WrappedSecretCodec.decode(byteArrayOf(1)) }
    }

    @Test
    fun `encoding refuses an empty iv`() {
        assertThrows(IllegalArgumentException::class.java) {
            WrappedSecretCodec.encode(WrappedSecret(iv = ByteArray(0), ciphertext = byteArrayOf(1)))
        }
    }
}
