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
import com.composables.icons.lucide.*

/** Curated Lucide icons for activities, keyed by the name stored in the DB. Order = picker order. */
val ActivityIcons: Map<String, ImageVector> = linkedMapOf(
    "Moon" to Lucide.Moon, "Bed" to Lucide.Bed, "Briefcase" to Lucide.Briefcase, "Laptop" to Lucide.Laptop,
    "Code" to Lucide.Code, "Mail" to Lucide.Mail, "Phone" to Lucide.Phone, "MessageCircle" to Lucide.MessageCircle,
    "Users" to Lucide.Users, "NotebookPen" to Lucide.NotebookPen, "BookOpen" to Lucide.BookOpen,
    "GraduationCap" to Lucide.GraduationCap, "Brain" to Lucide.Brain, "Languages" to Lucide.Languages,
    "Dumbbell" to Lucide.Dumbbell, "Footprints" to Lucide.Footprints, "Bike" to Lucide.Bike,
    "PersonStanding" to Lucide.PersonStanding, "Waves" to Lucide.Waves, "Utensils" to Lucide.Utensils,
    "Coffee" to Lucide.Coffee, "CookingPot" to Lucide.CookingPot, "ShoppingCart" to Lucide.ShoppingCart,
    "Brush" to Lucide.Brush, "Shirt" to Lucide.Shirt, "ShowerHead" to Lucide.ShowerHead, "Hammer" to Lucide.Hammer,
    "Wrench" to Lucide.Wrench, "Car" to Lucide.Car, "Bus" to Lucide.Bus, "TrainFront" to Lucide.TrainFront,
    "Plane" to Lucide.Plane, "House" to Lucide.House, "Baby" to Lucide.Baby, "Heart" to Lucide.Heart,
    "Dog" to Lucide.Dog, "Cat" to Lucide.Cat, "Tv" to Lucide.Tv, "Gamepad2" to Lucide.Gamepad2,
    "Clapperboard" to Lucide.Clapperboard, "Music" to Lucide.Music, "Headphones" to Lucide.Headphones,
    "Guitar" to Lucide.Guitar, "Mic" to Lucide.Mic, "Palette" to Lucide.Palette, "Camera" to Lucide.Camera,
    "Trees" to Lucide.Trees, "Sprout" to Lucide.Sprout, "Flower2" to Lucide.Flower2, "Sun" to Lucide.Sun,
    "Stethoscope" to Lucide.Stethoscope, "Pill" to Lucide.Pill, "Wallet" to Lucide.Wallet,
    "Smartphone" to Lucide.Smartphone, "PartyPopper" to Lucide.PartyPopper, "Sparkles" to Lucide.Sparkles,
    "Puzzle" to Lucide.Puzzle, "Star" to Lucide.Star, "Circle" to Lucide.Circle,
)

fun activityIcon(name: String): ImageVector = ActivityIcons[name] ?: Lucide.Circle

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
