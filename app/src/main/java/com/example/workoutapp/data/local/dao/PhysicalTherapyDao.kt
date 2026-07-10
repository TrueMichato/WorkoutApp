package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhysicalTherapyDao {

    // PT Routine CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: PhysicalTherapyRoutine): Long

    @Update
    suspend fun updateRoutine(routine: PhysicalTherapyRoutine)

    @Delete
    suspend fun deleteRoutine(routine: PhysicalTherapyRoutine)

    @Query("DELETE FROM pt_routines WHERE id = :routineId")
    suspend fun deleteRoutineById(routineId: Long)

    @Query("SELECT * FROM pt_routines WHERE id = :id")
    suspend fun getRoutineById(id: Long): PhysicalTherapyRoutine?

    @Query("SELECT * FROM pt_routines WHERE id = :id")
    fun getRoutineByIdFlow(id: Long): Flow<PhysicalTherapyRoutine?>

    @Query("SELECT * FROM pt_routines WHERE isArchived = 0 ORDER BY priority ASC, name ASC")
    fun getAllActive(): Flow<List<PhysicalTherapyRoutine>>

    @Query("SELECT * FROM pt_routines WHERE isActive = 1 AND isArchived = 0 ORDER BY priority ASC")
    fun getActiveRoutines(): Flow<List<PhysicalTherapyRoutine>>

    @Query("SELECT * FROM pt_routines WHERE isMustDo = 1 AND isActive = 1 AND isArchived = 0 ORDER BY priority ASC")
    fun getMustDoRoutines(): Flow<List<PhysicalTherapyRoutine>>

    @Query("SELECT * FROM pt_routines WHERE isArchived = 1 ORDER BY name ASC")
    fun getArchivedRoutines(): Flow<List<PhysicalTherapyRoutine>>

    // Status updates
    @Query("UPDATE pt_routines SET isActive = :isActive, updatedAt = :timestamp WHERE id = :routineId")
    suspend fun setActive(routineId: Long, isActive: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE pt_routines SET isArchived = :isArchived, updatedAt = :timestamp WHERE id = :routineId")
    suspend fun setArchived(routineId: Long, isArchived: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE pt_routines SET isMustDo = :isMustDo, updatedAt = :timestamp WHERE id = :routineId")
    suspend fun setMustDo(routineId: Long, isMustDo: Boolean, timestamp: Long = System.currentTimeMillis())

    // Routine exercises
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercise(ref: PTRoutineExerciseCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(refs: List<PTRoutineExerciseCrossRef>)

    @Delete
    suspend fun deleteRoutineExercise(ref: PTRoutineExerciseCrossRef)

    @Query("DELETE FROM pt_routine_exercises WHERE routineId = :routineId")
    suspend fun clearExercisesForRoutine(routineId: Long)

    @Query("SELECT * FROM pt_routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    suspend fun getExercisesForRoutine(routineId: Long): List<PTRoutineExerciseCrossRef>

    @Query("SELECT * FROM pt_routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    fun getExercisesForRoutineFlow(routineId: Long): Flow<List<PTRoutineExerciseCrossRef>>

    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN pt_routine_exercises pre ON e.id = pre.exerciseId
        WHERE pre.routineId = :routineId
        ORDER BY pre.orderIndex ASC
    """)
    fun getExerciseDetailsForRoutine(routineId: Long): Flow<List<Exercise>>

    // PT Session logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionLog(log: PTSessionLog): Long

    @Update
    suspend fun updateSessionLog(log: PTSessionLog)

    @Delete
    suspend fun deleteSessionLog(log: PTSessionLog)

    @Query("SELECT * FROM pt_session_logs WHERE id = :id")
    suspend fun getSessionLogById(id: Long): PTSessionLog?

    @Query("SELECT * FROM pt_session_logs WHERE routineId = :routineId ORDER BY startedAt DESC")
    fun getLogsForRoutine(routineId: Long): Flow<List<PTSessionLog>>

    @Query("SELECT * FROM pt_session_logs WHERE routineId = :routineId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLastLogForRoutine(routineId: Long): PTSessionLog?

    @Query("""
        SELECT * FROM pt_session_logs 
        WHERE startedAt >= :startDate AND startedAt < :endDate
        ORDER BY startedAt DESC
    """)
    fun getLogsInRange(startDate: Long, endDate: Long): Flow<List<PTSessionLog>>

    @Query("""
        SELECT * FROM pt_session_logs 
        WHERE routineId = :routineId AND startedAt >= :startOfDay AND startedAt < :endOfDay
    """)
    suspend fun getLogsForRoutineOnDay(routineId: Long, startOfDay: Long, endOfDay: Long): List<PTSessionLog>

    // Statistics
    @Query("SELECT COUNT(*) FROM pt_session_logs WHERE routineId = :routineId")
    suspend fun getLogCountForRoutine(routineId: Long): Int

    @Query("SELECT COUNT(*) FROM pt_session_logs WHERE completedAt IS NOT NULL")
    suspend fun getTotalCompletedSessions(): Int

    @Query("""
        SELECT AVG(painLevelAfter - painLevelBefore) 
        FROM pt_session_logs 
        WHERE routineId = :routineId AND painLevelBefore IS NOT NULL AND painLevelAfter IS NOT NULL
    """)
    suspend fun getAveragePainReduction(routineId: Long): Float?
}

