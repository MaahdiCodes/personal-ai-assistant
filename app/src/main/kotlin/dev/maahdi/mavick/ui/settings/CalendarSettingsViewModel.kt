package dev.maahdi.mavick.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.maahdi.mavick.AppContainer
import dev.maahdi.mavick.calendar.CalendarAccessException
import dev.maahdi.mavick.calendar.CalendarGateway
import dev.maahdi.mavick.calendar.CalendarSync
import dev.maahdi.mavick.calendar.DeviceCalendar
import dev.maahdi.mavick.data.settings.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalendarSettingsState(
    val permissionGranted: Boolean = false,
    /** The calendars Mavick may write to; empty until loaded, and without the permission. */
    val calendars: List<DeviceCalendar> = emptyList(),
    val loaded: Boolean = false,
    /** How many tasks have an event in the calendar now. */
    val eventCount: Int = 0,
    /** The calendar picker is open. */
    val picking: Boolean = false,
    /** The user just refused the permission, so Settings says how to allow it. */
    val permissionDenied: Boolean = false,
    /** Events are being written or removed. */
    val working: Boolean = false,
)

/** Settings › Calendar: which calendar tasks go to, and whether the phone lets Mavick reach it. */
class CalendarSettingsViewModel(
    private val gateway: CalendarGateway,
    private val openSync: suspend () -> CalendarSync,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CalendarSettingsState())

    val state: StateFlow<CalendarSettingsState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    private var refreshing: Job? = null

    /**
     * Reads the permission, the calendars and the event count again (the user may have changed
     * them in Android's settings). A newer refresh replaces one still running, so an old, slow
     * answer can never overwrite a newer one.
     */
    fun refresh(): Job {
        refreshing?.cancel()
        return viewModelScope.launch {
            val granted = gateway.hasPermission()
            val calendars = if (granted) loadCalendars() else emptyList()
            val count = countEvents()
            mutableState.update {
                it.copy(
                    permissionGranted = granted,
                    calendars = calendars,
                    loaded = true,
                    eventCount = count,
                    working = false,
                )
            }
        }.also { refreshing = it }
    }

    /** Opens the picker, with the calendars looked up fresh. */
    fun openPicker(): Job {
        mutableState.update { it.copy(picking = true, permissionDenied = false) }
        return refresh()
    }

    fun closePicker() {
        mutableState.update { it.copy(picking = false) }
    }

    /** The answer to the permission prompt: on yes, on to choosing the calendar. */
    fun onPermissionResult(granted: Boolean): Job {
        if (granted) return openPicker()
        mutableState.update { it.copy(permissionDenied = true) }
        return refresh()
    }

    /** Switches the feature on for [calendar] (or moves it there) and writes the events. */
    fun choose(calendar: DeviceCalendar): Job = viewModelScope.launch {
        mutableState.update { it.copy(picking = false, working = true, permissionDenied = false) }
        settings.update { it.copy(calendarEnabled = true, calendarId = calendar.id, calendarName = calendar.label) }
        reconcile()
        refresh().join()
    }

    /** Switches the feature off and removes the events Mavick added. */
    fun turnOff(): Job = viewModelScope.launch {
        mutableState.update { it.copy(working = true) }
        settings.update { it.copy(calendarEnabled = false) }
        reconcile()
        refresh().join()
    }

    private suspend fun loadCalendars(): List<DeviceCalendar> = withContext(io) {
        try {
            gateway.writableCalendars()
        } catch (e: CalendarAccessException) {
            emptyList()
        }
    }

    private suspend fun countEvents(): Int = withSync(fallback = 0) { eventCount() }

    private suspend fun reconcile() = withSync(fallback = Unit) { reconcileAll() }

    /** Runs [block] on the calendar sync, or gives [fallback] if the database can't be opened (Health says why). */
    private suspend fun <T> withSync(fallback: T, block: suspend CalendarSync.() -> T): T = try {
        openSync().block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    } catch (e: LinkageError) {
        fallback // the encryption library failed to load
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                CalendarSettingsViewModel(container.calendarGateway, container::openCalendarSync, container.settings)
            }
        }
    }
}
