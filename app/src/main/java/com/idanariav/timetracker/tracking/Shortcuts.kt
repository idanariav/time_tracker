package com.idanariav.timetracker.tracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.link.LinkActivity
import com.idanariav.timetracker.link.Links
import com.idanariav.timetracker.ui.iconBitmap
import com.idanariav.timetracker.ui.onColorInt

/** Long-press launcher shortcuts for the first few activities in grid order. */
object Shortcuts {
    fun update(context: Context, activities: List<ActivityItem>) {
        val max = minOf(4, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        val list = activities.take(max).mapIndexed { i, a ->
            ShortcutInfoCompat.Builder(context, "act_${a.id}")
                .setShortLabel(a.name)
                .setIcon(IconCompat.createWithBitmap(iconBitmap(a.icon, onColorInt(a.color), 192, a.color)))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, Uri.parse(Links.activity(a.id)), context, LinkActivity::class.java),
                )
                .setRank(i)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, list)
    }
}
