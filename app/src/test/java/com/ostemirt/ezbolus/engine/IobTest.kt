package com.ostemirt.ezbolus.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

private const val TOL = 1e-9
private const val LOOSE_TOL = 1e-6

private fun models(td: Double): List<CurveModel> = listOf(
    CurveModel.Linear(td),
    CurveModel.Bilinear(td),
    CurveModel.Exponential(td),
)

class IobTest {

    // ---- shared contract across all models ----

    @ParameterizedTest
    @ValueSource(doubles = [2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0])
    fun remainingAtZero_isOne_forAllModels(td: Double) {
        for (m in models(td)) {
            assertEquals(1.0, remainingFraction(0.0, m), TOL, "at t=0 for $m")
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0])
    fun remainingAtActionTime_isZero_forAllModels(td: Double) {
        for (m in models(td)) {
            assertEquals(0.0, remainingFraction(td, m), TOL, "at t=td for $m")
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [2.0, 3.0, 4.0, 5.0, 6.0, 7.0])
    fun remainingBeyondActionTime_isZero_forAllModels(td: Double) {
        for (m in models(td)) {
            for (past in doubleArrayOf(td + 0.001, td + 0.5, td + 5.0, td * 10)) {
                assertEquals(0.0, remainingFraction(past, m), TOL, "past t=td for $m at $past")
            }
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0])
    fun remainingIsAlwaysInUnitInterval_forAllModels(td: Double) {
        for (m in models(td)) {
            var t = 0.0
            while (t <= td + 0.5) {
                val r = remainingFraction(t, m)
                assertTrue(r in 0.0..1.0, "remaining out of [0,1] at t=$t for $m -> $r")
                t += 0.01
            }
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = [2.0, 3.0, 4.0, 5.0, 6.0, 7.0])
    fun remainingIsMonotonicNonIncreasing_forAllModels(td: Double) {
        for (m in models(td)) {
            var prev = remainingFraction(0.0, m)
            var t = 0.0
            while (t <= td) {
                val r = remainingFraction(t, m)
                assertTrue(
                    r <= prev + LOOSE_TOL,
                    "not monotone non-increasing at t=$t for $m: $r > $prev",
                )
                prev = r
                t += 0.01
            }
        }
    }

    // ---- linear specifics ----

    @ParameterizedTest
    @ValueSource(doubles = [2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0])
    fun linear_atHalfActionTime_isExactlyHalf(td: Double) {
        val r = remainingFraction(td / 2.0, CurveModel.Linear(td))
        assertEquals(0.5, r, TOL)
    }

    @Test
    fun linear_slopeIsConstant() {
        val m = CurveModel.Linear(5.0)
        val slope1 = remainingFraction(0.0, m) - remainingFraction(1.0, m)
        val slope2 = remainingFraction(2.0, m) - remainingFraction(3.0, m)
        val slope3 = remainingFraction(3.5, m) - remainingFraction(4.5, m)
        assertEquals(slope1, slope2, TOL)
        assertEquals(slope2, slope3, TOL)
        assertEquals(1.0 / 5.0, slope1, TOL)
    }

    // ---- bilinear specifics ----

    @Test
    fun bilinear_atPeak_equalsPeakRemaining() {
        val m = CurveModel.Bilinear(5.0)
        val tp = 5.0 * 0.4
        assertEquals(0.5, remainingFraction(tp, m), TOL)
    }

    @Test
    fun bilinear_segmentAMidpoint_isAverageOfEndpoints() {
        val m = CurveModel.Bilinear(5.0)
        val tp = 5.0 * 0.4
        assertEquals(0.75, remainingFraction(tp / 2.0, m), TOL)
    }

    @Test
    fun bilinear_segmentBMidpoint_isAverageOfEndpoints() {
        val m = CurveModel.Bilinear(5.0)
        val tp = 5.0 * 0.4
        val td = 5.0
        val midB = (tp + td) / 2.0
        assertEquals(0.25, remainingFraction(midB, m), TOL)
    }

    @Test
    fun bilinear_customPeak_isRespected() {
        val m = CurveModel.Bilinear(6.0, peakTimeFraction = 0.5, peakRemaining = 0.6)
        assertEquals(0.6, remainingFraction(3.0, m), TOL)
        assertEquals(0.8, remainingFraction(1.5, m), TOL)
        assertEquals(0.3, remainingFraction(4.5, m), TOL)
    }

    // ---- exponential specifics (OpenAPS peak-based) ----

    // Hand-computed at t = 150 min for td=5h, peakFraction=0.25:
    //   tau=112.5, a=0.75, exp(-8/3) ≈ 0.06948, S ≈ 2.6912
    //   bracket = 1.16667, exp(-4/3) ≈ 0.26360
    //   iob ≈ 1 - 2.6912*0.25*(1.16667*0.26360 + 1) ≈ 0.12033
    @Test
    fun exponential_default_matchesHandComputedMidpoint() {
        val m = CurveModel.Exponential(5.0, peakFraction = 0.25)
        assertEquals(0.12033, remainingFraction(2.5, m), 1e-3)
    }

    // Hand-computed at t = 60 min for td=5h, peakFraction=0.25:
    //   bracket = -0.77333, exp(-8/15) ≈ 0.58671
    //   iob ≈ 1 - 2.6912*0.25*(-0.77333*0.58671 + 1) ≈ 0.63245
    @Test
    fun exponential_default_matchesHandComputedEarly() {
        val m = CurveModel.Exponential(5.0, peakFraction = 0.25)
        assertEquals(0.63245, remainingFraction(1.0, m), 1e-3)
    }

    @Test
    fun exponential_scalesWithActionTime_notHardcodedConstant() {
        val short = remainingFraction(1.5, CurveModel.Exponential(3.0, peakFraction = 0.25))
        val long = remainingFraction(3.5, CurveModel.Exponential(7.0, peakFraction = 0.25))
        assertEquals(short, long, 1e-9)

        val shortAt1h = remainingFraction(1.0, CurveModel.Exponential(3.0, peakFraction = 0.25))
        val longAt1h = remainingFraction(1.0, CurveModel.Exponential(7.0, peakFraction = 0.25))
        assertTrue(longAt1h > shortAt1h, "longer DIA should retain more at 1h")
    }

    // ---- summing across doses (pooled — no meal/correction tag) ----

    @Test
    fun iob_withNoDoses_isZero() {
        val now = 1_000_000L
        assertEquals(0.0, iobAt(now, emptyList(), CurveModel.Linear(5.0)), TOL)
    }

    @Test
    fun iob_singleDose_isUnitsTimesRemaining() {
        val now = 3_600_000L * 100
        val d = Dose(units = 4.0, takenAtEpochMillis = now - 3_600_000L)
        val m = CurveModel.Linear(5.0)
        val expected = 4.0 * (1.0 - 1.0 / 5.0)
        assertEquals(expected, iobAt(now, listOf(d), m), TOL)
    }

    @Test
    fun iob_multipleOverlappingDoses_sumContributions() {
        val now = 3_600_000L * 100
        val m = CurveModel.Linear(5.0)
        val doses = listOf(
            Dose(3.0, now - 3_600_000L * 1),  // 1h -> 3*0.8 = 2.4
            Dose(2.0, now - 3_600_000L * 2),  // 2h -> 2*0.6 = 1.2
            Dose(5.0, now - 3_600_000L * 4),  // 4h -> 5*0.2 = 1.0
        )
        assertEquals(2.4 + 1.2 + 1.0, iobAt(now, doses, m), 1e-9)
    }

    @Test
    fun iob_doseExactlyAtActionTime_contributesZero() {
        val now = 3_600_000L * 100
        val d = Dose(10.0, now - 5 * 3_600_000L)
        assertEquals(0.0, iobAt(now, listOf(d), CurveModel.Linear(5.0)), TOL)
    }

    @Test
    fun iob_doseOlderThanActionTime_contributesZero() {
        val now = 3_600_000L * 100
        val d = Dose(10.0, now - 10 * 3_600_000L)
        assertEquals(0.0, iobAt(now, listOf(d), CurveModel.Linear(5.0)), TOL)
    }

    @Test
    fun iob_futureDose_isIgnored() {
        val now = 3_600_000L * 100
        val d = Dose(10.0, now + 3_600_000L)
        assertEquals(0.0, iobAt(now, listOf(d), CurveModel.Linear(5.0)), TOL)
    }

    // ---- decay-ring fill (peak-normalised: reset to full on dose, decay from there) ----

    private val hour = 3_600_000L

    @Test
    fun ring_freshSingleDose_isFull() {
        val now = hour * 100
        val doses = listOf(Dose(4.0, now))
        assertEquals(1.0f, iobRingState(now, doses, CurveModel.Linear(5.0)).fraction, 1e-6f)
    }

    @Test
    fun ring_noDoses_isEmpty() {
        val now = hour * 100
        val r = iobRingState(now, emptyList(), CurveModel.Linear(5.0))
        assertEquals(0f, r.fraction, 0f)
        assertEquals(0.0, r.iob, TOL)
    }

    @Test
    fun ring_freshDoseOnTopOfSpentOne_refillsToFull() {
        // The crossover case: a 6 U dose 3 h ago is mostly gone, then 2 U now.
        // The old iob/dosed-in-window ratio showed this ~half-full; peak-normalised
        // must read full, because the new dose lifts the peak.
        val now = hour * 100
        val m = CurveModel.Linear(5.0)
        val doses = listOf(Dose(6.0, now - hour * 3), Dose(2.0, now))
        assertEquals(1.0f, iobRingState(now, doses, m).fraction, 1e-6f)
    }

    @Test
    fun ring_halfwayThroughDecay_isHalfFull() {
        // Single 4 U dose on a 5 h linear curve: at t=2.5h, iob is half the peak.
        val doseAt = hour * 100
        val now = doseAt + (2.5 * hour).toLong()
        val m = CurveModel.Linear(5.0)
        val doses = listOf(Dose(4.0, doseAt))
        assertEquals(0.5f, iobRingState(now, doses, m).fraction, 1e-3f)
    }

    @Test
    fun ring_fillNeverExceedsOne_acrossOverlappingDecay() {
        val doseA = hour * 100
        val m = CurveModel.Bilinear(5.0)
        val doses = listOf(Dose(5.0, doseA), Dose(3.0, doseA + hour))
        var t = doseA
        while (t <= doseA + hour * 6) {
            val f = iobRingState(t, doses, m).fraction
            assertTrue(f in 0f..1f, "ring fill out of [0,1] at t=$t -> $f")
            t += hour / 6   // every 10 min
        }
    }
}
