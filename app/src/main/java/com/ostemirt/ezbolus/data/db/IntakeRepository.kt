package com.ostemirt.ezbolus.data.db

import android.content.Context
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import kotlinx.coroutines.flow.Flow

class IntakeRepository(context: Context) {
    private val dao = AppDatabase.get(context).intakeDao()

    val all: Flow<List<Intake>> = dao.observeAll()
    val recentInsulin: Flow<List<Intake>> = dao.observeRecentInsulin()

    /**
     * Save a confirmed calculation as up to three rows sharing the same
     * `takenAt` timestamp. Glucose and carbs are optional (correction-only
     * dose has no carbs; a dose entered without a matching glucose reading
     * is unusual but allowed).
     */
    suspend fun saveConfirmedDose(
        insulinUnits: Double,
        glucose: Double?,
        glucoseUnit: GlucoseUnit,
        carbsGrams: Double?,
        takenAt: Long = System.currentTimeMillis(),
    ): Long {
        val rows = buildList {
            add(
                Intake(
                    takenAt = takenAt,
                    kind = IntakeKind.INSULIN,
                    insulinUnits = insulinUnits,
                )
            )
            if (glucose != null) {
                add(
                    Intake(
                        takenAt = takenAt,
                        kind = IntakeKind.GLUCOSE,
                        glucoseValue = glucose,
                        glucoseUnit = glucoseUnit.name,
                    )
                )
            }
            if (carbsGrams != null && carbsGrams > 0.0) {
                add(
                    Intake(
                        takenAt = takenAt,
                        kind = IntakeKind.CARBS,
                        carbsGrams = carbsGrams,
                    )
                )
            }
        }
        dao.insertAll(rows)
        return takenAt
    }

    /** Delete all rows sharing a `takenAt` — used by the Undo-save snackbar. */
    suspend fun deleteAt(takenAt: Long) = dao.deleteByTakenAt(takenAt)

    /** Delete a single row from the history list. */
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
