package com.ostemirt.ezbolus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {

    @Insert
    suspend fun insert(intake: Intake): Long

    @Insert
    suspend fun insertAll(intakes: List<Intake>): List<Long>

    @Query("SELECT * FROM intake ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<Intake>>

    /** Most-recent insulin rows. Aged-out ones contribute 0 to IOB — the engine
     *  handles that — so we don't have to time-filter here; 100 is plenty of
     *  headroom for any reasonable action window. */
    @Query("SELECT * FROM intake WHERE kind = 'insulin' ORDER BY takenAt DESC LIMIT 100")
    fun observeRecentInsulin(): Flow<List<Intake>>

    /** Non-Flow snapshot used by the alarm scheduler / boot receiver
     *  where a one-shot read is enough. */
    @Query("SELECT * FROM intake WHERE kind = 'insulin' ORDER BY takenAt DESC LIMIT 100")
    suspend fun recentInsulinSnapshot(): List<Intake>

    @Query("SELECT * FROM intake ORDER BY takenAt DESC")
    suspend fun allSnapshot(): List<Intake>

    @Query("DELETE FROM intake")
    suspend fun deleteAll()

    @Query("DELETE FROM intake WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM intake WHERE takenAt = :takenAt")
    suspend fun deleteByTakenAt(takenAt: Long)
}
