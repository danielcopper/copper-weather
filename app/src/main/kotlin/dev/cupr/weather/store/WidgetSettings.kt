package dev.cupr.weather.store

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/** Which of the widget's weather icons move (D44): none, only "now"'s, or all, "now", the hours and the days. */
enum class Animation(
    override val stored: String,
) : StoredChoice {
    OFF("off"),
    NOW("now"),
    ALL("all"),
    ;

    /** Whether "now"'s icon moves at this level. */
    val now: Boolean
        get() =
            when (this) {
                OFF -> false
                NOW, ALL -> true
            }

    /** Whether the icons of the hours and the days move at this level. */
    val rows: Boolean
        get() =
            when (this) {
                OFF, NOW -> false
                ALL -> true
            }
}

/** What the flat size bucket shows beside "now" (D46): the next days or the next hours. */
enum class FlatStrip(
    override val stored: String,
) : StoredChoice {
    DAYS("days"),
    HOURS("hours"),
}

/**
 * One widget's settings from its configuration screen (D44): which icons move, what the flat bucket shows beside "now",
 * and the card's opacity in percent, 0 to 100 in steps of [OPACITY_STEP] (D45). Each defaults to what a widget without
 * settings shows: only "now" moves, the days, a fully opaque card.
 */
data class WidgetSettings(
    val animation: Animation = Animation.NOW,
    val flatStrip: FlatStrip = FlatStrip.DAYS,
    val opacity: Int = FULL,
) {
    init {
        require(isOpacity(opacity)) { "opacity $opacity" }
    }

    companion object {
        val DEFAULT = WidgetSettings()

        /** A fully opaque card, in percent. */
        const val FULL = 100

        /** The slider's step, in percent. */
        const val OPACITY_STEP = 10

        /** Whether [value] is an opacity the slider can set: 0 to [FULL] percent in steps of [OPACITY_STEP] (D45). */
        fun isOpacity(value: Int): Boolean = value in 0..FULL && value % OPACITY_STEP == 0
    }
}

/**
 * Keeps each widget's [WidgetSettings] by its id until the widget is removed; a widget with none stored has the
 * defaults (D44).
 */
class WidgetSettingsStore(
    private val prefs: SharedPreferences,
) {
    /**
     * The widget's settings; a choice stored under a name this app does not know, and an opacity that is no step of the
     * slider, read as their defaults, logged.
     */
    fun read(id: Int): WidgetSettings {
        val default = WidgetSettings.DEFAULT
        return WidgetSettings(
            animation = choice(id, ANIMATION, Animation.entries, default.animation),
            flatStrip = choice(id, FLAT_STRIP, FlatStrip.entries, default.flatStrip),
            opacity = opacity(id, default.opacity),
        )
    }

    fun write(
        id: Int,
        settings: WidgetSettings,
    ) = prefs.edit {
        putString(key(id, ANIMATION), settings.animation.stored)
        putString(key(id, FLAT_STRIP), settings.flatStrip.stored)
        putInt(key(id, OPACITY), settings.opacity)
    }

    /** Forgets the settings of the widgets [ids], removed from the home screen. */
    fun remove(ids: IntArray) =
        prefs.edit {
            for (id in ids) {
                FIELDS.forEach { remove(key(id, it)) }
            }
        }

    private fun <T : StoredChoice> choice(
        id: Int,
        field: String,
        entries: List<T>,
        default: T,
    ): T = prefs.choice(key(id, field), entries, default, TAG, what(id, field))

    private fun opacity(
        id: Int,
        default: Int,
    ): Int {
        val key = key(id, OPACITY)
        val stored = if (prefs.contains(key)) runCatching { prefs.getInt(key, default) }.getOrNull() else default
        val opacity = stored?.takeIf(WidgetSettings::isOpacity)
        if (opacity == null) Log.w(TAG, "${what(id, OPACITY)} is no step of the slider; it reads as $default")
        return opacity ?: default
    }

    private companion object {
        const val TAG = "WidgetSettings"
        const val ANIMATION = "animation"
        const val FLAT_STRIP = "flat_strip"
        const val OPACITY = "opacity"
        val FIELDS = listOf(ANIMATION, FLAT_STRIP, OPACITY)

        fun key(
            id: Int,
            field: String,
        ) = "$id.$field"

        /** What the log names a stored field by. */
        fun what(
            id: Int,
            field: String,
        ) = "Widget $id: its stored $field"
    }
}
