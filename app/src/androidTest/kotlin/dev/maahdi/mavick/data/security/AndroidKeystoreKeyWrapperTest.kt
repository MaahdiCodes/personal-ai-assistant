package dev.maahdi.mavick.data.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.security.GeneralSecurityException
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone against the real Android Keystore. */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreKeyWrapperTest {
    private val wrapper = AndroidKeystoreKeyWrapper(KEY_ALIAS)
    private val secret = ByteArray(32) { index -> index.toByte() }

    @After
    fun tearDown() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.apply {
            deleteEntry(KEY_ALIAS)
            deleteEntry(NEVER_CREATED_ALIAS)
        }
    }

    @Test
    fun unwrapReturnsTheOriginalSecret() {
        assertThat(wrapper.unwrap(wrapper.wrap(secret))).isEqualTo(secret)
    }

    @Test
    fun ciphertextDoesNotContainTheSecret() {
        assertThat(wrapper.wrap(secret).ciphertext.copyOf(secret.size)).isNotEqualTo(secret)
    }

    @Test
    fun everyWrapUsesAFreshIv() {
        val first = wrapper.wrap(secret)
        val second = wrapper.wrap(secret)

        assertThat(first.iv).isNotEqualTo(second.iv)
        assertThat(first.ciphertext).isNotEqualTo(second.ciphertext)
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val wrapped = wrapper.wrap(secret)
        val tampered = wrapped.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            wrapper.unwrap(WrappedSecret(wrapped.iv, tampered))
        }
    }

    @Test
    fun unwrappingWithoutTheKeystoreKeyFails() {
        val wrapped = wrapper.wrap(secret)

        assertThrows(GeneralSecurityException::class.java) {
            AndroidKeystoreKeyWrapper(NEVER_CREATED_ALIAS).unwrap(wrapped)
        }
    }

    private companion object {
        const val KEY_ALIAS = "mavick.test.key-wrapper"
        const val NEVER_CREATED_ALIAS = "mavick.test.never-created"
    }
}
