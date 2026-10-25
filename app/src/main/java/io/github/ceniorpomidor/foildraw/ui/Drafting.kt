package io.github.ceniorpomidor.foildraw.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** Чертёжные примитивы схем: заштрихованный элемент, как на эскизе, размерная стрелка и номер элемента. */
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

    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val arrowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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
    fun arrow(canvas: Canvas, x: Float, top: Float, bottom: Float, color: Int, widthDp: Float = 1.2f) {
        if (bottom - top < 2f) return
        val headLength = minOf(8f * density, (bottom - top) / 2)
        val headWidth = 4f * density
        // Наконечник-«стрелка» с выемкой сзади: так стрелка читается легче, чем треугольником.
        val notch = headLength * 0.7f
        arrowLine.color = color
        arrowLine.strokeWidth = widthDp * density
        fill.color = color
        canvas.drawLine(x, top + notch, x, bottom - notch, arrowLine)
        path.reset()
        path.moveTo(x, top)
        path.lineTo(x - headWidth, top + headLength)
        path.lineTo(x, top + notch)
        path.lineTo(x + headWidth, top + headLength)
        path.close()
        path.moveTo(x, bottom)
        path.lineTo(x - headWidth, bottom - headLength)
        path.lineTo(x, bottom - notch)
        path.lineTo(x + headWidth, bottom - headLength)
        path.close()
        canvas.drawPath(path, fill)
    }

    /** Горизонтальная выносная линия. */
    fun extension(canvas: Canvas, from: Float, to: Float, y: Float, color: Int) {
        dimensionLine.color = color
        canvas.drawLine(from, y, to, y, dimensionLine)
    }

    /** Кружок цвета элемента с его номером; цифра белая или тёмная — что контрастнее. */
    fun badge(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, number: Int) {
        fill.color = color
        canvas.drawCircle(cx, cy, radius, fill)
        val luminance = Color.luminance(color)
        badgeText.color = if (1.05f / (luminance + 0.05f) >= (luminance + 0.05f) / 0.062f) Color.WHITE else DARK_TEXT
        badgeText.textSize = radius * 1.2f
        val metrics = badgeText.fontMetrics
        canvas.drawText(number.toString(), cx, cy - (metrics.ascent + metrics.descent) / 2, badgeText)
    }

    private companion object {
        const val DARK_TEXT = 0xFF1A1C22.toInt()
    }
}
