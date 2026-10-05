package dev.maahdi.mavick.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * A file picked in Android's file picker (an AI model, a Takeout export). Needs no storage
 * permission: the picker gives Mavick access to just this file.
 */
class PickedFile(
    val name: String,
    /** Null when the picker doesn't say. */
    val sizeBytes: Long?,
    private val openStream: () -> InputStream,
) {
    fun open(): InputStream = openStream()

    companion object {
        fun from(resolver: ContentResolver, uri: Uri): PickedFile {
            var name = uri.lastPathSegment.orEmpty()
            var size: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
            return PickedFile(name, size) { resolver.openInputStream(uri) ?: throw FileNotFoundException("No access to the picked file") }
        }

        /** Deletes a picked file, if its app allows it. Returns whether it is gone. */
        fun delete(resolver: ContentResolver, uri: Uri): Boolean = try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: FileNotFoundException) {
            true // already gone
        } catch (e: SecurityException) {
            false
        } catch (e: UnsupportedOperationException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
