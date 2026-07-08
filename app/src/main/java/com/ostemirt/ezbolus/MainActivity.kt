package com.ostemirt.ezbolus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.ostemirt.ezbolus.ui.calculator.CalculatorScreen
import com.ostemirt.ezbolus.ui.history.HistoryScreen
import com.ostemirt.ezbolus.ui.onboarding.OnboardingRepository
import com.ostemirt.ezbolus.ui.onboarding.OnboardingScreen
import com.ostemirt.ezbolus.ui.settings.AboutScreen
import com.ostemirt.ezbolus.ui.settings.SettingsScreen
import com.ostemirt.ezbolus.ui.theme.EzBolusTheme
import com.ostemirt.ezbolus.widget.updateIobWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Screen { ONBOARDING, CALCULATOR, SETTINGS, HISTORY, ABOUT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EzBolusTheme {
                val context = LocalContext.current
                // null while the one-time onboarding-completed read is pending, so we
                // never flash the calculator (with default ratios) before the gate.
                var screen by remember { mutableStateOf<Screen?>(null) }
                LaunchedEffect(Unit) {
                    val completed = OnboardingRepository(context).completed.first()
                    screen = if (completed) Screen.CALCULATOR else Screen.ONBOARDING
                }
                when (screen) {
                    null -> Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {}
                    Screen.ONBOARDING -> OnboardingScreen(
                        onComplete = { screen = Screen.CALCULATOR },
                    )
                    Screen.CALCULATOR -> CalculatorScreen(
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenHistory = { screen = Screen.HISTORY },
                    )
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.CALCULATOR }
                        SettingsScreen(
                            onBack = { screen = Screen.CALCULATOR },
                            onOpenAbout = { screen = Screen.ABOUT },
                        )
                    }
                    Screen.HISTORY -> {
                        BackHandler { screen = Screen.CALCULATOR }
                        HistoryScreen(onBack = { screen = Screen.CALCULATOR })
                    }
                    Screen.ABOUT -> {
                        BackHandler { screen = Screen.SETTINGS }
                        AboutScreen(onBack = { screen = Screen.SETTINGS })
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
