package com.idanariav.timetracker.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.idanariav.timetracker.notify.TrackingNotification
import java.util.concurrent.TimeUnit

/** Smart suggestions depend on the clock, so re-evaluate widgets + notification every 15 minutes. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { refreshWidgets(applicationContext) }
        runCatching { TrackingNotification.update(applicationContext) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
