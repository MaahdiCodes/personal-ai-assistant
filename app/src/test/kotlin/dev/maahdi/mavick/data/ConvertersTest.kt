package dev.maahdi.mavick.data

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `local date round-trips`() {
        val date = LocalDate.of(2026, 2, 28)
        assertThat(converters.textToLocalDate(converters.localDateToText(date))).isEqualTo(date)
    }

    @Test
    fun `local time round-trips, with and without seconds`() {
        listOf(LocalTime.of(9, 5), LocalTime.of(17, 0, 30)).forEach { time ->
            assertThat(converters.textToLocalTime(converters.localTimeToText(time))).isEqualTo(time)
        }
    }

    @Test
    fun `local date-time round-trips`() {
        val dateTime = LocalDateTime.of(2026, 12, 31, 23, 59)
        assertThat(converters.textToLocalDateTime(converters.localDateTimeToText(dateTime))).isEqualTo(dateTime)
    }

    @Test
    fun `instant round-trips at millisecond precision`() {
        val instant = Instant.parse("2026-10-05T08:15:30.123Z")
        assertThat(converters.epochMillisToInstant(converters.instantToEpochMillis(instant))).isEqualTo(instant)
    }

    @Test
    fun `nulls stay null`() {
        assertThat(converters.localDateToText(null)).isNull()
        assertThat(converters.textToLocalDate(null)).isNull()
        assertThat(converters.localTimeToText(null)).isNull()
        assertThat(converters.textToLocalTime(null)).isNull()
        assertThat(converters.localDateTimeToText(null)).isNull()
        assertThat(converters.textToLocalDateTime(null)).isNull()
        assertThat(converters.instantToEpochMillis(null)).isNull()
        assertThat(converters.epochMillisToInstant(null)).isNull()
    }

    @Test
    fun `stored dates sort as text in calendar order, so SQL ORDER BY is correct`() {
        val dates = listOf(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1))

        val sortedAsText = dates.map { converters.localDateToText(it)!! }.sorted()

        assertThat(sortedAsText).containsExactly("2026-09-30", "2026-10-01", "2027-01-01").inOrder()
    }

    @Test
    fun `stored times sort as text in clock order`() {
        val times = listOf(LocalTime.of(17, 0), LocalTime.of(9, 5), LocalTime.of(9, 5, 30), LocalTime.MIDNIGHT)

        val sortedAsText = times.map { converters.localTimeToText(it)!! }.sorted()

        assertThat(sortedAsText).containsExactly("00:00", "09:05", "09:05:30", "17:00").inOrder()
    }
}
