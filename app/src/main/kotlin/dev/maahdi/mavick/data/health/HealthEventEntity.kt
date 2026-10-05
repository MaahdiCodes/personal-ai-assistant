package dev.maahdi.mavick.data.health

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.Instant

/**
 * When Android connected or disconnected Mavick's notification listener. Times only, never
 * content. It shows whether a phone (HyperOS especially) keeps stopping message reading.
 */
@Entity(tableName = "health_event", indices = [Index("at")])
data class HealthEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: HealthEventType,
    val at: Instant,
)

/** Stored by name: never rename a constant. */
enum class HealthEventType { LISTENER_CONNECTED, LISTENER_DISCONNECTED }

@Dao
interface HealthEventDao {
    @Insert
    suspend fun insert(event: HealthEventEntity)

    @Query("SELECT COUNT(*) FROM health_event WHERE type = :type AND at >= :since")
    suspend fun count(type: HealthEventType, since: Instant): Int

    @Query("DELETE FROM health_event WHERE at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Instant): Int
}
