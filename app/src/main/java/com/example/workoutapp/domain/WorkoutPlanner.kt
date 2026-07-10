package com.example.workoutapp.domain

import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.ExerciseProgrammingPreset
import com.example.workoutapp.data.model.MLPreferenceScore
import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.resolveStoredProgrammingPreset
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.model.toJson
import com.example.workoutapp.data.model.toRichPrescriptionData
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.MLFeedbackRepository
import com.example.workoutapp.data.repository.SessionExerciseConfig
import com.example.workoutapp.data.repository.UserGoalRepository
import com.example.workoutapp.data.repository.WorkoutSessionRepository
import com.example.workoutapp.domain.ml.WorkoutRecommender
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
data class WorkoutGenerationParams(
    val locationId: Long? = null,
    val durationMinutes: Int = 60,
    val plannedDate: Long = System.currentTimeMillis(),
    val timeSlot: TimeSlot = TimeSlot.ANYTIME,
    val selectedCategories: List<WorkoutCategory> = emptyList(),
    val sessionName: String? = null,
    val excludedExerciseIds: Set<Long> = emptySet()
)

data class WorkoutCompletionInput(
    val perceivedDifficulty: Int = 6,
    val energyLevel: Int = 6,
    val satisfactionRating: Int = 4,
    val notes: String = ""
)

data class WorkoutGenerationResult(
    val sessionId: Long,
    val draft: WorkoutPlanDraft
)

data class SaveWorkoutPlanTemplateInput(
    val name: String,
    val description: String = ""
)

data class PlannedExerciseSummary(
    val exerciseId: Long,
    val name: String,
    val categories: List<WorkoutCategory>,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val estimatedMinutes: Int,
    val recommendationReason: String = ""
)

data class WorkoutPlanDraft(
    val session: WorkoutSession,
    val exercises: List<SessionExerciseConfig>,
    val focusCategories: List<WorkoutCategory>,
    val estimatedDurationMinutes: Int,
    val reasoning: List<String>,
    val exerciseSummaries: List<PlannedExerciseSummary>
)

data class WorkoutPlannerCandidate(
    val exercise: Exercise,
    val categories: List<WorkoutCategory>,
    val requiredEquipmentIds: Set<Long>
)

