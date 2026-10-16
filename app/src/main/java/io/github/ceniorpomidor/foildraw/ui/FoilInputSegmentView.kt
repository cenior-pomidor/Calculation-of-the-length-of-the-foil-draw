package io.github.ceniorpomidor.foildraw.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import io.github.ceniorpomidor.foildraw.R

/**
 * Участок схемы ввода: кусок ленты фольги с элементом или промежутком
 * и размерной стрелкой справа, рядом с которой стоит поле ввода.
 */
class FoilInputSegmentView(context: Context, private val kind: Kind) : View(context) {

    enum class Kind { LEAD_IN, ELEMENT, GAP, LEAD_OUT }

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val elementFill = context.getColor(R.color.primary_container)
    private val elementLines = context.getColor(R.color.primary)
    private val rect = RectF()

    private val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = foilColor
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        foilPaint.shader = when (kind) {
            Kind.LEAD_IN -> LinearGradient(0f, 0f, 0f, h.toFloat(), 0, foilColor, Shader.TileMode.CLAMP)
            Kind.LEAD_OUT -> LinearGradient(0f, 0f, 0f, h.toFloat(), foilColor, 0, Shader.TileMode.CLAMP)
            else -> null
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val foilLeft = foilPaint.strokeWidth
        val foilRight = w - 34f * density
        val arrowX = w - 12f * density
        val half = drafting.dimensionLine.strokeWidth / 2

        canvas.drawLine(foilLeft, 0f, foilLeft, h, foilPaint)
        canvas.drawLine(foilRight, 0f, foilRight, h, foilPaint)

        when (kind) {
            Kind.ELEMENT -> {
                val inset = 10f * density
                rect.set(foilLeft + inset, half, foilRight - inset, h - half)
                drafting.hatch(canvas, rect, elementFill, elementLines)
                val extensionStart = rect.right + 3f * density
                val extensionEnd = arrowX + 6f * density
                drafting.extension(canvas, extensionStart, extensionEnd, half, foilColor)
                drafting.extension(canvas, extensionStart, extensionEnd, h - half, foilColor)
                drafting.arrow(canvas, arrowX, half, h - half, foilColor)
            }
            Kind.GAP -> drafting.arrow(canvas, arrowX, half, h - half, foilColor)
            Kind.LEAD_IN, Kind.LEAD_OUT -> Unit
        }
    }
}
