package com.idanariav.timetracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Group::class, Goal::class, GoalActivity::class, ActivityItem::class, Session::class, Rule::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao

    companion object {
        /** v1 stored emoji; v2 stores Lucide icon names in the same column. */
        private val EMOJI_TO_ICON = mapOf(
            "😴" to "Moon", "🛌" to "Bed", "💼" to "Briefcase", "💻" to "Laptop", "📧" to "Mail",
            "📞" to "Phone", "🤝" to "Users", "📝" to "NotebookPen", "📚" to "BookOpen", "🎓" to "GraduationCap",
            "🧠" to "Brain", "✍️" to "NotebookPen", "🏃" to "Footprints", "🏋️" to "Dumbbell", "🧘" to "PersonStanding",
            "🚴" to "Bike", "🚶" to "Footprints", "⚽" to "Sun", "🏊" to "Waves", "🍽️" to "Utensils",
            "☕" to "Coffee", "🍳" to "CookingPot", "🛒" to "ShoppingCart", "🧹" to "Brush", "🧺" to "Shirt",
            "🚿" to "ShowerHead", "🚗" to "Car", "🚌" to "Bus", "✈️" to "Plane", "🏠" to "House",
            "👪" to "Users", "❤️" to "Heart", "🧒" to "Baby", "🐶" to "Dog", "📺" to "Tv", "🎮" to "Gamepad2",
            "🎬" to "Clapperboard", "🎵" to "Music", "🎸" to "Guitar", "🎨" to "Palette", "📷" to "Camera",
            "🌳" to "Trees", "🙏" to "Sparkles", "🩺" to "Stethoscope", "💊" to "Pill", "💰" to "Wallet",
            "🔧" to "Wrench", "🪴" to "Sprout", "📱" to "Smartphone", "🎉" to "PartyPopper", "⭐" to "Star",
            "🧩" to "Puzzle",
        )

        /**
         * v3 splits groups (one per activity, for organizing) from goals (targets over any set of
         * activities). Each existing goal becomes a group with the same id/name/color, keeps existing
         * as a goal, and keeps tracking the activities it had. The activities table is rebuilt to
         * swap goalId → groupId and rename emoji → icon.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL("INSERT INTO activity_groups (id, name, color, sortOrder) SELECT id, name, color, sortOrder FROM goals")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_activities` (`goalId` INTEGER NOT NULL, `activityId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`goalId`, `activityId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_activities_activityId` ON `goal_activities` (`activityId`)")
                db.execSQL(
                    "INSERT INTO goal_activities (goalId, activityId) SELECT goalId, id FROM activities " +
                        "WHERE goalId IN (SELECT id FROM goals)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activities_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, `groupId` INTEGER, " +
                        "`archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO activities_new (id, name, icon, color, groupId, archived, sortOrder) " +
                        "SELECT id, name, emoji, color, CASE WHEN goalId IN (SELECT id FROM goals) THEN goalId END, " +
                        "archived, sortOrder FROM activities",
                )
                db.execSQL("DROP TABLE activities")
                db.execSQL("ALTER TABLE activities_new RENAME TO activities")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                EMOJI_TO_ICON.forEach { (emoji, icon) ->
                    db.execSQL("UPDATE activities SET emoji = ? WHERE emoji = ?", arrayOf(icon, emoji))
                }
                val known = EMOJI_TO_ICON.values.toSet().joinToString(",") { "'$it'" }
                db.execSQL("UPDATE activities SET emoji = 'Circle' WHERE emoji NOT IN ($known)")
            }
        }
    }
}
