package dev.maahdi.mavick.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.data.task.Briefing
import dev.maahdi.mavick.data.task.TaskChangeListener
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the home-screen widget showing today's tasks (docs/PLAN.md §5.9). It does nothing, and
 * opens no database, while no widget is on the home screen. It runs when a task changes, when the
 * switch for titles changes, and on the existing wake-ups (app start, restart, time change, the daily
 * alarm): never on a timer, so the widget can be a day behind after midnight until the next of those.
 * The widget's header shows the date so that is visible.
 */
class WidgetUpdater(
    private val context: Context,
    private val briefing: suspend (LocalDate) -> Briefing,
    private val settings: SettingsRepository,
    private val clock: () -> Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** The widget to draw; a test uses a receiver of its own so that Mavick's real one isn't called. */
    private val provider: ComponentName = ComponentName(context, TasksWidgetProvider::class.java),
) : TaskChangeListener {
    private val renderer = TasksWidgetRenderer(context)

    /** One update at a time, each reading the newest tasks, so a late one can't leave old tasks showing. */
    private val lock = Mutex()

    override suspend fun taskChanged(taskId: String) = update()

    /** Redraws every widget of Mavick. Never throws. */
    suspend fun update() {
        try {
            withContext(dispatcher) { lock.withLock { redraw() } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget update failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun redraw() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(provider)
        if (ids.isEmpty()) return
        val date = LocalDate.now(clock())
        val plan = try {
            WidgetPlanner.plan(briefing(date), date, settings.current.widgetShowTitles)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The database can't be opened (the phone has not been unlocked since it started).
            Log.w(TAG, "Widget could not read the tasks: ${e.javaClass.simpleName}")
            null
        } catch (e: LinkageError) {
            null
        }
        manager.updateAppWidget(ids, renderer.render(plan, DateFormat.is24HourFormat(context)))
    }

    private companion object {
        const val TAG = "Mavick"
    }
}
