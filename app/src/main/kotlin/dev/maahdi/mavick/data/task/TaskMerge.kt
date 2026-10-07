package dev.maahdi.mavick.data.task

/** What merging another list of tasks into this one will do. [toWrite] are the tasks that change here. */
data class MergePlan(
    val toWrite: List<TaskEntity>,
    /** Tasks this phone didn't have (deleted ones, which nobody sees, are counted apart in [addedDeleted]). */
    val added: Int,
    /** Tasks this phone had, replaced by a newer version. */
    val updated: Int,
    /** Tasks where this phone's version is newer, so it is kept. */
    val keptNewer: Int,
    /** Tasks already identical. */
    val unchanged: Int,
    /** Deleted tasks this phone didn't have, kept so an old copy elsewhere can't bring them back. */
    val addedDeleted: Int = 0,
)

/**
 * Merging two copies of the same tasks (a backup restored, and later the other phone's list):
 * by task ID, the version edited last wins (docs/PLAN.md §5.7 A). The answer never depends on which
 * copy is "this phone's": merging A into B and B into A ends with the same tasks, so two phones
 * always agree. A deleted task is kept as a deleted version, so deleting something wins over an
 * older copy instead of being undone by it.
 */
object TaskMerge {
    /** The version to keep of two copies of one task. */
    fun winner(a: TaskEntity, b: TaskEntity): TaskEntity = when {
        a.updatedAt != b.updatedAt -> if (a.updatedAt > b.updatedAt) a else b
        // Same moment: a deletion beats an edit, so a delete is never undone by a tie.
        (a.deletedAt != null) != (b.deletedAt != null) -> if (a.deletedAt != null) a else b
        // Still the same: pick by content, so the choice doesn't depend on the order.
        else -> if (contentKey(b) > contentKey(a)) b else a
    }

    fun plan(local: Collection<TaskEntity>, incoming: Collection<TaskEntity>): MergePlan {
        val here = local.associateBy { it.id }
        val toWrite = mutableListOf<TaskEntity>()
        var added = 0
        var updated = 0
        var keptNewer = 0
        var unchanged = 0
        var addedDeleted = 0
        // If the incoming list repeats an ID, its own newest copy is the one that counts.
        for (task in incoming.groupBy { it.id }.values.map { copies -> copies.reduce(::winner) }) {
            val mine = here[task.id]
            when {
                mine == null -> {
                    toWrite += task
                    if (task.deletedAt == null) added++ else addedDeleted++
                }
                mine == task -> unchanged++
                winner(mine, task) == mine -> keptNewer++
                else -> {
                    toWrite += task
                    updated++
                }
            }
        }
        return MergePlan(toWrite, added, updated, keptNewer, unchanged, addedDeleted)
    }

    /** Every field, as text, to compare two versions with the same time and state. */
    private fun contentKey(task: TaskEntity): String = listOf(
        task.title,
        task.notes,
        task.dueDate,
        task.dueTime,
        task.remindAt,
        task.priority.name,
        task.status.name,
        task.source.name,
        task.sourceExcerpt,
        task.createdAt.toEpochMilli(),
        task.deletedAt?.toEpochMilli(),
        task.reminderTime,
        task.repeatRule?.toStorageString(),
        task.completedAt?.toEpochMilli(),
    ).joinToString("\u0000") { it?.toString() ?: "\u0001" }
}
