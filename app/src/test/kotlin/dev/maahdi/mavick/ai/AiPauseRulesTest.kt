package dev.maahdi.mavick.ai

import android.os.PowerManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiPauseRulesTest {
    private fun reason(
        powerSave: Boolean = false,
        thermal: Int = PowerManager.THERMAL_STATUS_NONE,
        battery: Int = 80,
        charging: Boolean = false,
    ) = AiPauseRules.reason(powerSave, thermal, battery, charging)

    @Test
    fun `a cool phone with battery to spare may work`() {
        assertThat(reason()).isNull()
        assertThat(reason(thermal = PowerManager.THERMAL_STATUS_LIGHT)).isNull()
    }

    @Test
    fun `Battery Saver pauses the AI, even while charging`() {
        assertThat(reason(powerSave = true, charging = true)).isEqualTo(AiPause.BATTERY_SAVER)
    }

    @Test
    fun `a warm phone pauses the AI, from moderate upwards`() {
        assertThat(reason(thermal = PowerManager.THERMAL_STATUS_MODERATE)).isEqualTo(AiPause.HOT)
        assertThat(reason(thermal = PowerManager.THERMAL_STATUS_SEVERE, charging = true)).isEqualTo(AiPause.HOT)
    }

    @Test
    fun `a low battery pauses the AI unless it is charging`() {
        assertThat(reason(battery = 19)).isEqualTo(AiPause.BATTERY_LOW)
        assertThat(reason(battery = 20)).isNull()
        assertThat(reason(battery = 5, charging = true)).isNull()
    }

    @Test
    fun `a phone that doesn't report its battery level isn't held back by it`() {
        assertThat(reason(battery = Int.MIN_VALUE)).isNull()
    }
}
