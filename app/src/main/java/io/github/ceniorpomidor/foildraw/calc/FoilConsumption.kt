package io.github.ceniorpomidor.foildraw.calc

import kotlin.math.ceil

/** Доля фольги сверх тиража на запас и приладку. */
const val MAKE_READY_SHARE = 0.2

/**
 * Сколько фольги и роликов уходит на тираж. Тираж считается в этикетках; за один
 * удар печатается [labelsPerStrike] этикеток, так что ударов нужно тираж / этикеток
 * за удар с округлением вверх. Каждый удар протягивает фольгу на [pullMm], а на запас
 * и приладку закладывается ещё [MAKE_READY_SHARE] от расхода на печать.
 */
class FoilConsumption(pullMm: Double, run: Long, labelsPerStrike: Int, val rollLength: Double) {

    init {
        require(pullMm > 0.0) { "Протяжка должна быть больше нуля" }
        require(run > 0) { "Тираж должен быть больше нуля" }
        require(labelsPerStrike > 0) { "Этикеток за удар должно быть больше нуля" }
        require(rollLength > 0.0) { "Намотка ролика должна быть больше нуля" }
    }

    /** Ударов на тираж. */
    val strikes: Long = (run + labelsPerStrike - 1) / labelsPerStrike

    /** Фольга на печать тиража без запаса, м. */
    val runLength: Double = pullMm * strikes / 1000.0

    /** Фольга на запас и приладку, м. */
    val makeReadyLength: Double = runLength * MAKE_READY_SHARE

    val totalLength: Double get() = runLength + makeReadyLength

    val runRolls: Double get() = runLength / rollLength

    val makeReadyRolls: Double get() = makeReadyLength / rollLength

    /** Целых роликов на тираж вместе с запасом и приладкой. */
    val rollsNeeded: Int get() = ceil(totalLength / rollLength - 1e-9).toInt()
}
