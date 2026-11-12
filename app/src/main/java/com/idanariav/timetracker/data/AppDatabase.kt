package com.idanariav.timetracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Goal::class, ActivityItem::class, Session::class, Rule::class],
    version = 2,
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
