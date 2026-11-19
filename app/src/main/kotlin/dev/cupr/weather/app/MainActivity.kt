package dev.cupr.weather.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.cupr.weather.signin.SignInScreen

/** The app's only screen: signing in with Home Assistant and out again; it reads the status anew on each start. */
class MainActivity : ComponentActivity() {
    private var starts by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeFromPalette()
        setContent { WeatherTheme { SignInScreen(starts) } }
    }

    override fun onStart() {
        super.onStart()
        // The fetch on opening the screen (D20) goes here.
        starts++
    }
}