@Singleton
class WorkoutPlanner @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val equipmentRepository: EquipmentRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val userGoalRepository: UserGoalRepository,
    private val mlFeedbackRepository: MLFeedbackRepository,
    private val workoutRecommender: WorkoutRecommender
) {
    suspend fun previewWorkout(params: WorkoutGenerationParams): WorkoutPlanDraft = prepareDraft(params)

    suspend fun generateAndSaveWorkout(params: WorkoutGenerationParams): WorkoutGenerationResult {
        val draft = prepareDraft(params)
        val sessionId = sessionRepository.createSessionWithExercises(draft.session, draft.exercises)

        // Record each kept suggestion as an ML feedback event so later completions/skips can refine it.
        val allCategoryStats = userGoalRepository.getAllCategoryStats().first()
        val categoryDaysSince = allCategoryStats.associate { it.category to it.daysSinceLastTrained }
        val weights = runCatching { userGoalRepository.getCategoryWeights() }.getOrDefault(emptyMap())
        val currentBalanceScore = DashboardAnalytics.balanceScore(allCategoryStats, weights)

        draft.exerciseSummaries.forEach { summary ->
            val exercise = exerciseRepository.getExerciseById(summary.exerciseId) ?: return@forEach
            val chosenCategory = summary.categories.firstOrNull() ?: return@forEach
            val daysSinceExercise = exercise.lastPerformedAt
                ?.let { ((System.currentTimeMillis() - it) / MILLIS_PER_DAY).toInt() }
                ?: Int.MAX_VALUE

            mlFeedbackRepository.recordSuggestion(
                exerciseId = summary.exerciseId,
                category = chosenCategory,
                sessionId = sessionId,
                daysSinceExercise = daysSinceExercise,
                daysSinceCategory = categoryDaysSince[chosenCategory] ?: Int.MAX_VALUE,
                exerciseTimesPerformed = exercise.timesPerformed,
                difficulty = exercise.difficulty.toMlDifficultyLevel(),
                sessionDuration = draft.estimatedDurationMinutes,
                balanceScore = currentBalanceScore,
                timeSlot = draft.session.scheduledTimeSlot
            )
        }

        return WorkoutGenerationResult(sessionId = sessionId, draft = draft)
    }

    suspend fun saveDraftAsTemplate(
        draft: WorkoutPlanDraft,
        input: SaveWorkoutPlanTemplateInput,
        phase: TrainingPhase
    ): Long {
        val template = com.example.workoutapp.data.model.WorkoutPlanTemplate(
            name = input.name.trim().ifBlank { draft.session.name },
            description = input.description.trim(),
            notes = draft.session.notes,
            locationId = draft.session.locationId,
            targetDurationMinutes = draft.session.targetDurationMinutes,
            targetCategories = draft.session.targetCategories,
            scheduledTimeSlot = draft.session.scheduledTimeSlot,
            sourcePhase = phase
        )
        val templateExercises = draft.exercises.mapIndexed { index, config ->
            com.example.workoutapp.data.model.WorkoutPlanTemplateExercise(
                templateId = 0,
                exerciseId = config.exerciseId,
                orderIndex = index,
                section = config.section,
                plannedSets = config.sets,
                plannedReps = config.reps,
                plannedRestSeconds = config.restSeconds,
                prescriptionJson = config.prescriptionJson,
                coachingNotes = config.notes
            )
        }
        return sessionRepository.createPlanTemplate(template, templateExercises)
    }

    suspend fun playTemplate(templateId: Long): Long =
        sessionRepository.instantiateSessionFromTemplate(templateId)

    private suspend fun prepareDraft(params: WorkoutGenerationParams): WorkoutPlanDraft {
        val goal = userGoalRepository.getUserGoal()
        val resolvedLocationId = params.locationId ?: equipmentRepository.getDefaultLocation()?.id
        val availableEquipmentIds = resolvedLocationId
            ?.let { equipmentRepository.getEquipmentIdsForLocation(it).toSet() }
            ?: emptySet()

        val allExercises = exerciseRepository.getAllExercises().first()
        val candidates = allExercises.mapNotNull { exercise ->
            if (exercise.id in params.excludedExerciseIds) return@mapNotNull null
            val categories = exerciseRepository.getExerciseCategories(exercise.id)
                .filter { it != WorkoutCategory.CUSTOM }
            if (categories.isEmpty()) return@mapNotNull null

            val requiredEquipmentIds = exerciseRepository.getRequiredEquipmentIds(exercise.id).toSet()
            val matchesEquipment =
                resolvedLocationId == null || requiredEquipmentIds.all { it in availableEquipmentIds }

            if (!matchesEquipment) return@mapNotNull null
            if (params.selectedCategories.isNotEmpty() && categories.none { it in params.selectedCategories }) {
                return@mapNotNull null
            }

            WorkoutPlannerCandidate(
                exercise = exercise,
                categories = categories,
                requiredEquipmentIds = requiredEquipmentIds
            )
        }

        require(candidates.isNotEmpty()) {
            "No exercises match the selected location, equipment, and category filters yet."
        }

        val categoryPriority = WorkoutCategory.rotationCategories().associateWith {
            userGoalRepository.calculateCategoryPriority(it)
        }
        val categoryNeglectDays = WorkoutCategory.rotationCategories().associateWith {
            userGoalRepository.getCategoryStats(it)?.daysSinceLastTrained ?: Int.MAX_VALUE
        }

        // Load ML preference scores for personalization
        val (categoryScores, exerciseScores) = workoutRecommender.loadPreferenceScores()
        val totalSamples = mlFeedbackRepository.getTotalEventCount()
        val balanceScore = DashboardAnalytics.balanceScore(
            userGoalRepository.getAllCategoryStats().first(),
            userGoalRepository.getCategoryWeights()
        )

        return WorkoutPlannerEngine.buildDraft(
            params = params.copy(locationId = resolvedLocationId),
            goal = goal,
            candidates = candidates,
            categoryPriority = categoryPriority,
            categoryNeglectDays = categoryNeglectDays,
            mlCategoryScores = categoryScores,
            mlExerciseScores = exerciseScores,
            mlTotalSamples = totalSamples,
            balanceScore = balanceScore,
            workoutRecommender = workoutRecommender
        )
    }

    suspend fun completeWorkout(
        sessionId: Long,
        completionInput: WorkoutCompletionInput
    ) {
        val existingSession = sessionRepository.getSessionById(sessionId) ?: return
        val sessionExercises = sessionRepository.getExercisesForSessionSync(sessionId)
        val completedExercises = sessionExercises.filter { it.isCompleted && !it.isSkipped }

        val durationMinutes = existingSession.startedAt
            ?.let { startedAt ->
                max(
                    1,
                    TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startedAt).toInt()
                )
            }
            ?: existingSession.targetDurationMinutes

        sessionRepository.completeSession(sessionId, durationMinutes)

        val completedSession = sessionRepository.getSessionById(sessionId)?.copy(
            perceivedDifficulty = completionInput.perceivedDifficulty,
            energyLevel = completionInput.energyLevel,
            satisfactionRating = completionInput.satisfactionRating,
            postSessionNotes = completionInput.notes,
            updatedAt = System.currentTimeMillis()
        ) ?: return
        sessionRepository.updateSession(completedSession)

        completedExercises.forEach { sessionExercise ->
            exerciseRepository.markExercisePerformed(sessionExercise.exerciseId)
        }

        val categoryFrequency = linkedMapOf<WorkoutCategory, Int>()
        completedExercises.forEach { sessionExercise ->
            exerciseRepository.getExerciseCategories(sessionExercise.exerciseId)
                .filter { it != WorkoutCategory.CUSTOM }
                .forEach { category ->
                    categoryFrequency[category] = (categoryFrequency[category] ?: 0) + 1
                }
        }

        val totalAssignments = categoryFrequency.values.sum().coerceAtLeast(1)
        categoryFrequency.forEach { (category, count) ->
            userGoalRepository.recordCategoryTraining(category)
            val minutes = (durationMinutes.toFloat() * count / totalAssignments).roundToInt().coerceAtLeast(1)
            userGoalRepository.addCategoryTrainingMinutes(category, minutes)
        }

        userGoalRepository.recalculateDaysSinceLastTrained()

        // Record ML feedback for learning
        val completedIds = completedExercises.map { it.exerciseId }.toSet()
        val skippedIds = sessionExercises.filter { it.isSkipped }.map { it.exerciseId }.toSet()
        mlFeedbackRepository.recordWorkoutCompletion(
            sessionId = sessionId,
            completedExerciseIds = completedIds,
            skippedExerciseIds = skippedIds,
            perceivedDifficulty = completionInput.perceivedDifficulty,
            satisfactionRating = completionInput.satisfactionRating
        )

        // Update cached preference scores periodically
        val allExerciseIds = sessionExercises.map { it.exerciseId }
        mlFeedbackRepository.cachePreferenceScores(allExerciseIds)
    }
}

