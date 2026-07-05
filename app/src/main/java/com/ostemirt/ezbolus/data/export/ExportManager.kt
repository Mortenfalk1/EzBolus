package com.ostemirt.ezbolus.data.export

import android.content.Context
import android.net.Uri
import com.ostemirt.ezbolus.data.db.AppDatabase
import com.ostemirt.ezbolus.data.db.Intake
import com.ostemirt.ezbolus.data.settings.AppSettings
import com.ostemirt.ezbolus.data.settings.CurveModelKind
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.data.settings.NotificationStyle
import com.ostemirt.ezbolus.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON schema (v1):
 * ```
 * {
 *   "schema": 1,
 *   "exportedAt": <epoch millis>,
 *   "settings": { ... AppSettings fields ... },
 *   "intakes":  [ { id, takenAt, kind, insulinUnits?, glucoseValue?,
 *                   glucoseUnit?, carbsGrams?, note? }, ... ]
 * }
 * ```
 * Import replaces ALL existing data. There is no partial merge — for a
 * personal single-user app the round-trip contract "export produces a file
 * that reproduces the app state exactly on import" is what matters.
 */
class ExportManager(private val context: Context) {

    private val settingsRepo by lazy { SettingsRepository(context) }
    private val db by lazy { AppDatabase.get(context) }

    // ---- Export ----

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val settings = settingsRepo.settings.first()
        val intakes = db.intakeDao().allSnapshot()
        val json = ExportJson.buildJson(settings, intakes).toString(2)
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            requireNotNull(out) { "Could not open output stream for $uri" }
            out.write(json.toByteArray(Charsets.UTF_8))
        }
        intakes.size
    }

    // ---- Import ----

    suspend fun importFrom(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open input stream for $uri" }
            input.readBytes().toString(Charsets.UTF_8)
        }
        val root = JSONObject(json)
        val schema = root.optInt("schema", 0)
        require(schema == 1) { "Unsupported export schema version: $schema" }

        val settings = ExportJson.parseSettings(root.getJSONObject("settings"))
        val intakes = ExportJson.parseIntakes(root.getJSONArray("intakes"))

        val dao = db.intakeDao()
        dao.deleteAll()
        if (intakes.isNotEmpty()) dao.insertAll(intakes)
        settingsRepo.replace(settings)

        ImportSummary(intakeCount = intakes.size)
    }
}

data class ImportSummary(val intakeCount: Int)

/**
 * Pure-Kotlin JSON (de)serialisation for the backup format. Kept as a
 * separate object so the schema is testable without touching Room,
 * DataStore, or an Android Context.
 */
object ExportJson {

    fun buildJson(settings: AppSettings, intakes: List<Intake>): JSONObject {
        val settingsJson = JSONObject().apply {
            put("icr", settings.icr)
            put("isf", settings.isf)
            put("target", settings.target)
            put("glucoseUnit", settings.glucoseUnit.name)
            put("actionTimeHours", settings.actionTimeHours)
            put("curveModel", settings.curveModel.name)
            put("alertEnabled", settings.alertEnabled)
            put("alertThresholdUnits", settings.alertThresholdUnits)
            put("alertReArmOnRise", settings.alertReArmOnRise)
            put("alertStyle", settings.alertStyle.name)
            put("dosingIncrement", settings.dosingIncrement)
            put("libreStalenessMinutes", settings.libreStalenessMinutes)
        }
        val intakesJson = JSONArray().apply {
            for (i in intakes) put(
                JSONObject().apply {
                    put("id", i.id)
                    put("takenAt", i.takenAt)
                    put("kind", i.kind)
                    i.insulinUnits?.let { put("insulinUnits", it) }
                    i.glucoseValue?.let { put("glucoseValue", it) }
                    i.glucoseUnit?.let { put("glucoseUnit", it) }
                    i.carbsGrams?.let { put("carbsGrams", it) }
                    i.note?.let { put("note", it) }
                }
            )
        }
        return JSONObject().apply {
            put("schema", 1)
            put("exportedAt", System.currentTimeMillis())
            put("settings", settingsJson)
            put("intakes", intakesJson)
        }
    }

    fun parseSettings(o: JSONObject): AppSettings = AppSettings(
        icr = o.getDouble("icr"),
        isf = o.getDouble("isf"),
        target = o.getDouble("target"),
        glucoseUnit = GlucoseUnit.entries.first { it.name == o.getString("glucoseUnit") },
        actionTimeHours = o.getDouble("actionTimeHours"),
        curveModel = CurveModelKind.entries.first { it.name == o.getString("curveModel") },
        alertEnabled = o.getBoolean("alertEnabled"),
        alertThresholdUnits = o.getDouble("alertThresholdUnits"),
        alertReArmOnRise = o.getBoolean("alertReArmOnRise"),
        alertStyle = NotificationStyle.entries.firstOrNull { it.name == o.optString("alertStyle") }
            ?: AppSettings.Default.alertStyle,
        dosingIncrement = o.getDouble("dosingIncrement"),
        libreStalenessMinutes = if (o.has("libreStalenessMinutes"))
            o.getInt("libreStalenessMinutes") else AppSettings.Default.libreStalenessMinutes,
    )

    fun parseIntakes(arr: JSONArray): List<Intake> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                Intake(
                    // Reset id so Room autogenerates fresh ones (avoids PK collisions).
                    id = 0,
                    takenAt = o.getLong("takenAt"),
                    kind = o.getString("kind"),
                    insulinUnits = if (o.has("insulinUnits")) o.getDouble("insulinUnits") else null,
                    glucoseValue = if (o.has("glucoseValue")) o.getDouble("glucoseValue") else null,
                    glucoseUnit = if (o.has("glucoseUnit")) o.getString("glucoseUnit") else null,
                    carbsGrams = if (o.has("carbsGrams")) o.getDouble("carbsGrams") else null,
                    note = if (o.has("note")) o.getString("note") else null,
                )
            )
        }
    }
}
