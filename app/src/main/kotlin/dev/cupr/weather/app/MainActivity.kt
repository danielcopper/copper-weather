package dev.cupr.weather.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette

/** The app's screen. Until signing in exists, it only says that the app is not set up yet. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The bars' scrims are the screen's ground, so the system draws no colour of its own there (D7).
        val dark = resources.configuration.isNightModeActive
        val ground = Palette.of(dark).surfaceBackground
        val bars = if (dark) SystemBarStyle.dark(ground) else SystemBarStyle.light(ground, ground)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent { WeatherTheme { NotSetUpScreen() } }
    }
}

/** The app's name and the line that it is not set up yet. */
@Composable
fun NotSetUpScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.not_set_up),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
