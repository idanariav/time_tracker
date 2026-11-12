package com.idanariav.timetracker.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.idanariav.timetracker.R
import com.idanariav.timetracker.tracking.Graph
import com.idanariav.timetracker.ui.MainActivity
import com.idanariav.timetracker.ui.TimeFmt
import com.idanariav.timetracker.widget.refreshWidgets
import kotlinx.coroutines.launch

/** Ongoing, silent notification with a live chronometer, Stop, and two suggested switches. */
object TrackingNotification {
    private const val CHANNEL = "tracking"
    private const val ID = 1

    fun ensureChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Current activity", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the running timer"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    suspend fun update(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val running = Graph.dao.runningOnce()
        if (!Graph.prefs.settings.value.notificationEnabled || running == null || !canPost(context)) {
            nm.cancel(ID)
            return
        }
        val act = Graph.dao.activity(running.activityId) ?: return nm.cancel(ID)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${act.emoji} ${act.name}")
            .setContentText("Since ${TimeFmt.clock(running.startTime)}")
            .setWhen(running.startTime)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setColor(act.color)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(open)
            .addAction(0, "Stop", actionIntent(context, ActionReceiver.STOP, 0L))
        Graph.suggestionsNow(2).forEach { s ->
            b.addAction(0, "${s.emoji} ${s.name}", actionIntent(context, ActionReceiver.SWITCH, s.id))
        }
        nm.notify(ID, b.build())
    }

    private fun actionIntent(context: Context, action: String, activityId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action.hashCode() * 31 + activityId).toInt(),
            Intent(context, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_ID, activityId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                when (intent.action) {
                    STOP -> Graph.tracker.stop()
                    SWITCH -> Graph.tracker.switchTo(intent.getLongExtra(EXTRA_ID, 0L))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val STOP = "com.idanariav.timetracker.STOP"
        const val SWITCH = "com.idanariav.timetracker.SWITCH"
        const val EXTRA_ID = "activityId"
    }
}

/** Restores the notification/widgets after reboot or app update; the running entry itself lives in the DB. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                runCatching { TrackingNotification.update(context) }
                runCatching { refreshWidgets(context) }
            } finally {
                pending.finish()
            }
        }
    }
}
