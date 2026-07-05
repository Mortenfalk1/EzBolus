package com.ostemirt.ezbolus.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TOL = 1e-9

class BolusTest {

    @Test
    fun carbBolus_isCarbsOverIcr() {
        val r = computeBolus(carbsGrams = 60.0, glucose = 100.0, icr = 10.0, isf = 40.0, target = 100.0, iob = 0.0)
        assertEquals(6.0, r.carbBolus, TOL)
    }

    @Test
    fun carbBolus_zeroCarbs_isZero() {
        val r = computeBolus(0.0, 100.0, 10.0, 40.0, 100.0, 0.0)
        assertEquals(0.0, r.carbBolus, TOL)
    }

    @Test
    fun rawCorrection_glucoseAboveTarget_isPositive() {
        val r = computeBolus(0.0, 180.0, 10.0, 40.0, 100.0, 0.0)
        assertEquals(2.0, r.rawCorrection, TOL)
        assertEquals(2.0, r.correctionAfterIob, TOL)
        assertEquals(2.0, r.total, TOL)
    }

    @Test
    fun glucoseBelowTarget_correctionIsNotPositive_andTotalIsCarbBolusOnly() {
        val r = computeBolus(30.0, 70.0, 10.0, 40.0, 100.0, 0.0)
        assertTrue(r.rawCorrection < 0.0)
        assertEquals(0.0, r.correctionAfterIob, TOL)
        assertEquals(3.0, r.carbBolus, TOL)
        assertEquals(3.0, r.total, TOL)
    }

    @Test
    fun glucoseExactlyAtTarget_correctionIsZero() {
        val r = computeBolus(0.0, 100.0, 10.0, 40.0, 100.0, 0.0)
        assertEquals(0.0, r.rawCorrection, TOL)
        assertEquals(0.0, r.correctionAfterIob, TOL)
    }

    @Test
    fun carbBolus_isNeverReducedByIob() {
        // Pooled IOB still doesn't touch the carb term — only the correction is reduced.
        val r = computeBolus(60.0, 180.0, 10.0, 40.0, 100.0, iob = 100.0)
        assertEquals(6.0, r.carbBolus, TOL)
        assertEquals(0.0, r.correctionAfterIob, TOL)
        assertEquals(6.0, r.total, TOL)
    }

    @Test
    fun iob_largerThanRawCorrection_flooredAtZero() {
        val r = computeBolus(0.0, 150.0, 10.0, 50.0, 100.0, iob = 3.0)
        assertEquals(1.0, r.rawCorrection, TOL)
        assertEquals(0.0, r.correctionAfterIob, TOL)
        assertEquals(0.0, r.total, TOL)
        assertTrue(r.correctionAfterIob >= 0.0)
    }

    @Test
    fun iob_partial_reducesCorrectionOnly() {
        val r = computeBolus(45.0, 200.0, 15.0, 40.0, 100.0, iob = 1.5)
        assertEquals(3.0, r.carbBolus, TOL)
        assertEquals(2.5, r.rawCorrection, TOL)
        assertEquals(1.0, r.correctionAfterIob, TOL)
        assertEquals(4.0, r.total, TOL)
    }

    @Test
    fun totalWithCarbsAndFullyAbsorbedCorrection_equalsCarbBolusExactly() {
        val r = computeBolus(75.0, 160.0, 15.0, 40.0, 100.0, iob = 5.0)
        assertEquals(5.0, r.carbBolus, TOL)
        assertEquals(0.0, r.correctionAfterIob, TOL)
        assertEquals(5.0, r.total, TOL)
    }

    @Test
    fun zeroCarbs_atTarget_zeroIob_isZero() {
        val r = computeBolus(0.0, 100.0, 10.0, 40.0, 100.0, 0.0)
        assertEquals(0.0, r.total, TOL)
    }

    // ---- correction-only mode ----

    @Test
    fun correctionOnly_matchesFullFormulaWithZeroCarbs() {
        val a = computeCorrectionOnly(glucose = 8.5, isf = 2.2, target = 5.3, iob = 0.5)
        val b = computeBolus(carbsGrams = 0.0, glucose = 8.5, icr = 999.0, isf = 2.2, target = 5.3, iob = 0.5)
        assertEquals(b.rawCorrection, a.rawCorrection, TOL)
        assertEquals(b.correctionAfterIob, a.correctionAfterIob, TOL)
        assertEquals(b.total, a.total, TOL)
        assertEquals(0.0, a.carbBolus, TOL)
    }

    @Test
    fun correctionOnly_belowTarget_yieldsZeroDose() {
        val r = computeCorrectionOnly(glucose = 5.0, isf = 2.2, target = 5.3, iob = 0.0)
        assertTrue(r.rawCorrection <= 0.0)
        assertEquals(0.0, r.correctionAfterIob, TOL)
        assertEquals(0.0, r.total, TOL)
    }

    // ---- rounding ----

    @Test
    fun roundToIncrement_halfUnit_examples() {
        assertEquals(3.0, roundToIncrement(2.8, 0.5), TOL)
        assertEquals(3.0, roundToIncrement(3.1, 0.5), TOL)
        assertEquals(3.5, roundToIncrement(3.3, 0.5), TOL)
        assertEquals(0.0, roundToIncrement(0.24, 0.5), TOL)
        assertEquals(0.5, roundToIncrement(0.26, 0.5), TOL)
    }

    @Test
    fun roundToIncrement_tenthUnit_examples() {
        assertEquals(2.5, roundToIncrement(2.53, 0.1), 1e-9)
        assertEquals(2.6, roundToIncrement(2.56, 0.1), 1e-9)
    }

    // ---- floor / ceil for pen users ----

    @Test
    fun floorToIncrement_wholeUnits() {
        assertEquals(2.0, floorToIncrement(2.5, 1.0), 1e-9)
        assertEquals(2.0, floorToIncrement(2.99, 1.0), 1e-9)
        assertEquals(0.0, floorToIncrement(0.9, 1.0), 1e-9)
        assertEquals(3.0, floorToIncrement(3.0, 1.0), 1e-9)   // already on a step
    }

    @Test
    fun ceilToIncrement_wholeUnits() {
        assertEquals(3.0, ceilToIncrement(2.5, 1.0), 1e-9)
        assertEquals(3.0, ceilToIncrement(2.01, 1.0), 1e-9)
        assertEquals(1.0, ceilToIncrement(0.1, 1.0), 1e-9)
        assertEquals(3.0, ceilToIncrement(3.0, 1.0), 1e-9)    // already on a step
    }

    @Test
    fun floorAndCeil_halfUnits_bracketExactValue() {
        val exact = 2.53
        assertEquals(2.5, floorToIncrement(exact, 0.5), 1e-9)
        assertEquals(3.0, ceilToIncrement(exact, 0.5), 1e-9)
    }

    @Test
    fun floorEqualsCeil_whenValueIsOnStep() {
        assertEquals(floorToIncrement(2.0, 1.0), ceilToIncrement(2.0, 1.0), 1e-9)
        assertEquals(floorToIncrement(1.5, 0.5), ceilToIncrement(1.5, 0.5), 1e-9)
    }

    @Test
    fun floor_rejectsNegative() {
        try {
            floorToIncrement(-0.1, 1.0)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* ok */ }
    }
}
