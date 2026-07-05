package com.ostemirt.ezbolus.engine

import kotlin.math.exp

/**
 * A logged insulin dose.
 *
 * The user's dosing model is *pooled* — every dose contributes to a single
 * IOB pool that reduces the next correction bolus (safety note documented
 * in the README's "meal vs correction IOB" section: a pooled model can
 * under-dose a correction because meal-tagged insulin is already spoken
 * for by food eaten. The user explicitly opted for pooled — confirm with
 * your care team).
 */
data class Dose(
    val units: Double,
    val takenAtEpochMillis: Long,
)

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Fraction of a single dose still on board `elapsedHours` after injection,
 * under the given curve model. Result is clamped to [0.0, 1.0].
 *
 * Contract:
 *  - remainingFraction(0, m) == 1.0 for every model
 *  - remainingFraction(m.actionTimeHours, m) == 0.0 for every model
 *  - remainingFraction(t > actionTimeHours, m) == 0.0
 *  - the function is monotonically non-increasing in `elapsedHours` for all
 *    supported models (linear/bilinear are piecewise-linear non-increasing;
 *    exponential is verified in tests).
 */
fun remainingFraction(elapsedHours: Double, model: CurveModel): Double {
    if (elapsedHours <= 0.0) return 1.0
    val td = model.actionTimeHours
    if (elapsedHours >= td) return 0.0

    return when (model) {
        is CurveModel.Linear -> 1.0 - elapsedHours / td

        is CurveModel.Bilinear -> {
            val tp = td * model.peakTimeFraction
            val rp = model.peakRemaining
            if (elapsedHours <= tp) {
                // Segment A: (0, 1) -> (tp, rp)
                1.0 + (rp - 1.0) * (elapsedHours / tp)
            } else {
                // Segment B: (tp, rp) -> (td, 0)
                rp * (1.0 - (elapsedHours - tp) / (td - tp))
            }
        }

        is CurveModel.Exponential -> {
            // All time terms in minutes, per OpenAPS reference.
            val tdMin = td * 60.0
            val tpMin = tdMin * model.peakFraction
            val tMin = elapsedHours * 60.0

            val tau = tpMin * (1.0 - tpMin / tdMin) / (1.0 - 2.0 * tpMin / tdMin)
            val a = 2.0 * tau / tdMin
            val s = 1.0 / (1.0 - a + (1.0 + a) * exp(-tdMin / tau))

            val bracket = (tMin * tMin) / (tau * tdMin * (1.0 - a)) - tMin / tdMin - 1.0
            val iob = 1.0 - s * (1.0 - a) * (bracket * exp(-tMin / tau) + 1.0)
            iob.coerceIn(0.0, 1.0)
        }
    }
}

/**
 * Insulin on board (units) from `doses`. Doses in the future (takenAt > now)
 * are ignored. Doses older than the action time contribute exactly 0.0.
 */
fun iobAt(nowEpochMillis: Long, doses: List<Dose>, model: CurveModel): Double {
    var sum = 0.0
    for (d in doses) {
        val elapsedHours = (nowEpochMillis - d.takenAtEpochMillis) / MILLIS_PER_HOUR
        if (elapsedHours < 0.0) continue
        sum += d.units * remainingFraction(elapsedHours, model)
    }
    return sum
}

/**
 * Everything the IOB decay ring needs, computed in one place so the home-screen
 * widget and the in-app card render identically.
 *
 * [fraction] (0..1) is the ring's fill: current IOB relative to the IOB the
 * instant of the last dose — its peak, since insulin only decays between doses.
 * So the ring snaps to full the moment a dose is taken and drains to empty as
 * the whole pool clears. A new (overlapping) dose lifts the peak, refilling the
 * ring, instead of being diluted by already-spent insulin — the failure of the
 * old `iob / units-dosed-in-window` ratio, which made a fresh dose taken on top
 * of older ones look half-decayed.
 */
data class IobRingState(
    val iob: Double,
    val fraction: Float,
)

fun iobRingState(
    nowEpochMillis: Long,
    doses: List<Dose>,
    model: CurveModel,
): IobRingState {
    val iob = iobAt(nowEpochMillis, doses, model)
    val lastDoseAt = doses
        .filter { it.takenAtEpochMillis <= nowEpochMillis }
        .maxOfOrNull { it.takenAtEpochMillis }
    val peakIob = lastDoseAt?.let { iobAt(it, doses, model) } ?: 0.0
    val fraction = if (peakIob > 0.0)
        (iob / peakIob).toFloat().coerceIn(0f, 1f) else 0f
    return IobRingState(iob, fraction)
}
