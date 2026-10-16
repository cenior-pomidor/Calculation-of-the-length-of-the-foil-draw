package io.github.ceniorpomidor.foildraw.calc

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Рисунок одного удара на фольге: элементы и промежутки между ними,
 * перечисленные по направлению протяжки, в миллиметрах.
 */
class FoilPattern(heights: List<Double>, gaps: List<Double>) {
    val starts: DoubleArray
    val ends: DoubleArray

    init {
        require(heights.size >= 2) { "Нужно хотя бы два элемента" }
        require(gaps.size == heights.size - 1) { "Промежутков должно быть на один меньше, чем элементов" }
        require(heights.all { it > 0.0 }) { "Высота элемента должна быть больше нуля" }
        require(gaps.all { it >= 0.0 }) { "Промежуток не может быть отрицательным" }
        starts = DoubleArray(heights.size)
        ends = DoubleArray(heights.size)
        var position = 0.0
        for (i in heights.indices) {
            starts[i] = position
            position += heights[i]
            ends[i] = position
            if (i < gaps.size) position += gaps[i]
        }
    }

    val size: Int get() = starts.size

    /** Длина рисунка от начала первого элемента до конца последнего. */
    val length: Double get() = ends.last()

    val maxHeight: Double get() = (0 until size).maxOf { ends[it] - starts[it] }
}

data class FoilResult(
    /** Рекомендуемая протяжка: зазоры между оттисками максимально выровнены. */
    val pull: Double,
    /** Наименьшая протяжка, при которой зазор не меньше заданного. */
    val minPull: Double,
    /** Наибольшая протяжка с тем же вложением оттисков; бесконечность, если вложения нет. */
    val maxPull: Double,
    /** Наименьший зазор между оттисками при рекомендуемой протяжке. */
    val clearance: Double,
    /** Протяжка без вложения: длина рисунка плюс заданный зазор. */
    val plainPull: Double,
) {
    val isNested: Boolean get() = pull < plainPull - EPS

    /** Доля сэкономленной фольги относительно протяжки без вложения. */
    val savings: Double get() = (1.0 - pull / plainPull).coerceAtLeast(0.0)
}

private const val EPS = 1e-9

/**
 * Расчёт протяжки фольги при постоянном шаге.
 *
 * Каждый удар выбивает на фольге один и тот же рисунок, после чего фольга
 * сдвигается на протяжку a. Удары с номерами i и i + d расходятся на d·a,
 * и для каждой пары элементов (i, j) есть интервал сдвигов, при которых
 * элемент j более позднего удара попадает на элемент i более раннего или
 * подходит к нему ближе допустимого зазора. Протяжка допустима, если ни одно
 * её кратное не попадает внутрь таких интервалов.
 */
object FoilCalculator {

    fun calculate(pattern: FoilPattern, minGap: Double): FoilResult {
        require(minGap >= 0.0) { "Зазор не может быть отрицательным" }

        val (minPull, maxPull) = firstWindow(pattern, minGap)
        val pull = if (maxPull.isInfinite()) minPull else mostEvenPull(pattern, minPull, maxPull)
        return FoilResult(
            pull = pull,
            minPull = minPull,
            maxPull = maxPull,
            clearance = clearance(pattern, pull),
            plainPull = pattern.length + minGap,
        )
    }

    /**
     * Наименьший зазор между оттисками разных ударов при протяжке [pull].
     * Отрицательное значение означает наложение оттисков.
     */
    fun clearance(pattern: FoilPattern, pull: Double): Double {
        require(pull > 0.0) { "Протяжка должна быть больше нуля" }
        var result = Double.POSITIVE_INFINITY
        for (span in forbiddenShifts(pattern, 0.0)) {
            val firstAbove = max(1.0, ceil(span.hi / pull - EPS))
            result = min(result, firstAbove * pull - span.hi)
            val lastBelow = floor(span.lo / pull + EPS)
            if (lastBelow >= 1.0) result = min(result, span.lo - lastBelow * pull)
            var d = max(1.0, lastBelow + 1.0)
            while (d < firstAbove) {
                val shift = d * pull
                result = min(result, -min(shift - span.lo, span.hi - shift))
                d += 1.0
            }
        }
        return result
    }

    private class Span(val lo: Double, val hi: Double)

    /** Сдвиги из (lo, hi) недопустимы: оттиски окажутся ближе, чем [gap]. */
    private fun forbiddenShifts(pattern: FoilPattern, gap: Double): List<Span> {
        val spans = ArrayList<Span>()
        for (i in 0 until pattern.size) {
            for (j in i until pattern.size) {
                spans += Span(pattern.starts[j] - pattern.ends[i] - gap, pattern.ends[j] - pattern.starts[i] + gap)
            }
        }
        return spans
    }

    /**
     * Первое (самое экономное) окно допустимых протяжек [minPull, maxPull].
     * Протяжка a недопустима, если d·a попадает в (lo, hi), то есть если a
     * лежит в (lo/d, hi/d) хотя бы для одного d. Перебираем эти интервалы по
     * возрастанию начала и ищем первую точку, которую они не покрывают.
     */
    private fun firstWindow(pattern: FoilPattern, gap: Double): Pair<Double, Double> {
        val lowerBound = pattern.maxHeight + gap
        val intervals = ArrayList<Span>()
        for (span in forbiddenShifts(pattern, gap)) {
            val maxMultiple = floor(span.hi / lowerBound + EPS).toInt()
            for (d in 1..maxMultiple) intervals += Span(span.lo / d, span.hi / d)
        }
        intervals.sortBy { it.lo }

        var pull = lowerBound
        var index = 0
        while (index < intervals.size && intervals[index].lo < pull - EPS) {
            pull = max(pull, intervals[index].hi)
            index++
        }
        val maxPull = if (index < intervals.size) max(pull, intervals[index].lo) else Double.POSITIVE_INFINITY
        return pull to maxPull
    }

    /**
     * Протяжка из окна, при которой наименьший зазор между оттисками
     * наибольший. Внутри окна кратные протяжки не пересекают границ
     * интервалов, поэтому каждый зазор линейно зависит от a, а наименьший
     * из них вогнут; максимум лежит на краю окна или на пересечении
     * убывающего и возрастающего зазоров.
     */
    private fun mostEvenPull(pattern: FoilPattern, minPull: Double, maxPull: Double): Double {
        if (maxPull - minPull < EPS) return minPull

        val middle = (minPull + maxPull) / 2
        val shrinking = ArrayList<Pair<Double, Double>>() // зазор = lo − d·a
        val growing = ArrayList<Pair<Double, Double>>() // зазор = d·a − hi
        for (span in forbiddenShifts(pattern, 0.0)) {
            val below = floor(span.lo / middle)
            if (below >= 1.0) shrinking += span.lo to below
            growing += span.hi to max(1.0, ceil(span.hi / middle))
        }

        fun worstGap(pull: Double): Double = min(
            shrinking.minOfOrNull { (lo, d) -> lo - d * pull } ?: Double.POSITIVE_INFINITY,
            growing.minOf { (hi, d) -> d * pull - hi },
        )

        val candidates = mutableListOf(minPull, maxPull)
        for ((lo, below) in shrinking) {
            for ((hi, above) in growing) {
                val pull = (lo + hi) / (below + above)
                if (pull > minPull && pull < maxPull) candidates += pull
            }
        }
        var best = minPull
        var bestGap = worstGap(minPull)
        for (pull in candidates) {
            val gap = worstGap(pull)
            if (gap > bestGap + EPS || (gap > bestGap - EPS && pull < best)) {
                best = pull
                bestGap = gap
            }
        }
        return best
    }
}
