package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.PhysicalTherapyDao
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhysicalTherapyRepository @Inject constructor(
    private val ptDao: PhysicalTherapyDao
) {
    // Routine CRUD
    suspend fun createRoutine(routine: PhysicalTherapyRoutine): Long = ptDao.insertRoutine(routine)

    suspend fun updateRoutine(routine: PhysicalTherapyRoutine) = ptDao.updateRoutine(routine)

    suspend fun deleteRoutine(routineId: Long) = ptDao.deleteRoutineById(routineId)

    suspend fun getRoutineById(id: Long): PhysicalTherapyRoutine? = ptDao.getRoutineById(id)

    fun getRoutineByIdFlow(id: Long): Flow<PhysicalTherapyRoutine?> = ptDao.getRoutineByIdFlow(id)

    fun getAllActiveRoutines(): Flow<List<PhysicalTherapyRoutine>> = ptDao.getAllActive()

    fun getActiveRoutines(): Flow<List<PhysicalTherapyRoutine>> = ptDao.getActiveRoutines()

    fun getMustDoRoutines(): Flow<List<PhysicalTherapyRoutine>> = ptDao.getMustDoRoutines()

    fun getArchivedRoutines(): Flow<List<PhysicalTherapyRoutine>> = ptDao.getArchivedRoutines()

    // Status updates
    suspend fun setRoutineActive(routineId: Long, isActive: Boolean) =
        ptDao.setActive(routineId, isActive)

    suspend fun archiveRoutine(routineId: Long) = ptDao.setArchived(routineId, true)

    suspend fun unarchiveRoutine(routineId: Long) = ptDao.setArchived(routineId, false)

    suspend fun setMustDo(routineId: Long, isMustDo: Boolean) =
        ptDao.setMustDo(routineId, isMustDo)

    // Routine exercises
    suspend fun addExerciseToRoutine(
        routineId: Long,
        exerciseId: Long,
        orderIndex: Int,
        sets: Int = 3,
        reps: String = "10-15",
        holdSeconds: Int? = null,
        restSeconds: Int = 30,
        instructions: String = ""
    ) {
        ptDao.insertRoutineExercise(
            PTRoutineExerciseCrossRef(
                routineId = routineId,
                exerciseId = exerciseId,
                orderIndex = orderIndex,
                prescribedSets = sets,
                prescribedReps = reps,
                prescribedHoldSeconds = holdSeconds,
                prescribedRestSeconds = restSeconds,
                specialInstructions = instructions
            )
        )
    }

    suspend fun setRoutineExercises(routineId: Long, exercises: List<PTRoutineExerciseCrossRef>) {
        ptDao.clearExercisesForRoutine(routineId)
        ptDao.insertRoutineExercises(exercises)
    }

    suspend fun getExercisesForRoutine(routineId: Long): List<PTRoutineExerciseCrossRef> =
        ptDao.getExercisesForRoutine(routineId)

    fun getExercisesForRoutineFlow(routineId: Long): Flow<List<PTRoutineExerciseCrossRef>> =
        ptDao.getExercisesForRoutineFlow(routineId)

    fun getExerciseDetailsForRoutine(routineId: Long): Flow<List<Exercise>> =
        ptDao.getExerciseDetailsForRoutine(routineId)

    // Session logging
    suspend fun startSession(routineId: Long, timeSlot: TimeSlot = TimeSlot.ANYTIME): Long {
        return ptDao.insertSessionLog(
            PTSessionLog(
                routineId = routineId,
                startedAt = System.currentTimeMillis(),
                timeSlot = timeSlot
            )
        )
    }

    suspend fun completeSession(
        logId: Long,
        exercisesCompleted: Int,
        exercisesTotal: Int,
        painBefore: Int? = null,
        painAfter: Int? = null,
        notes: String = "",
        symptomChanges: String = ""
    ) {
        val existing = ptDao.getSessionLogById(logId) ?: return
        ptDao.updateSessionLog(
            existing.copy(
                completedAt = System.currentTimeMillis(),
                exercisesCompleted = exercisesCompleted,
                exercisesTotal = exercisesTotal,
                painLevelBefore = painBefore,
                painLevelAfter = painAfter,
                notes = notes,
                symptomChanges = symptomChanges
            )
        )
    }

    fun getLogsForRoutine(routineId: Long): Flow<List<PTSessionLog>> =
        ptDao.getLogsForRoutine(routineId)

    suspend fun getLastLogForRoutine(routineId: Long): PTSessionLog? =
        ptDao.getLastLogForRoutine(routineId)

    fun getLogsInRange(startDate: Long, endDate: Long): Flow<List<PTSessionLog>> =
        ptDao.getLogsInRange(startDate, endDate)

    /**
     * Check if a routine has been completed today
     */
    suspend fun isRoutineCompletedToday(routineId: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        val logs = ptDao.getLogsForRoutineOnDay(routineId, startOfDay, endOfDay)
        return logs.any { it.completedAt != null }
    }

    /**
     * Get the number of times routine was completed today (for multiple daily routines)
     */
    suspend fun getTimesCompletedToday(routineId: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        val logs = ptDao.getLogsForRoutineOnDay(routineId, startOfDay, endOfDay)
        return logs.count { it.completedAt != null }
    }

    // Statistics
    suspend fun getLogCountForRoutine(routineId: Long): Int =
        ptDao.getLogCountForRoutine(routineId)

    suspend fun getTotalCompletedSessions(): Int = ptDao.getTotalCompletedSessions()

    suspend fun getAveragePainReduction(routineId: Long): Float? =
        ptDao.getAveragePainReduction(routineId)
}

