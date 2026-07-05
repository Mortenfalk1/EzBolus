package com.ostemirt.ezbolus

import android.app.Application
import com.ostemirt.ezbolus.notify.IobAlarmScheduler
import com.ostemirt.ezbolus.notify.NotificationChannels
import com.ostemirt.ezbolus.widget.IobWidgetRefreshWorker
import com.ostemirt.ezbolus.widget.updateIobWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EzBolusApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        IobWidgetRefreshWorker.schedule(this)
        scope.launch {
            // Re-arm the alarm on app start too — in case settings changed while
            // we weren't running.
            IobAlarmScheduler(this@EzBolusApplication).reschedule()
            // Force a widget re-render on cold start so it reflects fresh IOB.
            updateIobWidget(this@EzBolusApplication)
        }
    }
}
