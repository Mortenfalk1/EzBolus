package com.ostemirt.ezbolus.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ostemirt.ezbolus.data.db.Intake
import com.ostemirt.ezbolus.data.db.IntakeRepository
import com.ostemirt.ezbolus.data.settings.AppSettings
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.data.settings.SettingsRepository
import com.ostemirt.ezbolus.notify.IobAlarmScheduler
import com.ostemirt.ezbolus.widget.requestIobWidgetRefresh
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = IntakeRepository(app.applicationContext)
    private val settingsRepo = SettingsRepository(app.applicationContext)
    private val scheduler = IobAlarmScheduler(app.applicationContext)

    val intakes: StateFlow<List<Intake>> = repo.all.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    /** Needed by the "log a past dose" dialog for the glucose unit label and pen step. */
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Default,
    )

    fun delete(id: Long) = viewModelScope.launch {
        repo.deleteById(id)
        scheduler.reschedule()
        requestIobWidgetRefresh(getApplication())
    }

    /** Log a dose taken outside the calculator flow, at a user-picked past `takenAt`.
     *  Mirrors `CalculatorViewModel.confirmDose` but with an explicit timestamp. */
    fun saveManualDose(
        units: Double,
        glucose: Double?,
        glucoseUnit: GlucoseUnit,
        carbsGrams: Double?,
        takenAt: Long,
        onSaved: (takenAt: Long) -> Unit,
    ) = viewModelScope.launch {
        repo.saveConfirmedDose(
            insulinUnits = units,
            glucose = glucose,
            glucoseUnit = glucoseUnit,
            carbsGrams = carbsGrams,
            takenAt = takenAt,
        )
        scheduler.reschedule()
        requestIobWidgetRefresh(getApplication())
        onSaved(takenAt)
    }

    fun undoSave(takenAt: Long) = viewModelScope.launch {
        repo.deleteAt(takenAt)
        scheduler.reschedule()
        requestIobWidgetRefresh(getApplication())
    }
}
