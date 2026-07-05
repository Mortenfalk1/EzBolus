package com.ostemirt.ezbolus.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only history row: a single glucose reading, a single carb entry,
 * or a single insulin dose. One user action (confirming a bolus) typically
 * writes 1–3 rows sharing the same `takenAt`.
 */
@Entity(
    tableName = "intake",
    indices = [Index("takenAt"), Index("kind")],
)
data class Intake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Wall-clock time the reading/dose applies to. */
    val takenAt: Long,

    /** One of [IntakeKind]. Stored as string so the DB schema is stable. */
    val kind: String,

    /** Insulin units, when kind = "insulin". */
    val insulinUnits: Double? = null,

    /** Glucose value in `glucoseUnit`, when kind = "glucose". */
    val glucoseValue: Double? = null,

    /** "MG_DL" or "MMOL_L" — stored per-row so history survives future unit switches. */
    val glucoseUnit: String? = null,

    /** Grams of carbs, when kind = "carbs". */
    val carbsGrams: Double? = null,

    val note: String? = null,
)

object IntakeKind {
    const val INSULIN = "insulin"
    const val GLUCOSE = "glucose"
    const val CARBS = "carbs"
}