private fun Difficulty.toMlDifficultyLevel(): Int = level.coerceIn(1, 5)

object WorkoutPlannerEngine {
    fun buildDraft(
        params: WorkoutGenerationParams,
        goal: UserGoal,
        candidates: List<WorkoutPlannerCandidate>,
        categoryPriority: Map<WorkoutCategory, Float>,
        categoryNeglectDays: Map<WorkoutCategory, Int>,
        now: Long = System.currentTimeMillis(),
        // ML parameters (optional, for backward compatibility)
        mlCategoryScores: Map<WorkoutCategory, MLPreferenceScore> = emptyMap(),
        mlExerciseScores: Map<Long, MLPreferenceScore> = emptyMap(),
        mlTotalSamples: Int = 0,
        balanceScore: Int = 50,
        workoutRecommender: WorkoutRecommender? = null
    ): WorkoutPlanDraft {
        require(candidates.isNotEmpty()) { "Cannot build a workout without candidates." }

        val eligibleCandidates = if (params.selectedCategories.isEmpty()) {
            candidates
        } else {
            candidates.filter { candidate ->
                candidate.categories.any { it in params.selectedCategories }
            }
        }.filterNot { it.exercise.id in params.excludedExerciseIds }
        require(eligibleCandidates.isNotEmpty()) { "Cannot build a workout without candidates." }

        val focusCategories = determineFocusCategories(
            params = params,
            goal = goal,
            candidates = eligibleCandidates,
            categoryPriority = categoryPriority,
            categoryNeglectDays = categoryNeglectDays,
            durationMinutes = params.durationMinutes
        )
        val focusSet = focusCategories.toSet()
        val targetExerciseCount = estimateTargetExerciseCount(params.durationMinutes)
        val minExerciseCount = minOf(3, targetExerciseCount)
        val maxPerCategory = max(2, ceil(targetExerciseCount / focusCategories.size.toFloat()).toInt())
        val targetSeconds = params.durationMinutes * 60

        val baseScores = eligibleCandidates.associateWith { candidate ->
            scoreCandidate(
                candidate = candidate,
                focusCategories = focusSet,
                categoryPriority = categoryPriority,
                categoryNeglectDays = categoryNeglectDays,
                goal = goal,
                now = now,
                targetDurationMinutes = params.durationMinutes
            )
        }

        // Pre-compute ML adjustments for all candidates
        val mlAdjustments = if (workoutRecommender != null && mlTotalSamples > 10) {
            eligibleCandidates.associateWith { candidate ->
                workoutRecommender.calculateMLAdjustment(
                    candidate = candidate,
                    categoryScores = mlCategoryScores,
                    exerciseScores = mlExerciseScores,
                    totalSamples = mlTotalSamples,
                    balanceScore = balanceScore
                )
            }
        } else {
            emptyMap()
        }

        val picked = mutableListOf<WorkoutPlannerCandidate>()
        val pickedConfigs = mutableListOf<SessionExerciseConfig>()
        val categoryUsage = mutableMapOf<WorkoutCategory, Int>()
        var estimatedSeconds = 0
        val remaining = eligibleCandidates.toMutableList()

        while (remaining.isNotEmpty() && picked.size < targetExerciseCount) {
            val nextCandidate = remaining.maxByOrNull { candidate ->
                val base = baseScores.getValue(candidate)
                val mlBoost = mlAdjustments[candidate] ?: 0f
                val dynamic = dynamicSelectionBonus(
                    candidate = candidate,
                    alreadyPicked = picked,
                    focusCategories = focusSet,
                    categoryUsage = categoryUsage,
                    maxPerCategory = maxPerCategory
                )
                // ML adjustment is weighted at 20% of total score
                base + dynamic + (mlBoost * 0.2f * base.coerceAtLeast(1f))
            } ?: break

            val config = toSessionExerciseConfig(nextCandidate, goal)
            val blockSeconds = estimateBlockSeconds(nextCandidate.exercise, config)
            val wouldExceedBudget = estimatedSeconds + blockSeconds > targetSeconds

            if (wouldExceedBudget && picked.size >= minExerciseCount) {
                remaining.remove(nextCandidate)
                continue
            }

            picked += nextCandidate
            pickedConfigs += config
            estimatedSeconds += blockSeconds
            nextCandidate.categories.forEach { category ->
                categoryUsage[category] = (categoryUsage[category] ?: 0) + 1
            }
            remaining.remove(nextCandidate)
        }

        if (picked.isEmpty()) {
            val fallback = eligibleCandidates.maxBy { baseScores.getValue(it) }
            picked += fallback
            pickedConfigs += toSessionExerciseConfig(fallback, goal)
            estimatedSeconds = estimateBlockSeconds(fallback.exercise, pickedConfigs.first())
        }

        val estimatedMinutes = max(1, (estimatedSeconds / 60f).roundToInt())
        val exerciseSummaries = picked.zip(pickedConfigs).map { (candidate, config) ->
            PlannedExerciseSummary(
                exerciseId = candidate.exercise.id,
                name = candidate.exercise.name,
                categories = candidate.categories,
                sets = config.sets,
                reps = config.reps,
                restSeconds = config.restSeconds,
                estimatedMinutes = max(1, (estimateBlockSeconds(candidate.exercise, config) / 60f).roundToInt()),
                recommendationReason = buildExerciseReason(
                    candidate = candidate,
                    focusCategories = focusSet,
                    categoryNeglectDays = categoryNeglectDays,
                    phase = goal.currentPhase,
                    locationMatched = params.locationId != null
                )
            )
        }
        val reasoning = buildReasoning(
            params = params,
            goal = goal,
            focusCategories = focusCategories,
            categoryNeglectDays = categoryNeglectDays,
            picked = picked,
            estimatedMinutes = estimatedMinutes
        )

        val sessionName = params.sessionName?.takeIf { it.isNotBlank() }
            ?: buildSessionName(focusCategories, goal.currentPhase)

        val session = WorkoutSession(
            name = sessionName,
            notes = reasoning.joinToString(separator = "\n• ", prefix = "• "),
            locationId = params.locationId,
            targetDurationMinutes = params.durationMinutes,
            targetCategories = Json.encodeToString(focusCategories.map { it.name }),
            plannedDate = params.plannedDate,
            scheduledTimeSlot = params.timeSlot
        )

        return WorkoutPlanDraft(
            session = session,
            exercises = pickedConfigs,
            focusCategories = focusCategories,
            estimatedDurationMinutes = estimatedMinutes,
            reasoning = reasoning,
            exerciseSummaries = exerciseSummaries
        )
    }

