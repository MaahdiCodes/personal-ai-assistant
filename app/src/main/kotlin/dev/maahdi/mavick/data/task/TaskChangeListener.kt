package dev.maahdi.mavick.data.task

/**
 * Something that follows the tasks: the calendar events written for them, the home-screen widget.
 * [TaskRepository] tells each one after a change is saved.
 */
interface TaskChangeListener {
    /**
     * The task [taskId] was created, changed, finished, deleted or put back. Look at the task as it is
     * now: the call may arrive late, or twice. A failure here never undoes the change.
     */
    suspend fun taskChanged(taskId: String)
}
