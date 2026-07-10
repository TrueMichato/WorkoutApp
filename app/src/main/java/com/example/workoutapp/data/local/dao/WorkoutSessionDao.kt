package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    // Session CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Delete
    suspend fun delete(session: WorkoutSession)

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions ORDER BY plannedDate DESC, scheduledTimeSlot ASC")
    fun getAll(): Flow<List<WorkoutSession>>

    // By status
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY plannedDate DESC")
    fun getByStatus(status: SessionStatus): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun getInProgressSession(): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    fun getInProgressSessionFlow(): Flow<WorkoutSession?>

    // By date
    @Query("""
        SELECT * FROM workout_sessions 
        WHERE plannedDate >= :startOfDay AND plannedDate < :endOfDay
        ORDER BY scheduledTimeSlot ASC
    """)
    fun getSessionsForDay(startOfDay: Long, endOfDay: Long): Flow<List<WorkoutSession>>

    @Query("""
        SELECT * FROM workout_sessions 
        WHERE plannedDate >= :startDate AND plannedDate < :endDate
        ORDER BY plannedDate ASC, scheduledTimeSlot ASC
    """)
    fun getSessionsInRange(startDate: Long, endDate: Long): Flow<List<WorkoutSession>>

    @Query("""
        SELECT * FROM workout_sessions 
        WHERE status = 'COMPLETED' AND completedAt >= :startDate AND completedAt < :endDate
        ORDER BY completedAt DESC
    """)
    fun getCompletedInRange(startDate: Long, endDate: Long): Flow<List<WorkoutSession>>

    // Recent and upcoming
    @Query("""
        SELECT * FROM workout_sessions 
        WHERE status = 'COMPLETED'
        ORDER BY completedAt DESC
        LIMIT :limit
    """)
    fun getRecentCompleted(limit: Int = 20): Flow<List<WorkoutSession>>

    @Query("""
        SELECT * FROM workout_sessions 
        WHERE status = 'PLANNED' AND plannedDate >= :now
        ORDER BY plannedDate ASC, scheduledTimeSlot ASC
        LIMIT :limit
    """)
    fun getUpcoming(now: Long, limit: Int = 10): Flow<List<WorkoutSession>>

    // Status updates
    @Query("UPDATE workout_sessions SET status = :status, updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateStatus(sessionId: Long, status: SessionStatus, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE workout_sessions SET status = 'IN_PROGRESS', startedAt = :timestamp, updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun startSession(sessionId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE workout_sessions 
        SET status = 'COMPLETED', completedAt = :timestamp, actualDurationMinutes = :duration, updatedAt = :timestamp 
        WHERE id = :sessionId
    """)
    suspend fun completeSession(sessionId: Long, duration: Int, timestamp: Long = System.currentTimeMillis())

    // Session exercises
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionExercise(exercise: SessionExercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionExercises(exercises: List<SessionExercise>): List<Long>

    @Update
    suspend fun updateSessionExercise(exercise: SessionExercise)

    @Delete
    suspend fun deleteSessionExercise(exercise: SessionExercise)

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun getExercisesForSession(sessionId: Long): Flow<List<SessionExercise>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getExercisesForSessionSync(sessionId: Long): List<SessionExercise>

    @Query("UPDATE session_exercises SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setExerciseCompleted(id: Long, isCompleted: Boolean)

    @Query("UPDATE session_exercises SET isSkipped = :isSkipped WHERE id = :id")
    suspend fun setExerciseSkipped(id: Long, isSkipped: Boolean)

    // Set logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLog(log: SetLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLogs(logs: List<SetLog>): List<Long>

    @Update
    suspend fun updateSetLog(log: SetLog)

    @Delete
    suspend fun deleteSetLog(log: SetLog)

    @Query("SELECT * FROM set_logs WHERE sessionExerciseId = :sessionExerciseId ORDER BY setNumber ASC")
    fun getSetLogsForExercise(sessionExerciseId: Long): Flow<List<SetLog>>

    @Query("SELECT * FROM set_logs WHERE sessionExerciseId = :sessionExerciseId ORDER BY setNumber ASC")
    suspend fun getSetLogsForExerciseSync(sessionExerciseId: Long): List<SetLog>

    // Statistics
    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status = 'COMPLETED'")
    suspend fun getCompletedSessionCount(): Int

    @Query("SELECT SUM(actualDurationMinutes) FROM workout_sessions WHERE status = 'COMPLETED'")
    suspend fun getTotalTrainingMinutes(): Int?

    @Query("""
        SELECT COUNT(*) FROM workout_sessions 
        WHERE status = 'COMPLETED' AND completedAt >= :startDate AND completedAt < :endDate
    """)
    suspend fun getSessionCountInRange(startDate: Long, endDate: Long): Int
}

