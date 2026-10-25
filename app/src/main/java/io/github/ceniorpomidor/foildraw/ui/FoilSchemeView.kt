package io.github.ceniorpomidor.foildraw.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import io.github.ceniorpomidor.foildraw.R
import io.github.ceniorpomidor.foildraw.calc.FoilPattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Лента матовой серебряной фольги под клише. Прокрутка вниз прокручивает работу
 * пресса: клише опускается и выбивает из фольги все элементы удара, затем фольга
 * протягивается на шаг и следующий удар выбивает те же элементы между прежними
 * оттисками. Прокрутка вверх отматывает ленту назад; лента бесконечна.
 */
class FoilSchemeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var pattern: FoilPattern? = null
    private var pull = 0.0
    private var scale = 0f

    /** Насколько прокручена работа пресса, в пикселях; [strikeScroll] пикселей — один удар. */
    private var offset = 0.0

    private val density = resources.displayMetrics.density
    private val drafting = Drafting(context)
    private val dimensionColor = context.getColor(R.color.on_surface_variant)
    private val surfaceColor = context.getColor(R.color.card)
    private val dieColor = context.getColor(R.color.primary)
    private val foilEdge = context.getColor(R.color.foil_edge)
    private val foilShade = context.getColor(R.color.foil_shade)
    private val foilLight = context.getColor(R.color.foil_light)
    private val topPadding = 14f * density
    private val bottomPadding = 30f * density
    private val inset = 12f * density

    /** Прокрутка на один удар: половина — удар клише, половина — протяжка фольги. */
    val strikeScroll = 150f * density

    private val scroller = Scroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private val rect = RectF()
    private val grainMatrix = Matrix()
    private val silverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val grainPaint = Paint().apply {
        shader = BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val kerbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val shadowMatrix = Matrix()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(0f, 0f, 0f, 1f, 0x29000000, 0, Shader.TileMode.CLAMP)
    }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val diePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 3f * density), 0f)
    }
    private val edgeFadePaint = Paint()
    private val dimensionText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        color = context.getColor(R.color.on_surface)
    }

    fun show(pattern: FoilPattern, pull: Double) {
        this.pattern = pattern
        this.pull = pull
        updateScale()
        contentDescription = context.getString(R.string.scheme_title) + ": " +
            context.getString(R.string.value_mm, Format.mm(pull))
        invalidate()
    }

    /** Масштаб: в окне рисунок клише и ещё две протяжки уже выбитой ленты над ним. */
    private fun updateScale() {
        val pattern = pattern ?: return
        scale = if (height > 0) ((height - topPadding - bottomPadding) / (pattern.length + 2 * pull)).toFloat() else 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallback = (360f * density).roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(fallback, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateScale()
        val tapeLeft = tapeLeft()
        silverPaint.shader = LinearGradient(
            tapeLeft, 0f, tapeRight(), 0f,
            intArrayOf(foilEdge, foilShade, foilLight, foilShade, foilEdge),
            floatArrayOf(0f, 0.16f, 0.48f, 0.84f, 1f),
            Shader.TileMode.CLAMP,
        )
        // Верх и низ окна растворяются в фоне: лента уходит за край.
        val top = (26f * density / h).coerceAtMost(0.25f)
        val bottom = (18f * density / h).coerceAtMost(0.2f)
        val clear = surfaceColor and 0x00FFFFFF
        edgeFadePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(surfaceColor, clear, clear, surfaceColor),
            floatArrayOf(0f, top, 1f - bottom, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun tapeLeft() = 1f * density

    private fun tapeRight() = width - 64f * density

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downX = event.x
                downY = event.y
                lastY = event.y
                dragging = false
                // Лента стоит внутри прокручиваемого экрана: жест сначала забирает она.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = abs(event.x - downX)
                    val dy = event.y - downY
                    when {
                        // Горизонтальный жест — листание экранов, вертикальный вниз у начала
                        // ленты — прокрутка самого экрана вверх: отдаём родителям.
                        dx > touchSlop && dx > abs(dy) || dy > touchSlop && offset <= 0.0 -> {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            recycleVelocity()
                            return false
                        }
                        abs(dy) > touchSlop -> {
                            dragging = true
                            lastY = event.y
                        }
                    }
                }
                if (dragging) {
                    scrollBy(lastY - event.y)
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    tracker.computeCurrentVelocity(1000, maxFling.toFloat())
                    settle(-tracker.yVelocity)
                }
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) settle(0f)
                recycleVelocity()
            }
        }
        return true
    }

    /**
     * Доводит ленту до ближайшего покоя — клише поднято до удара или после него, —
     * чтобы прокрутка не замирала посреди удара или протяжки.
     */
    private fun settle(speed: Float) {
        val start = offset.roundToInt()
        val half = strikeScroll / 2
        if (abs(speed) >= minFling) {
            scroller.fling(0, start, 0, speed.roundToInt(), 0, 0, 0, MAX_OFFSET)
            scroller.finalY = ((scroller.finalY / half).roundToInt() * half).roundToInt().coerceIn(0, MAX_OFFSET)
        } else {
            val target = ((offset / half).roundToInt() * half).roundToInt()
            scroller.startScroll(0, start, 0, target - start, 260)
        }
        postInvalidateOnAnimation()
    }

    /** Прокручивает ленту на [dy] пикселей вниз (отрицательное — назад, не дальше начала ролика). */
    fun scrollBy(dy: Float) {
        offset = (offset + dy).coerceIn(0.0, MAX_OFFSET.toDouble())
        invalidate()
    }

    private fun recycleVelocity() {
        velocity?.recycle()
        velocity = null
        dragging = false
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offset = scroller.currY.toDouble()
            postInvalidateOnAnimation()
        }
    }

    /** Ударов, элементы которых уже выбиты полностью. */
    fun strikesPunched(): Int {
        val (done, press) = phase()
        return done + if (press >= REVEAL_END) 1 else 0
    }

    /** Сколько ударов завершено (выбито и протянуто) и как далеко зашёл следующий удар (0…1). */
    private fun phase(): Pair<Int, Float> {
        val t = offset / strikeScroll
        val done = floor(t).toInt()
        val press = ((t - done) / PRESS_SHARE).toFloat().coerceAtMost(1f)
        return done to press
    }

    override fun onDraw(canvas: Canvas) {
        val pattern = pattern ?: return
        if (scale <= 0f) updateScale()
        if (scale <= 0f) return

        val t = offset / strikeScroll
        val (done, press) = phase()
        val travel = ((t - done - PRESS_SHARE) / (1 - PRESS_SHARE)).coerceAtLeast(0.0)
        // Сдвиг ленты от начала ролика, мм: по шагу после каждого удара, с плавным разгоном и торможением.
        val shift = (done + smooth(0.0, 1.0, travel)) * pull
        val reveal = smooth(REVEAL_START.toDouble(), REVEAL_END.toDouble(), press.toDouble()).toFloat()
        val squeeze = if (travel > 0.0) 0f else sin(PI * press).toFloat()

        val dieTop = height - bottomPadding - (pattern.length * scale).toFloat()
        fun y(mm: Double) = (dieTop + (mm - shift) * scale).toFloat()

        val tapeLeft = tapeLeft()
        val tapeRight = tapeRight()

        // Фольга: матовое серебро — мягкий перелив поперёк ленты и мелкое зерно, которое едет вместе с ней.
        canvas.drawRect(tapeLeft, 0f, tapeRight, height.toFloat(), silverPaint)
        val tile = GRAIN_HEIGHT.toDouble()
        val grainShift = ((dieTop - shift * scale) % tile + tile) % tile
        grainMatrix.setTranslate(tapeLeft, grainShift.toFloat())
        grainPaint.shader.setLocalMatrix(grainMatrix)
        canvas.drawRect(tapeLeft, 0f, tapeRight, height.toFloat(), grainPaint)
        kerbPaint.color = foilEdge
        canvas.drawLine(tapeLeft, 0f, tapeLeft, height.toFloat(), kerbPaint)
        canvas.drawLine(tapeRight, 0f, tapeRight, height.toFloat(), kerbPaint)

        // Выбитые места: в окне видны только удары, которые до него дотягиваются.
        val lastStrike = done + if (reveal > 0f) 1 else 0
        val windowTop = shift - dieTop / scale
        val windowBottom = shift + (height - dieTop) / scale
        val firstStrike = max(1, floor((windowTop - pattern.length) / pull).toInt() + 1)
        for (strike in firstStrike..min(lastStrike, floor(windowBottom / pull).toInt() + 1)) {
            val alpha = if (strike <= done) 1f else reveal
            val start = (strike - 1) * pull
            for (element in 0 until pattern.size) {
                rect.set(tapeLeft + inset, y(start + pattern.starts[element]), tapeRight - inset, y(start + pattern.ends[element]))
                if (rect.bottom >= 0f && rect.top <= height) drawHole(canvas, alpha)
            }
        }

        // Клише: пунктир на месте следующего удара; при ударе прижимает фольгу.
        diePaint.color = withAlpha(dieColor, 0.7f + 0.3f * squeeze)
        diePaint.strokeWidth = (1.2f + 0.8f * squeeze) * density
        pressPaint.color = withAlpha(dieColor, 0.28f * squeeze * (1f - reveal))
        for (element in 0 until pattern.size) {
            rect.set(tapeLeft + inset, dieTop + (pattern.starts[element] * scale).toFloat(), tapeRight - inset, dieTop + (pattern.ends[element] * scale).toFloat())
            if (squeeze > 0f) canvas.drawRect(rect, pressPaint)
            canvas.drawRect(rect, diePaint)
        }

        // Справа — шаг протяжки: от начала прежнего удара до начала следующего.
        val arrowX = tapeRight + 18f * density
        val pullTop = dieTop - (pull * scale).toFloat()
        drafting.extension(canvas, tapeRight - inset, arrowX + 6f * density, pullTop, dimensionColor)
        drafting.extension(canvas, tapeRight - inset, arrowX + 6f * density, dieTop, dimensionColor)
        drafting.arrow(canvas, arrowX, pullTop, dieTop, dimensionColor, 1.4f)
        canvas.save()
        val middle = (pullTop + dieTop) / 2
        val baseline = arrowX + 6f * density + dimensionText.textSize
        canvas.rotate(-90f, baseline, middle)
        canvas.drawText(context.getString(R.string.value_mm, Format.mm(pull)), baseline, middle, dimensionText)
        canvas.restore()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), edgeFadePaint)
    }

    /** Выбитое место: сквозь ленту виден фон, по краю — тонкий срез, сверху — тень от кромки. */
    private fun drawHole(canvas: Canvas, alpha: Float) {
        holePaint.color = withAlpha(surfaceColor, alpha)
        canvas.drawRect(rect, holePaint)
        val shadow = min(3f * density, rect.height() / 2)
        shadowMatrix.setScale(1f, shadow)
        shadowMatrix.postTranslate(0f, rect.top)
        shadowPaint.shader.setLocalMatrix(shadowMatrix)
        shadowPaint.alpha = (alpha * 255).roundToInt()
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + shadow, shadowPaint)
        cutPaint.color = withAlpha(foilEdge, alpha)
        canvas.drawRect(rect, cutPaint)
    }

    private fun withAlpha(color: Int, alpha: Float) = color and 0x00FFFFFF or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    /** Зерно фольги: мелкий шум и едва заметные прожилки вдоль ленты (от прокатки), светлые и тёмные точки. */
    private fun grain(): Bitmap {
        val random = Random(7)
        val streaks = FloatArray(GRAIN_WIDTH) { random.nextFloat() * 2 - 1 }
        val pixels = IntArray(GRAIN_WIDTH * GRAIN_HEIGHT) {
            val value = 0.25f * streaks[it % GRAIN_WIDTH] + 0.75f * (random.nextFloat() * 2 - 1)
            ((abs(value) * 30).roundToInt() shl 24) or if (value > 0) 0xFFFFFF else 0
        }
        return Bitmap.createBitmap(pixels, GRAIN_WIDTH, GRAIN_HEIGHT, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        /** Доля прокрутки удара, за которую клише опускается и поднимается; остальное — протяжка. */
        const val PRESS_SHARE = 0.5

        /** Когда за время удара проступают выбитые места. */
        const val REVEAL_START = 0.4f
        const val REVEAL_END = 0.8f

        const val GRAIN_WIDTH = 256
        const val GRAIN_HEIGHT = 64
        const val MAX_OFFSET = Int.MAX_VALUE / 2

        /** Плавный переход 0 → 1 между [from] и [to]. */
        fun smooth(from: Double, to: Double, value: Double): Double {
            val x = ((value - from) / (to - from)).coerceIn(0.0, 1.0)
            return x * x * (3 - 2 * x)
        }
    }
}
