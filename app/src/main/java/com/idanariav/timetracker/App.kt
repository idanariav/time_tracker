package com.idanariav.timetracker

import android.app.Application
import com.idanariav.timetracker.notify.TrackingNotification
import com.idanariav.timetracker.tracking.Graph
import com.idanariav.timetracker.tracking.Seed
import com.idanariav.timetracker.tracking.Shortcuts
import com.idanariav.timetracker.ui.TimeFmt
import com.idanariav.timetracker.widget.WidgetRefreshWorker
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        TimeFmt.init(this)
        TrackingNotification.ensureChannel(this)
        WidgetRefreshWorker.schedule(this)
        Graph.appScope.launch {
            Seed.ifNeeded()
            runCatching { Shortcuts.update(this@App, Graph.dao.allActivitiesOnce().filter { !it.archived }) }
            Graph.tracker.refreshSurfaces()
        }
    }
}
