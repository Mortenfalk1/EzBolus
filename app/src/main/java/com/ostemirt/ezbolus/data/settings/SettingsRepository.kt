package com.ostemirt.ezbolus.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ezbolus_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ICR = doublePreferencesKey("icr")
        val ISF = doublePreferencesKey("isf")
        val TARGET = doublePreferencesKey("target")
        val GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
        val ACTION_TIME_HOURS = doublePreferencesKey("action_time_hours")
        val CURVE_MODEL = stringPreferencesKey("curve_model")
        val ALERT_ENABLED = booleanPreferencesKey("alert_enabled")
        val ALERT_THRESHOLD = doublePreferencesKey("alert_threshold")
        val ALERT_REARM_ON_RISE = booleanPreferencesKey("alert_rearm_on_rise")
        val ALERT_STYLE = stringPreferencesKey("alert_style")
        val DOSING_INCREMENT = doublePreferencesKey("dosing_increment")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val next = transform(p.toAppSettings())
            p[Keys.ICR] = next.icr
            p[Keys.ISF] = next.isf
            p[Keys.TARGET] = next.target
            p[Keys.GLUCOSE_UNIT] = next.glucoseUnit.name
            p[Keys.ACTION_TIME_HOURS] = next.actionTimeHours
            p[Keys.CURVE_MODEL] = next.curveModel.name
            p[Keys.ALERT_ENABLED] = next.alertEnabled
            p[Keys.ALERT_THRESHOLD] = next.alertThresholdUnits
            p[Keys.ALERT_REARM_ON_RISE] = next.alertReArmOnRise
            p[Keys.ALERT_STYLE] = next.alertStyle.name
            p[Keys.DOSING_INCREMENT] = next.dosingIncrement
        }
    }

    /** Wholesale replace (used by import). */
    suspend fun replace(new: AppSettings) = update { new }

    /**
     * Switch glucose unit AND auto-convert the ISF + target so the values
     * remain clinically identical. Alert threshold (in insulin U) and ICR
     * (in grams) are unit-independent and stay the same.
     */
    suspend fun changeGlucoseUnit(to: GlucoseUnit) {
        update { current ->
            if (current.glucoseUnit == to) current
            else current.copy(
                glucoseUnit = to,
                isf = GlucoseUnit.convert(current.isf, current.glucoseUnit, to),
                target = GlucoseUnit.convert(current.target, current.glucoseUnit, to),
            )
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val unitLabel = this[Keys.GLUCOSE_UNIT]
        val unit = GlucoseUnit.entries.firstOrNull { it.name == unitLabel } ?: GlucoseUnit.MG_DL
        val defaults = defaultsFor(unit)
        return AppSettings(
            icr = this[Keys.ICR] ?: AppSettings.Default.icr,
            isf = this[Keys.ISF] ?: defaults.isf,
            target = this[Keys.TARGET] ?: defaults.target,
            glucoseUnit = unit,
            actionTimeHours = this[Keys.ACTION_TIME_HOURS] ?: AppSettings.Default.actionTimeHours,
            curveModel = CurveModelKind.entries.firstOrNull { it.name == this[Keys.CURVE_MODEL] }
                ?: AppSettings.Default.curveModel,
            alertEnabled = this[Keys.ALERT_ENABLED] ?: AppSettings.Default.alertEnabled,
            alertThresholdUnits = this[Keys.ALERT_THRESHOLD]
                ?: AppSettings.Default.alertThresholdUnits,
            alertReArmOnRise = this[Keys.ALERT_REARM_ON_RISE]
                ?: AppSettings.Default.alertReArmOnRise,
            alertStyle = NotificationStyle.entries.firstOrNull { it.name == this[Keys.ALERT_STYLE] }
                ?: AppSettings.Default.alertStyle,
            dosingIncrement = this[Keys.DOSING_INCREMENT]
                ?: AppSettings.Default.dosingIncrement,
        )
    }
}
