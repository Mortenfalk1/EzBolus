package com.ostemirt.ezbolus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ostemirt.ezbolus.ui.calculator.CalculatorScreen
import com.ostemirt.ezbolus.ui.history.HistoryScreen
import com.ostemirt.ezbolus.ui.settings.SettingsScreen
import com.ostemirt.ezbolus.ui.theme.EzBolusTheme
import com.ostemirt.ezbolus.widget.updateIobWidget
import kotlinx.coroutines.launch

private enum class Screen { CALCULATOR, SETTINGS, HISTORY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EzBolusTheme {
                var screen by remember { mutableStateOf(Screen.CALCULATOR) }
                when (screen) {
                    Screen.CALCULATOR -> CalculatorScreen(
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenHistory = { screen = Screen.HISTORY },
                    )
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.CALCULATOR }
                        SettingsScreen(onBack = { screen = Screen.CALCULATOR })
                    }
                    Screen.HISTORY -> {
                        BackHandler { screen = Screen.CALCULATOR }
                        HistoryScreen(onBack = { screen = Screen.CALCULATOR })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cold start refreshes the widget from EzBolusApplication.onCreate, but a
        // warm resume (returning from background) never re-runs that. Push a fresh
        // render here so the widget's IOB matches app state whenever we foreground.
        lifecycleScope.launch {
            updateIobWidget(applicationContext)
        }
    }
}
