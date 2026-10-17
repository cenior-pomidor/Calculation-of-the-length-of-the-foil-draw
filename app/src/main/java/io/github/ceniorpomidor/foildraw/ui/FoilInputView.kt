package io.github.ceniorpomidor.foildraw.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
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
import android.widget.TextView
import io.github.ceniorpomidor.foildraw.R
import kotlin.math.roundToInt

/**
 * Схема ввода: лента фольги с элементами и промежутками в масштабе введённых
 * размеров. Поля ввода стоят столбцом справа, к каждому от его размерной стрелки
 * ведёт выноска. При изменении размера схема плавно перестраивается, а размер
 * поля, в котором стоит курсор, подсвечивается.
 */
class FoilInputView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val foilColor = context.getColor(R.color.on_surface_variant)
    private val leaderColor = context.getColor(R.color.outline)
    private val primary = context.getColor(R.color.primary)
    private val onPrimary = context.getColor(R.color.on_primary)
    private val primaryContainer = context.getColor(R.color.primary_container)

    private val slot = 60f * density
    private val lead = 22f * density
    private val fieldWidth = resources.getDimensionPixelSize(R.dimen.field_width)
    private val unitMargin = resources.getDimensionPixelSize(R.dimen.unit_margin)

    /** Поля и единицы по порядку участков: элемент, промежуток, элемент, … */
    private val fields = ArrayList<EditText>()
    private val units = ArrayList<TextView>()
    private val lastSizes = ArrayList<Double>()
    private var dimensions = 0
    private var focused = -1
    private var fieldLeft = 0f

    // Границы участков в долях длины рисунка; при изменении размеров перетекают от from к to.
    private var from = DoubleArray(0)
    private var to = DoubleArray(0)
    private var progress = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 260
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    private val rect = RectF()
    private val leader = Path()
    private val foilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        setWillNotDraw(false)
    }

    /** Добавляет поле очередного участка; [initial] — размер для схемы, пока поле не заполнено. */
    fun addDimension(field: EditText, unit: TextView, initial: Double) {
        val index = fields.size
        fields += field
        units += unit
        lastSizes += initial
        addView(field)
        addView(unit)
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
        val boundaries = DoubleArray(count + 1)
        for (i in 0 until count) {
            values.getOrNull(i)?.takeIf { it >= 0.0 && it.isFinite() }?.let { lastSizes[i] = it }
            boundaries[i + 1] = boundaries[i] + lastSizes[i]
        }
        val total = boundaries[count]
        for (i in boundaries.indices) boundaries[i] = if (total > 0.0) boundaries[i] / total else i.toDouble() / count

        if (count != dimensions) {
            dimensions = count
            fields.forEachIndexed { i, field -> field.visibility = if (i < count) View.VISIBLE else View.GONE }
            units.forEachIndexed { i, unit -> unit.visibility = if (i < count) View.VISIBLE else View.GONE }
            animator.cancel()
            from = boundaries
            to = boundaries
            progress = 1f
            requestLayout()
            invalidate()
        } else if (!to.contentEquals(boundaries)) {
            from = current()
            to = boundaries
            animator.cancel()
            progress = 0f
            animator.start()
        }
    }

    private fun current() = DoubleArray(to.size) { from[it] + (to[it] - from[it]) * progress }

    private fun slotCenter(index: Int) = lead + (index + 0.5f) * slot

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fieldSpec = MeasureSpec.makeMeasureSpec(fieldWidth, MeasureSpec.EXACTLY)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (i in 0 until dimensions) {
            fields[i].measure(fieldSpec, free)
            units[i].measure(free, free)
        }
        val height = (2 * lead + dimensions * slot).roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val unitWidth = (0 until dimensions).maxOfOrNull { units[it].measuredWidth } ?: 0
        val unitLeft = width - unitWidth
        val left = unitLeft - unitMargin - fieldWidth
        fieldLeft = left.toFloat()
        for (i in 0 until dimensions) {
            val center = slotCenter(i).roundToInt()
            val field = fields[i]
            val fieldTop = center - field.measuredHeight / 2
            field.layout(left, fieldTop, left + fieldWidth, fieldTop + field.measuredHeight)
            val unit = units[i]
            val unitTop = center - unit.measuredHeight / 2
            unit.layout(unitLeft, unitTop, unitLeft + unit.measuredWidth, unitTop + unit.measuredHeight)
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
        val bounds = current()
        val arrowX = fieldLeft - 42f * density
        val foilLeft = foilPaint.strokeWidth
        val foilRight = arrowX - 14f * density
        val inset = 10f * density
        val top = lead
        val span = height - 2 * lead
        fun y(share: Double) = (top + share * span).toFloat()

        canvas.drawLine(foilLeft, 0f, foilLeft, height.toFloat(), foilPaint)
        canvas.drawLine(foilRight, 0f, foilRight, height.toFloat(), foilPaint)

        if (focused in 0 until dimensions && focused % 2 == 1) {
            fillPaint.color = primary
            fillPaint.alpha = 0x24
            canvas.drawRect(foilLeft, y(bounds[focused]), foilRight, y(bounds[focused + 1]), fillPaint)
            fillPaint.alpha = 0xFF
        }
        for (i in 0 until dimensions step 2) {
            rect.set(foilLeft + inset, y(bounds[i]), foilRight - inset, y(bounds[i + 1]))
            // Совсем тонкий элемент всё равно должен быть виден.
            val minHeight = 2f * density
            if (rect.height() < minHeight) rect.inset(0f, (rect.height() - minHeight) / 2)
            if (i == focused) drafting.hatch(canvas, rect, primary, onPrimary) else drafting.hatch(canvas, rect, primaryContainer, primary)
        }
        for (boundary in bounds) {
            drafting.extension(canvas, foilRight - inset + 3f * density, arrowX + 6f * density, y(boundary), foilColor)
        }

        for (i in 0 until dimensions) {
            val color = if (i == focused) primary else foilColor
            val start = y(bounds[i])
            val end = y(bounds[i + 1])
            if (end - start >= 2f) drafting.arrow(canvas, arrowX, start, end, color)

            val middle = (start + end) / 2
            val target = slotCenter(i)
            leaderPaint.color = if (i == focused) primary else leaderColor
            leaderPaint.strokeWidth = (if (i == focused) 1.5f else 1f) * density
            leader.reset()
            leader.moveTo(arrowX, middle)
            leader.lineTo(arrowX + 10f * density, middle)
            leader.lineTo(fieldLeft - 12f * density, target)
            leader.lineTo(fieldLeft - 4f * density, target)
            canvas.drawPath(leader, leaderPaint)
            fillPaint.color = leaderPaint.color
            canvas.drawCircle(arrowX, middle, 2.5f * density, fillPaint)
        }
    }
}
