package dev.maahdi.mavick.testing

import dev.maahdi.mavick.ai.GenerationRequest
import dev.maahdi.mavick.ai.ImportResult
import dev.maahdi.mavick.ai.LanguageModel
import dev.maahdi.mavick.ai.ModelStore
import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.data.task.TaskEntity
import dev.maahdi.mavick.reminders.DailyChores
import dev.maahdi.mavick.reminders.Notifier
import dev.maahdi.mavick.reminders.ReminderScheduler
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** The test time zone: no daylight saving, so local times map to instants one-to-one. */
val TEST_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

/** Monday 5 October 2026, 10:00 — the "now" most tests start from. */
val MONDAY_10AM: LocalDateTime = LocalDateTime.of(2026, 10, 5, 10, 0)

/** A clock tests can set and move forward. */
class MutableClock(private var now: Instant, private val zone: ZoneId = TEST_ZONE) : Clock() {
    constructor(local: LocalDateTime, zone: ZoneId = TEST_ZONE) : this(local.atZone(zone).toInstant(), zone)

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now

    fun setLocal(local: LocalDateTime) {
        now = local.atZone(zone).toInstant()
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** Records alarms instead of setting real ones. */
class FakeReminderScheduler : ReminderScheduler {
    val alarms = mutableMapOf<String, LocalDateTime>()
    var dailyAt: LocalDateTime? = null
        private set

    override fun schedule(taskId: String, at: LocalDateTime) {
        alarms[taskId] = at
    }

    override fun cancel(taskId: String) {
        alarms.remove(taskId)
    }

    override fun scheduleDaily(at: LocalDateTime) {
        dailyAt = at
    }
}

/** Counts the daily chores instead of doing them; [failCleanUp] makes the clean-up throw. */
class FakeDailyChores(private val failCleanUp: Boolean = false) : DailyChores {
    var cleanUps = 0
        private set
    var dailies = 0
        private set

    override suspend fun cleanUp() {
        cleanUps++
        if (failCleanUp) throw IllegalStateException("clean-up failed")
    }

    override suspend fun daily() {
        dailies++
    }
}

/** Runs ViewModels' coroutines (Dispatchers.Main) at once, on the test thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Bytes that pass ModelStore's checks (the LiteRT-LM header, then filler), at a test size. */
fun fakeModelBytes(size: Int = 4_096): ByteArray = ModelStore.MAGIC + ByteArray(size - ModelStore.MAGIC.size) { (it % 251).toByte() }

/** The smallest "model" the test stores accept, so tests don't copy hundreds of megabytes. */
const val TEST_MIN_MODEL_BYTES = 1_024L

/** A model store in [directory] that takes small test models; with [withModel], one is imported. */
fun testModelStore(directory: File, withModel: Boolean = true, now: Instant = TEST_NOW): ModelStore =
    ModelStore(directory, freeBytes = { Long.MAX_VALUE }, minModelBytes = TEST_MIN_MODEL_BYTES).also { store ->
        if (withModel) check(store.import(fakeModelBytes().inputStream(), "test.litertlm", null, now) is ImportResult.Imported)
    }

/** A clock for timing that only moves when told to. */
class FakeElapsed(var millis: Long = 1_000_000) : () -> Long {
    override fun invoke(): Long = millis
}

/** An AI model that gives prepared answers, in order, and records what it was asked. */
class FakeLanguageModel(vararg replies: String) : LanguageModel {
    private val replies = ArrayDeque(replies.toList())
    val requests = mutableListOf<GenerationRequest>()

    /** Thrown by the next [generate] instead of answering. */
    var failure: Exception? = null
    var closed = false
        private set

    override fun generate(request: GenerationRequest): String {
        check(!closed) { "The model was used after closing" }
        requests += request
        failure?.let { throw it }
        return replies.removeFirstOrNull() ?: error("No prepared answer left")
    }

    fun answer(vararg more: String) {
        replies.addAll(more)
    }

    override fun close() {
        closed = true
    }
}

/** Records notifications instead of showing them. */
class FakeNotifier : Notifier {
    data class Shown(val taskId: String, val title: String, val missed: Boolean)

    val reminders = mutableListOf<Shown>()
    val cancelled = mutableListOf<String>()
    val briefings = mutableListOf<Briefing>()

    override fun showReminder(task: TaskEntity, missed: Boolean) {
        reminders += Shown(task.id, task.title, missed)
    }

    override fun cancelReminder(taskId: String) {
        cancelled += taskId
    }

    override fun showBriefing(briefing: Briefing) {
        briefings += briefing
    }
}
