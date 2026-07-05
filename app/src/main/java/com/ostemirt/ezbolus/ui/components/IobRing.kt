package com.ostemirt.ezbolus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ostemirt.ezbolus.ui.theme.EzBolusText
import com.ostemirt.ezbolus.ui.theme.LocalKindColors

/**
 * The 52dp IOB decay donut from the design.
 *
 * Track: outlineVariant, 5dp stroke.
 * Arc:   insulin kind color, 5dp stroke, rounded caps, drawn clockwise
 *        from top, sweep proportional to `fraction`.
 * Centre: monospace number showing `iob` (1 decimal).
 */
@Composable
fun IobRing(
    iob: Double,
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    strokeWidth: Dp = 5.dp,
    arcColor: Color = LocalKindColors.current.insulin,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            // Progress arc — start at 12 o'clock, sweep clockwise
            if (clamped > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "%.1f".format(iob),
            style = EzBolusText.IobRingCenter,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
