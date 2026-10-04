package dev.maahdi.mavick.health

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.security.DatabaseKeyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class StorageHealthCheckTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private fun check(countTasks: suspend () -> Int) = StorageHealthCheck(countTasks, ioDispatcher = dispatcher)

    @Test
    fun `working database reports ready with the task count`() = scope.runTest {
        assertThat(check { 3 }.run()).isEqualTo(StorageStatus.Ready(taskCount = 3))
    }

    @Test
    fun `key problem reports its own explanation`() = scope.runTest {
        val status = check { throw DatabaseKeyException("The database key file is damaged.") }.run()

        assertThat(status).isEqualTo(StorageStatus.Failed("The database key file is damaged."))
    }

    @Test
    fun `other failures report the error type`() = scope.runTest {
        val status = check { throw IllegalStateException("boom") }.run()

        assertThat(status).isInstanceOf(StorageStatus.Failed::class.java)
        assertThat((status as StorageStatus.Failed).reason).contains("IllegalStateException")
    }

    @Test
    fun `missing native encryption library is reported, not crashed on`() = scope.runTest {
        val status = check { throw UnsatisfiedLinkError("no sqlcipher") }.run()

        assertThat(status).isEqualTo(StorageStatus.Failed("The encryption library is missing from this build."))
    }

    @Test
    fun `cancellation is not swallowed`() {
        assertThrows(CancellationException::class.java) {
            scope.runTest { check { throw CancellationException("screen closed") }.run() }
        }
    }
}