    private fun determineFocusCategories(
        params: WorkoutGenerationParams,
        goal: UserGoal,
        candidates: List<WorkoutPlannerCandidate>,
        categoryPriority: Map<WorkoutCategory, Float>,
        categoryNeglectDays: Map<WorkoutCategory, Int>,
        durationMinutes: Int
    ): List<WorkoutCategory> {
        if (params.selectedCategories.isNotEmpty()) {
            return params.selectedCategories
        }

        val availableCategories = candidates.flatMap { it.categories }.distinct()
        val focusCount = when {
            durationMinutes <= 25 -> 1
            durationMinutes <= 50 -> 2
            else -> 3
        }
        val severelyNeglected = availableCategories
            .filter { (categoryNeglectDays[it] ?: 0) >= goal.maxDaysWithoutCategory }
            .sortedByDescending { categoryNeglectDays[it] ?: 0 }

        val orderedByPriority = availableCategories.sortedByDescending { category ->
            val priority = categoryPriority[category] ?: category.defaultWeight
            val neglectBoost = ((categoryNeglectDays[category] ?: 0).toFloat() / goal.maxDaysWithoutCategory.coerceAtLeast(1))
                .coerceAtMost(2f)
            priority + neglectBoost
        }

        return buildList {
            if (goal.ensureWeeklyVariety) {
                severelyNeglected.forEach { category ->
                    if (size < focusCount && category !in this) add(category)
                }
            }
            orderedByPriority.forEach { category ->
                if (size < focusCount && category !in this) add(category)
            }
        }.ifEmpty { listOf(WorkoutCategory.STRENGTH) }
    }

