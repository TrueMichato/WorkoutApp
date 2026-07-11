package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.ExerciseDao
import com.example.workoutapp.data.model.*
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
    // family relationship (see ExerciseVariationCrossRef). These invariants are enforced here
    // rather than purely in SQL so callers get a clear, user-displayable error message:
    //   - no self-link
    //   - no duplicate parentage (a variation can't be re-linked without detaching first)
    //   - no multi-level nesting (a main exercise can't also be a variation of something else,
    //     and a variation can't also have its own variations)
    suspend fun linkVariation(parentExerciseId: Long, variationExerciseId: Long, focus: String) {
        require(parentExerciseId != variationExerciseId) {
            "An exercise cannot be a variation of itself."
        }
        val parent = exerciseDao.getById(parentExerciseId)
            ?: throw IllegalArgumentException("The selected main exercise no longer exists.")
        val variation = exerciseDao.getById(variationExerciseId)
            ?: throw IllegalArgumentException("The selected variation exercise no longer exists.")

        check(exerciseDao.countLinksAsVariation(parentExerciseId) == 0) {
            "\"${parent.name}\" is already a variation of another exercise, so it can't also be a main exercise."
        }
        check(exerciseDao.countVariationsForParent(variationExerciseId) == 0) {
            "\"${variation.name}\" already has its own variations, so it can't also be a variation."
        }
        check(exerciseDao.getVariationLink(variationExerciseId) == null) {
            "\"${variation.name}\" is already a variation of another exercise. Detach it first."
        }

        exerciseDao.insertVariationLink(
            ExerciseVariationCrossRef(
                variationExerciseId = variationExerciseId,
                parentExerciseId = parentExerciseId,
                focus = focus.trim()
            )
        )
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

/**
 * A main exercise together with all of its linked variations. [variations] always includes
 * [root] itself when [root] happens to be a variation exercise being viewed from its own
 * perspective (see [ExerciseRepository.getFamily]), so callers should filter it out by id when
 * rendering "other variations" lists.
 */
data class ExerciseFamily(
    val root: Exercise,
    val variations: List<ExerciseVariationMember>
)

data class ExerciseVariationMember(
    val exercise: Exercise,
    val focus: String
)
