package dev.maahdi.mavick.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLockTest {
    private var enabled = true
    private var now = 0L
    private val lock = AppLock(isEnabled = { enabled }, elapsedRealtime = { now }, timeoutMillis = FIVE_MINUTES)

    @Test
    fun `locks the first time Mavick is opened`() {
        lock.onScreenShown(canAuthenticate = true)

        assertThat(lock.locked.value).isTrue()
    }

    @Test
    fun `stays open after a short trip away`() {
        lock.onScreenShown(canAuthenticate = true)
        lock.onUnlocked()

        lock.onScreenHidden()
        now += FIVE_MINUTES - 1
        lock.onScreenShown(canAuthenticate = true)

        assertThat(lock.locked.value).isFalse()
    }

    @Test
    fun `locks again after five minutes away`() {
        lock.onScreenShown(canAuthenticate = true)
        lock.onUnlocked()

        lock.onScreenHidden()
        now += FIVE_MINUTES
        lock.onScreenShown(canAuthenticate = true)

        assertThat(lock.locked.value).isTrue()
    }

    @Test
    fun `stays locked until unlocked, however soon it is shown again`() {
        lock.onScreenShown(canAuthenticate = true)
        lock.onScreenHidden()
        now += 1_000
        lock.onScreenShown(canAuthenticate = true)

        assertThat(lock.locked.value).isTrue()
    }

    @Test
    fun `never locks when app lock is off`() {
        enabled = false

        lock.onScreenShown(canAuthenticate = true)

        assertThat(lock.locked.value).isFalse()
    }

    @Test
    fun `never locks a phone that has no screen lock, since nothing could unlock it`() {
        lock.onScreenShown(canAuthenticate = false)

        assertThat(lock.locked.value).isFalse()
    }

    @Test
    fun `turning app lock on takes effect the next time Mavick opens`() {
        enabled = false
        lock.onScreenShown(canAuthenticate = true)
        enabled = true

        lock.onScreenHidden()
        now += 1_000
        lock.onScreenShown(canAuthenticate = true)

        assertThat(lock.locked.value).isTrue()
    }

    private companion object {
        const val FIVE_MINUTES = 5 * 60 * 1000L
    }
}
