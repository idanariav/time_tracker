package com.idanariav.timetracker.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Goal::class, ActivityItem::class, Session::class, Rule::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao
}
