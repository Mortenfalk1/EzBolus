package com.ostemirt.ezbolus.ui.calculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ostemirt.ezbolus.data.db.IntakeRepository
import com.ostemirt.ezbolus.data.settings.AppSettings
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.data.settings.SettingsRepository
import com.ostemirt.ezbolus.engine.Dose
import com.ostemirt.ezbolus.engine.IobRingState
import com.ostemirt.ezbolus.engine.iobRingState
import com.ostemirt.ezbolus.notify.IobAlarmScheduler
import com.ostemirt.ezbolus.widget.updateIobWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalculatorViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app.applicationContext)
    private val intakeRepo = IntakeRepository(app.applicationContext)
    private val scheduler = IobAlarmScheduler(app.applicationContext)

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Default,
    )

    /** Ticks every 30 seconds so the displayed IOB doesn't get stale between calculations. */
    private val timeTicks = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    /** Current IOB (units) plus the decay-ring fill (0..1), from the shared
     *  [iobRingState] so the in-app card and the home-screen widget render the
     *  same ring. The fill snaps to full on each dose and drains with total IOB.
     *  Empty history → 0.0.
     *
     *  IMPORTANT: `now` is read fresh inside the compute rather than pulled
     *  from `timeTicks`. If we used the cached tick value, a just-saved dose
     *  whose `takenAt` is a few seconds after the last tick would compute
     *  a negative `elapsedHours`, be skipped as "future", and vanish from
     *  IOB for up to 30 s. */
    private val iobRingFlow: StateFlow<IobRingState> = combine(
        settings,
        intakeRepo.recentInsulin,
        timeTicks,
    ) { s, rows, _ ->
        val now = System.currentTimeMillis()
        val model = s.toEngineCurve()
        val doses = rows.mapNotNull { row ->
            row.insulinUnits?.let { Dose(units = it, takenAtEpochMillis = row.takenAt) }
        }
        iobRingState(now, doses, model)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IobRingState(0.0, 0f))

    val currentIob: StateFlow<Double> = iobRingFlow
        .map { it.iob }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val currentIobFraction: StateFlow<Float> = iobRingFlow
        .map { it.fraction }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** Persist a confirmed dose. Returns the `takenAt` timestamp so the UI can Undo. */
    fun confirmDose(
        units: Double,
        glucose: Double?,
        glucoseUnit: GlucoseUnit,
        carbsGrams: Double?,
        onSaved: (takenAt: Long) -> Unit,
    ) {
        viewModelScope.launch {
            val takenAt = intakeRepo.saveConfirmedDose(
                insulinUnits = units,
                glucose = glucose,
                glucoseUnit = glucoseUnit,
                carbsGrams = carbsGrams,
            )
            scheduler.reschedule()
            updateIobWidget(getApplication())
            onSaved(takenAt)
        }
    }

    fun undoSave(takenAt: Long) {
        viewModelScope.launch {
            intakeRepo.deleteAt(takenAt)
            scheduler.reschedule()
            updateIobWidget(getApplication())
        }
    }

    /** True until the user either edits a ratio in Settings or saves any dose. */
    val showFirstRunNudge: StateFlow<Boolean> = combine(
        settings,
        intakeRepo.all,
    ) { s, intakes ->
        val untouched = s.icr == AppSettings.Default.icr &&
            s.isf == AppSettings.Default.isf &&
            s.target == AppSettings.Default.target &&
            s.glucoseUnit == AppSettings.Default.glucoseUnit
        untouched && intakes.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
