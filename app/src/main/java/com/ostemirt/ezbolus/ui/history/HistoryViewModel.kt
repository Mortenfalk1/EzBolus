package com.ostemirt.ezbolus.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ostemirt.ezbolus.data.db.Intake
import com.ostemirt.ezbolus.data.db.IntakeRepository
import com.ostemirt.ezbolus.notify.IobAlarmScheduler
import com.ostemirt.ezbolus.widget.updateIobWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = IntakeRepository(app.applicationContext)
    private val scheduler = IobAlarmScheduler(app.applicationContext)

    val intakes: StateFlow<List<Intake>> = repo.all.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    fun delete(id: Long) = viewModelScope.launch {
        repo.deleteById(id)
        scheduler.reschedule()
        updateIobWidget(getApplication())
    }
}
