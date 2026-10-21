package io.github.ceniorpomidor.foildraw.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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
 * Схема ввода: лента фольги с элементами и промежутками. Поле размера стоит
 * в центре своего элемента или промежутка; у промежутка через центр проходит
 * размерная стрелка. Высоты участков складываются из минимума, в который
 * помещается поле, и доли, пропорциональной размеру, поэтому при вводе схема
 * плавно перестраивается. У каждого элемента свой цвет и номер — те же, что на
 * схеме оттисков.
 */
class FoilInputView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val primary = context.getColor(R.color.primary)
    private val elementColors = ElementColors.of(context)

    private val elementMin = 50f * density
    private val gapMin = 64f * density
    private val flexPerSegment = 20f * density
    private val lead = 18f * density
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
    private val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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

    private fun minHeight(index: Int) = if (index % 2 == 0) elementMin else gapMin

    /** Верхние и нижние границы участков в пикселях при текущем кадре анимации. */
    private fun boundaries(): FloatArray {
        val flex = dimensions * flexPerSegment
        val result = FloatArray(dimensions + 1)
        result[0] = lead
        for (i in 0 until dimensions) {
            val share = (from[i + 1] - from[i]) + ((to[i + 1] - to[i]) - (from[i + 1] - from[i])) * progress
            result[i + 1] = result[i] + minHeight(i) + flex * share.toFloat()
        }
        return result
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val boxWidthSpec = MeasureSpec.makeMeasureSpec(boxWidth, MeasureSpec.EXACTLY)
        val boxHeightSpec = MeasureSpec.makeMeasureSpec(boxHeight, MeasureSpec.EXACTLY)
        for (i in 0 until dimensions) boxes[i].measure(boxWidthSpec, boxHeightSpec)
        var height = 2 * lead + dimensions * flexPerSegment
        for (i in 0 until dimensions) height += minHeight(i)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height.roundToInt(), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = placeBoxes()

    private fun placeBoxes() {
        if (dimensions == 0) return
        val bounds = boundaries()
        val left = (width - boxWidth) / 2
        for (i in 0 until dimensions) {
            val top = ((bounds[i] + bounds[i + 1]) / 2).roundToInt() - boxHeight / 2
            boxes[i].layout(left, top, left + boxWidth, top + boxHeight)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val fade = (lead / h).coerceAtMost(0.3f)
        foilPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0, foilColor, foilColor, 0),
            floatArrayOf(0f, fade, 1f - fade, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (dimensions == 0) return
        val bounds = boundaries()
        val foilLeft = foilPaint.strokeWidth
        val foilRight = width - foilPaint.strokeWidth
        val inset = 10f * density
        val centerX = width / 2f

        canvas.drawLine(foilLeft, 0f, foilLeft, height.toFloat(), foilPaint)
        canvas.drawLine(foilRight, 0f, foilRight, height.toFloat(), foilPaint)

        for (i in 0 until dimensions) {
            val top = bounds[i]
            val bottom = bounds[i + 1]
            if (i % 2 == 0) {
                val number = i / 2
                val color = elementColors[number % elementColors.size]
                rect.set(foilLeft + inset, top, foilRight - inset, bottom)
                drafting.hatch(canvas, rect, color and 0x00FFFFFF or 0x3D000000, color)
                if (i == focused) {
                    focusPaint.color = color
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, focusPaint)
                }
                drafting.badge(canvas, rect.left + 22f * density, rect.centerY(), 12f * density, color, number + 1)
            } else {
                val color = if (i == focused) primary else foilColor
                if (i == focused) {
                    bandPaint.color = primary and 0x00FFFFFF or 0x1F000000
                    canvas.drawRect(foilLeft + inset, top, foilRight - inset, bottom, bandPaint)
                }
                drafting.arrow(canvas, centerX, top, bottom, color)
            }
        }
    }
}
