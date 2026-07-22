package com.cybersentinel.phoneguard.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min

/**
 * Anello di stato: un arco circolare che rappresenta una percentuale, con
 * il valore al centro. View leggera disegnata su Canvas, senza dipendenze.
 */
class RingGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var percent = 0f
    private var arcColor = 0xFF00E676.toInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = themeColor(com.google.android.material.R.attr.colorOnSurface)
    }
    private val oval = RectF()

    fun setValue(pct: Int, color: Int) {
        percent = pct.coerceIn(0, 100).toFloat()
        arcColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val stroke = size * 0.11f
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        trackPaint.color = ColorUtils.setAlphaComponent(arcColor, 55)
        arcPaint.color = arcColor

        val r = (size - stroke) / 2f - 2f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        canvas.drawArc(oval, -90f, 360f * percent / 100f, false, arcPaint)

        textPaint.textSize = size * 0.26f
        val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("${percent.toInt()}%", cx, y, textPaint)
    }

    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
