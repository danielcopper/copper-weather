package dev.cupr.weather.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cupr.weather.R
import dev.cupr.weather.app.Choice
import dev.cupr.weather.app.Services
import dev.cupr.weather.store.Animation
import dev.cupr.weather.store.FlatStrip
import dev.cupr.weather.store.WidgetSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The configuration screen of the widget [id] (D44): which of its icons move, what it shows beside "now" one row high,
 * and its card's opacity on a slider from 0 to 100 % in steps of 10 %, its value in percent beside it (D45). Its stored
 * settings are read off the main thread; "Speichern" stores the settings shown and draws the widget with them ([save]),
 * then calls [onDone] with whether it did.
 */
@Composable
fun SetupScreen(
    id: Int,
    onDone: (saved: Boolean) -> Unit,
) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var settings by rememberSaveable(stateSaver = SettingsSaver) { mutableStateOf<WidgetSettings?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (settings == null) settings = withContext(Services.io) { Services.widgetSettings(app).read(id) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.setup_title, stringResource(R.string.widget_label)),
                style = MaterialTheme.typography.headlineSmall,
            )
            val current = settings ?: return@Column
            Choice(R.string.setup_animation, Animation.entries, current.animation, ::animationLabel) {
                settings = current.copy(animation = it)
            }
            Choice(R.string.setup_flat_strip, FlatStrip.entries, current.flatStrip, ::stripLabel) {
                settings = current.copy(flatStrip = it)
            }
            Opacity(current.opacity) { settings = current.copy(opacity = it) }
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch { onDone(withContext(Services.io) { save(app, id, current) }) }
                },
            ) { Text(stringResource(R.string.setup_save)) }
        }
    }
}

/** The card's opacity (D45): a slider from 0 to 100 % in steps of 10 %, and its value in percent beside it. */
@Composable
private fun Opacity(
    opacity: Int,
    onChange: (Int) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.setup_opacity),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = opacity.toFloat(),
                onValueChange = { onChange(step(it)) },
                valueRange = 0f..WidgetSettings.FULL.toFloat(),
                // The positions between the ends: 10 to 90 %.
                steps = WidgetSettings.FULL / WidgetSettings.OPACITY_STEP - 1,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.setup_opacity_value, opacity), modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** The slider's step nearest to [value], in percent. */
private fun step(value: Float): Int = (value / WidgetSettings.OPACITY_STEP).roundToInt() * WidgetSettings.OPACITY_STEP

@StringRes
private fun animationLabel(animation: Animation): Int =
    when (animation) {
        Animation.OFF -> R.string.setup_animation_off
        Animation.NOW -> R.string.setup_animation_now
        Animation.ALL -> R.string.setup_animation_all
    }

@StringRes
private fun stripLabel(strip: FlatStrip): Int =
    when (strip) {
        FlatStrip.DAYS -> R.string.setup_flat_strip_days
        FlatStrip.HOURS -> R.string.setup_flat_strip_hours
    }

/**
 * Keeps the settings shown, changed or not, across a configuration change, by the names the store keeps them under; a
 * name it does not know restores none, and the screen reads the stored settings again.
 */
internal val SettingsSaver =
    listSaver<WidgetSettings?, String>(
        save = { settings ->
            settings?.let { listOf(it.animation.stored, it.flatStrip.stored, it.opacity.toString()) }.orEmpty()
        },
        restore = { saved ->
            val animation = Animation.entries.firstOrNull { it.stored == saved.getOrNull(0) }
            val strip = FlatStrip.entries.firstOrNull { it.stored == saved.getOrNull(1) }
            val opacity =
                saved.getOrNull(2)?.toIntOrNull()?.takeIf { it in 0..WidgetSettings.FULL && step(it.toFloat()) == it }
            if (animation == null || strip == null || opacity == null) {
                null
            } else {
                WidgetSettings(animation, strip, opacity)
            }
        },
    )
