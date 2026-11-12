package com.idanariav.timetracker.tracking

import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Goal
import com.idanariav.timetracker.data.Rule

/** First-launch starter set so the app is usable immediately; everything is editable/deletable. */
object Seed {
    suspend fun ifNeeded() {
        val prefs = Graph.prefs
        val dao = Graph.dao
        if (prefs.seeded) return
        if (dao.activityCount() == 0) {
            val rest = dao.putGoal(Goal(name = "Rest", color = 0xFF9575CD.toInt(), targetMinutes = 8 * 60, sortOrder = 1))
            val work = dao.putGoal(Goal(name = "Work", color = 0xFF64B5F6.toInt(), targetMinutes = 6 * 60, sortOrder = 2))
            val health = dao.putGoal(Goal(name = "Health", color = 0xFF81C784.toInt(), targetMinutes = 60, sortOrder = 3))
            val fun_ = dao.putGoal(Goal(name = "Leisure", color = 0xFFFFB74D.toInt(), targetMinutes = 120, isLimit = true, sortOrder = 4))
            val acts = listOf(
                ActivityItem(name = "Sleep", icon = "Moon", color = 0xFF9575CD.toInt(), goalId = rest),
                ActivityItem(name = "Work", icon = "Briefcase", color = 0xFF64B5F6.toInt(), goalId = work),
                ActivityItem(name = "Email", icon = "Mail", color = 0xFF4FC3F7.toInt(), goalId = work),
                ActivityItem(name = "Exercise", icon = "Footprints", color = 0xFF81C784.toInt(), goalId = health),
                ActivityItem(name = "Eat", icon = "Utensils", color = 0xFFAED581.toInt(), goalId = health),
                ActivityItem(name = "Commute", icon = "Car", color = 0xFF90A4AE.toInt()),
                ActivityItem(name = "Chores", icon = "Brush", color = 0xFFA1887F.toInt()),
                ActivityItem(name = "Reading", icon = "BookOpen", color = 0xFF4DB6AC.toInt(), goalId = fun_),
                ActivityItem(name = "TV", icon = "Tv", color = 0xFFFFB74D.toInt(), goalId = fun_),
            ).mapIndexed { i, a -> a.copy(sortOrder = i) }
            val ids = acts.map { dao.putActivity(it) }
            dao.putRule(Rule(activityId = ids[0], startMinute = 22 * 60, endMinute = 2 * 60))
            dao.putRule(Rule(activityId = ids[4], startMinute = 12 * 60, endMinute = 14 * 60))
        }
        prefs.seeded = true
    }
}
