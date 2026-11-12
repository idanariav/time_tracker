package com.idanariav.timetracker.tracking

import android.content.Context
import androidx.room.Room
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.AppDatabase
import com.idanariav.timetracker.data.Prefs
import com.idanariav.timetracker.data.TrackerDao
import com.idanariav.timetracker.domain.suggest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDateTime

/** Tiny service locator: one DB, prefs and tracker per process (app, widgets, receivers share it). */
object Graph {
    lateinit var db: AppDatabase
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var tracker: Tracker
        private set
    val dao: TrackerDao get() = db.dao()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        val app = context.applicationContext
        db = Room.databaseBuilder(app, AppDatabase::class.java, "tracker.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        prefs = Prefs(app)
        tracker = Tracker(app, db, prefs)
    }

    private const val HISTORY_MS = 28L * 24 * 3600 * 1000

    suspend fun suggestionsNow(limit: Int): List<ActivityItem> {
        val acts = dao.allActivitiesOnce()
        return suggest(
            acts, rulesOnce(), dao.sessionsSince(System.currentTimeMillis() - HISTORY_MS),
            dao.runningOnce(), LocalDateTime.now(), limit,
        )
    }

    private suspend fun rulesOnce() = dao.rules().first()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun suggestionFlow(limit: Int): Flow<List<ActivityItem>> =
        combine(dao.activeActivities(), dao.rules(), dao.running()) { a, r, run -> Triple(a, r, run) }
            .mapLatest { (acts, rules, running) ->
                suggest(
                    acts, rules, dao.sessionsSince(System.currentTimeMillis() - HISTORY_MS),
                    running, LocalDateTime.now(), limit,
                )
            }
}
