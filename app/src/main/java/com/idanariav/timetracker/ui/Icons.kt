package com.idanariav.timetracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.Lucide
import com.idanariav.timetracker.domain.IconCatalogData
import com.idanariav.timetracker.domain.parseIconCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hand-picked everyday icons: the "Common" set shown first in the icon picker. */
val CommonIcons: List<String> = listOf(
    "Moon", "Bed", "Briefcase", "Laptop", "Code", "Mail", "Phone", "MessageCircle",
    "Users", "NotebookPen", "BookOpen", "GraduationCap", "Brain", "Languages", "Dumbbell", "Footprints",
    "Bike", "PersonStanding", "Waves", "Utensils", "Coffee", "CookingPot", "ShoppingCart", "Brush",
    "Shirt", "ShowerHead", "Hammer", "Wrench", "Car", "Bus", "TrainFront", "Plane",
    "House", "Baby", "Heart", "Dog", "Cat", "Tv", "Gamepad2", "Clapperboard",
    "Music", "Headphones", "Guitar", "Mic", "Palette", "Camera", "Trees", "Sprout",
    "Flower2", "Sun", "Stethoscope", "Pill", "Wallet", "Smartphone", "PartyPopper", "Sparkles",
    "Puzzle", "Star", "Circle",
)

/** Any Lucide icon by name (see [LucideCatalog]); unknown names fall back to a circle. */
fun activityIcon(name: String): ImageVector = LucideCatalog.icon(name) ?: Lucide.Circle

/** Lazily loaded search metadata (tags/categories) for all icons, from assets/lucide_icons.tsv. */
object IconCatalog {
    @Volatile private var cached: IconCatalogData? = null

    suspend fun load(context: android.content.Context): IconCatalogData = cached ?: withContext(Dispatchers.IO) {
        context.assets.open("lucide_icons.tsv").bufferedReader().useLines { parseIconCatalog(it) }
    }.also { cached = it }
}

private val bitmapCache = LruCache<String, Bitmap>(64)

/**
 * Renders a Lucide icon to a bitmap for surfaces that can't take an ImageVector
 * (Glance widgets, notification large icon, launcher shortcuts). With [background] set,
 * the icon is drawn centred on a filled circle of that color.
 */
fun iconBitmap(name: String, tint: Int, sizePx: Int, background: Int? = null): Bitmap =
    vectorBitmap(activityIcon(name), name, tint, sizePx, background)

/** Same as [iconBitmap] for any vector; [key] identifies it in the cache. */
fun vectorBitmap(vector: ImageVector, key: String, tint: Int, sizePx: Int, background: Int? = null): Bitmap =
    bitmapCache.get("$key|$tint|$sizePx|$background") ?: run {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (background != null) {
            c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
            // Icon at ~55% of the circle, centred.
            val inset = sizePx * 0.225f
            c.translate(inset, inset)
            c.scale((sizePx - 2 * inset) / vector.viewportWidth, (sizePx - 2 * inset) / vector.viewportHeight)
        } else {
            c.scale(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight)
        }
        drawGroup(vector.root, c, tint)
        bmp.also { bitmapCache.put("$key|$tint|$sizePx|$background", it) }
    }

private fun drawGroup(g: VectorGroup, c: Canvas, tint: Int) {
    c.save()
    c.translate(g.translationX + g.pivotX, g.translationY + g.pivotY)
    c.rotate(g.rotation)
    c.scale(g.scaleX, g.scaleY)
    c.translate(-g.pivotX, -g.pivotY)
    for (node in g) {
        when (node) {
            is VectorGroup -> drawGroup(node, c, tint)
            is VectorPath -> {
                val path = node.pathData.toPath().asAndroidPath()
                path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                    android.graphics.Path.FillType.EVEN_ODD
                } else {
                    android.graphics.Path.FillType.WINDING
                }
                if (node.fill != null) {
                    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = tint })
                }
                if (node.stroke != null && node.strokeLineWidth > 0f) {
                    c.drawPath(
                        path,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            color = tint
                            strokeWidth = node.strokeLineWidth
                            strokeCap = when (node.strokeLineCap) {
                                StrokeCap.Round -> Paint.Cap.ROUND
                                StrokeCap.Square -> Paint.Cap.SQUARE
                                else -> Paint.Cap.BUTT
                            }
                            strokeJoin = when (node.strokeLineJoin) {
                                StrokeJoin.Round -> Paint.Join.ROUND
                                StrokeJoin.Bevel -> Paint.Join.BEVEL
                                else -> Paint.Join.MITER
                            }
                        },
                    )
                }
            }
        }
    }
    c.restore()
}
