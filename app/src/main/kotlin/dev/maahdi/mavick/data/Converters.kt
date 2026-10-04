package dev.maahdi.mavick.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * How java.time values are stored. Dates and times use ISO-8601 text, which sorts correctly as
 * text (so SQL ORDER BY works); instants use epoch milliseconds.
 */
class Converters {
    @TypeConverter
    fun localDateToText(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun textToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToText(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun textToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun localDateTimeToText(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun textToLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
