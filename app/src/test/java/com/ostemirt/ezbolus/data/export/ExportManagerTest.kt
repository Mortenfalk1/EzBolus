package com.ostemirt.ezbolus.data.export

import com.ostemirt.ezbolus.data.db.Intake
import com.ostemirt.ezbolus.data.db.IntakeKind
import com.ostemirt.ezbolus.data.settings.AppSettings
import com.ostemirt.ezbolus.data.settings.CurveModelKind
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.data.settings.NotificationStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-Kotlin round-trip: build JSON -> parse back -> assert equality.
 *
 * `ExportJson` doesn't touch Room, DataStore, or Context, so this runs as
 * a fast JVM test alongside the engine tests.
 */
class ExportManagerTest {

    private val sampleSettings = AppSettings(
        icr = 10.0,
        isf = 2.2,
        target = 5.3,
        glucoseUnit = GlucoseUnit.MMOL_L,
        actionTimeHours = 4.5,
        curveModel = CurveModelKind.EXPONENTIAL,
        alertEnabled = true,
        alertThresholdUnits = 1.5,
        alertReArmOnRise = true,
        alertStyle = NotificationStyle.ALARM,
        dosingIncrement = 0.5,
    )

    private val sampleIntakes = listOf(
        Intake(id = 1, takenAt = 1_700_000_000_000L, kind = IntakeKind.INSULIN,
            insulinUnits = 6.5),
        Intake(id = 2, takenAt = 1_700_000_000_000L, kind = IntakeKind.GLUCOSE,
            glucoseValue = 9.8, glucoseUnit = "MMOL_L"),
        Intake(id = 3, takenAt = 1_700_000_000_000L, kind = IntakeKind.CARBS,
            carbsGrams = 45.0),
        Intake(id = 4, takenAt = 1_700_003_600_000L, kind = IntakeKind.INSULIN,
            insulinUnits = 2.0, note = "correction"),
    )

    @Test
    fun settings_roundTripPreservesAllFields() {
        val json = ExportJson.buildJson(sampleSettings, emptyList())
            .getJSONObject("settings")
        val parsed = ExportJson.parseSettings(json)
        assertEquals(sampleSettings, parsed)
    }

    @Test
    fun intakes_roundTripPreservesFieldsExceptId() {
        val json = ExportJson.buildJson(sampleSettings, sampleIntakes)
            .getJSONArray("intakes")
        val parsed = ExportJson.parseIntakes(json)
        // IDs are reset to 0 so Room autogenerates on re-insert.
        val expected = sampleIntakes.map { it.copy(id = 0) }
        assertEquals(expected, parsed)
    }

    @Test
    fun intakes_omitNullOptionalFields() {
        val insulinOnly = listOf(
            Intake(id = 1, takenAt = 1_700_000_000_000L, kind = IntakeKind.INSULIN,
                insulinUnits = 3.0),
        )
        val json = ExportJson.buildJson(sampleSettings, insulinOnly)
            .getJSONArray("intakes")
            .getJSONObject(0)
        assertTrue(!json.has("glucoseValue"), "no glucoseValue in an insulin row")
        assertTrue(!json.has("carbsGrams"), "no carbsGrams in an insulin row")
        assertTrue(!json.has("note"), "no note when absent")
    }

    @Test
    fun schemaVersion_isOne() {
        val json = ExportJson.buildJson(sampleSettings, sampleIntakes)
        assertEquals(1, json.getInt("schema"))
    }

    @Test
    fun exportedAt_isPositive() {
        val json = ExportJson.buildJson(sampleSettings, sampleIntakes)
        assertTrue(json.getLong("exportedAt") > 0L)
    }

    @Test
    fun alertStyle_missingInLegacyJson_fallsBackToGentle() {
        val legacy = ExportJson.buildJson(sampleSettings.copy(alertStyle = NotificationStyle.GENTLE), emptyList())
            .getJSONObject("settings")
        legacy.remove("alertStyle")   // simulate a pre-alertStyle export
        val parsed = ExportJson.parseSettings(legacy)
        assertEquals(NotificationStyle.GENTLE, parsed.alertStyle)
    }

}
