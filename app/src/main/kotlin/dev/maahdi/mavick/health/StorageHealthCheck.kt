package dev.maahdi.mavick.health

import dev.maahdi.mavick.data.security.DatabaseKeyException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface StorageStatus {
    data object Checking : StorageStatus

    data class Ready(val taskCount: Int) : StorageStatus

    data class Failed(val reason: String) : StorageStatus
}

/**
 * Confirms the encrypted database opens and answers a query. Shown on the home screen, so a
 * problem on either phone is visible straight away.
 */
class StorageHealthCheck(
    private val countTasks: suspend () -> Int,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun run(): StorageStatus = withContext(ioDispatcher) {
        try {
            StorageStatus.Ready(countTasks())
        } catch (e: CancellationException) {
            throw e
        } catch (e: DatabaseKeyException) {
            StorageStatus.Failed(e.message ?: "The database key could not be read.")
        } catch (e: Exception) {
            StorageStatus.Failed("The encrypted database could not be opened (${e.javaClass.simpleName}).")
        } catch (e: UnsatisfiedLinkError) {
            StorageStatus.Failed("The encryption library is missing from this build.")
        }
    }
}
