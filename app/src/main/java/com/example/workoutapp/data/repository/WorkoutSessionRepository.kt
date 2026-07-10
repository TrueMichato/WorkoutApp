package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.WorkoutSessionDao
import com.example.workoutapp.data.local.dao.WorkoutPlanTemplateDao
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutSessionRepository @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val templateDao: WorkoutPlanTemplateDao
) {
    // Session CRUD
    suspend fun createSession(session: WorkoutSession): Long = sessionDao.insert(session)

    suspend fun updateSession(session: WorkoutSession) = sessionDao.update(session)

    suspend fun deleteSession(sessionId: Long) = sessionDao.deleteById(sessionId)

    suspend fun getSessionById(id: Long): WorkoutSession? = sessionDao.getById(id)

    fun getSessionByIdFlow(id: Long): Flow<WorkoutSession?> = sessionDao.getByIdFlow(id)

    fun getAllSessions(): Flow<List<WorkoutSession>> = sessionDao.getAll()

    fun getSessionsByStatus(status: SessionStatus): Flow<List<WorkoutSession>> =
        sessionDao.getByStatus(status)

    suspend fun getInProgressSession(): WorkoutSession? = sessionDao.getInProgressSession()

    fun getInProgressSessionFlow(): Flow<WorkoutSession?> = sessionDao.getInProgressSessionFlow()

    fun getRecentCompletedSessions(limit: Int = 20): Flow<List<WorkoutSession>> =
        sessionDao.getRecentCompleted(limit)

    fun getUpcomingSessions(limit: Int = 10): Flow<List<WorkoutSession>> =
        sessionDao.getUpcoming(System.currentTimeMillis(), limit)

    // Date-based queries
    fun getSessionsForToday(): Flow<List<WorkoutSession>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        return sessionDao.getSessionsForDay(startOfDay, endOfDay)
    }

    fun getSessionsForDate(date: Long): Flow<List<WorkoutSession>> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        return sessionDao.getSessionsForDay(startOfDay, endOfDay)
    }

    fun getSessionsInWeek(weekStartDate: Long): Flow<List<WorkoutSession>> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = weekStartDate
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 7)
        val end = calendar.timeInMillis
        return sessionDao.getSessionsInRange(start, end)
    }

    fun getCompletedSessionsInRange(startDate: Long, endDate: Long): Flow<List<WorkoutSession>> =
        sessionDao.getCompletedInRange(startDate, endDate)

    // Session lifecycle
    suspend fun startSession(sessionId: Long) = sessionDao.startSession(sessionId)

    suspend fun completeSession(sessionId: Long, durationMinutes: Int) =
        sessionDao.completeSession(sessionId, durationMinutes)

    suspend fun skipSession(sessionId: Long) =
        sessionDao.updateStatus(sessionId, SessionStatus.SKIPPED)

    suspend fun markPartialComplete(sessionId: Long) =
        sessionDao.updateStatus(sessionId, SessionStatus.PARTIAL)

    // Session exercises
    suspend fun addExerciseToSession(sessionExercise: SessionExercise): Long =
        sessionDao.insertSessionExercise(sessionExercise)

    suspend fun addExercisesToSession(exercises: List<SessionExercise>): List<Long> =
        sessionDao.insertSessionExercises(exercises)

    suspend fun updateSessionExercise(exercise: SessionExercise) =
        sessionDao.updateSessionExercise(exercise)

    suspend fun removeSessionExercise(exercise: SessionExercise) =
        sessionDao.deleteSessionExercise(exercise)

    fun getExercisesForSession(sessionId: Long): Flow<List<SessionExercise>> =
        sessionDao.getExercisesForSession(sessionId)

    suspend fun getExercisesForSessionSync(sessionId: Long): List<SessionExercise> =
        sessionDao.getExercisesForSessionSync(sessionId)

    suspend fun markExerciseCompleted(exerciseId: Long, completed: Boolean = true) =
        sessionDao.setExerciseCompleted(exerciseId, completed)

    suspend fun markExerciseSkipped(exerciseId: Long, skipped: Boolean = true) =
        sessionDao.setExerciseSkipped(exerciseId, skipped)

    // Set logging
    suspend fun logSet(setLog: SetLog): Long = sessionDao.insertSetLog(setLog)

    suspend fun updateSetLog(setLog: SetLog) = sessionDao.updateSetLog(setLog)

    suspend fun deleteSetLog(setLog: SetLog) = sessionDao.deleteSetLog(setLog)

    fun getSetLogsForExercise(sessionExerciseId: Long): Flow<List<SetLog>> =
        sessionDao.getSetLogsForExercise(sessionExerciseId)

    suspend fun getSetLogsForExerciseSync(sessionExerciseId: Long): List<SetLog> =
        sessionDao.getSetLogsForExerciseSync(sessionExerciseId)

    // Statistics
    fun getCompletedSessionCount(): Flow<Int> = sessionDao.getCompletedSessionCount()

    fun getTotalTrainingMinutes(): Flow<Int> =
        sessionDao.getTotalTrainingMinutes().map { it ?: 0 }

    suspend fun getSessionCountInRange(startDate: Long, endDate: Long): Int =
        sessionDao.getSessionCountInRange(startDate, endDate)

    // Reusable plan templates
    fun getPlanTemplates(): Flow<List<WorkoutPlanTemplateSummary>> =
        templateDao.getTemplateSummaries()

    suspend fun getPlanTemplateById(templateId: Long): WorkoutPlanTemplate? =
        templateDao.getTemplateById(templateId)

    suspend fun getExercisesForPlanTemplate(templateId: Long): List<WorkoutPlanTemplateExercise> =
        templateDao.getExercisesForTemplate(templateId)

    suspend fun createPlanTemplate(
        template: WorkoutPlanTemplate,
        exercises: List<WorkoutPlanTemplateExercise>
    ): Long {
        val templateId = templateDao.insertTemplate(template)
        templateDao.replaceTemplateExercises(
            templateId = templateId,
            exercises = exercises.mapIndexed { index, exercise ->
                exercise.copy(id = 0, templateId = templateId, orderIndex = index)
            }
        )
        return templateId
    }

    suspend fun updatePlanTemplate(
        template: WorkoutPlanTemplate,
        exercises: List<WorkoutPlanTemplateExercise>
    ) {
        require(template.id != 0L) { "Template id is required for updates." }
        templateDao.updateTemplateWithExercises(
            template = template,
            exercises = exercises.mapIndexed { index, exercise ->
                exercise.copy(id = 0, templateId = template.id, orderIndex = index)
            }
        )
    }

    suspend fun deletePlanTemplate(templateId: Long) = templateDao.deleteTemplateById(templateId)

    suspend fun instantiateSessionFromTemplate(
        templateId: Long,
        plannedDate: Long = System.currentTimeMillis(),
        sessionNameOverride: String? = null
    ): Long {
        val template = templateDao.getTemplateById(templateId)
            ?: error("Workout plan template not found.")
        val templateExercises = templateDao.getExercisesForTemplate(templateId)
        require(templateExercises.isNotEmpty()) { "This workout plan does not contain any exercises yet." }

        val session = WorkoutSession(
            name = sessionNameOverride?.takeIf { it.isNotBlank() } ?: template.name,
            notes = template.notes,
            locationId = template.locationId,
            targetDurationMinutes = template.targetDurationMinutes,
            targetCategories = template.targetCategories,
            plannedDate = plannedDate,
            scheduledTimeSlot = template.scheduledTimeSlot
        )

        val configs = templateExercises.map {
            SessionExerciseConfig(
                exerciseId = it.exerciseId,
                sets = it.plannedSets,
                reps = it.plannedReps,
                restSeconds = it.plannedRestSeconds,
                section = it.section,
                prescriptionJson = it.prescriptionJson,
                notes = it.coachingNotes
            )
        }
        return createSessionWithExercises(session, configs)
    }

    /**
     * Create a complete workout session with exercises
     */
    suspend fun createSessionWithExercises(
        session: WorkoutSession,
        exerciseConfigs: List<SessionExerciseConfig>
    ): Long {
        val sessionExercises = exerciseConfigs.mapIndexed { index, config ->
            SessionExercise(
                sessionId = 0,
                exerciseId = config.exerciseId,
                orderIndex = index,
                section = config.section,
                plannedSets = config.sets,
                plannedReps = config.reps,
                plannedRestSeconds = config.restSeconds,
                prescriptionJson = config.prescriptionJson,
                notes = config.notes
            )
        }
        return sessionDao.insertWithExercises(session, sessionExercises)
    }
}

/**
 * Configuration for adding an exercise to a session
 */
data class SessionExerciseConfig(
    val exerciseId: Long,
    val section: PlanExerciseSection = PlanExerciseSection.MAIN,
    val sets: Int = 3,
    val reps: String = "8-12",
    val restSeconds: Int = 90,
    val prescriptionJson: String = "",
    val notes: String = ""
)
