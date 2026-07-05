package com.ostemirt.ezbolus.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrossingTest {

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun thresholdAboveCurrent_returnsAlreadyBelow() {
        val m = CurveModel.Linear(5.0)
        val doses = listOf(Dose(2.0, now - hour))
        val r = findCrossing(doses, now, m, threshold = 3.0)
        assertTrue(r is CrossingResult.AlreadyBelow, "expected AlreadyBelow, got $r")
    }

    @Test
    fun currentEqualsThreshold_returnsAlreadyBelow() {
        val m = CurveModel.Linear(5.0)
        val doses = listOf(Dose(2.0, now - hour))
        val current = iobAt(now, doses, m)
        val r = findCrossing(doses, now, m, threshold = current)
        assertTrue(r is CrossingResult.AlreadyBelow, "at threshold should count as AlreadyBelow, got $r")
    }

    @Test
    fun linear_thresholdZero_crossesAtLastDoseExpiry() {
        val m = CurveModel.Linear(5.0)
        val takenAt = now - hour
        val doses = listOf(Dose(4.0, takenAt))
        val r = findCrossing(doses, now, m, threshold = 0.0, minuteTolerance = 0.5)
        assertTrue(r is CrossingResult.Found, "expected Found, got $r")
        val f = r as CrossingResult.Found
        val expectedMillis = takenAt + (5.0 * hour).toLong()
        val actualHours = (f.epochMillis - now) / 3_600_000.0
        val expectedHours = (expectedMillis - now) / 3_600_000.0
        assertEquals(expectedHours, actualHours, 0.02)
    }

    @Test
    fun linear_findsCrossingWithinMinuteTolerance() {
        val m = CurveModel.Linear(5.0)
        val doses = listOf(Dose(5.0, now))
        // iob(t) = 5*(1 - t/5) = 5 - t. Crosses 2.0 at t = 3.0 h.
        val r = findCrossing(doses, now, m, threshold = 2.0)
        assertTrue(r is CrossingResult.Found)
        val f = r as CrossingResult.Found
        val hours = (f.epochMillis - now) / 3_600_000.0
        assertEquals(3.0, hours, 0.02)
    }

    @Test
    fun addingNewDose_pushesCrossingLater() {
        val m = CurveModel.Linear(5.0)
        val doses1 = listOf(Dose(3.0, now))
        val r1 = findCrossing(doses1, now, m, threshold = 1.0)
        val t1 = ((r1 as CrossingResult.Found).epochMillis - now) / 3_600_000.0

        val doses2 = doses1 + Dose(2.0, now)
        val r2 = findCrossing(doses2, now, m, threshold = 1.0)
        val t2 = ((r2 as CrossingResult.Found).epochMillis - now) / 3_600_000.0

        assertTrue(t2 > t1, "adding a new dose must push the crossing later: t1=$t1 t2=$t2")
    }

    @Test
    fun overlappingDoses_crossingIsFirstTimeSumDropsBelow() {
        val m = CurveModel.Linear(5.0)
        val doses = listOf(Dose(3.0, now), Dose(2.0, now - hour))
        val threshold = 1.5
        val r = findCrossing(doses, now, m, threshold = threshold, minuteTolerance = 0.25)
        assertTrue(r is CrossingResult.Found)
        val at = (r as CrossingResult.Found).epochMillis
        assertEquals(threshold, iobAt(at, doses, m), 0.01)
    }

    @Test
    fun exponential_solverConverges_andFedBack_matchesThreshold() {
        val m = CurveModel.Exponential(5.0, peakFraction = 0.25)
        val doses = listOf(Dose(4.0, now))
        val r = findCrossing(doses, now, m, threshold = 1.0, minuteTolerance = 0.25)
        assertTrue(r is CrossingResult.Found)
        val f = r as CrossingResult.Found
        assertEquals(1.0, iobAt(f.epochMillis, doses, m), 0.02)
    }

    @Test
    fun bilinear_solverConverges_andFedBack_matchesThreshold() {
        val m = CurveModel.Bilinear(5.0)
        val doses = listOf(Dose(6.0, now))
        val r = findCrossing(doses, now, m, threshold = 2.0)
        assertTrue(r is CrossingResult.Found)
        val f = r as CrossingResult.Found
        assertEquals(2.0, iobAt(f.epochMillis, doses, m), 0.02)
    }

    @Test
    fun linear_crossingHitsThresholdToSubSecondPrecision() {
        // Regula-falsi step should land iob(crossing) essentially ON the
        // threshold, not up to `minuteTolerance` below it. Steep dose to make
        // any timing slop show up as a large IOB error: 20 U over 2 h -> 10 U/h,
        // so 0.5 min of slop would be ~0.083 U. We require < 0.005 U.
        val m = CurveModel.Linear(2.0)
        val doses = listOf(Dose(20.0, now))
        val r = findCrossing(doses, now, m, threshold = 2.0)
        assertTrue(r is CrossingResult.Found)
        val f = r as CrossingResult.Found
        assertEquals(2.0, iobAt(f.epochMillis, doses, m), 0.005)
    }

    @Test
    fun noDoses_returnsNoCrossing() {
        val m = CurveModel.Linear(5.0)
        val r = findCrossing(emptyList(), now, m, threshold = 1.0)
        assertTrue(r is CrossingResult.AlreadyBelow || r is CrossingResult.NoCrossing)
    }

    @Test
    fun allDosesAgedOut_returnsAlreadyBelow() {
        val m = CurveModel.Linear(5.0)
        val doses = listOf(Dose(10.0, now - 10 * hour))
        val r = findCrossing(doses, now, m, threshold = 1.0)
        assertTrue(r is CrossingResult.AlreadyBelow)
    }
}
