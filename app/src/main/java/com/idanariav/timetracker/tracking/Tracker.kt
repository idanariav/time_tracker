package com.idanariav.timetracker.tracking

import android.content.Context
import androidx.room.withTransaction
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.AppDatabase
import com.idanariav.timetracker.data.Goal
import com.idanariav.timetracker.data.Prefs
import com.idanariav.timetracker.data.Rule
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.planOverlaps
import com.idanariav.timetracker.notify.TrackingNotification
import com.idanariav.timetracker.widget.refreshWidgets
import kotlinx.coroutines.launch
import kotlin.math.max

/** What a switch/stop changed, so it can be undone. */
data class Change(val newSessionId: Long?, val previous: Session?)

/**
 * All writes go through here so the "one running activity" invariant holds and every
 * change refreshes widgets, the notification and launcher shortcuts.
 */
class Tracker(private val context: Context, private val db: AppDatabase, private val prefs: Prefs) {
    private val dao = db.dao()

    /** Ends the running entry (if any) and starts [activityId]. Returns null if it was already running. */
    suspend fun switchTo(activityId: Long, at: Long = System.currentTimeMillis()): Change? {
        val change = db.withTransaction {
            val running = dao.runningOnce()
            if (running?.activityId == activityId) return@withTransaction null
            if (running != null) dao.updateSession(running.copy(endTime = max(at, running.startTime)))
            Change(dao.insertSession(Session(activityId = activityId, startTime = at)), running)
        }
        if (change != null) onSessionsChanged()
        return change
    }

    suspend fun stop(at: Long = System.currentTimeMillis()): Change? {
        val running = dao.runningOnce() ?: return null
        dao.updateSession(running.copy(endTime = max(at, running.startTime)))
        onSessionsChanged()
        return Change(null, running)
    }

    /** Toggle semantics for NFC tags: stop if this activity is running, otherwise switch to it. */
    suspend fun toggle(activityId: Long): Boolean {
        val running = dao.runningOnce()
        return if (running?.activityId == activityId) {
            stop(); false
        } else {
            switchTo(activityId); true
        }
    }

    suspend fun undo(change: Change) {
        db.withTransaction {
            change.newSessionId?.let { dao.deleteSessionById(it) }
            change.previous?.let { dao.updateSession(it.copy(endTime = null)) }
        }
        onSessionsChanged()
    }

    /** Insert or update an entry; overlapping entries are trimmed/split/removed so the edit wins. */
    suspend fun saveSession(s: Session) {
        db.withTransaction {
            val id = if (s.id == 0L) dao.insertSession(s) else s.id.also { dao.updateSession(s) }
            val saved = s.copy(id = id)
            val others = dao.overlapping(saved.startTime, saved.endTime ?: Long.MAX_VALUE, id)
            val plan = planOverlaps(saved, others)
            plan.deletes.forEach { dao.deleteSession(it) }
            plan.updates.forEach { dao.updateSession(it) }
            plan.inserts.forEach { dao.insertSession(it) }
        }
        onSessionsChanged()
    }

    suspend fun deleteSession(s: Session) {
        dao.deleteSession(s)
        onSessionsChanged()
    }

    suspend fun saveActivity(a: ActivityItem): Long {
        val toSave = if (a.id == 0L) a.copy(sortOrder = dao.maxActivitySort() + 1) else a
        val id = dao.putActivity(toSave)
        onActivitiesChanged()
        return if (a.id == 0L) id else a.id
    }

    suspend fun deleteActivity(id: Long) {
        db.withTransaction {
            dao.deleteSessionsFor(id)
            dao.deleteRulesFor(id)
            dao.deleteActivity(id)
        }
        onActivitiesChanged()
        onSessionsChanged()
    }

    /** Persists a new order for the given (already reordered) list. */
    suspend fun reorderActivities(ordered: List<ActivityItem>) {
        dao.updateActivities(ordered.mapIndexed { i, a -> a.copy(sortOrder = i) })
        onActivitiesChanged()
    }

    suspend fun saveGoal(g: Goal) {
        dao.putGoal(if (g.id == 0L) g.copy(sortOrder = dao.maxGoalSort() + 1) else g)
    }

    suspend fun deleteGoal(id: Long) {
        db.withTransaction {
            dao.unassignGoal(id)
            dao.deleteGoal(id)
        }
    }

    suspend fun saveRule(r: Rule) {
        dao.putRule(r)
        refreshSurfaces()
    }

    suspend fun deleteRule(id: Long) {
        dao.deleteRule(id)
        refreshSurfaces()
    }

    private fun onSessionsChanged() = refreshSurfaces()

    private fun onActivitiesChanged() {
        Graph.appScope.launch {
            runCatching { Shortcuts.update(context, dao.allActivitiesOnce().filter { !it.archived }) }
        }
        refreshSurfaces()
    }

    /** Widgets + notification; launched on the app scope so callers aren't held up. */
    fun refreshSurfaces() {
        Graph.appScope.launch {
            runCatching { TrackingNotification.update(context) }
            runCatching { refreshWidgets(context) }
        }
    }
}
