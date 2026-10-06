package dev.maahdi.mavick.data.calendar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CalendarLinkDao {
    @Upsert
    suspend fun upsert(link: CalendarLinkEntity)

    @Query("SELECT * FROM calendar_link WHERE taskId = :taskId")
    suspend fun find(taskId: String): CalendarLinkEntity?

    @Query("SELECT * FROM calendar_link")
    suspend fun all(): List<CalendarLinkEntity>

    @Query("DELETE FROM calendar_link WHERE taskId = :taskId")
    suspend fun delete(taskId: String)

    @Query("SELECT COUNT(*) FROM calendar_link")
    suspend fun count(): Int
}
