package dev.maahdi.mavick.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The "New task" tile in Quick Settings. Android binds this service only while the quick-settings
 * panel is showing; it holds no data and does no work in the background (docs/PLAN.md §5.9).
 */
class NewTaskTileService : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        // On a locked phone, ask for the unlock first; Mavick's own app lock follows after that.
        if (isLocked) unlockAndRun(::openNewTask) else openNewTask()
    }

    private fun openNewTask() {
        val intent = WidgetIntents.newTask(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            // Only Android 13 (the oldest Mavick runs on) lacks the version that takes a PendingIntent.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
