package com.frost.tvlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

/** Grows a view slightly while it has D-pad focus. */
fun View.focusScale(scale: Float = 1.07f) {
    setOnFocusChangeListener { v, hasFocus ->
        val s = if (hasFocus) scale else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(120).start()
    }
}

/**
 * "Frosted glass" look: translucent white panels with a soft top sheen and a thin light edge.
 * (Real background blur is not available on most Android TV versions, so the frost is simulated.)
 */
object Glass {
    val ACCENT: Int = Color.rgb(110, 220, 255)

    fun panel(
        ctx: Context,
        radiusDp: Int,
        fillTop: Int,
        stroke: Int,
        strokeDp: Int = 1,
        fillBottom: Int = fillTop
    ): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = ctx.dpf(radiusDp.toFloat())
        if (fillBottom != fillTop) {
            d.orientation = GradientDrawable.Orientation.TOP_BOTTOM
            d.colors = intArrayOf(fillTop, fillBottom)
        } else {
            d.setColor(fillTop)
        }
        d.setStroke(ctx.dp(strokeDp), stroke)
        return d
    }

    /** Normal / focused / selected states. "Selected" is used for move mode and active tabs. */
    fun focusable(ctx: Context, radiusDp: Int = 20): Drawable {
        val sl = StateListDrawable()
        sl.addState(
            intArrayOf(android.R.attr.state_selected, android.R.attr.state_focused),
            panel(ctx, radiusDp, 0x66FFFFFF, ACCENT, 3)
        )
        sl.addState(
            intArrayOf(android.R.attr.state_selected),
            panel(ctx, radiusDp, 0x40FFFFFF, ACCENT, 2)
        )
        sl.addState(
            intArrayOf(android.R.attr.state_focused),
            panel(ctx, radiusDp, 0x59FFFFFF, Color.WHITE, 3)
        )
        sl.addState(
            intArrayOf(),
            panel(ctx, radiusDp, 0x38FFFFFF, 0x3DFFFFFF, 1, 0x14FFFFFF)
        )
        return sl
    }
}
