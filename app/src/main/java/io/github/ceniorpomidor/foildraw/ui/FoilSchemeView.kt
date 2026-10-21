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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Лента фольги от начала ролика после нескольких ударов с выбранной протяжкой.
 * Все оттиски одного удара одного цвета, у каждого удара свой цвет; номер удара
 * подписан на каждом оттиске, справа — размер протяжки. Схема целиком вписывается
 * в отведённую ей высоту и обрывается там, где легли бы следующие удары, — к этому
 * месту уже видно, как фольга используется в работе.
 */
class FoilSchemeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var pattern: FoilPattern? = null
    private var pull = 0.0
    private var strikes = 0
    private var spanMm = 0.0

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val tapeColor = context.getColor(R.color.outline)
    private val textColor = context.getColor(R.color.on_surface)
    private val surfaceColor = context.getColor(R.color.card)
    private val strikeColors = StrikeColors.of(context)
    private val padding = 10f * density

    private val rect = RectF()
    private val label = RectF()
    private val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fadePaint = Paint()
    private val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        strokeCap = Paint.Cap.ROUND
    }
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
        // Не больше ударов, чем цветов: иначе цвета пришлось бы повторять.
        strikes = (ceil(pattern.length / pull).toInt() + 2).coerceIn(3, strikeColors.size)
        spanMm = (strikes - 1) * pull + pattern.ends[0]
        contentDescription = context.getString(R.string.scheme_title) + ": " +
            context.getString(R.string.value_mm, Format.mm(pull))
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallback = (360f * density).roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(fallback, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val fade = (padding / h).coerceAtMost(0.3f)
        val stops = floatArrayOf(0f, fade, 1f - fade, 1f)
        foilPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0, foilColor, foilColor, 0), stops, Shader.TileMode.CLAMP)
        val tape = tapeColor and 0x00FFFFFF or 0x1A000000
        tapePaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0, tape, tape, 0), stops, Shader.TileMode.CLAMP)
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
        val scale = (height - 2 * padding) / spanMm
        fun y(mm: Double) = (padding + mm * scale).toFloat()
        val windowBottom = y(spanMm)

        // Оттиски в видимом окне: удар, верх и видимый низ.
        val visible = ArrayList<Impression>()
        for (strike in 1..strikes) {
            val shift = (strike - 1) * pull
            for (element in 0 until pattern.size) {
                if (pattern.starts[element] + shift >= spanMm) continue
                visible += Impression(strike, y(pattern.starts[element] + shift), y(pattern.ends[element] + shift), windowBottom)
            }
        }
        // Если подпись не помещается внутри самого тонкого оттиска, номера ударов
        // выносятся в столбец слева от ленты.
        val pillHeight = labelPaint.textSize * 1.45f
        val inline = visible.all { it.visibleBottom - it.top >= pillHeight }
        val gutter = if (inline) 0f else 30f * density

        val foilLeft = gutter + foilPaint.strokeWidth
        val foilRight = width - 64f * density
        val inset = 8f * density
        canvas.drawRect(foilLeft, 0f, foilRight, height.toFloat(), tapePaint)
        canvas.drawLine(foilLeft, 0f, foilLeft, height.toFloat(), foilPaint)
        canvas.drawLine(foilRight, 0f, foilRight, height.toFloat(), foilPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), windowBottom)
        for (impression in visible) {
            val color = strikeColors[impression.strike - 1]
            rect.set(foilLeft + inset, impression.top, foilRight - inset, impression.bottom)
            drafting.hatch(canvas, rect, color and 0x00FFFFFF or 0x4D000000, color)
            if (inline) drawLabels(canvas, impression.strike, color, impression.visibleBottom)
        }
        canvas.restore()
        if (!inline) drawGutterLabels(canvas, visible, gutter, foilLeft + inset)
        canvas.drawRect(0f, windowBottom - 36f * density, width.toFloat(), windowBottom + 1f, fadePaint)

        val arrowX = foilRight + 18f * density
        val pullTop = y(0.0)
        val pullBottom = y(pull)
        drafting.extension(canvas, foilRight - inset, arrowX + 6f * density, pullTop, foilColor)
        drafting.extension(canvas, foilRight - inset, arrowX + 6f * density, pullBottom, foilColor)
        drafting.arrow(canvas, arrowX, pullTop, pullBottom, foilColor, 1.4f)
        canvas.save()
        val middle = (pullTop + pullBottom) / 2
        val baseline = arrowX + 6f * density + dimensionText.textSize
        canvas.rotate(-90f, baseline, middle)
        canvas.drawText(context.getString(R.string.value_mm, Format.mm(pull)), baseline, middle, dimensionText)
        canvas.restore()
    }

    private class Impression(val strike: Int, val top: Float, val bottom: Float, windowBottom: Float) {
        val visibleBottom = min(bottom, windowBottom)
        val center get() = (top + visibleBottom) / 2
    }

    /**
     * Номера ударов в кружках столбцом слева от ленты, с выноской к своему оттиску.
     * Кружки стоят напротив оттисков, а где те ближе друг к другу, чем размер
     * кружка, — раздвигаются, чтобы не налезать.
     */
    private fun drawGutterLabels(canvas: Canvas, impressions: List<Impression>, gutter: Float, tapeX: Float) {
        val sorted = impressions.filter { it.visibleBottom > it.top }.sortedBy { it.center }
        if (sorted.isEmpty()) return
        val top = padding
        val bottom = height - padding
        var radius = 9f * density
        if (sorted.size * (2 * radius + 2f * density) > bottom - top) radius = 7f * density
        val step = 2 * radius + 2f * density
        val positions = FloatArray(sorted.size) { sorted[it].center }
        for (i in positions.indices) {
            val floor = if (i == 0) top + radius else positions[i - 1] + step
            positions[i] = maxOf(positions[i], floor)
        }
        for (i in positions.indices.reversed()) {
            val ceiling = if (i == positions.lastIndex) bottom - radius else positions[i + 1] - step
            positions[i] = minOf(positions[i], ceiling)
        }
        val badgeX = gutter / 2 - 1f * density
        sorted.forEachIndexed { i, impression ->
            val color = strikeColors[impression.strike - 1]
            leaderPaint.color = color
            canvas.drawLine(badgeX + radius, positions[i], tapeX, impression.center, leaderPaint)
            drafting.badge(canvas, badgeX, positions[i], radius, color, impression.strike)
        }
    }

    /** Номер удара в кружке его цвета слева и «удар N» по центру видимой части оттиска. */
    private fun drawLabels(canvas: Canvas, strike: Int, color: Int, visibleBottom: Float) {
        val visibleHeight = visibleBottom - rect.top
        val centerY = (rect.top + visibleBottom) / 2
        val badgeRadius = min(9f * density, visibleHeight / 2 - 1f * density)
        if (badgeRadius >= 5f * density) {
            drafting.badge(canvas, rect.left + 8f * density + badgeRadius, centerY, badgeRadius, color, strike)
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

}
