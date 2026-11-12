package com.idanariav.timetracker.tracking

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.link.LinkActivity
import com.idanariav.timetracker.link.Links

/** Long-press launcher shortcuts for the first few activities in grid order. */
object Shortcuts {
    fun update(context: Context, activities: List<ActivityItem>) {
        val max = minOf(4, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        val list = activities.take(max).mapIndexed { i, a ->
            ShortcutInfoCompat.Builder(context, "act_${a.id}")
                .setShortLabel(a.name)
                .setIcon(IconCompat.createWithBitmap(emojiBitmap(a.emoji, a.color)))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, Uri.parse(Links.activity(a.id)), context, LinkActivity::class.java),
                )
                .setRank(i)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, list)
    }

    fun emojiBitmap(emoji: String, color: Int, size: Int = 192): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f; textAlign = Paint.Align.CENTER }
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(emoji, size / 2f, y, p)
        return bmp
    }
}