    private fun scoreCandidate(
        candidate: WorkoutPlannerCandidate,
        focusCategories: Set<WorkoutCategory>,
        categoryPriority: Map<WorkoutCategory, Float>,
        categoryNeglectDays: Map<WorkoutCategory, Int>,
        goal: UserGoal,
        now: Long,
        targetDurationMinutes: Int
    ): Float {
        val focusMatches = candidate.categories.filter { it in focusCategories }
        val relevantCategories = focusMatches.ifEmpty { candidate.categories }
        val priorityScore = relevantCategories.maxOfOrNull {
            categoryPriority[it] ?: it.defaultWeight
        } ?: 0f
        val neglectBoost = relevantCategories.maxOfOrNull {
            (categoryNeglectDays[it] ?: 0).toFloat() / goal.maxDaysWithoutCategory.coerceAtLeast(1)
        }?.coerceIn(0f, 2.5f) ?: 0f
        val fairnessBoost = relevantCategories.count {
            (categoryNeglectDays[it] ?: 0) >= goal.maxDaysWithoutCategory
        } * 0.9f
        val recoveryPenalty = relevantCategories.maxOfOrNull { category ->
            val daysSinceCategory = categoryNeglectDays[category] ?: goal.maxDaysWithoutCategory
            if (daysSinceCategory >= goal.minDaysBetweenSameCategory) 0f
            else (goal.minDaysBetweenSameCategory - daysSinceCategory + 1) * 1.35f
        } ?: 0f
        val daysSinceLastPerformed = candidate.exercise.lastPerformedAt
            ?.let { ((now - it).coerceAtLeast(0L) / MILLIS_PER_DAY).toFloat() }
            ?: goal.maxDaysWithoutCategory.toFloat()
        val recencyBonus = (daysSinceLastPerformed / goal.maxDaysWithoutCategory.coerceAtLeast(1)).coerceIn(0f, 1.5f)
        val noveltyBonus = if (candidate.exercise.timesPerformed == 0) 1.1f else 0f
        val categoryMatchBonus = if (focusMatches.isNotEmpty()) 1.2f else 0.1f
        val favoriteBonus = if (candidate.exercise.isFavorite) 0.35f else 0f
        val compoundBonus = if (candidate.exercise.isCompound && targetDurationMinutes <= 45) 0.35f else 0f
        val shortSessionPenalty = if (!candidate.exercise.isCompound && targetDurationMinutes <= 20) 0.25f else 0f
        val repetitionPenalty = (candidate.exercise.timesPerformed * 0.08f).coerceAtMost(1.2f)

        return (priorityScore * 2.6f) + neglectBoost + fairnessBoost + recencyBonus + noveltyBonus +
            categoryMatchBonus + favoriteBonus + compoundBonus - repetitionPenalty - recoveryPenalty - shortSessionPenalty
    }

