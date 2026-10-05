package dev.maahdi.mavick.reminders

/**
 * Work that rides on Mavick's existing alarms instead of a background job of its own
 * (docs/PLAN.md §0.7): deleting old messages and checking that message reading still works.
 */
interface DailyChores {
    /** Cheap clean-up that is fine at any time, including right after a restart. */
    suspend fun cleanUp()

    /** Once a day, at the briefing time: checks whose warnings shouldn't come at night. */
    suspend fun daily()

    companion object {
        val NONE = object : DailyChores {
            override suspend fun cleanUp() = Unit

            override suspend fun daily() = Unit
        }
    }
}
