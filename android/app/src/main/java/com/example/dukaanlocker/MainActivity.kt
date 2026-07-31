package com.example.dukaanlocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyLanguage()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DukaanLockerApp(
                onLanguageChanged = { code ->
                    LockerStorage.saveLanguage(this, code)
                }
            )
        }
    }

    private fun applyLanguage() {
        val code = LockerStorage.getLanguage(this)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
