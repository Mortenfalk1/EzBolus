package com.ostemirt.ezbolus.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/**
 * Numbers a calculator screen wants to display:
 * carb term, raw correction, correction after IOB (floored at 0), and total.
 *
 * total = carbBolus + correctionAfterIob.
 * The carb term itself is never reduced by IOB — IOB only reduces the
 * correction portion, and never below zero.
 */
data class BolusResult(
    val carbBolus: Double,
    val rawCorrection: Double,
    val correctionAfterIob: Double,
    val total: Double,
)

/**
 * Core dosing math (pooled-IOB model).
 *
 *   carbBolus         = carbs / icr                             (always full)
 *   rawCorrection     = (glucose - target) / isf                (can be negative)
 *   correctionAfterIob = max(0, rawCorrection - iob)            (floor at 0)
 *   total             = carbBolus + correctionAfterIob
 *
 * @param iob TOTAL insulin on board in units (pooled — no meal/correction split).
 */
fun computeBolus(
    carbsGrams: Double,
    glucose: Double,
    icr: Double,
    isf: Double,
    target: Double,
    iob: Double,
): BolusResult {
    require(carbsGrams >= 0.0) { "carbsGrams must be >= 0: $carbsGrams" }
    require(icr > 0.0) { "icr must be > 0: $icr" }
    require(isf > 0.0) { "isf must be > 0: $isf" }
    require(iob >= 0.0) { "iob must be >= 0: $iob" }

    val carbBolus = carbsGrams / icr
    val rawCorrection = (glucose - target) / isf
    val correctionAfterIob = max(0.0, rawCorrection - iob)
    val total = carbBolus + correctionAfterIob
    return BolusResult(carbBolus, rawCorrection, correctionAfterIob, total)
}

/**
 * Correction-only dose: user did not enter carbs, so they want the
 * correction that would bring current glucose down to `target`, minus
 * whatever is already on board.
 */
fun computeCorrectionOnly(
    glucose: Double,
    isf: Double,
    target: Double,
    iob: Double,
): BolusResult = computeBolus(
    carbsGrams = 0.0,
    glucose = glucose,
    icr = 1.0,     // unused (carbs=0), any positive value satisfies the require
    isf = isf,
    target = target,
    iob = iob,
)

/**
 * Round a unit value to a delivery-device increment (e.g. 0.5 or 0.1 U).
 * Rounding is applied ONCE, at the end of the calculation — never mid-pipeline.
 * Uses round-half-to-even (Kotlin default) which is fine for a "midpoint hint";
 * the calculator UI exposes floor/ceil separately so the user still chooses.
 */
fun roundToIncrement(units: Double, increment: Double = 0.5): Double {
    require(increment > 0.0) { "increment must be > 0: $increment" }
    return round(units / increment) * increment
}

/** Round DOWN to the nearest increment. Never goes negative. */
fun floorToIncrement(units: Double, increment: Double): Double {
    require(increment > 0.0) { "increment must be > 0: $increment" }
    require(units >= 0.0) { "units must be >= 0: $units" }
    return floor(units / increment) * increment
}

/** Round UP to the nearest increment. */
fun ceilToIncrement(units: Double, increment: Double): Double {
    require(increment > 0.0) { "increment must be > 0: $increment" }
    require(units >= 0.0) { "units must be >= 0: $units" }
    return ceil(units / increment) * increment
}