    private fun dynamicSelectionBonus(
        candidate: WorkoutPlannerCandidate,
        alreadyPicked: List<WorkoutPlannerCandidate>,
        focusCategories: Set<WorkoutCategory>,
        categoryUsage: Map<WorkoutCategory, Int>,
        maxPerCategory: Int
    ): Float {
        var bonus = 0f
        val focusMatches = candidate.categories.filter { it in focusCategories }

        if (alreadyPicked.isEmpty() && candidate.exercise.isCompound) {
            bonus += 0.5f
        }

        if (focusMatches.isNotEmpty()) {
            val leastUsed = focusMatches.minOf { categoryUsage[it] ?: 0 }
            val uncoveredFocus = focusMatches.count { (categoryUsage[it] ?: 0) == 0 }
            bonus += (maxPerCategory - leastUsed).coerceAtLeast(0) * 0.35f
            bonus += uncoveredFocus * 0.6f
            val mostUsed = focusMatches.maxOf { categoryUsage[it] ?: 0 }
            if (mostUsed >= maxPerCategory) bonus -= 0.9f
        }

        val lastPickedCategories = alreadyPicked.lastOrNull()?.categories.orEmpty().toSet()
        if (candidate.categories.any { it in lastPickedCategories }) {
            bonus -= 0.3f
        }

        if (!candidate.exercise.isCompound && alreadyPicked.lastOrNull()?.exercise?.isCompound == true) {
            bonus += 0.15f
        }

        return bonus
    }

    private fun toSessionExerciseConfig(
        candidate: WorkoutPlannerCandidate,
        goal: UserGoal
    ): SessionExerciseConfig {
        val preset = candidate.exercise.resolveStoredProgrammingPreset(goal.currentPhase)
        val baseSets = resolveSuggestedSetCount(preset?.setsText ?: candidate.exercise.defaultSets.toString(), candidate.exercise.defaultSets)
        val baseReps = preset?.repsText?.ifBlank { candidate.exercise.defaultReps } ?: candidate.exercise.defaultReps
        val baseRest = preset?.restSeconds ?: candidate.exercise.defaultRestSeconds

        val progressionReady = goal.autoProgressionEnabled &&
            candidate.exercise.timesPerformed >= goal.progressionThreshold
        val progressionCategories = setOf(
            WorkoutCategory.STRENGTH,
            WorkoutCategory.HYPERTROPHY,
            WorkoutCategory.FUNCTIONAL,
            WorkoutCategory.SKILLS
        )
        val progressedSets = if (progressionReady && candidate.categories.any { it in progressionCategories }) {
            baseSets + 1
        } else {
            baseSets
        }
        val restDelta = when {
            !progressionReady -> 0
            candidate.categories.any { it == WorkoutCategory.ENDURANCE || it == WorkoutCategory.DEXTERITY } -> -10
            candidate.categories.any { it == WorkoutCategory.STRENGTH } -> 15
            else -> 0
        }

        return SessionExerciseConfig(
            exerciseId = candidate.exercise.id,
            section = PlanExerciseSection.MAIN,
            sets = progressedSets,
            reps = baseReps,
            restSeconds = (baseRest + restDelta).coerceAtLeast(0),
            prescriptionJson = preset?.toRichPrescriptionData()?.toJson().orEmpty()
        )
    }

    private fun estimateBlockSeconds(
        exercise: Exercise,
        config: SessionExerciseConfig
    ): Int {
        val movementSeconds = exercise.estimatedDurationSeconds.coerceAtLeast(60)
        val restSeconds = config.restSeconds * (config.sets - 1).coerceAtLeast(0)
        return movementSeconds + restSeconds
    }

