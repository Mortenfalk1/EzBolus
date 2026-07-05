package com.ostemirt.ezbolus.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.glance.Image
import com.ostemirt.ezbolus.MainActivity
import com.ostemirt.ezbolus.data.db.AppDatabase
import com.ostemirt.ezbolus.data.db.IntakeKind
import com.ostemirt.ezbolus.data.settings.SettingsRepository
import com.ostemirt.ezbolus.engine.Dose
import com.ostemirt.ezbolus.engine.iobRingState
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * The IOB home-screen widget.
 *
 * On each render (`provideGlance`), we load the current settings + recent
 * insulin doses, compute IOB via the engine, render the decay ring to a
 * Bitmap, and hand the Composable a snapshot. The Bitmap path exists
 * because Glance has no Canvas equivalent — see [RingBitmap].
 */
class IobWidget : GlanceAppWidget() {

    // Exact so LocalSize reports the real widget size and the ring can scale to
    // fit short (1-row) heights instead of being clipped.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        provideContent { WidgetContent(context, snapshot) }
    }

    @Composable
    private fun WidgetContent(context: Context, s: WidgetSnapshot) {
        val palette = paletteFor(context)
        val ringBitmap = RingBitmap.render(
            iobText = "%.1f".format(s.iob),
            arcFraction = s.fraction,
            trackColor = palette.track.toArgb(),
            arcColor = palette.arc.toArgb(),
            textColor = palette.onBg.toArgb(),
        )
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Scale the ring to the available height so it never clips on a short
        // (1-row) widget, capping it at 64dp on taller ones.
        val vPad = 8.dp
        val ringSize = (LocalSize.current.height - vPad * 2).coerceIn(28.dp, 64.dp)
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.bg))
                .clickable(actionStartActivity(openApp))
                .padding(horizontal = 16.dp, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(ringBitmap),
                contentDescription = "IOB ring",
                modifier = GlanceModifier.size(ringSize),
            )
            Column(
                modifier = GlanceModifier.padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (s.iob > 0.001) "${"%.1f".format(s.iob)} U active"
                    else "No insulin on board",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(palette.onBg),
                    ),
                )
                Text(
                    text = s.subtitle(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(palette.onBgVariant),
                    ),
                )
            }
        }
    }
}

/**
 * Snapshot of the state the widget renders. Kept flat so the composable
 * doesn't do any I/O on the main thread.
 */
data class WidgetSnapshot(
    val iob: Double,
    /** Ring fill in [0, 1]: IOB relative to its peak at the last dose. Full right
     *  after a dose, draining to empty as the pool clears. See [iobRingState]. */
    val fraction: Float,
    val lastDoseUnits: Double?,
    val lastDoseAgoMinutes: Long?,
) {
    fun subtitle(): String {
        if (lastDoseUnits == null || lastDoseAgoMinutes == null) return "Log a dose to see IOB"
        val ago = formatAgo(lastDoseAgoMinutes)
        return "Last dose: ${"%.1f".format(lastDoseUnits)} U · $ago ago"
    }

    private fun formatAgo(minutes: Long): String = when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "${h}h" else "${h}h ${m}m"
        }
    }
}

suspend fun loadSnapshot(context: Context): WidgetSnapshot {
    val settings = SettingsRepository(context).settings.first()
    val model = settings.toEngineCurve()
    val rows = AppDatabase.get(context).intakeDao().recentInsulinSnapshot()
    val doses = rows.mapNotNull { r ->
        r.insulinUnits?.let { Dose(units = it, takenAtEpochMillis = r.takenAt) }
    }
    val now = System.currentTimeMillis()
    val ring = iobRingState(now, doses, model)

    val lastInsulin = rows.firstOrNull { it.kind == IntakeKind.INSULIN }
    val lastDoseUnits = lastInsulin?.insulinUnits
    val lastDoseAgoMinutes = lastInsulin?.let { ((now - it.takenAt) / 60_000.0).roundToInt().toLong() }

    return WidgetSnapshot(ring.iob, ring.fraction, lastDoseUnits, lastDoseAgoMinutes)
}

// ---- Palette helpers ----

data class WidgetPalette(
    val bg: Color,
    val onBg: Color,
    val onBgVariant: Color,
    val track: Color,
    val arc: Color,
)

fun paletteFor(context: Context): WidgetPalette {
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return if (isDark) WidgetPalette(
        bg = Color(0xFF161C12),
        onBg = Color(0xFFE9EEDF),
        onBgVariant = Color(0xFFB6C0AA),
        track = Color(0xFF39422F),
        arc = Color(0xFFA7C957),
    ) else WidgetPalette(
        bg = Color(0xFFF4ECD6),
        onBg = Color(0xFF1A2418),
        onBgVariant = Color(0xFF4C5A45),
        track = Color(0xFFE4E6D6),
        arc = Color(0xFF386641),
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
