package com.cybersentinel.phoneguard.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * Diagramma a ciambella (donut) composto da segmenti proporzionali.
 * View leggera su Canvas, senza dipendenze esterne. Il totale al centro
 * è impostabile come etichetta.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Segment(val value: Float, val color: Int)

    private var segments: List<Segment> = emptyList()
    private var centerLabel: String = ""

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33888888
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = themeColor(com.google.android.material.R.attr.colorOnSurface)
    }
    private val oval = RectF()

    fun setData(segments: List<Segment>, centerLabel: String = "") {
        this.segments = segments.filter { it.value > 0 }
        this.centerLabel = centerLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val stroke = size * 0.18f
        segmentPaint.strokeWidth = stroke
        emptyPaint.strokeWidth = stroke

        val r = (size - stroke) / 2f - 2f
        oval.set(cx - r, cy - r, cx + r, cy + r)

        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) {
            canvas.drawArc(oval, 0f, 360f, false, emptyPaint)
        } else {
            var start = -90f
            val gap = if (segments.size > 1) 2f else 0f
            for (seg in segments) {
                val sweep = 360f * seg.value / total
                segmentPaint.color = seg.color
                canvas.drawArc(oval, start + gap / 2, sweep - gap, false, segmentPaint)
                start += sweep
            }
        }

        if (centerLabel.isNotEmpty()) {
            textPaint.textSize = size * 0.16f
            val y = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(centerLabel, cx, y, textPaint)
        }
    }

    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
