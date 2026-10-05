package dev.maahdi.mavick.ai

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.toHex
import dev.maahdi.mavick.testing.TEST_MIN_MODEL_BYTES
import dev.maahdi.mavick.testing.TEST_NOW
import dev.maahdi.mavick.testing.fakeModelBytes
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory: File get() = File(folder.root, "models")

    /** What the phone says is free (counting clearable cache). */
    private var free = Long.MAX_VALUE
    private val store by lazy { ModelStore(directory, freeBytes = { free }, minModelBytes = TEST_MIN_MODEL_BYTES) }

    private fun import(bytes: ByteArray, name: String = "gemma.litertlm", expectedSize: Long? = bytes.size.toLong()) =
        store.import(bytes.inputStream(), name, expectedSize, TEST_NOW)

    @Test
    fun `a model is copied in, checked, and described`() {
        val bytes = fakeModelBytes(5_000)
        val progress = mutableListOf<Long>()

        val result = store.import(bytes.inputStream(), "gemma.litertlm", bytes.size.toLong(), TEST_NOW) { progress += it }

        val expected = ModelInfo("gemma.litertlm", 5_000, MessageDigest.getInstance("SHA-256").digest(bytes).toHex(), TEST_NOW)
        assertThat(result).isEqualTo(ImportResult.Imported(expected))
        assertThat(store.info()).isEqualTo(expected)
        assertThat(store.hasModel()).isTrue()
        assertThat(store.modelFile.readBytes()).isEqualTo(bytes)
        assertThat(progress.last()).isEqualTo(5_000)
        assertThat(directory.listFiles()!!.map { it.name }).containsExactly(ModelStore.MODEL_FILE_NAME, ModelStore.INFO_FILE_NAME)
    }

    @Test
    fun `a file that isn't a LiteRT-LM model is refused at once`() {
        val notAModel = "PK\u0003\u0004 a zip file".toByteArray() + ByteArray(5_000)

        assertThat(import(notAModel)).isEqualTo(ImportResult.Rejected(ImportProblem.NOT_A_MODEL))
        assertThat(store.hasModel()).isFalse()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
    }

    @Test
    fun `an empty file, or one too small for a model, is refused`() {
        assertThat(import(ByteArray(0))).isEqualTo(ImportResult.Rejected(ImportProblem.NOT_A_MODEL))
        assertThat(import(fakeModelBytes(512))).isEqualTo(ImportResult.Rejected(ImportProblem.TOO_SMALL))
        assertThat(store.hasModel()).isFalse()
    }

    @Test
    fun `a file that arrives incomplete is refused`() {
        val bytes = fakeModelBytes(5_000)

        assertThat(import(bytes, expectedSize = 6_000)).isEqualTo(ImportResult.Rejected(ImportProblem.INCOMPLETE))
        assertThat(store.hasModel()).isFalse()
    }

    @Test
    fun `a read that fails part way is refused, and leaves no part file`() {
        val failing = object : InputStream() {
            private var left = 3_000
            override fun read(): Int {
                if (left-- <= 0) throw IOException("cable pulled")
                return if (3_000 - left <= ModelStore.MAGIC.size) ModelStore.MAGIC[2_999 - left].toInt() else 7
            }
        }

        val result = store.import(failing, "gemma.litertlm", null, TEST_NOW)

        assertThat(result).isEqualTo(ImportResult.Rejected(ImportProblem.READ_FAILED))
        assertThat(directory.listFiles()!!.toList()).isEmpty()
    }

    @Test
    fun `a model that wouldn't leave the phone room to spare is refused before copying`() {
        free = 5_000 + ModelStore.FREE_SPACE_MARGIN - 1

        assertThat(import(fakeModelBytes(5_000))).isEqualTo(ImportResult.Rejected(ImportProblem.NO_SPACE))
        assertThat(directory.listFiles()!!.toList()).isEmpty()

        free = 5_000 + ModelStore.FREE_SPACE_MARGIN
        assertThat(import(fakeModelBytes(5_000))).isInstanceOf(ImportResult.Imported::class.java)
    }

    @Test
    fun `a write that fails on a full phone says the phone is full`() {
        val failing = object : InputStream() {
            private var sent = 0
            override fun read(): Int {
                if (sent >= 2_000) {
                    free = 0 // the phone filled up meanwhile
                    throw IOException("ENOSPC")
                }
                return if (sent < ModelStore.MAGIC.size) ModelStore.MAGIC[sent++].toInt() else 7.also { sent++ }
            }
        }

        assertThat(store.import(failing, "gemma.litertlm", null, TEST_NOW)).isEqualTo(ImportResult.Rejected(ImportProblem.NO_SPACE))
    }

    @Test
    fun `when the phone can't say how much is free, the copy goes ahead, and a failed read is just a failed read`() {
        val unknownSpace = ModelStore(directory, freeBytes = { throw IOException("no volume") }, minModelBytes = TEST_MIN_MODEL_BYTES)
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("cable pulled")
        }

        assertThat(unknownSpace.import(failing, "gemma.litertlm", 5_000, TEST_NOW)).isEqualTo(ImportResult.Rejected(ImportProblem.READ_FAILED))
        val bytes = fakeModelBytes(5_000)
        assertThat(unknownSpace.import(bytes.inputStream(), "gemma.litertlm", 5_000, TEST_NOW)).isInstanceOf(ImportResult.Imported::class.java)
    }

    @Test
    fun `a new model replaces the old one only once it checks out`() {
        val first = fakeModelBytes(4_000)
        import(first, name = "first.litertlm")

        import(ByteArray(4_000), name = "broken.litertlm")
        assertThat(store.info()!!.name).isEqualTo("first.litertlm")
        assertThat(store.modelFile.readBytes()).isEqualTo(first)

        val second = fakeModelBytes(6_000)
        import(second, name = "second.litertlm")
        assertThat(store.info()!!.name).isEqualTo("second.litertlm")
        assertThat(store.modelFile.readBytes()).isEqualTo(second)
    }

    @Test
    fun `a missing or damaged record means no model`() {
        import(fakeModelBytes())

        File(directory, ModelStore.INFO_FILE_NAME).writeText("name=gemma\nsize=not a number\n")
        assertThat(store.info()).isNull()

        File(directory, ModelStore.INFO_FILE_NAME).delete()
        assertThat(store.hasModel()).isFalse()
    }

    @Test
    fun `a model file that changed size since import doesn't count`() {
        import(fakeModelBytes())

        store.modelFile.appendBytes(ByteArray(10))

        assertThat(store.info()).isNull()
    }

    @Test
    fun `removing deletes the model and its record`() {
        import(fakeModelBytes())

        store.remove()

        assertThat(store.hasModel()).isFalse()
        assertThat(directory.listFiles()!!.toList()).isEmpty()
    }

    @Test
    fun `a blank name is replaced`() {
        import(fakeModelBytes(), name = "  ")

        assertThat(store.info()!!.name).isEqualTo(ModelStore.MODEL_FILE_NAME)
    }
}
