package dev.cupr.weather.tap

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.cupr.weather.app.MainActivity

/** Where a tap on the widget goes (D17, D39), as the pending intents its views hold. */
object Taps {
    /** The scheme of the links the Home Assistant app opens. */
    const val HOME_ASSISTANT_SCHEME = "homeassistant"

    /** The dashboard's weather pop-up (D39): the dashboard's start page with the pop-up's hash. */
    const val WEATHER = "$HOME_ASSISTANT_SCHEME://navigate/dashboard-zuhause/start#wetter"

    private const val WEATHER_REQUEST = 100
    private const val APP_REQUEST = 101

    /** The weather pop-up, opened by [TapActivity]. */
    fun weather(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            WEATHER_REQUEST,
            Intent(context, TapActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * The app's screen, to sign in or see why there is no data, started as the app's icon starts it, so a screen
     * already open comes to the front instead of a second one.
     */
    fun app(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            APP_REQUEST,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
