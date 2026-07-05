package com.ostemirt.ezbolus.engine

/**
 * How a single dose's active insulin decays over time.
 *
 * All models use `actionTimeHours` (a.k.a. DIA, duration of insulin action).
 * Remaining-fraction is 1.0 at t=0 and 0.0 at t>=actionTimeHours, monotonically.
 */
sealed interface CurveModel {
    val actionTimeHours: Double

    /**
     * Straight-line decay from 1.0 -> 0.0 across [0, actionTimeHours].
     * Simplest, most conservative model.
     */
    data class Linear(override val actionTimeHours: Double) : CurveModel

    /**
     * Two linear segments in the remaining-fraction curve.
     *
     * Segment A: (0, 1.0) -> (peakTime, peakRemaining)
     * Segment B: (peakTime, peakRemaining) -> (actionTimeHours, 0.0)
     *
     * peakTime = actionTimeHours * peakTimeFraction (in (0, 1)).
     * peakRemaining is the remaining fraction at that peak (in (0, 1)).
     * Default: peak at 40% of action time with 50% still on board — a
     * simple approximation of rapid-analog kinetics without a full exponential.
     */
    data class Bilinear(
        override val actionTimeHours: Double,
        val peakTimeFraction: Double = 0.4,
        val peakRemaining: Double = 0.5,
    ) : CurveModel {
        init {
            require(peakTimeFraction > 0.0 && peakTimeFraction < 1.0) {
                "peakTimeFraction must be in (0, 1): $peakTimeFraction"
            }
            require(peakRemaining > 0.0 && peakRemaining < 1.0) {
                "peakRemaining must be in (0, 1): $peakRemaining"
            }
        }
    }

    /**
     * OpenAPS peak-based exponential model (dm61).
     * Source: https://github.com/openaps/oref0/blob/master/lib/iob/calculate.js
     * Also documented at https://openaps.readthedocs.io/en/latest/
     *
     * Formula (t, td, tp all in minutes; peak tp = actionTimeHours*60*peakFraction):
     *   tau = tp * (1 - tp/td) / (1 - 2*tp/td)
     *   a   = 2*tau/td
     *   S   = 1 / (1 - a + (1 + a) * exp(-td/tau))
     *   IOB(t) = 1 - S*(1-a) * ( (t^2/(tau*td*(1-a)) - t/td - 1)*exp(-t/tau) + 1 )
     *
     * peakFraction must be strictly less than 0.5 or tau blows up. The default
     * 0.25 puts the activity peak a quarter of the way into the action window —
     * e.g. 75 min peak for a 5 h DIA, matching NovoRapid / Humalog defaults
     * used in OpenAPS.
     */
    data class Exponential(
        override val actionTimeHours: Double,
        val peakFraction: Double = 0.25,
    ) : CurveModel {
        init {
            require(peakFraction > 0.0 && peakFraction < 0.5) {
                "peakFraction must be in (0, 0.5): $peakFraction"
            }
        }
    }
}
