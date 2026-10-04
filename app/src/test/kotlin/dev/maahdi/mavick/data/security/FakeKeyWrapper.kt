package dev.maahdi.mavick.data.security

/**
 * Stands in for the Android Keystore in JVM tests. It "encrypts" by XOR-ing with a fixed byte,
 * which is enough to prove the stored file never contains the plain key.
 */
class FakeKeyWrapper : KeyWrapper {
    var wrapCalls = 0
        private set

    /** When set, [unwrap] throws this instead of decrypting. */
    var unwrapFailure: Throwable? = null

    /** When set, [unwrap] returns a copy of this instead of decrypting. */
    var unwrapResultOverride: ByteArray? = null

    override fun wrap(plaintext: ByteArray): WrappedSecret {
        wrapCalls++
        return WrappedSecret(iv = ByteArray(IV_SIZE) { 7 }, ciphertext = plaintext.xorMask())
    }

    override fun unwrap(wrapped: WrappedSecret): ByteArray {
        unwrapFailure?.let { throw it }
        unwrapResultOverride?.let { return it.copyOf() }
        return wrapped.ciphertext.xorMask()
    }

    private fun ByteArray.xorMask() = ByteArray(size) { index -> (this[index].toInt() xor MASK).toByte() }

    private companion object {
        const val IV_SIZE = 12
        const val MASK = 0x5A
    }
}
