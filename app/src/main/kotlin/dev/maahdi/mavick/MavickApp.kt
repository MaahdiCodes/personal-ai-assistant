package dev.maahdi.mavick

import android.app.Application

class MavickApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Cheap: the container only creates things when they are first used.
        container = AppContainer(this)
    }
}
