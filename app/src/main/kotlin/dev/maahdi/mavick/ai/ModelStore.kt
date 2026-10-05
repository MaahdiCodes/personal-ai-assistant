package dev.maahdi.mavick.ai

import dev.maahdi.mavick.data.toHex
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties

/** The imported model file: its original name, size, SHA-256 checksum and when it was imported. */
data class ModelInfo(val name: String, val sizeBytes: Long, val sha256: String, val importedAt: Instant)

/** Why a file wasn't imported. */
enum class ImportProblem {
    /** It doesn't start like a LiteRT-LM model (.litertlm). */
    NOT_A_MODEL,

    /** Far smaller than any language model: probably the wrong file. */
    TOO_SMALL,
    NO_SPACE,

    /** Fewer bytes arrived than the file has. */
    INCOMPLETE,

    /** Reading the copy back gave a different checksum: the phone's storage damaged it. */
    COPY_DAMAGED,
    READ_FAILED,
}

sealed interface ImportResult {
    data class Imported(val info: ModelInfo) : ImportResult

    data class Rejected(val problem: ImportProblem) : ImportResult
}

/**
 * Keeps the one AI model Mavick uses, in its private storage (docs/PLAN.md §5.3, Model install).
 * The phone has no internet for Mavick, so the model arrives as a file: downloaded on the PC, copied
 * to the phone's Download folder by scripts/push-model.ps1, and picked in Settings.
 *
 * An import is checked on the way in, and only a complete, verified copy replaces the current
 * model. The caller closes any loaded model first (ModelHost.whileClosed).
 */
class ModelStore(
    private val directory: File,
    /**
     * Bytes Mavick may still write, counting cached files Android would clear to make room
     * (StorageManager.getAllocatableBytes on the phone). May throw an IOException.
     */
    private val freeBytes: () -> Long,
    /** Smaller files are refused; tests lower it. */
    private val minModelBytes: Long = MIN_MODEL_BYTES,
) {
    val modelFile: File get() = File(directory, MODEL_FILE_NAME)
    private val infoFile: File get() = File(directory, INFO_FILE_NAME)
    private val partFile: File get() = File(directory, "$MODEL_FILE_NAME.part")

    fun hasModel(): Boolean = info() != null

    /** The imported model, or null if there is none (or its record is damaged). */
    fun info(): ModelInfo? {
        if (!modelFile.isFile || !infoFile.isFile) return null
        return try {
            val properties = Properties().apply { infoFile.inputStream().use(::load) }
            ModelInfo(
                name = properties.getProperty(KEY_NAME) ?: return null,
                sizeBytes = properties.getProperty(KEY_SIZE)?.toLongOrNull() ?: return null,
                sha256 = properties.getProperty(KEY_SHA256) ?: return null,
                importedAt = properties.getProperty(KEY_IMPORTED_AT)?.toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null,
            ).takeIf { it.sizeBytes == modelFile.length() }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Copies a model from [source] (closed by the caller), checking that it is a LiteRT-LM model,
     * that it fits, that it arrived whole ([expectedSize], when known) and, by reading the copy
     * back, that the copy matches what was read. [onProgress] gets the bytes copied so far.
     */
    fun import(source: InputStream, name: String, expectedSize: Long?, now: Instant, onProgress: (Long) -> Unit = {}): ImportResult {
        directory.mkdirs()
        partFile.delete()
        val result = try {
            if (expectedSize != null && freeOrUnknown() < expectedSize + FREE_SPACE_MARGIN) {
                ImportResult.Rejected(ImportProblem.NO_SPACE)
            } else {
                copyAndCheck(source, expectedSize, onProgress, now, name)
            }
        } catch (e: IOException) {
            // A full phone shows up as a failed write.
            ImportResult.Rejected(if (freeOrUnknown() < FREE_SPACE_MARGIN) ImportProblem.NO_SPACE else ImportProblem.READ_FAILED)
        }
        if (result !is ImportResult.Imported) partFile.delete()
        return result
    }

    /** When the phone can't say, the copy goes ahead and a full phone shows up as a failed write. */
    private fun freeOrUnknown(): Long = try {
        freeBytes()
    } catch (e: IOException) {
        Long.MAX_VALUE
    }

    /** Deletes the model and its record. */
    fun remove() {
        infoFile.delete()
        modelFile.delete()
        partFile.delete()
    }

    private fun copyAndCheck(source: InputStream, expectedSize: Long?, onProgress: (Long) -> Unit, now: Instant, name: String): ImportResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        DigestInputStream(source.buffered(BUFFER_SIZE), digest).use { input ->
            partFile.outputStream().buffered(BUFFER_SIZE).use { output ->
                val header = input.readNBytes(MAGIC.size)
                if (!header.contentEquals(MAGIC)) return ImportResult.Rejected(ImportProblem.NOT_A_MODEL)
                output.write(header)
                copied = header.size.toLong()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (copied % PROGRESS_STEP < read) onProgress(copied)
                }
            }
        }
        onProgress(copied)
        if (expectedSize != null && copied != expectedSize) return ImportResult.Rejected(ImportProblem.INCOMPLETE)
        if (copied < minModelBytes) return ImportResult.Rejected(ImportProblem.TOO_SMALL)
        val sha256 = digest.digest().toHex()
        if (checksumOf(partFile) != sha256) return ImportResult.Rejected(ImportProblem.COPY_DAMAGED)

        val info = ModelInfo(name.trim().ifEmpty { MODEL_FILE_NAME }, copied, sha256, now)
        infoFile.delete()
        Files.move(partFile.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        writeInfo(info)
        return ImportResult.Imported(info)
    }

    private fun checksumOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream().buffered(BUFFER_SIZE), digest).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (input.read(buffer) >= 0) Unit
        }
        return digest.digest().toHex()
    }

    /** Written beside, then moved into place, so a damaged record can't describe a model. */
    private fun writeInfo(info: ModelInfo) {
        val properties = Properties().apply {
            setProperty(KEY_NAME, info.name)
            setProperty(KEY_SIZE, info.sizeBytes.toString())
            setProperty(KEY_SHA256, info.sha256)
            setProperty(KEY_IMPORTED_AT, info.importedAt.toEpochMilli().toString())
        }
        val temporary = File(directory, "$INFO_FILE_NAME.part")
        temporary.outputStream().use { properties.store(it, null) }
        Files.move(temporary.toPath(), infoFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val MODEL_FILE_NAME = "model.litertlm"
        const val INFO_FILE_NAME = "model.properties"

        /** Every LiteRT-LM model file starts with these 8 bytes. */
        val MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)

        /** Gemma 3 1B is about 557 MB; nothing usable is under 10 MB. */
        const val MIN_MODEL_BYTES = 10L * 1024 * 1024

        /** Room left for the phone after the copy: the copy is checked before the old model goes. */
        const val FREE_SPACE_MARGIN = 200L * 1024 * 1024

        private const val BUFFER_SIZE = 1024 * 1024
        private const val PROGRESS_STEP = 8L * 1024 * 1024
        private const val KEY_NAME = "name"
        private const val KEY_SIZE = "size"
        private const val KEY_SHA256 = "sha256"
        private const val KEY_IMPORTED_AT = "importedAt"
    }
}
