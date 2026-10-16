package io.github.ceniorpomidor.foildraw.calc

import kotlin.math.ceil

/** Доля фольги сверх тиража, которая уходит на приладку. */
const val MAKE_READY_SHARE = 0.2

/**
 * Сколько фольги и роликов уходит на тираж: каждый удар протягивает фольгу
 * на [pullMm], а на приладку закладывается ещё [MAKE_READY_SHARE] от расхода на тираж.
 */
class FoilConsumption(pullMm: Double, run: Long, val rollLength: Double) {

    init {
        require(pullMm > 0.0) { "Протяжка должна быть больше нуля" }
        require(run > 0) { "Тираж должен быть больше нуля" }
        require(rollLength > 0.0) { "Намотка ролика должна быть больше нуля" }
    }

    /** Фольга на тираж, м. */
    val runLength: Double = pullMm * run / 1000.0

    /** Фольга на приладку, м. */
    val makeReadyLength: Double = runLength * MAKE_READY_SHARE

    val totalLength: Double get() = runLength + makeReadyLength

    val runRolls: Double get() = runLength / rollLength

    val makeReadyRolls: Double get() = makeReadyLength / rollLength

    /** Целых роликов на тираж вместе с приладкой. */
    val rollsNeeded: Int get() = ceil(totalLength / rollLength - 1e-9).toInt()
}
