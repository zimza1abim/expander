package com.rrajath.expander.service

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Opaque reading surface with continuous corners and a restrained edge highlight. */
internal class FluidSuggestionSurface(
    private val topColor: Int,
    private val bottomColor: Int,
    private val edgeColor: Int,
    private val radius: Float,
    private val hairline: Float
) : Drawable() {
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = hairline
    }

    override fun onBoundsChange(bounds: Rect) {
        val inset = hairline / 2f
        val l = bounds.left + inset
        val t = bounds.top + inset
        val r = bounds.right - inset
        val b = bounds.bottom - inset
        val corner = minOf(radius, (r - l) / 2f, (b - t) / 2f)
        // Flatter shoulders than circular corners, flowing into the vertical edges.
        val c = corner * 0.22f
        path.reset()
        path.moveTo(l + corner, t)
        path.lineTo(r - corner, t)
        path.cubicTo(r - c, t, r, t + c, r, t + corner)
        path.lineTo(r, b - corner)
        path.cubicTo(r, b - c, r - c, b, r - corner, b)
        path.lineTo(l + corner, b)
        path.cubicTo(l + c, b, l, b - c, l, b - corner)
        path.lineTo(l, t + corner)
        path.cubicTo(l, t + c, l + c, t, l + corner, t)
        path.close()
        fill.shader = LinearGradient(l, t, r * 0.35f, b,
            topColor, bottomColor, Shader.TileMode.CLAMP)
        edge.shader = LinearGradient(l, t, r, b,
            intArrayOf(edgeColor, edgeColor and 0x00FFFFFF or 0x40000000, edgeColor),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, fill)
        canvas.drawPath(path, edge)
    }

    override fun getOutline(outline: Outline) { outline.setPath(path) }
    override fun setAlpha(alpha: Int) { fill.alpha = alpha; edge.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { fill.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
