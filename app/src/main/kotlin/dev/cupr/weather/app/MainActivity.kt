package dev.cupr.weather.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.cupr.weather.signin.SignInScreen
import kotlinx.coroutines.launch

/**
 * The app's only screen: signing in with Home Assistant and out again, the last fetch and the fetch interval. Each time
 * it starts while signed in, the app fetches (D28), and the screen reads the status anew.
 */
class MainActivity : ComponentActivity() {
    private var starts by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeFromPalette()
        setContent { WeatherTheme { SignInScreen(starts) } }
    }

    override fun onStart() {
        super.onStart()
        starts++
        lifecycleScope.launch(Services.io) { Services.opened(applicationContext) }
    }
}
