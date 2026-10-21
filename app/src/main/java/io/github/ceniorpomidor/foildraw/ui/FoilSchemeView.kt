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
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import io.github.ceniorpomidor.foildraw.R
import io.github.ceniorpomidor.foildraw.calc.FoilPattern
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Бесконечная лента фольги от начала ролика: оттиски ударов с выбранной протяжкой
 * дорисовываются по мере прокрутки вниз. Все оттиски одного удара одного цвета,
 * соседние удары — разных цветов, на каждом оттиске подписан номер удара.
 * Справа у первых ударов — размер протяжки.
 */
class FoilSchemeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var pattern: FoilPattern? = null
    private var pull = 0.0
    private var scale = 0f

    /** Насколько лента прокручена вниз, в пикселях. */
    var offset = 0f
        private set

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val tapeColor = context.getColor(R.color.outline)
    private val textColor = context.getColor(R.color.on_surface)
    private val surfaceColor = context.getColor(R.color.card)
    private val strikeColors = StrikeColors.of(context)
    private val padding = 10f * density

    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var velocity: VelocityTracker? = null
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private val rect = RectF()
    private val label = RectF()
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = foilColor
    }
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tapeColor and 0x00FFFFFF or 0x1A000000
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgeFadePaint = Paint()
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
        val old = this.pattern
        val changed = old == null || this.pull != pull ||
            !old.starts.contentEquals(pattern.starts) || !old.ends.contentEquals(pattern.ends)
        this.pattern = pattern
        this.pull = pull
        updateScale()
        if (changed) {
            scroller.forceFinished(true)
            offset = 0f
        }
        contentDescription = context.getString(R.string.scheme_title) + ": " +
            context.getString(R.string.value_mm, Format.mm(pull))
        invalidate()
    }

    /** Масштаб: самый тонкий элемент вмещает подпись, но рисунок с двумя протяжками не мельче окна. */
    private fun updateScale() {
        val pattern = pattern ?: return
        val smallest = (0 until pattern.size).minOf { pattern.ends[it] - pattern.starts[it] }
        val readable = labelPaint.textSize * 1.8 / smallest
        val fitting = if (height > 0) (height - 2 * padding) / (pattern.length + 2 * pull) else 0.0
        scale = max(readable, fitting).toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallback = (360f * density).roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(fallback, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateScale()
        // Верх и низ окна растворяются в фоне: лента уходит за край.
        val fade = (18f * density / h).coerceAtMost(0.2f)
        val clear = surfaceColor and 0x00FFFFFF
        edgeFadePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(surfaceColor, clear, clear, surfaceColor),
            floatArrayOf(0f, fade, 1f - fade, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downY = event.y
                lastY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - downY) > touchSlop) {
                    dragging = true
                    lastY = event.y
                    // Вертикальная прокрутка ленты — не повод листать экраны.
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    scrollBy(lastY - event.y)
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    tracker.computeCurrentVelocity(1000, maxFling.toFloat())
                    scroller.fling(0, offset.roundToInt(), 0, -tracker.yVelocity.roundToInt(), 0, 0, 0, Int.MAX_VALUE / 2)
                    postInvalidateOnAnimation()
                }
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL -> recycleVelocity()
        }
        return true
    }

    /** Прокручивает ленту на [dy] пикселей вниз (отрицательное — вверх, не выше начала ролика). */
    fun scrollBy(dy: Float) {
        offset = max(0f, offset + dy)
        invalidate()
    }

    private fun recycleVelocity() {
        velocity?.recycle()
        velocity = null
        dragging = false
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offset = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    /** Номера ударов, оттиски которых сейчас видны в окне. */
    fun visibleStrikes(): IntRange {
        val pattern = pattern ?: return IntRange.EMPTY
        val from = (offset - padding) / scale
        val to = (offset - padding + height) / scale
        return max(1, floor((from - pattern.length) / pull).toInt() + 1)..(floor(to / pull).toInt() + 1)
    }

    override fun onDraw(canvas: Canvas) {
        val pattern = pattern ?: return
        if (scale <= 0f) updateScale()
        fun y(mm: Double) = (padding + mm * scale - offset).toFloat()

        val tapeLeft = edgePaint.strokeWidth
        val tapeRight = width - 64f * density
        val inset = 8f * density
        canvas.drawRect(tapeLeft, 0f, tapeRight, height.toFloat(), tapePaint)
        canvas.drawLine(tapeLeft, 0f, tapeLeft, height.toFloat(), edgePaint)
        canvas.drawLine(tapeRight, 0f, tapeRight, height.toFloat(), edgePaint)

        // Рисуются только удары, оттиски которых попадают в окно, — лента бесконечна.
        for (strike in visibleStrikes()) {
            val shift = (strike - 1) * pull
            val color = strikeColors[(strike - 1) % strikeColors.size]
            for (element in 0 until pattern.size) {
                rect.set(tapeLeft + inset, y(pattern.starts[element] + shift), tapeRight - inset, y(pattern.ends[element] + shift))
                if (rect.bottom < 0f || rect.top > height) continue
                drafting.hatch(canvas, rect, color and 0x00FFFFFF or 0x4D000000, color)
                drawLabel(canvas, strike)
            }
        }

        val arrowX = tapeRight + 18f * density
        val pullTop = y(0.0)
        val pullBottom = y(pull)
        if (pullBottom > 0f) {
            drafting.extension(canvas, tapeRight - inset, arrowX + 6f * density, pullTop, foilColor)
            drafting.extension(canvas, tapeRight - inset, arrowX + 6f * density, pullBottom, foilColor)
            drafting.arrow(canvas, arrowX, pullTop, pullBottom, foilColor, 1.4f)
            canvas.save()
            val middle = (pullTop + pullBottom) / 2
            val baseline = arrowX + 6f * density + dimensionText.textSize
            canvas.rotate(-90f, baseline, middle)
            canvas.drawText(context.getString(R.string.value_mm, Format.mm(pull)), baseline, middle, dimensionText)
            canvas.restore()
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), edgeFadePaint)
    }

    /** «удар N» по центру оттиска, на подложке, чтобы читалось поверх штриховки. */
    private fun drawLabel(canvas: Canvas, strike: Int) {
        val pillHeight = labelPaint.textSize * 1.45f
        if (rect.height() < pillHeight) return
        val text = context.getString(R.string.strike_label, strike)
        val halfWidth = labelPaint.measureText(text) / 2 + 7f * density
        label.set(rect.centerX() - halfWidth, rect.centerY() - pillHeight / 2, rect.centerX() + halfWidth, rect.centerY() + pillHeight / 2)
        fillPaint.color = surfaceColor and 0x00FFFFFF or 0xE6000000.toInt()
        canvas.drawRoundRect(label, pillHeight / 2, pillHeight / 2, fillPaint)
        val metrics = labelPaint.fontMetrics
        canvas.drawText(text, rect.centerX(), rect.centerY() - (metrics.ascent + metrics.descent) / 2, labelPaint)
    }
}
