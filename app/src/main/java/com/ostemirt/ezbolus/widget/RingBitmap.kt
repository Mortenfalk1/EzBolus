package com.ostemirt.ezbolus.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Draws the IOB ring as a Bitmap. Glance doesn't expose a Canvas composable,
 * so the ring is baked into an Image the widget shows.
 *
 * `arcFraction` in [0, 1] is the fraction of the ring to fill (0 = empty,
 * 1 = full loop).
 */
object RingBitmap {

    fun render(
        iobText: String,
        arcFraction: Float,
        trackColor: Int,
        arcColor: Int,
        textColor: Int,
        sizePx: Int = 256,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val stroke = sizePx * 0.09f
        val inset = stroke / 2f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = trackColor
        }
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        if (arcFraction > 0f) {
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = arcColor
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(
                rect,
                -90f,
                360f * arcFraction.coerceIn(0f, 1f),
                false,
                arcPaint,
            )
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val fm = textPaint.fontMetrics
        val textY = sizePx / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(iobText, sizePx / 2f, textY, textPaint)

        return bitmap
    }
}
