package dev.cupr.weather.app

import android.app.Application
import androidx.work.Configuration

/**
 * The app's Application, for WorkManager's on-demand initialization: the manifest removes WorkManager's start-up
 * initializer, so its database opens only when the app schedules or watches a fetch (D28).
 */
class WeatherApp :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
