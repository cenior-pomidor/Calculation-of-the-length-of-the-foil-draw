package io.github.ceniorpomidor.foildraw.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Экраны, которые листаются горизонтальным свайпом. Каждый дочерний view —
 * отдельный экран во всю ширину; вертикальную прокрутку внутри экранов
 * не перехватывает.
 */
class SwipePager(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    var page = 0
        private set

    /** Вызывается, когда пользователь пролистал на другой экран. */
    var onPageChanged: ((Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingVelocity = 400f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null

    private val animator = ValueAnimator().apply {
        duration = 280
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
    }

    fun setPage(index: Int, animate: Boolean) {
        val target = index.coerceIn(0, maxOf(childCount - 1, 0))
        page = target
        updateAccessibility()
        if (animate && width > 0) settleTo(target) else {
            animator.cancel()
            scrollTo(target * width, 0)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(childWidth, childHeight)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) getChildAt(i).layout(i * width, 0, (i + 1) * width, height)
        if (!animator.isRunning && !dragging) scrollTo(page * width, 0)
        updateAccessibility()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                dragging = animator.isRunning
                if (dragging) animator.cancel()
            }
            MotionEvent.ACTION_MOVE -> if (!dragging) startDragIfHorizontal(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) startDragIfHorizontal(event)
                if (dragging) {
                    val maxScroll = (childCount - 1) * width
                    val next = scrollX + (lastX - event.x)
                    // За крайними экранами лента тянется с сопротивлением.
                    val resisted = when {
                        next < 0 -> scrollX + (lastX - event.x) / 3
                        next > maxScroll -> scrollX + (lastX - event.x) / 3
                        else -> next
                    }
                    scrollTo(resisted.roundToInt(), 0)
                    lastX = event.x
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    tracker.computeCurrentVelocity(1000)
                    val speed = tracker.xVelocity
                    val target = when {
                        speed < -flingVelocity -> page + 1
                        speed > flingVelocity -> page - 1
                        else -> (scrollX.toFloat() / width).roundToInt()
                    }
                    finishDrag(target)
                }
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) finishDrag((scrollX.toFloat() / width).roundToInt())
                recycleVelocity()
            }
        }
        return true
    }

    private fun startDragIfHorizontal(event: MotionEvent) {
        val dx = abs(event.x - downX)
        val dy = abs(event.y - downY)
        if (dx > touchSlop && dx > dy * 1.2f) {
            dragging = true
            lastX = event.x
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun finishDrag(target: Int) {
        dragging = false
        val clamped = target.coerceIn(0, childCount - 1)
        val changed = clamped != page
        page = clamped
        updateAccessibility()
        settleTo(clamped)
        if (changed) onPageChanged?.invoke(clamped)
    }

    private fun settleTo(index: Int) {
        animator.cancel()
        animator.setIntValues(scrollX, index * width)
        animator.start()
    }

    private fun recycleVelocity() {
        velocity?.recycle()
        velocity = null
    }

    /** Экраны за кадром не должны попадаться TalkBack и навигации фокусом. */
    private fun updateAccessibility() {
        for (i in 0 until childCount) {
            getChildAt(i).importantForAccessibility =
                if (i == page) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
}
