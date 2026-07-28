package com.example.dukaanlocker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark mode: white icons on transparent dark bar
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            DukaanLockerApp(
                onThemeChange = { isDark ->
                    if (isDark) {
                        // Dark theme: white status bar icons
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                        )
                    } else {
                        // Light theme: dark (black) status bar icons on light background
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.light(
                                scrim = Color.TRANSPARENT,
                                darkScrim = Color.TRANSPARENT
                            ),
                            navigationBarStyle = SystemBarStyle.light(
                                scrim = Color.TRANSPARENT,
                                darkScrim = Color.TRANSPARENT
                            )
                        )
                    }
                }
            )
        }
    }
}
