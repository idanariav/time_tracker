package com.idanariav.timetracker.tracking

import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Goal
import com.idanariav.timetracker.data.GoalActivity
import com.idanariav.timetracker.data.Group
import com.idanariav.timetracker.data.Rule

/** First-launch starter set so the app is usable immediately; everything is editable/deletable. */
object Seed {
    suspend fun ifNeeded() {
        val prefs = Graph.prefs
        val dao = Graph.dao
        if (prefs.seeded) return
        if (dao.activityCount() == 0) {
            val rest = dao.putGroup(Group(name = "Rest", color = 0xFF9575CD.toInt(), sortOrder = 1))
            val work = dao.putGroup(Group(name = "Work", color = 0xFF64B5F6.toInt(), sortOrder = 2))
            val health = dao.putGroup(Group(name = "Health", color = 0xFF81C784.toInt(), sortOrder = 3))
            val leisure = dao.putGroup(Group(name = "Leisure", color = 0xFFFFB74D.toInt(), sortOrder = 4))
            val acts = listOf(
                ActivityItem(name = "Sleep", icon = "Moon", color = 0xFF9575CD.toInt(), groupId = rest),
                ActivityItem(name = "Work", icon = "Briefcase", color = 0xFF64B5F6.toInt(), groupId = work),
                ActivityItem(name = "Email", icon = "Mail", color = 0xFF4FC3F7.toInt(), groupId = work),
                ActivityItem(name = "Exercise", icon = "Footprints", color = 0xFF81C784.toInt(), groupId = health),
                ActivityItem(name = "Eat", icon = "Utensils", color = 0xFFAED581.toInt(), groupId = health),
                ActivityItem(name = "Commute", icon = "Car", color = 0xFF90A4AE.toInt()),
                ActivityItem(name = "Chores", icon = "Brush", color = 0xFFA1887F.toInt()),
                ActivityItem(name = "Reading", icon = "BookOpen", color = 0xFF4DB6AC.toInt(), groupId = leisure),
                ActivityItem(name = "TV", icon = "Tv", color = 0xFFFFB74D.toInt(), groupId = leisure),
            ).mapIndexed { i, a -> a.copy(sortOrder = i) }
            val ids = acts.map { dao.putActivity(it) }
            val (sleep, workAct, email, exercise) = ids
            val tv = ids[8]
            val goals = listOf(
                Goal(name = "Sleep 8h", color = 0xFF9575CD.toInt(), targetMinutes = 8 * 60, sortOrder = 1) to listOf(sleep),
                Goal(name = "Focused work", color = 0xFF64B5F6.toInt(), targetMinutes = 6 * 60, sortOrder = 2) to listOf(workAct, email),
                Goal(name = "Move", color = 0xFF81C784.toInt(), targetMinutes = 60, sortOrder = 3) to listOf(exercise),
                // A subset of Leisure: TV counts, Reading doesn't.
                Goal(name = "Screen time", color = 0xFFFFB74D.toInt(), targetMinutes = 120, isLimit = true, sortOrder = 4) to listOf(tv),
            )
            goals.forEach { (g, members) ->
                val gid = dao.putGoal(g)
                dao.insertLinks(members.map { GoalActivity(gid, it) })
            }
            dao.putRule(Rule(suggestIds = listOf(sleep), startMinute = 22 * 60, endMinute = 2 * 60))
            dao.putRule(Rule(suggestIds = listOf(ids[4]), startMinute = 12 * 60, endMinute = 14 * 60))
            // Activity trigger example: while Work runs, Email and Commute are likely next.
            dao.putRule(Rule(suggestIds = listOf(email, ids[5]), afterActivityId = workAct))
        }
        prefs.seeded = true
    }
}
