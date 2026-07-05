package com.ostemirt.ezbolus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Roboto = FontFamily.Default   // System Roboto on Android

/**
 * Typography scale matched to the design spec.
 *
 * Design-spec sizes (Roboto):
 *   App bar title            22 / 400
 *   Screen big number        46 / 500  letter-spacing -1.5
 *   Dose card number         38 / 500  letter-spacing -1.0
 *   Section / card title     15 / 600
 *   Body / list value        16 / 400
 *   Result eyebrow           12 / 700  uppercase, letter-spacing 0.8
 *   Label / meta / support   11-13 / 400
 *   Mono IOB centre          13-14 / 600  (Roboto Mono, tabular)
 *
 * Those roles are mapped onto Material3's typography slots below. Where a
 * role doesn't cleanly fit an M3 slot, screens grab an explicit TextStyle
 * (see [BigDose], [DoseChoice], [Eyebrow], [IobRingCenter]).
 */
val EzBolusTypography = Typography(
    titleLarge = TextStyle(fontFamily = Roboto, fontSize = 22.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontFamily = Roboto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Roboto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),

    bodyLarge = TextStyle(fontFamily = Roboto, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Roboto, fontSize = 12.sp),

    labelLarge = TextStyle(fontFamily = Roboto, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Roboto, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Roboto, fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/** Extended styles not covered by Material's default typography slots. */
object EzBolusText {
    val BigDose = TextStyle(
        fontFamily = Roboto,
        fontSize = 46.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1.5).sp,
    )
    val DoseChoice = TextStyle(
        fontFamily = Roboto,
        fontSize = 38.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp,
    )
    val Eyebrow = TextStyle(
        fontFamily = Roboto,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )
    val IobRingCenter = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val ActiveNumber = TextStyle(
        fontFamily = Roboto,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
    )
}
