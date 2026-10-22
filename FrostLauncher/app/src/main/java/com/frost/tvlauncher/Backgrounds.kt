package com.frost.tvlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** A soft colored light spot. cx/cy/r are fractions of the screen. */
class Glow(val cx: Float, val cy: Float, val r: Float, val color: Int)

/** A background: a diagonal gradient plus optional glows. Wallpapers have glows, palettes don't. */
class Bg(val id: String, val name: String, val colors: IntArray, val glows: List<Glow> = emptyList())

private fun c(v: Long): Int = v.toInt()

object Backgrounds {

    val wallpapers: List<Bg> = listOf(
        Bg(
            "wp_aurora", "Aurora", intArrayOf(c(0xFF0B1026), c(0xFF1A2252)),
            listOf(
                Glow(0.20f, 0.30f, 0.55f, c(0x8838E8C6)),
                Glow(0.85f, 0.20f, 0.50f, c(0x887C4DFF)),
                Glow(0.60f, 1.00f, 0.60f, c(0x6600B0FF))
            )
        ),
        Bg(
            "wp_dusk", "Dusk", intArrayOf(c(0xFF1B0F2E), c(0xFF4A1942)),
            listOf(
                Glow(0.15f, 0.90f, 0.60f, c(0x88FF6B6B)),
                Glow(0.90f, 0.30f, 0.55f, c(0x77B388FF))
            )
        ),
        Bg(
            "wp_ocean", "Ocean", intArrayOf(c(0xFF031A2E), c(0xFF0A4D68)),
            listOf(
                Glow(0.80f, 0.15f, 0.50f, c(0x7700E5FF)),
                Glow(0.20f, 0.85f, 0.60f, c(0x663D5AFE))
            )
        ),
        Bg(
            "wp_forest", "Forest", intArrayOf(c(0xFF06150F), c(0xFF14402B)),
            listOf(
                Glow(0.25f, 0.20f, 0.55f, c(0x7766FF99)),
                Glow(0.85f, 0.90f, 0.50f, c(0x5500BFA5))
            )
        ),
        Bg(
            "wp_sunset", "Sunset", intArrayOf(c(0xFF2A0E26), c(0xFF7A2E3A)),
            listOf(
                Glow(0.75f, 0.85f, 0.70f, c(0x99FFA94D)),
                Glow(0.20f, 0.20f, 0.50f, c(0x66FF5C8A))
            )
        ),
        Bg(
            "wp_midnight", "Midnight", intArrayOf(c(0xFF05060D), c(0xFF141A33)),
            listOf(Glow(0.50f, 0.10f, 0.60f, c(0x554F6BFF)))
        ),
        Bg(
            "wp_lavender", "Lavender", intArrayOf(c(0xFF1E1535), c(0xFF3F2D73)),
            listOf(
                Glow(0.30f, 0.25f, 0.55f, c(0x88C58BFF)),
                Glow(0.85f, 0.85f, 0.50f, c(0x6640C4FF))
            )
        ),
        Bg(
            "wp_ember", "Ember", intArrayOf(c(0xFF140A0A), c(0xFF3A1410)),
            listOf(
                Glow(0.80f, 0.20f, 0.55f, c(0x88FF7A1A)),
                Glow(0.15f, 0.85f, 0.50f, c(0x66FF3D3D))
            )
        )
    )

    val palettes: List<Bg> = listOf(
        Bg("pal_graphite", "Graphite", intArrayOf(c(0xFF1A1A1F), c(0xFF3B3B46))),
        Bg("pal_slate", "Slate", intArrayOf(c(0xFF1E293B), c(0xFF475569))),
        Bg("pal_indigo", "Indigo", intArrayOf(c(0xFF1E1B4B), c(0xFF4338CA))),
        Bg("pal_emerald", "Emerald", intArrayOf(c(0xFF052E26), c(0xFF0F766E))),
        Bg("pal_rose", "Rose", intArrayOf(c(0xFF3B0A24), c(0xFFBE185D))),
        Bg("pal_amber", "Amber", intArrayOf(c(0xFF2B1A05), c(0xFFB45309))),
        Bg("pal_teal", "Teal", intArrayOf(c(0xFF042F3A), c(0xFF0891B2))),
        Bg("pal_violet", "Violet", intArrayOf(c(0xFF2E1065), c(0xFF7C3AED))),
        Bg("pal_neon", "Neon", intArrayOf(c(0xFF0F172A), c(0xFF7C3AED), c(0xFF06B6D4))),
        Bg("pal_black", "Pure Black", intArrayOf(c(0xFF000000), c(0xFF0D0D0D)))
    )

    private val all: List<Bg> = wallpapers + palettes

    fun byId(id: String?): Bg = all.firstOrNull { it.id == id } ?: wallpapers[0]

    fun draw(canvas: Canvas, w: Float, h: Float, bg: Bg) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, bg.colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        for (g in bg.glows) {
            val rad = g.r * maxOf(w, h)
            val x = g.cx * w
            val y = g.cy * h
            p.shader = RadialGradient(x, y, rad, g.color, g.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, rad, p)
        }
        p.shader = null
    }
}

/** Full-screen background. */
class BackgroundView(context: Context) : View(context) {
    var bg: Bg = Backgrounds.wallpapers[0]
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        Backgrounds.draw(canvas, width.toFloat(), height.toFloat(), bg)
    }
}

/** Small rounded preview used in the Themes picker. */
class PreviewView(context: Context, private val bg: Bg) : View(context) {
    private val clip = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        clip.reset()
        val r = context.dpf(12f)
        clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clip)
        Backgrounds.draw(canvas, width.toFloat(), height.toFloat(), bg)
        canvas.restore()
    }
}
