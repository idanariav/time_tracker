package com.idanariav.timetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    // ---- Activities ----
    @Query("SELECT * FROM activities WHERE archived = 0 ORDER BY sortOrder, id")
    fun activeActivities(): Flow<List<ActivityItem>>

    @Query("SELECT * FROM activities ORDER BY archived, sortOrder, id")
    fun allActivities(): Flow<List<ActivityItem>>

    @Query("SELECT * FROM activities ORDER BY archived, sortOrder, id")
    suspend fun allActivitiesOnce(): List<ActivityItem>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun activity(id: Long): ActivityItem?

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun activityCount(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM activities")
    suspend fun maxActivitySort(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putActivity(a: ActivityItem): Long

    @Update
    suspend fun updateActivities(list: List<ActivityItem>)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteActivity(id: Long)

    // ---- Goals ----
    @Query("SELECT * FROM goals ORDER BY sortOrder, id")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY sortOrder, id")
    suspend fun goalsOnce(): List<Goal>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM goals")
    suspend fun maxGoalSort(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putGoal(g: Goal): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("UPDATE activities SET goalId = NULL WHERE goalId = :goalId")
    suspend fun unassignGoal(goalId: Long)

    // ---- Sessions ----
    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun running(): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun runningOnce(): Session?

    @Query("SELECT * FROM sessions WHERE startTime < :to AND (endTime IS NULL OR endTime > :from) ORDER BY startTime")
    fun sessionsIn(from: Long, to: Long): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE startTime < :to AND (endTime IS NULL OR endTime > :from) ORDER BY startTime")
    suspend fun sessionsInOnce(from: Long, to: Long): List<Session>

    @Query(
        "SELECT * FROM sessions WHERE id != :excludeId AND startTime < :to " +
            "AND (endTime IS NULL OR endTime > :from) ORDER BY startTime",
    )
    suspend fun overlapping(from: Long, to: Long, excludeId: Long): List<Session>

    @Query("SELECT * FROM sessions WHERE startTime >= :from ORDER BY startTime")
    suspend fun sessionsSince(from: Long): List<Session>

    @Query("SELECT * FROM sessions ORDER BY startTime")
    suspend fun allSessions(): List<Session>

    @Query("SELECT MAX(endTime) FROM sessions WHERE endTime <= :t AND id != :excludeId")
    suspend fun prevEnd(t: Long, excludeId: Long): Long?

    @Query("SELECT MIN(startTime) FROM sessions WHERE startTime >= :t AND id != :excludeId")
    suspend fun nextStart(t: Long, excludeId: Long): Long?

    @Query("SELECT COUNT(*) FROM sessions WHERE activityId = :activityId")
    suspend fun sessionCount(activityId: Long): Int

    @Insert
    suspend fun insertSession(s: Session): Long

    @Update
    suspend fun updateSession(s: Session)

    @Delete
    suspend fun deleteSession(s: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM sessions WHERE activityId = :activityId")
    suspend fun deleteSessionsFor(activityId: Long)

    // ---- Rules ----
    @Query("SELECT * FROM rules ORDER BY sortOrder, id")
    fun rules(): Flow<List<Rule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRule(r: Rule): Long

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("DELETE FROM rules WHERE activityId = :activityId OR afterActivityId = :activityId")
    suspend fun deleteRulesFor(activityId: Long)
}
