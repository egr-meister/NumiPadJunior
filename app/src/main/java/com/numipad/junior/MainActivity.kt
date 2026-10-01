package com.numipad.junior

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.numipad.junior.ui.NumiPadNavHost
import com.numipad.junior.ui.theme.NumiPadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Launch screen uses the app identity and is dismissed on the first frame (no artificial delay).
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as NumiPadApp).container
        setContent {
            NumiPadTheme {
                NumiPadNavHost(container)
            }
        }
    }
}
