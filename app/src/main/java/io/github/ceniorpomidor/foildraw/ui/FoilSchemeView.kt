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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Лента фольги от начала ролика после нескольких ударов с выбранной протяжкой.
 * Каждый оттиск подписан номером удара, цвет и номер в кружке — элемент клише,
 * первый удар заштрихован, справа — размер протяжки. Схема обрывается там, где
 * легли бы следующие удары, — к этому месту уже видно, как фольга используется в работе.
 */
class FoilSchemeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var pattern: FoilPattern? = null
    private var pull = 0.0
    private var strikes = 0
    private var spanMm = 0.0
    private var scale = 0f

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val textColor = context.getColor(R.color.on_surface)
    private val surfaceColor = context.getColor(R.color.card)
    private val elementColors = ElementColors.of(context)
    private val padding = 10f * density

    private val rect = RectF()
    private val label = RectF()
    private val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val fadePaint = Paint()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = textColor
    }
    private val dimensionText = Paint(labelPaint).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }

    fun show(pattern: FoilPattern, pull: Double) {
        this.pattern = pattern
        this.pull = pull
        // Столько ударов, чтобы между началом ленты и обрывом поместился полный шаг работы.
        strikes = (ceil(pattern.length / pull).toInt() + 2).coerceIn(3, MAX_STRIKES)
        spanMm = (strikes - 1) * pull + pattern.ends[0]
        val smallest = (0 until pattern.size).minOf { pattern.ends[it] - pattern.starts[it] }
        val wanted = max(MIN_ELEMENT_DP / smallest, MIN_HEIGHT_DP / spanMm)
        scale = (min(wanted, MAX_HEIGHT_DP / spanMm) * density).toFloat()
        contentDescription = context.getString(R.string.scheme_title) + ": " +
            context.getString(R.string.value_mm, Format.mm(pull))
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (pattern == null) 0 else (2 * padding + spanMm * scale).roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val fade = (padding / h).coerceAtMost(0.3f)
        foilPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0, foilColor, foilColor, 0),
            floatArrayOf(0f, fade, 1f - fade, 1f),
            Shader.TileMode.CLAMP,
        )
        // Низ схемы растворяется в фоне: лента продолжается дальше.
        val fadeHeight = 36f * density
        fadePaint.shader = LinearGradient(
            0f, h - padding - fadeHeight, 0f, h - padding,
            surfaceColor and 0x00FFFFFF, surfaceColor,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val pattern = pattern ?: return
        fun y(mm: Double) = (padding + mm * scale).toFloat()
        val windowBottom = y(spanMm)

        val foilLeft = foilPaint.strokeWidth
        val foilRight = width - 64f * density
        val inset = 8f * density
        canvas.drawLine(foilLeft, 0f, foilLeft, height.toFloat(), foilPaint)
        canvas.drawLine(foilRight, 0f, foilRight, height.toFloat(), foilPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), windowBottom)
        for (strike in 1..strikes) {
            val shift = (strike - 1) * pull
            for (element in 0 until pattern.size) {
                if (pattern.starts[element] + shift >= spanMm) continue
                val color = elementColors[element % elementColors.size]
                rect.set(foilLeft + inset, y(pattern.starts[element] + shift), foilRight - inset, y(pattern.ends[element] + shift))
                val radius = minOf(5f * density, rect.height() / 2)
                if (strike == 1) {
                    drafting.hatch(canvas, rect, color and 0x00FFFFFF or 0x4D000000, color)
                } else {
                    fillPaint.color = color and 0x00FFFFFF or 0x4D000000
                    canvas.drawRoundRect(rect, radius, radius, fillPaint)
                    outlinePaint.color = color
                    canvas.drawRoundRect(rect, radius, radius, outlinePaint)
                }
                drawLabels(canvas, strike, element, color, min(rect.bottom, windowBottom))
            }
        }
        canvas.restore()
        canvas.drawRect(0f, windowBottom - 36f * density, width.toFloat(), windowBottom + 1f, fadePaint)

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

    /** Номер элемента в кружке слева и «удар N» по центру видимой части оттиска. */
    private fun drawLabels(canvas: Canvas, strike: Int, element: Int, color: Int, visibleBottom: Float) {
        val visibleHeight = visibleBottom - rect.top
        val centerY = (rect.top + visibleBottom) / 2
        val badgeRadius = min(9f * density, visibleHeight / 2 - 1f * density)
        if (badgeRadius >= 6f * density) {
            drafting.badge(canvas, rect.left + 8f * density + badgeRadius, centerY, badgeRadius, color, element + 1)
        }
        val pillHeight = labelPaint.textSize * 1.45f
        if (visibleHeight < pillHeight) return
        val text = context.getString(R.string.strike_label, strike)
        val halfWidth = labelPaint.measureText(text) / 2 + 7f * density
        // Подложка, чтобы подпись читалась поверх штриховки и цветной заливки.
        label.set(rect.centerX() - halfWidth, centerY - pillHeight / 2, rect.centerX() + halfWidth, centerY + pillHeight / 2)
        fillPaint.color = surfaceColor and 0x00FFFFFF or 0xE6000000.toInt()
        canvas.drawRoundRect(label, pillHeight / 2, pillHeight / 2, fillPaint)
        val metrics = labelPaint.fontMetrics
        canvas.drawText(text, rect.centerX(), centerY - (metrics.ascent + metrics.descent) / 2, labelPaint)
    }

    private companion object {
        const val MAX_STRIKES = 14
        const val MIN_ELEMENT_DP = 26.0
        const val MIN_HEIGHT_DP = 300.0
        const val MAX_HEIGHT_DP = 1000.0
    }
}
