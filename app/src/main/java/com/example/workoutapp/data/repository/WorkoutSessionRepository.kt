package com.example.workoutapp.data.repository

import androidx.room.withTransaction
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.local.dao.ExerciseDao
import com.example.workoutapp.data.local.dao.MLFeedbackDao
import com.example.workoutapp.data.local.dao.UserGoalDao
import com.example.workoutapp.data.local.dao.WorkoutSessionDao
import com.example.workoutapp.data.local.dao.WorkoutPlanTemplateDao
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class WorkoutSessionRepository @Inject constructor(
    private val database: WorkoutDatabase,
    private val sessionDao: WorkoutSessionDao,
    private val templateDao: WorkoutPlanTemplateDao,
    private val exerciseDao: ExerciseDao,
    private val userGoalDao: UserGoalDao,
    private val mlFeedbackDao: MLFeedbackDao
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

    suspend fun completeWorkout(
        sessionId: Long,
        input: WorkoutSessionCompletionInput,
        now: Long = System.currentTimeMillis()
    ): WorkoutSessionCompletionResult = database.withTransaction {
        val session = sessionDao.getById(sessionId)
            ?: error("Workout session $sessionId no longer exists.")
        val sessionExercises = sessionDao.getExercisesForSessionSync(sessionId)
        val completedExercises = WorkoutCompletionSemantics.completedExercises(sessionExercises)
        val skippedExercises = WorkoutCompletionSemantics.skippedExercises(sessionExercises)
        val unfinishedExercises = WorkoutCompletionSemantics.unfinishedExercises(sessionExercises)

        if (WorkoutCompletionSemantics.isFinalized(session.status)) {
            return@withTransaction WorkoutSessionCompletionResult(
                sessionId = sessionId,
                status = session.status,
                alreadyFinalized = true,
                completedExerciseCount = completedExercises.size,
                skippedExerciseCount = skippedExercises.size,
                unfinishedExerciseCount = unfinishedExercises.size
            )
        }
        check(session.status == SessionStatus.PLANNED || session.status == SessionStatus.IN_PROGRESS) {
            "Workout session $sessionId is ${session.status} and cannot be completed."
        }

        val finalStatus = WorkoutCompletionSemantics.finalSessionStatus(sessionExercises)
        val startedAt = session.startedAt ?: now
        val durationMinutes = session.startedAt
            ?.let { started -> max(1, ((now - started) / 60_000L).toInt()) }
            ?: session.targetDurationMinutes

        val updated = sessionDao.finalizeOpenSession(
            sessionId = sessionId,
            status = finalStatus,
            startedAt = startedAt,
            completedAt = now,
            duration = durationMinutes,
            perceivedDifficulty = input.perceivedDifficulty,
            energyLevel = input.energyLevel,
            satisfactionRating = input.satisfactionRating,
            notes = input.notes.trim()
        )
        if (updated == 0) {
            val current = sessionDao.getById(sessionId)
                ?: error("Workout session $sessionId no longer exists.")
            check(WorkoutCompletionSemantics.isFinalized(current.status)) {
                "Workout session $sessionId changed to ${current.status} before completion could be saved."
            }
            return@withTransaction WorkoutSessionCompletionResult(
                sessionId = sessionId,
                status = current.status,
                alreadyFinalized = true,
                completedExerciseCount = completedExercises.size,
                skippedExerciseCount = skippedExercises.size,
                unfinishedExerciseCount = unfinishedExercises.size
            )
        }

        val completedExerciseIds = completedExercises.map { it.exerciseId }.distinct()
        if (completedExerciseIds.isNotEmpty()) {
            val exerciseUpdates = exerciseDao.markPerformed(completedExerciseIds, now)
            check(exerciseUpdates == completedExerciseIds.size) {
                "Workout completion could not update all completed exercise stats."
            }
        }

        val categoryFrequency = linkedMapOf<WorkoutCategory, Int>()
        completedExercises.forEach { sessionExercise ->
            exerciseDao.getCategoriesForExercise(sessionExercise.exerciseId)
                .filter { it != WorkoutCategory.CUSTOM }
                .forEach { category ->
                    categoryFrequency[category] = (categoryFrequency[category] ?: 0) + 1
                }
        }
        val totalAssignments = categoryFrequency.values.sum().coerceAtLeast(1)
        categoryFrequency.forEach { (category, count) ->
            check(userGoalDao.recordCategoryTraining(category, now) == 1) {
                "Category stats for $category are not initialized."
            }
            val minutes = (durationMinutes.toFloat() * count / totalAssignments).roundToInt().coerceAtLeast(1)
            check(userGoalDao.addCategoryMinutes(category, minutes, now) == 1) {
                "Category stats for $category are not initialized."
            }
        }
        userGoalDao.recalculateDaysSinceLastTrained(now)

        updateCompletionFeedback(
            sessionId = sessionId,
            completedExerciseIds = completedExercises.map { it.exerciseId }.toSet(),
            skippedExerciseIds = skippedExercises.map { it.exerciseId }.toSet(),
            notCompletedExerciseIds = unfinishedExercises.map { it.exerciseId }.toSet(),
            input = input
        )
        cachePreferenceScores(sessionExercises.map { it.exerciseId }.distinct(), now)

        WorkoutSessionCompletionResult(
            sessionId = sessionId,
            status = finalStatus,
            alreadyFinalized = false,
            completedExerciseCount = completedExercises.size,
            skippedExerciseCount = skippedExercises.size,
            unfinishedExerciseCount = unfinishedExercises.size
        )
    }

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

    suspend fun markExerciseCompleted(exerciseId: Long, completed: Boolean = true) {
        check(sessionDao.setExerciseCompleted(exerciseId, completed) == 1) {
            "Session exercise $exerciseId no longer exists."
        }
    }

    suspend fun markExerciseSkipped(exerciseId: Long, skipped: Boolean = true) {
        check(sessionDao.setExerciseSkipped(exerciseId, skipped) == 1) {
            "Session exercise $exerciseId no longer exists."
        }
    }

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

    private suspend fun updateCompletionFeedback(
        sessionId: Long,
        completedExerciseIds: Set<Long>,
        skippedExerciseIds: Set<Long>,
        notCompletedExerciseIds: Set<Long>,
        input: WorkoutSessionCompletionInput
    ) {
        val events = mlFeedbackDao.getEventsForSession(sessionId)
        events.forEach { event ->
            val action = when (event.exerciseId) {
                in completedExerciseIds -> FeedbackAction.COMPLETED
                in skippedExerciseIds -> FeedbackAction.SKIPPED
                in notCompletedExerciseIds -> FeedbackAction.NOT_COMPLETED
                else -> event.action
            }
            mlFeedbackDao.updateEventOutcome(
                sessionId = sessionId,
                exerciseId = event.exerciseId,
                action = action,
                difficulty = input.perceivedDifficulty,
                rating = input.satisfactionRating
            )
        }
    }

    private suspend fun cachePreferenceScores(exerciseIds: List<Long>, now: Long) {
        val scores = mutableListOf<MLPreferenceScore>()
        exerciseIds.forEach { exerciseId ->
            val positive = mlFeedbackDao.getPositiveCountForExercise(exerciseId)
            val negative = mlFeedbackDao.getNegativeCountForExercise(exerciseId)
            val total = positive + negative
            if (total > 0) {
                scores.add(
                    MLPreferenceScore(
                        key = "exercise:$exerciseId",
                        score = calculatePreference(positive, negative),
                        confidence = minOf(1f, total / 20f),
                        sampleCount = total,
                        updatedAt = now
                    )
                )
            }
        }

        WorkoutCategory.rotationCategories().forEach { category ->
            val positive = mlFeedbackDao.getPositiveCountForCategory(category)
            val negative = mlFeedbackDao.getNegativeCountForCategory(category)
            val total = positive + negative
            if (total > 0) {
                scores.add(
                    MLPreferenceScore(
                        key = "category:${category.name}",
                        score = calculatePreference(positive, negative),
                        confidence = minOf(1f, total / 30f),
                        sampleCount = total,
                        updatedAt = now
                    )
                )
            }
        }

        if (scores.isNotEmpty()) {
            mlFeedbackDao.insertPreferenceScores(scores)
        }
    }

    private fun calculatePreference(positive: Int, negative: Int): Float {
        val total = positive + negative
        if (total == 0) return 0f
        val p = positive.toFloat() / total
        val z = 1.96f
        val denominator = 1 + z * z / total
        val center = p + z * z / (2 * total)
        val spread = kotlin.math.sqrt((p * (1 - p) + z * z / (4 * total)) / total)
        return ((center - z * spread) / denominator * 2 - 1).coerceIn(-1f, 1f)
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

data class WorkoutSessionCompletionInput(
    val perceivedDifficulty: Int,
    val energyLevel: Int,
    val satisfactionRating: Int,
    val notes: String
)

data class WorkoutSessionCompletionResult(
    val sessionId: Long,
    val status: SessionStatus,
    val alreadyFinalized: Boolean,
    val completedExerciseCount: Int,
    val skippedExerciseCount: Int,
    val unfinishedExerciseCount: Int
)
