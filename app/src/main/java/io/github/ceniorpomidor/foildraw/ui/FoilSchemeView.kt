package io.github.ceniorpomidor.foildraw.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import io.github.ceniorpomidor.foildraw.R
import io.github.ceniorpomidor.foildraw.calc.FoilPattern
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Участок фольги в установившемся режиме: оттиски соседних ударов при
 * выбранной протяжке. Один удар заштрихован, остальные пронумерованы
 * в порядке ударов, справа — размер протяжки.
 */
class FoilSchemeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var pattern: FoilPattern? = null
    private var pull = 0.0

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val primary = context.getColor(R.color.primary)
    private val primaryContainer = context.getColor(R.color.primary_container)
    private val onPrimaryContainer = context.getColor(R.color.on_primary_container)
    private val strikeColor = context.getColor(R.color.strike)
    private val onStrike = context.getColor(R.color.on_strike)

    private val rect = RectF()
    private val badge = RectF()

    private val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val dimensionText = Paint(labelPaint).apply {
        color = context.getColor(R.color.on_surface)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }

    fun show(pattern: FoilPattern, pull: Double) {
        this.pattern = pattern
        this.pull = pull
        contentDescription = context.getString(R.string.scheme_title) + ": " +
            context.getString(R.string.value_mm, Format.mm(pull))
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val fade = (24f * density / h).coerceAtMost(0.3f)
        foilPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0, foilColor, foilColor, 0),
            floatArrayOf(0f, fade, 1f - fade, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val pattern = pattern ?: return
        val margin = pattern.length * 0.08
        val from = -margin
        val to = max(pattern.length, pull) + margin
        val top = 4f * density
        val bottom = height - 4f * density
        val scale = (bottom - top) / (to - from)
        fun y(mm: Double) = (top + (mm - from) * scale).toFloat()

        val foilLeft = foilPaint.strokeWidth
        val foilRight = minOf(width - 64f * density, foilLeft + 280f * density)
        val inset = 8f * density
        canvas.drawLine(foilLeft, 0f, foilLeft, height.toFloat(), foilPaint)
        canvas.drawLine(foilRight, 0f, foilRight, height.toFloat(), foilPaint)

        val strikes = (floor((from - pattern.length) / pull).toInt()..ceil(to / pull).toInt())
            .filter { strike -> (0 until pattern.size).any { visible(pattern, it, strike * pull, from, to) } }

        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), bottom)
        strikes.forEachIndexed { order, strike ->
            for (element in 0 until pattern.size) {
                if (!visible(pattern, element, strike * pull, from, to)) continue
                rect.set(
                    foilLeft + inset,
                    y(pattern.starts[element] + strike * pull),
                    foilRight - inset,
                    y(pattern.ends[element] + strike * pull),
                )
                if (strike == 0) {
                    drafting.hatch(canvas, rect, primaryContainer, primary)
                } else {
                    val radius = minOf(6f * density, rect.height() / 2)
                    fillPaint.color = strikeColor
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                }
                drawLabel(canvas, (order + 1).toString(), strike == 0)
            }
        }
        canvas.restore()

        val arrowX = foilRight + 18f * density
        val pullTop = y(0.0)
        val pullBottom = y(pull)
        drafting.extension(canvas, foilRight - inset, arrowX + 6f * density, pullTop, foilColor)
        drafting.extension(canvas, foilRight - inset, arrowX + 6f * density, pullBottom, foilColor)
        drafting.arrow(canvas, arrowX, pullTop, pullBottom, foilColor)
        canvas.save()
        val middle = (pullTop + pullBottom) / 2
        val baseline = arrowX + 6f * density + dimensionText.textSize
        canvas.rotate(-90f, baseline, middle)
        canvas.drawText(context.getString(R.string.value_mm, Format.mm(pull)), baseline, middle, dimensionText)
        canvas.restore()
    }

    private fun visible(pattern: FoilPattern, element: Int, shift: Double, from: Double, to: Double) =
        pattern.ends[element] + shift > from && pattern.starts[element] + shift < to

    private fun drawLabel(canvas: Canvas, text: String, highlighted: Boolean) {
        if (rect.height() < labelPaint.textSize * 1.1f) return
        val metrics = labelPaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2
        if (highlighted) {
            val halfWidth = labelPaint.measureText(text) / 2 + 6f * density
            val halfHeight = labelPaint.textSize * 0.75f
            badge.set(rect.centerX() - halfWidth, rect.centerY() - halfHeight, rect.centerX() + halfWidth, rect.centerY() + halfHeight)
            fillPaint.color = primaryContainer
            canvas.drawRoundRect(badge, halfHeight, halfHeight, fillPaint)
            labelPaint.color = onPrimaryContainer
        } else {
            labelPaint.color = onStrike
        }
        canvas.drawText(text, rect.centerX(), baseline, labelPaint)
    }
}
