package io.github.ceniorpomidor.foildraw.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.EditText
import io.github.ceniorpomidor.foildraw.R
import kotlin.math.roundToInt

/**
 * Схема ввода: лента фольги с элементами и промежутками строго в масштабе
 * введённых размеров, справа — столбец полей. Поле стоит напротив середины своего
 * участка, к нему от размерной стрелки идёт выноска; если участки тоньше полей,
 * поля раздвигаются, а выноски идут с изломом. При вводе схема плавно перестраивается. Участок, размер которого редактируется,
 * подсвечивается.
 */
class FoilInputView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val tapeColor = context.getColor(R.color.outline)
    private val primary = context.getColor(R.color.primary)
    private val onPrimary = context.getColor(R.color.on_primary)
    private val primaryContainer = context.getColor(R.color.primary_container)

    /** Средняя высота участка: столько, чтобы поля в среднем помещались без сдвига. */
    private val slot = 58f * density
    private val fieldGap = 6f * density
    private val lead = 20f * density
    private val boxWidth = resources.getDimensionPixelSize(R.dimen.box_width)
    private val boxHeight = resources.getDimensionPixelSize(R.dimen.box_height)

    /** Поля по порядку участков: элемент, промежуток, элемент, … */
    private val boxes = ArrayList<View>()
    private val lastSizes = ArrayList<Double>()
    private var dimensions = 0
    private var focused = -1

    // Доли участков в длине рисунка (нарастающим итогом); при изменении размеров перетекают от from к to.
    private var from = DoubleArray(0)
    private var to = DoubleArray(0)
    private var progress = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 260
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener {
            progress = it.animatedValue as Float
            placeBoxes()
            invalidate()
        }
    }

    private val rect = RectF()
    private val leader = Path()
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val extensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        pathEffect = DashPathEffect(floatArrayOf(3f * density, 3f * density), 0f)
    }
    private val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        setWillNotDraw(false)
    }

    /** Добавляет поле очередного участка; [initial] — размер для схемы, пока поле не заполнено. */
    fun addDimension(box: View, field: EditText, initial: Double) {
        val index = boxes.size
        boxes += box
        lastSizes += initial
        addView(box)
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) focused = index else if (focused == index) focused = -1
            invalidate()
        }
    }

    /**
     * Показывает первые [count] участков с размерами [values]. Для пустых и
     * нечисловых значений схема держит последний введённый размер.
     */
    fun show(count: Int, values: List<Double?>) {
        val shares = DoubleArray(count + 1)
        for (i in 0 until count) {
            values.getOrNull(i)?.takeIf { it >= 0.0 && it.isFinite() }?.let { lastSizes[i] = it }
            shares[i + 1] = shares[i] + lastSizes[i]
        }
        val total = shares[count]
        for (i in shares.indices) shares[i] = if (total > 0.0) shares[i] / total else i.toDouble() / count

        if (count != dimensions) {
            dimensions = count
            boxes.forEachIndexed { i, box -> box.visibility = if (i < count) View.VISIBLE else View.GONE }
            animator.cancel()
            from = shares
            to = shares
            progress = 1f
            requestLayout()
            invalidate()
        } else if (!to.contentEquals(shares)) {
            from = DoubleArray(to.size) { from[it] + (to[it] - from[it]) * progress }
            to = shares
            animator.cancel()
            progress = 0f
            animator.start()
        }
    }

    /** Границы участков в пикселях при текущем кадре анимации — строго в масштабе размеров. */
    private fun boundaries(): FloatArray {
        val span = dimensions * slot
        return FloatArray(dimensions + 1) { i -> lead + span * (from[i] + (to[i] - from[i]) * progress).toFloat() }
    }

    /**
     * Центры полей: напротив середины своего участка, а где участки тоньше поля —
     * раздвинуты ровно настолько, чтобы поля не налезали друг на друга.
     */
    private fun fieldCenters(bounds: FloatArray): FloatArray {
        val step = boxHeight + fieldGap
        val centers = FloatArray(dimensions) { (bounds[it] + bounds[it + 1]) / 2 }
        val top = boxHeight / 2f
        val bottom = height - boxHeight / 2f
        for (i in centers.indices) centers[i] = maxOf(centers[i], if (i == 0) top else centers[i - 1] + step)
        for (i in centers.indices.reversed()) centers[i] = minOf(centers[i], if (i == centers.lastIndex) bottom else centers[i + 1] - step)
        return centers
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val boxWidthSpec = MeasureSpec.makeMeasureSpec(boxWidth, MeasureSpec.EXACTLY)
        val boxHeightSpec = MeasureSpec.makeMeasureSpec(boxHeight, MeasureSpec.EXACTLY)
        for (i in 0 until dimensions) boxes[i].measure(boxWidthSpec, boxHeightSpec)
        val height = 2 * lead + dimensions * slot
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height.roundToInt(), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = placeBoxes()

    private fun placeBoxes() {
        if (dimensions == 0) return
        val centers = fieldCenters(boundaries())
        val left = width - boxWidth
        for (i in 0 until dimensions) {
            val top = centers[i].roundToInt() - boxHeight / 2
            boxes[i].layout(left, top, left + boxWidth, top + boxHeight)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val fade = (lead / h).coerceAtMost(0.3f)
        val stops = floatArrayOf(0f, fade, 1f - fade, 1f)
        edgePaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0, foilColor, foilColor, 0), stops, Shader.TileMode.CLAMP)
        val tape = tapeColor and 0x00FFFFFF or 0x1A000000
        tapePaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0, tape, tape, 0), stops, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (dimensions == 0) return
        val bounds = boundaries()
        val centers = fieldCenters(bounds)
        val fieldLeft = (width - boxWidth).toFloat()
        val arrowX = fieldLeft - 22f * density
        val tapeLeft = 2f * density
        val tapeRight = arrowX - 20f * density
        val inset = 10f * density

        // Лента фольги: полупрозрачная полоса с кромками.
        canvas.drawRect(tapeLeft, 0f, tapeRight, height.toFloat(), tapePaint)
        canvas.drawLine(tapeLeft, 0f, tapeLeft, height.toFloat(), edgePaint)
        canvas.drawLine(tapeRight, 0f, tapeRight, height.toFloat(), edgePaint)

        for (i in 0 until dimensions) {
            val top = bounds[i]
            val bottom = bounds[i + 1]
            val isElement = i % 2 == 0
            val isFocused = i == focused
            if (isElement) {
                rect.set(tapeLeft + inset, top, tapeRight - inset, bottom)
                if (isFocused) drafting.hatch(canvas, rect, primary, onPrimary) else drafting.hatch(canvas, rect, primaryContainer, primary)
            } else if (isFocused) {
                fillPaint.color = primary and 0x00FFFFFF or 0x24000000
                canvas.drawRect(tapeLeft + inset, top, tapeRight - inset, bottom, fillPaint)
            }
        }

        // Выносные линии от краёв элементов к размерной цепочке.
        extensionPaint.color = foilColor and 0x00FFFFFF or 0x99000000.toInt()
        for (boundary in bounds) {
            canvas.drawLine(tapeRight - inset, boundary, arrowX + 7f * density, boundary, extensionPaint)
        }

        val gap = 3f * density
        for (i in 0 until dimensions) {
            val isElement = i % 2 == 0
            val isFocused = i == focused
            val color = when {
                isFocused -> primary
                isElement -> primary
                else -> foilColor
            }
            drafting.arrow(canvas, arrowX, bounds[i] + gap, bounds[i + 1] - gap, color, if (isFocused) 2f else 1.4f)

            // Выноска от середины стрелки к полю: прямая, если поле напротив участка, иначе с изломом.
            val middle = (bounds[i] + bounds[i + 1]) / 2
            val target = centers[i]
            leaderPaint.color = color
            leaderPaint.strokeWidth = (if (isFocused) 2f else 1.2f) * density
            leader.reset()
            leader.moveTo(arrowX, middle)
            leader.lineTo(arrowX + 6f * density, middle)
            leader.lineTo(fieldLeft - 8f * density, target)
            leader.lineTo(fieldLeft - 3f * density, target)
            canvas.drawPath(leader, leaderPaint)
            fillPaint.color = color
            canvas.drawCircle(arrowX, middle, (if (isFocused) 3.5f else 3f) * density, fillPaint)
        }
    }
}
