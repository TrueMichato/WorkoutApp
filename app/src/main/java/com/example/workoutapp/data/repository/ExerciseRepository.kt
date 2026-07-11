package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.ExerciseDao
import com.example.workoutapp.data.local.dao.ExerciseFamilyMutation
import com.example.workoutapp.data.local.dao.ExerciseSaveOutcome
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    // Exercise CRUD
    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insert(exercise)

    suspend fun updateExercise(exercise: Exercise) {
        check(exerciseDao.update(exercise) == 1) { "Exercise ${exercise.id} no longer exists." }
    }

    suspend fun hardDeleteExerciseIfUnreferenced(exerciseId: Long) {
        exerciseDao.hardDeleteIfUnreferenced(exerciseId)
    }

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getById(id)

    fun getExerciseByIdFlow(id: Long): Flow<Exercise?> = exerciseDao.getByIdFlow(id)

    fun getAllExercises(): Flow<List<Exercise>> = exerciseDao.getAllActive()

    fun getAllExercisesIncludingArchived(): Flow<List<Exercise>> = exerciseDao.getAll()

    suspend fun getAllExercisesIncludingArchivedSync(): List<Exercise> = exerciseDao.getAllSync()

    fun getFavoriteExercises(): Flow<List<Exercise>> = exerciseDao.getFavorites()

    fun getRecentlyPerformed(limit: Int = 20): Flow<List<Exercise>> = exerciseDao.getRecentlyPerformed(limit)

    fun getMostPerformed(limit: Int = 20): Flow<List<Exercise>> = exerciseDao.getMostPerformed(limit)

    fun getNeglectedExercises(daysSince: Int = 14): Flow<List<Exercise>> {
        val timestamp = System.currentTimeMillis() - (daysSince.toLong() * 24 * 60 * 60 * 1000)
        return exerciseDao.getNeglected(timestamp)
    }

    fun searchExercises(query: String): Flow<List<Exercise>> = exerciseDao.search(query)

    fun getExercisesByCategory(category: WorkoutCategory): Flow<List<Exercise>> =
        exerciseDao.getExercisesByCategory(category)

    fun getExercisesByEquipment(equipmentId: Long): Flow<List<Exercise>> =
        exerciseDao.getExercisesByEquipment(equipmentId)

    fun getExercisesByMuscle(muscleGroup: MuscleGroup): Flow<List<Exercise>> =
        exerciseDao.getExercisesByMuscle(muscleGroup)

    fun getExercisesByDifficulty(difficulty: Difficulty): Flow<List<Exercise>> =
        exerciseDao.getByDifficulty(difficulty)

    // Tracking
    suspend fun markExercisePerformed(exerciseId: Long) {
        check(exerciseDao.markPerformed(exerciseId) == 1) { "Exercise $exerciseId no longer exists." }
    }

    suspend fun setFavorite(exerciseId: Long, isFavorite: Boolean) =
        exerciseDao.setFavorite(exerciseId, isFavorite)

    suspend fun archiveExercise(exerciseId: Long) {
        check(exerciseDao.setArchived(exerciseId, true) == 1) { "Exercise $exerciseId no longer exists." }
    }

    suspend fun unarchiveExercise(exerciseId: Long) {
        check(exerciseDao.setArchived(exerciseId, false) == 1) { "Exercise $exerciseId no longer exists." }
    }

    // Categories
    suspend fun setExerciseCategories(exerciseId: Long, categories: List<WorkoutCategory>) {
        exerciseDao.clearCategoriesForExercise(exerciseId)
        val refs = categories.map { ExerciseCategoryCrossRef(exerciseId, it) }
        exerciseDao.insertCategoryRefs(refs)
    }

    suspend fun getExerciseCategories(exerciseId: Long): List<WorkoutCategory> =
        exerciseDao.getCategoriesForExercise(exerciseId)

    // Equipment
    suspend fun setExerciseEquipment(exerciseId: Long, equipmentIds: List<Long>, allRequired: Boolean = true) {
        exerciseDao.clearEquipmentForExercise(exerciseId)
        val refs = equipmentIds.map { ExerciseEquipmentCrossRef(exerciseId, it, isRequired = allRequired) }
        exerciseDao.insertEquipmentRefs(refs)
    }

    suspend fun getRequiredEquipmentIds(exerciseId: Long): List<Long> =
        exerciseDao.getRequiredEquipmentIds(exerciseId)

    // Muscle groups
    suspend fun setExerciseMuscles(exerciseId: Long, primary: List<MuscleGroup>, secondary: List<MuscleGroup> = emptyList()) {
        exerciseDao.clearMusclesForExercise(exerciseId)
        val primaryRefs = primary.map { ExerciseMuscleCrossRef(exerciseId, it, isPrimary = true) }
        val secondaryRefs = secondary.map { ExerciseMuscleCrossRef(exerciseId, it, isPrimary = false) }
        exerciseDao.insertMuscleRefs(primaryRefs + secondaryRefs)
    }

    suspend fun getPrimaryMuscles(exerciseId: Long): List<MuscleGroup> =
        exerciseDao.getPrimaryMuscles(exerciseId)

    suspend fun getSecondaryMuscles(exerciseId: Long): List<MuscleGroup> =
        exerciseDao.getSecondaryMuscles(exerciseId)

    // Custom categories
    fun getAllCustomCategories(): Flow<List<CustomCategory>> = exerciseDao.getAllCustomCategories()

    suspend fun createCustomCategory(name: String, description: String = ""): Long =
        exerciseDao.insertCustomCategory(CustomCategory(name = name, description = description))

    suspend fun deleteCustomCategory(category: CustomCategory) = exerciseDao.deleteCustomCategory(category)

    // Stats
    fun getActiveExerciseCount(): Flow<Int> = exerciseDao.getActiveCount()

    suspend fun getFavoriteExerciseCount(): Int = exerciseDao.getFavoriteCount()

    /**
     * Create a complete exercise with all relationships
     */
    suspend fun createExerciseWithRelations(
        exercise: Exercise,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup> = emptyList()
    ): Long {
        return exerciseDao.insertWithRelations(
            exercise = exercise,
            categories = categories,
            equipmentIds = equipmentIds,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles
        )
    }

    suspend fun updateExerciseWithRelations(
        exercise: Exercise,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup> = emptyList()
    ) {
        exerciseDao.updateWithRelations(
            exercise = exercise,
            categories = categories,
            equipmentIds = equipmentIds,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles
        )
    }

    // Exercise family / variations
    //
    // Variations are full, independent Exercise rows; `exercise_variations` only records the
    // family relationship (see ExerciseVariationCrossRef). Invariant checks (no self-link, no
    // duplicate parentage, no multi-level nesting) and the mutation itself are centralized in
    // ExerciseDao's @Transaction helpers so validation and mutation are atomic - no window where
    // one caller's check passes and a concurrent caller's insert races it, and no window where an
    // exercise/relations save partially commits while a rejected family change leaves it
    // inconsistent.

    /**
     * Atomically creates or updates [exercise] together with its relations AND its exercise
     * family mutation in one DB transaction (see [ExerciseDao.insertWithRelationsAndFamily] /
     * [ExerciseDao.updateWithRelationsAndFamily]). [existingExerciseId] is null to create a new
     * exercise, or the id of the exercise being edited. Any validation failure (self-link,
     * missing/invalid parent, invalid nesting) or unexpected persistence exception leaves the
     * database completely unchanged - the exercise row, its relations, and its family link (or
     * lack thereof) all stay exactly as they were before the call, so callers can safely treat
     * "not Success" as "nothing was persisted" when deciding whether to clean up newly-copied
     * media. [CancellationException] is always rethrown so cancellation semantics are preserved.
     */
    suspend fun saveExerciseWithFamily(
        existingExerciseId: Long?,
        exercise: Exercise,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup> = emptyList(),
        familyMutation: ExerciseFamilyMutation = ExerciseFamilyMutation.NoChange
    ): ExerciseSaveResult {
        return try {
            val outcome = if (existingExerciseId == null) {
                exerciseDao.insertWithRelationsAndFamily(
                    exercise = exercise,
                    categories = categories,
                    equipmentIds = equipmentIds,
                    primaryMuscles = primaryMuscles,
                    secondaryMuscles = secondaryMuscles,
                    familyMutation = familyMutation
                )
            } else {
                exerciseDao.updateWithRelationsAndFamily(
                    exercise = exercise.copy(id = existingExerciseId),
                    categories = categories,
                    equipmentIds = equipmentIds,
                    primaryMuscles = primaryMuscles,
                    secondaryMuscles = secondaryMuscles,
                    familyMutation = familyMutation
                )
            }
            outcome.toSaveResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExerciseSaveResult.Failure(ExerciseSaveError.PersistFailed)
        }
    }

    private fun ExerciseSaveOutcome.toSaveResult(): ExerciseSaveResult = when (this) {
        is ExerciseSaveOutcome.Success -> ExerciseSaveResult.Success(exerciseId)
        ExerciseSaveOutcome.ExerciseNotFound -> ExerciseSaveResult.Failure(ExerciseSaveError.ExerciseNotFound)
        ExerciseSaveOutcome.SelfLink -> ExerciseSaveResult.Failure(ExerciseSaveError.SelfLink)
        is ExerciseSaveOutcome.ParentNotFound -> ExerciseSaveResult.Failure(ExerciseSaveError.ParentNotFound)
        is ExerciseSaveOutcome.ParentIsAlreadyVariation ->
            ExerciseSaveResult.Failure(ExerciseSaveError.ParentIsAlreadyVariation(parentName))
        is ExerciseSaveOutcome.ExerciseHasOwnVariations ->
            ExerciseSaveResult.Failure(ExerciseSaveError.ExerciseHasOwnVariations(exerciseName))
        ExerciseSaveOutcome.AlreadyLinkedElsewhere ->
            ExerciseSaveResult.Failure(ExerciseSaveError.PersistFailed)
    }

    /**
     * Atomically validates and links [variationExerciseId] as a variation of
     * [parentExerciseId] (see [ExerciseDao.linkVariation]), for standalone linking outside of an
     * exercise-editor save (e.g. CSV import). Requires the variation be explicitly detached
     * first if it is already linked elsewhere. [CancellationException] is always rethrown.
     */
    suspend fun linkVariationResult(parentExerciseId: Long, variationExerciseId: Long, focus: String): ExerciseFamilyLinkResult {
        return try {
            when (val outcome = exerciseDao.linkVariation(parentExerciseId, variationExerciseId, focus.trim())) {
                is ExerciseSaveOutcome.Success -> ExerciseFamilyLinkResult.Success
                ExerciseSaveOutcome.ExerciseNotFound ->
                    ExerciseFamilyLinkResult.Failure(ExerciseFamilyLinkError.VariationNotFound)
                ExerciseSaveOutcome.SelfLink ->
                    ExerciseFamilyLinkResult.Failure(ExerciseFamilyLinkError.SelfLink)
                is ExerciseSaveOutcome.ParentNotFound ->
                    ExerciseFamilyLinkResult.Failure(ExerciseFamilyLinkError.ParentNotFound)
                is ExerciseSaveOutcome.ParentIsAlreadyVariation ->
                    ExerciseFamilyLinkResult.Failure(ExerciseFamilyLinkError.ParentIsAlreadyVariation(outcome.parentName))
                is ExerciseSaveOutcome.ExerciseHasOwnVariations ->
                    ExerciseFamilyLinkResult.Failure(ExerciseFamilyLinkError.ExerciseHasOwnVariations(outcome.exerciseName))
                ExerciseSaveOutcome.AlreadyLinkedElsewhere ->
                    ExerciseFamilyLinkResult.Failure(ExerciseFamilyLinkError.AlreadyLinkedElsewhere)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExerciseFamilyLinkResult.Failure(ExerciseFamilyLinkError.PersistFailed)
        }
    }

    /**
     * Throwing convenience wrapper over [linkVariationResult] for existing callers/tests written
     * against the original exception-based contract.
     */
    suspend fun linkVariation(parentExerciseId: Long, variationExerciseId: Long, focus: String) {
        when (val result = linkVariationResult(parentExerciseId, variationExerciseId, focus)) {
            is ExerciseFamilyLinkResult.Success -> Unit
            is ExerciseFamilyLinkResult.Failure -> when (result.error) {
                is ExerciseFamilyLinkError.VariationNotFound, is ExerciseFamilyLinkError.ParentNotFound ->
                    throw IllegalArgumentException(result.error.message)
                is ExerciseFamilyLinkError.SelfLink ->
                    throw IllegalArgumentException(result.error.message)
                else -> throw IllegalStateException(result.error.message)
            }
        }
    }

    suspend fun unlinkVariation(variationExerciseId: Long) {
        exerciseDao.deleteVariationLink(variationExerciseId)
    }

    suspend fun updateVariationFocus(variationExerciseId: Long, focus: String) {
        check(exerciseDao.updateVariationFocus(variationExerciseId, focus.trim()) == 1) {
            "This exercise is no longer linked as a variation."
        }
    }

    suspend fun isMainExercise(exerciseId: Long): Boolean = exerciseDao.countVariationsForParent(exerciseId) > 0

    suspend fun isVariationExercise(exerciseId: Long): Boolean = exerciseDao.getVariationLink(exerciseId) != null

    suspend fun getParentExerciseId(variationExerciseId: Long): Long? =
        exerciseDao.getVariationLink(variationExerciseId)?.parentExerciseId

    /**
     * Resolves any exercise to the id of its family's main/root exercise - itself, if it is
     * standalone or already the main exercise.
     */
    suspend fun resolveFamilyRootId(exerciseId: Long): Long =
        exerciseDao.getVariationLink(exerciseId)?.parentExerciseId ?: exerciseId

    /**
     * Builds a `variationExerciseId -> parentExerciseId` map for every linked variation in one
     * query, so the workout generator can resolve each candidate's family root cheaply without
     * a lookup per exercise. Exercises absent from this map are standalone (their family root is
     * themselves).
     */
    suspend fun getFamilyRootIdsForAll(): Map<Long, Long> =
        exerciseDao.getAllVariationLinks().associate { it.variationExerciseId to it.parentExerciseId }

    /**
     * Builds a `exerciseId -> name` map for every exercise, including archived ones, in one
     * query - used to resolve a variation's parent name for display even when the parent itself
     * has been filtered out of the currently visible result set (e.g. archived, or excluded by a
     * search/category filter).
     */
    suspend fun getAllExerciseNamesById(): Map<Long, String> =
        exerciseDao.getAllSync().associate { it.id to it.name }

    /**
     * Returns the full family (main exercise + all variations) that [exerciseId] belongs to, or
     * null if [exerciseId] is a standalone exercise with no family relationship at all.
     */
    suspend fun getFamily(exerciseId: Long): ExerciseFamily? {
        val rootId = resolveFamilyRootId(exerciseId)
        val root = exerciseDao.getById(rootId) ?: return null
        val links = exerciseDao.getVariationLinksForParent(rootId)
        if (links.isEmpty()) return null
        val variations = links.mapNotNull { link ->
            exerciseDao.getById(link.variationExerciseId)?.let { ExerciseVariationMember(it, link.focus) }
        }
        return ExerciseFamily(root = root, variations = variations)
    }

    fun getFamilyFlow(exerciseId: Long): Flow<List<ExerciseVariationCrossRef>> =
        exerciseDao.getVariationLinksForParentFlow(exerciseId)
}
