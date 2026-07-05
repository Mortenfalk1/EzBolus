package com.ostemirt.ezbolus.data.settings

import com.ostemirt.ezbolus.engine.CurveModel

/** Which decay curve the user picked for IOB. Serialisable label form of engine CurveModel. */
enum class CurveModelKind { LINEAR, BILINEAR, EXPONENTIAL }

/**
 * How aggressively the IOB threshold notification is delivered.
 *
 * The README recommends [GENTLE] on safety grounds: a stale/late notification
 * must never drive a dose decision, and an alarm implies action. [ALARM] is
 * available for users who explicitly want an audible wake-up (typically
 * overnight monitoring). Copy stays neutral either way.
 */
enum class NotificationStyle { GENTLE, ALARM }

/**
 * The whole set of single-value user settings.
 * Hourly ICR/ISF/target variants live in Room later — not here.
 */
data class AppSettings(
    val icr: Double,                    // grams of carbs covered per 1 U of insulin
    val isf: Double,                    // glucose drop (in `glucoseUnit`) per 1 U of insulin
    val target: Double,                 // target glucose in `glucoseUnit`
    val glucoseUnit: GlucoseUnit,

    val actionTimeHours: Double,        // duration of insulin action, 2.0..7.0 step 0.5
    val curveModel: CurveModelKind,

    val alertEnabled: Boolean,
    val alertThresholdUnits: Double,    // in insulin U, independent of glucose unit
    val alertReArmOnRise: Boolean,
    val alertStyle: NotificationStyle,

    /** Smallest increment your pen (or pump) can deliver, in insulin U.
     *  Typical values: 1.0 for standard whole-unit pens, 0.5 for half-unit
     *  pens (e.g. NovoPen Echo), 0.1 for pumps. Drives the round-up / round-down
     *  options shown on the result card. */
    val dosingIncrement: Double,
) {
    fun toEngineCurve(): CurveModel = when (curveModel) {
        CurveModelKind.LINEAR -> CurveModel.Linear(actionTimeHours)
        CurveModelKind.BILINEAR -> CurveModel.Bilinear(actionTimeHours)
        CurveModelKind.EXPONENTIAL -> CurveModel.Exponential(actionTimeHours)
    }

    companion object {
        /** First-launch defaults. mg/dL is the more common starting point; user
         *  can flip to mmol/L in Settings and the values auto-convert. */
        val Default = AppSettings(
            icr = 10.0,
            isf = 40.0,
            target = 100.0,
            glucoseUnit = GlucoseUnit.MG_DL,
            actionTimeHours = 4.0,
            curveModel = CurveModelKind.LINEAR,
            alertEnabled = false,
            alertThresholdUnits = 2.0,
            alertReArmOnRise = true,
            alertStyle = NotificationStyle.GENTLE,   // README default; user can escalate
            dosingIncrement = 1.0,      // whole-unit pen — user's stated device
        )
    }
}

/** Pen / pump options offered in Settings. */
val supportedDosingIncrements: List<Double> = listOf(0.1, 0.5, 1.0)

/** The 11 supported action times per the README: 2.0..7.0 in 0.5 steps. */
val supportedActionTimes: List<Double> =
    generateSequence(2.0) { it + 0.5 }.takeWhile { it <= 7.0 }.toList()
