package com.ostemirt.ezbolus.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ostemirt.ezbolus.data.settings.AppSettings
import com.ostemirt.ezbolus.data.settings.CurveModelKind
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.data.settings.NotificationStyle
import com.ostemirt.ezbolus.data.settings.SettingsRepository
import com.ostemirt.ezbolus.notify.IobAlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app.applicationContext)
    private val scheduler = IobAlarmScheduler(app.applicationContext)

    val state: StateFlow<AppSettings> = repo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings.Default,
    )

    private fun launchUpdate(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        repo.update(transform)
        // Any setting change might affect crossing time or whether we alert at all.
        scheduler.reschedule()
    }

    fun setIcr(v: Double) = launchUpdate { it.copy(icr = v) }
    fun setIsf(v: Double) = launchUpdate { it.copy(isf = v) }
    fun setTarget(v: Double) = launchUpdate { it.copy(target = v) }
    fun setActionTime(v: Double) = launchUpdate { it.copy(actionTimeHours = v) }
    fun setCurve(v: CurveModelKind) = launchUpdate { it.copy(curveModel = v) }
    fun setAlertEnabled(v: Boolean) = launchUpdate { it.copy(alertEnabled = v) }
    fun setAlertThreshold(v: Double) = launchUpdate { it.copy(alertThresholdUnits = v) }
    fun setAlertReArm(v: Boolean) = launchUpdate { it.copy(alertReArmOnRise = v) }
    fun setDosingIncrement(v: Double) = launchUpdate { it.copy(dosingIncrement = v) }
    fun setAlertStyle(v: NotificationStyle) = launchUpdate { it.copy(alertStyle = v) }
    fun changeGlucoseUnit(to: GlucoseUnit) = viewModelScope.launch {
        repo.changeGlucoseUnit(to); scheduler.reschedule()
    }
}
