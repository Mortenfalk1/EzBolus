package com.ostemirt.ezbolus.engine

import kotlin.math.roundToLong

/**
 * Result of solving `iob(t) == threshold` for the first future crossing.
 */
sealed interface CrossingResult {
    /** First epoch-millis at or after `now` where IOB reaches (crosses down through) threshold. */
    data class Found(val epochMillis: Long) : CrossingResult

    /**
     * IOB is already at or below threshold at `now`. Nothing to schedule.
     */
    data object AlreadyBelow : CrossingResult

    /**
     * No doses on board (or all aged out) — nothing can drop from above.
     */
    data object NoCrossing : CrossingResult
}

/**
 * Solve for the first time at or after `nowEpochMillis` when total IOB from
 * `doses` decays to `threshold`. Uses bisection on minutes for all curve
 * models — closed-form for linear/bilinear is not worth the complexity given
 * bisection converges in ~20 iterations to sub-minute precision.
 */
fun findCrossing(
    doses: List<Dose>,
    nowEpochMillis: Long,
    model: CurveModel,
    threshold: Double,
    minuteTolerance: Double = 0.5,
    maxIterations: Int = 64,
): CrossingResult {
    require(threshold >= 0.0) { "threshold must be >= 0: $threshold" }
    require(minuteTolerance > 0.0) { "minuteTolerance must be > 0: $minuteTolerance" }

    val currentIob = iobAt(nowEpochMillis, doses, model)
    if (currentIob <= threshold) return CrossingResult.AlreadyBelow

    val td = model.actionTimeHours
    val latestHoursAhead = doses.maxOfOrNull { d ->
        val hoursSince = (nowEpochMillis - d.takenAtEpochMillis) / 3_600_000.0
        val remaining = td - hoursSince
        if (remaining > 0.0) remaining else 0.0
    } ?: return CrossingResult.NoCrossing

    if (latestHoursAhead <= 0.0) return CrossingResult.NoCrossing

    // Bracket the crossing so that iob(lo) > threshold >= iob(hi).
    var lo = 0.0
    var hi = latestHoursAhead
    var iterations = 0
    val toleranceHours = minuteTolerance / 60.0

    while ((hi - lo) > toleranceHours && iterations < maxIterations) {
        val mid = (lo + hi) / 2.0
        val midMillis = nowEpochMillis + (mid * 3_600_000.0).roundToLong()
        val midIob = iobAt(midMillis, doses, model)
        if (midIob > threshold) lo = mid else hi = mid
        iterations++
    }

    // Return the *exact* crossing, not the rounded-down upper bracket. Over a
    // sub-minute [lo, hi] window IOB is essentially linear, so one regula-falsi
    // step lands on iob(t) == threshold to sub-second precision — otherwise the
    // alarm would be scheduled up to `minuteTolerance` late, firing below the
    // threshold. iob is monotone non-increasing here, so iobLo > iobHi.
    val iobLo = iobAt(nowEpochMillis + (lo * 3_600_000.0).roundToLong(), doses, model)
    val iobHi = iobAt(nowEpochMillis + (hi * 3_600_000.0).roundToLong(), doses, model)
    val crossHours = if (iobLo > iobHi) {
        val frac = ((iobLo - threshold) / (iobLo - iobHi)).coerceIn(0.0, 1.0)
        lo + frac * (hi - lo)
    } else {
        hi   // degenerate flat segment — fall back to the guaranteed-below bound
    }

    val crossingMillis = nowEpochMillis + (crossHours * 3_600_000.0).roundToLong()
    return CrossingResult.Found(crossingMillis)
}
