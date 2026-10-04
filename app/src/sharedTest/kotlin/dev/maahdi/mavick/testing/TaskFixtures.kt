package dev.maahdi.mavick.testing

import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.data.task.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** A fixed moment, so tests never depend on the real clock. Whole milliseconds, as stored. */
val TEST_NOW: Instant = Instant.parse("2026-10-05T08:00:00Z")

fun task(
    id: String = UUID.randomUUID().toString(),
    title: String = "Test task",
    dueDate: LocalDate? = null,
    dueTime: LocalTime? = null,
    status: TaskStatus = TaskStatus.OPEN,
    createdAt: Instant = TEST_NOW,
    deletedAt: Instant? = null,
): TaskEntity = TaskEntity(
    id = id,
    title = title,
    dueDate = dueDate,
    dueTime = dueTime,
    status = status,
    createdAt = createdAt,
    updatedAt = createdAt,
    deletedAt = deletedAt,
)