    private fun estimateTargetExerciseCount(durationMinutes: Int): Int = when {
        durationMinutes <= 20 -> 3
        durationMinutes <= 35 -> 4
        durationMinutes <= 50 -> 5
        durationMinutes <= 70 -> 6
        else -> 7
    }

    private fun buildSessionName(
        focusCategories: List<WorkoutCategory>,
        trainingPhase: TrainingPhase
    ): String {
        return when (focusCategories.size) {
            0 -> "${trainingPhase.displayName} Workout"
            1 -> "${focusCategories.first().displayName} Session"
            2 -> focusCategories.joinToString(separator = " + ") { it.displayName } + " Workout"
            else -> "${trainingPhase.displayName} Smart Workout"
        }
    }

    private fun buildReasoning(
        params: WorkoutGenerationParams,
        goal: UserGoal,
        focusCategories: List<WorkoutCategory>,
        categoryNeglectDays: Map<WorkoutCategory, Int>,
        picked: List<WorkoutPlannerCandidate>,
        estimatedMinutes: Int
    ): List<String> {
        val neglectedFocus = focusCategories.filter {
            (categoryNeglectDays[it] ?: 0) >= goal.maxDaysWithoutCategory
        }
        val protectedBySpacing = focusCategories.filter {
            (categoryNeglectDays[it] ?: goal.maxDaysWithoutCategory) < goal.minDaysBetweenSameCategory
        }
        val progressionCount = picked.count {
            goal.autoProgressionEnabled && it.exercise.timesPerformed >= goal.progressionThreshold
        }

        return buildList {
            add("Focused on ${focusCategories.joinToString { it.displayName.lowercase() }} using your ${goal.currentPhase.displayName.lowercase()} phase weighting.")
            if (params.locationId != null) {
                add("Matched every exercise to the equipment available at your selected location.")
            }
            if (neglectedFocus.isNotEmpty()) {
                add("Protected rotation by pulling in ${neglectedFocus.joinToString { it.displayName.lowercase() }} because those categories were undertrained.")
            }
            if (protectedBySpacing.isNotEmpty()) {
                add("Down-weighted ${protectedBySpacing.joinToString { it.displayName.lowercase() }} because they were trained too recently for your spacing rules.")
            }
            if (progressionCount > 0) {
                add("Included $progressionCount progression-ready movement${if (progressionCount == 1) "" else "s"} from exercises you already know well.")
            }
            add("Planned ${picked.size} exercises for about $estimatedMinutes minutes while trying to avoid same-category clustering.")
        }
    }

    private fun buildExerciseReason(
        candidate: WorkoutPlannerCandidate,
        focusCategories: Set<WorkoutCategory>,
        categoryNeglectDays: Map<WorkoutCategory, Int>,
        phase: TrainingPhase,
        locationMatched: Boolean
    ): String {
        val reasons = mutableListOf<String>()
        val focusMatches = candidate.categories.filter { it in focusCategories }
        if (focusMatches.isNotEmpty()) {
            reasons += "fits your ${focusMatches.joinToString { it.displayName.lowercase() }} focus"
        }
        val neglected = candidate.categories.firstOrNull { (categoryNeglectDays[it] ?: 0) >= 7 }
        if (neglected != null) {
            reasons += "helps rotate ${neglected.displayName.lowercase()} back in"
        }
        if (candidate.exercise.timesPerformed == 0) {
            reasons += "adds variety from your library"
        } else if (candidate.exercise.timesPerformed >= 3) {
            reasons += "has enough history for ${phase.displayName.lowercase()} progression"
        }
        if (locationMatched && candidate.requiredEquipmentIds.isNotEmpty()) {
            reasons += "matches equipment at your current location"
        }
        return reasons.joinToString(separator = " • ").ifBlank {
            "Fits your current phase, duration, and equipment constraints."
        }
    }
}


private fun resolveSuggestedSetCount(setsText: String, fallback: Int): Int {
    val numbers = Regex("\\d+").findAll(setsText).mapNotNull { it.value.toIntOrNull() }.toList()
    return when {
        numbers.isEmpty() -> fallback
        numbers.size == 1 -> numbers.first().coerceIn(1, 20)
        else -> ((numbers.first() + numbers.last()) / 2).coerceIn(1, 20)
    }
}
