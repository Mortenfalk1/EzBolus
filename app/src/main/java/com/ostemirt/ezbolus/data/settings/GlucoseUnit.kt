package com.ostemirt.ezbolus.data.settings

/**
 * Glucose unit the UI displays and the user enters values in.
 *
 * The engine is unit-agnostic — it multiplies/divides doubles. So the
 * whole app just needs to be self-consistent: if the user picks mmol/L,
 * then target, ISF and glucose input are all in mmol/L, and the math
 * works out the same.
 *
 * The conversion factor 18.0182 comes from molar mass of glucose:
 * 1 mmol/L = 18.0182 mg/dL (rounded to 4 decimals is enough for clinical
 * display; endocrinologists commonly use 18.0 or 18.0182).
 */
enum class GlucoseUnit(val label: String) {
    MG_DL("mg/dL"),
    MMOL_L("mmol/L");

    companion object {
        const val MG_DL_PER_MMOL_L = 18.0182

        fun convert(value: Double, from: GlucoseUnit, to: GlucoseUnit): Double = when {
            from == to -> value
            from == MG_DL && to == MMOL_L -> value / MG_DL_PER_MMOL_L
            from == MMOL_L && to == MG_DL -> value * MG_DL_PER_MMOL_L
            else -> value
        }
    }
}

/**
 * Sensible defaults per unit — a starting point until the user enters
 * their own values. Users typically overwrite these on first launch.
 */
data class GlucoseDefaults(val target: Double, val isf: Double, val alertHighExample: Double)

fun defaultsFor(unit: GlucoseUnit): GlucoseDefaults = when (unit) {
    GlucoseUnit.MG_DL -> GlucoseDefaults(target = 100.0, isf = 40.0, alertHighExample = 180.0)
    GlucoseUnit.MMOL_L -> GlucoseDefaults(target = 5.3, isf = 2.2, alertHighExample = 10.0)
}
