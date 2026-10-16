package io.github.ceniorpomidor.foildraw.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Чертёжные примитивы схем: заштрихованный элемент, как на эскизе, и размерная стрелка. */
class Drafting(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val hatchStep = 7f * density
    private val path = Path()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hatchLines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    /** Толщина размерных и выносных линий. */
    val dimensionLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    fun hatch(canvas: Canvas, rect: RectF, fillColor: Int, lineColor: Int) {
        val radius = minOf(6f * density, rect.height() / 2, rect.width() / 2)
        fill.color = fillColor
        hatchLines.color = lineColor
        outline.color = lineColor
        canvas.drawRoundRect(rect, radius, radius, fill)

        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        // Штрихи привязаны к координатам холста, чтобы соседние элементы штриховались в одной фазе.
        val first = rect.left - rect.height()
        var x = first - (first + rect.bottom).mod(hatchStep)
        while (x < rect.right) {
            canvas.drawLine(x, rect.bottom, x + rect.height(), rect.top, hatchLines)
            x += hatchStep
        }
        canvas.restore()
        canvas.drawRoundRect(rect, radius, radius, outline)
    }

    /** Вертикальная размерная стрелка от [top] до [bottom] с остриями на концах. */
    fun arrow(canvas: Canvas, x: Float, top: Float, bottom: Float, color: Int) {
        val headLength = minOf(7f * density, (bottom - top) / 2)
        val headWidth = 3.5f * density
        dimensionLine.color = color
        fill.color = color
        canvas.drawLine(x, top + headLength, x, bottom - headLength, dimensionLine)
        path.reset()
        path.moveTo(x, top)
        path.lineTo(x - headWidth, top + headLength)
        path.lineTo(x + headWidth, top + headLength)
        path.close()
        path.moveTo(x, bottom)
        path.lineTo(x - headWidth, bottom - headLength)
        path.lineTo(x + headWidth, bottom - headLength)
        path.close()
        canvas.drawPath(path, fill)
    }

    /** Горизонтальная выносная линия. */
    fun extension(canvas: Canvas, from: Float, to: Float, y: Float, color: Int) {
        dimensionLine.color = color
        canvas.drawLine(from, y, to, y, dimensionLine)
    }
}
