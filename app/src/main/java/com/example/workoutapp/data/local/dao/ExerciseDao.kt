package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Describes what should happen to an exercise's family relationship as part of an atomic
 * exercise save (see [ExerciseDao.insertWithRelationsAndFamily] /
 * [ExerciseDao.updateWithRelationsAndFamily]). [LinkTo] implicitly reparents - it detaches any
 * existing link for this exercise before linking the new one, since the editor UI always
 * presents this as a single "change main exercise" action, not two separate steps.
 */
sealed class ExerciseFamilyMutation {
    data object NoChange : ExerciseFamilyMutation()
    data object Detach : ExerciseFamilyMutation()
    data class LinkTo(val parentExerciseId: Long, val focus: String) : ExerciseFamilyMutation()
}

/**
 * Typed outcome of an atomic exercise+relations(+family) save, so a validation failure never
 * leaves a partially-written exercise row, relation set, or family link - see
 * [ExerciseDao.insertWithRelationsAndFamily] / [ExerciseDao.updateWithRelationsAndFamily].
 */
sealed class ExerciseSaveOutcome {
    data class Success(val exerciseId: Long) : ExerciseSaveOutcome()
    data object ExerciseNotFound : ExerciseSaveOutcome()
    data object SelfLink : ExerciseSaveOutcome()
    data class ParentNotFound(val parentExerciseId: Long) : ExerciseSaveOutcome()
    data class ParentIsAlreadyVariation(val parentName: String) : ExerciseSaveOutcome()
    data class ExerciseHasOwnVariations(val exerciseName: String) : ExerciseSaveOutcome()
    data object AlreadyLinkedElsewhere : ExerciseSaveOutcome()
}

@Dao
interface ExerciseDao {

    // Basic CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>): List<Long>

    @Update
    suspend fun update(exercise: Exercise): Int

    @Delete
    suspend fun delete(exercise: Exercise): Int

    @Query("DELETE FROM exercises WHERE id = :exerciseId")
    suspend fun deleteById(exerciseId: Long): Int

    // Queries
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllActive(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllSync(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 AND isArchived = 0 ORDER BY name ASC")
    fun getFavorites(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY lastPerformedAt DESC LIMIT :limit")
    fun getRecentlyPerformed(limit: Int = 20): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY timesPerformed DESC LIMIT :limit")
    fun getMostPerformed(limit: Int = 20): Flow<List<Exercise>>

    @Query("""
        SELECT * FROM exercises 
        WHERE isArchived = 0 
        AND (lastPerformedAt IS NULL OR lastPerformedAt < :beforeTimestamp)
        ORDER BY lastPerformedAt ASC, timesPerformed ASC
        LIMIT :limit
    """)
    fun getNeglected(beforeTimestamp: Long, limit: Int = 20): Flow<List<Exercise>>

    // Search
    @Query("""
        SELECT * FROM exercises 
        WHERE isArchived = 0 
        AND (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun search(query: String): Flow<List<Exercise>>

    // By difficulty
    @Query("SELECT * FROM exercises WHERE difficulty = :difficulty AND isArchived = 0 ORDER BY name ASC")
    fun getByDifficulty(difficulty: Difficulty): Flow<List<Exercise>>

    // Update tracking fields
    @Query("UPDATE exercises SET lastPerformedAt = :timestamp, timesPerformed = timesPerformed + 1, updatedAt = :timestamp WHERE id = :exerciseId")
    suspend fun markPerformed(exerciseId: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE exercises SET lastPerformedAt = :timestamp, timesPerformed = timesPerformed + 1, updatedAt = :timestamp WHERE id IN (:exerciseIds)")
    suspend fun markPerformed(exerciseIds: List<Long>, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE exercises SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :exerciseId")
    suspend fun setFavorite(exerciseId: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE exercises SET isArchived = :isArchived, updatedAt = :timestamp WHERE id = :exerciseId")
    suspend fun setArchived(exerciseId: Long, isArchived: Boolean, timestamp: Long = System.currentTimeMillis()): Int

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :exerciseId) +
            (SELECT COUNT(*) FROM workout_plan_template_exercises WHERE exerciseId = :exerciseId) +
            (SELECT COUNT(*) FROM pt_routine_exercises WHERE exerciseId = :exerciseId) +
            (SELECT COUNT(*) FROM ml_feedback_events WHERE exerciseId = :exerciseId)
    """)
    suspend fun getReferenceCount(exerciseId: Long): Int

    @Transaction
    suspend fun hardDeleteIfUnreferenced(exerciseId: Long) {
        val referenceCount = getReferenceCount(exerciseId)
        check(referenceCount == 0) {
            "Cannot permanently delete an exercise used by workout history, saved plans, PT routines, or feedback."
        }
        check(countVariationsForParent(exerciseId) == 0) {
            "Cannot permanently delete an exercise that still has variations linked to it. Detach its variations first."
        }
        check(countLinksAsVariation(exerciseId) == 0) {
            "Cannot permanently delete an exercise that is still linked as a variation. Detach it from its main exercise first."
        }
        clearCategoriesForExercise(exerciseId)
        clearEquipmentForExercise(exerciseId)
        clearMusclesForExercise(exerciseId)
        clearCustomCategoriesForExercise(exerciseId)
        check(deleteById(exerciseId) == 1) { "Exercise $exerciseId no longer exists." }
    }

    // Exercise family / variation links
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVariationLink(link: ExerciseVariationCrossRef)

    @Query("DELETE FROM exercise_variations WHERE variationExerciseId = :variationExerciseId")
    suspend fun deleteVariationLink(variationExerciseId: Long): Int

    @Query("UPDATE exercise_variations SET focus = :focus WHERE variationExerciseId = :variationExerciseId")
    suspend fun updateVariationFocus(variationExerciseId: Long, focus: String): Int

    @Query("SELECT * FROM exercise_variations WHERE variationExerciseId = :variationExerciseId")
    suspend fun getVariationLink(variationExerciseId: Long): ExerciseVariationCrossRef?

    @Query("SELECT * FROM exercise_variations WHERE parentExerciseId = :parentExerciseId ORDER BY variationExerciseId ASC")
    suspend fun getVariationLinksForParent(parentExerciseId: Long): List<ExerciseVariationCrossRef>

    @Query("SELECT * FROM exercise_variations WHERE parentExerciseId = :parentExerciseId ORDER BY variationExerciseId ASC")
    fun getVariationLinksForParentFlow(parentExerciseId: Long): Flow<List<ExerciseVariationCrossRef>>

    @Query("SELECT COUNT(*) FROM exercise_variations WHERE parentExerciseId = :exerciseId")
    suspend fun countVariationsForParent(exerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM exercise_variations WHERE variationExerciseId = :exerciseId")
    suspend fun countLinksAsVariation(exerciseId: Long): Int

    // All links at once - used by the workout generator to resolve every exercise's family root
    // in a single query instead of one lookup per candidate.
    @Query("SELECT * FROM exercise_variations")
    suspend fun getAllVariationLinks(): List<ExerciseVariationCrossRef>

    // Category cross-references
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryRef(ref: ExerciseCategoryCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryRefs(refs: List<ExerciseCategoryCrossRef>)

    @Query("DELETE FROM exercise_categories WHERE exerciseId = :exerciseId")
    suspend fun clearCategoriesForExercise(exerciseId: Long)

    @Query("SELECT category FROM exercise_categories WHERE exerciseId = :exerciseId")
    suspend fun getCategoriesForExercise(exerciseId: Long): List<WorkoutCategory>

    @Query("SELECT exerciseId FROM exercise_categories WHERE category = :category")
    suspend fun getExerciseIdsForCategory(category: WorkoutCategory): List<Long>

    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN exercise_categories ec ON e.id = ec.exerciseId
        WHERE ec.category = :category AND e.isArchived = 0
        ORDER BY e.name ASC
    """)
    fun getExercisesByCategory(category: WorkoutCategory): Flow<List<Exercise>>

    // Equipment cross-references
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipmentRef(ref: ExerciseEquipmentCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipmentRefs(refs: List<ExerciseEquipmentCrossRef>)

    @Query("DELETE FROM exercise_equipment WHERE exerciseId = :exerciseId")
    suspend fun clearEquipmentForExercise(exerciseId: Long)

    @Query("SELECT equipmentId FROM exercise_equipment WHERE exerciseId = :exerciseId AND isRequired = 1")
    suspend fun getRequiredEquipmentIds(exerciseId: Long): List<Long>

    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN exercise_equipment ee ON e.id = ee.exerciseId
        WHERE ee.equipmentId = :equipmentId AND e.isArchived = 0
        ORDER BY e.name ASC
    """)
    fun getExercisesByEquipment(equipmentId: Long): Flow<List<Exercise>>

    // Muscle group cross-references
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuscleRef(ref: ExerciseMuscleCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuscleRefs(refs: List<ExerciseMuscleCrossRef>)

    @Query("DELETE FROM exercise_muscles WHERE exerciseId = :exerciseId")
    suspend fun clearMusclesForExercise(exerciseId: Long)

    @Query("SELECT muscleGroup FROM exercise_muscles WHERE exerciseId = :exerciseId AND isPrimary = 1")
    suspend fun getPrimaryMuscles(exerciseId: Long): List<MuscleGroup>

    @Query("SELECT muscleGroup FROM exercise_muscles WHERE exerciseId = :exerciseId AND isPrimary = 0")
    suspend fun getSecondaryMuscles(exerciseId: Long): List<MuscleGroup>

    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN exercise_muscles em ON e.id = em.exerciseId
        WHERE em.muscleGroup = :muscleGroup AND e.isArchived = 0
        ORDER BY e.name ASC
    """)
    fun getExercisesByMuscle(muscleGroup: MuscleGroup): Flow<List<Exercise>>

    // Custom categories
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCategory(category: CustomCategory): Long

    @Query("SELECT * FROM custom_categories ORDER BY name ASC")
    fun getAllCustomCategories(): Flow<List<CustomCategory>>

    @Delete
    suspend fun deleteCustomCategory(category: CustomCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCategoryRef(ref: ExerciseCustomCategoryCrossRef)

    @Query("DELETE FROM exercise_custom_categories WHERE exerciseId = :exerciseId")
    suspend fun clearCustomCategoriesForExercise(exerciseId: Long)

    // Count queries
    @Query("SELECT COUNT(*) FROM exercises WHERE isArchived = 0")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM exercises WHERE isFavorite = 1 AND isArchived = 0")
    suspend fun getFavoriteCount(): Int

    @Transaction
    suspend fun insertWithRelations(
        exercise: Exercise,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup>
    ): Long {
        require(exercise.id == 0L) { "New exercises cannot already have an id." }
        val exerciseId = insert(exercise)
        replaceRelations(exerciseId, categories, equipmentIds, primaryMuscles, secondaryMuscles)
        return exerciseId
    }

    @Transaction
    suspend fun updateWithRelations(
        exercise: Exercise,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup>
    ) {
        require(exercise.id != 0L) { "Exercise id is required for updates." }
        check(update(exercise) == 1) { "Exercise ${exercise.id} no longer exists." }
        replaceRelations(exercise.id, categories, equipmentIds, primaryMuscles, secondaryMuscles)
    }

    /**
     * Atomically creates a brand-new exercise, its category/equipment/muscle relations, AND its
     * family link (if any) in a single DB transaction. Family validity is checked BEFORE any row
     * is written, so a rejected family mutation (self-link, missing parent, invalid nesting)
     * leaves absolutely nothing behind - not even the exercise row itself - and the caller can
     * safely clean up any newly-copied media because nothing referencing it was ever persisted.
     */
    @Transaction
    suspend fun insertWithRelationsAndFamily(
        exercise: Exercise,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup>,
        familyMutation: ExerciseFamilyMutation
    ): ExerciseSaveOutcome {
        require(exercise.id == 0L) { "New exercises cannot already have an id." }
        validateFamilyMutation(existingExerciseId = null, mutation = familyMutation)?.let { return it }
        val exerciseId = insert(exercise)
        replaceRelations(exerciseId, categories, equipmentIds, primaryMuscles, secondaryMuscles)
        applyFamilyMutation(exerciseId, familyMutation)
        return ExerciseSaveOutcome.Success(exerciseId)
    }

    /**
     * Atomically updates an existing exercise, replaces its relations, AND applies its family
     * mutation (link/reparent/detach/no-change) in a single DB transaction. Family validity is
     * checked BEFORE the exercise row or any relation is touched, and reparenting (detach old
     * link, then link new one) happens entirely inside this same transaction - so a failed
     * validation or a failed link insert rolls back the exercise edit too, instead of leaving a
     * silently-detached variation with a half-saved exercise row.
     */
    @Transaction
    suspend fun updateWithRelationsAndFamily(
        exercise: Exercise,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup>,
        familyMutation: ExerciseFamilyMutation
    ): ExerciseSaveOutcome {
        require(exercise.id != 0L) { "Exercise id is required for updates." }
        if (getById(exercise.id) == null) return ExerciseSaveOutcome.ExerciseNotFound
        validateFamilyMutation(existingExerciseId = exercise.id, mutation = familyMutation)?.let { return it }
        check(update(exercise) == 1) { "Exercise ${exercise.id} no longer exists." }
        replaceRelations(exercise.id, categories, equipmentIds, primaryMuscles, secondaryMuscles)
        applyFamilyMutation(exercise.id, familyMutation)
        return ExerciseSaveOutcome.Success(exercise.id)
    }

    /**
     * Atomically validates and links [variationExerciseId] as a variation of
     * [parentExerciseId], closing the check-then-insert gap: without a single transaction, two
     * concurrent callers could both pass the "not already linked" check and both attempt to
     * insert a link. Unlike the implicit reparenting done by [updateWithRelationsAndFamily],
     * this standalone entry point requires the variation be explicitly detached first if it is
     * already linked elsewhere (see [ExerciseSaveOutcome.AlreadyLinkedElsewhere]).
     */
    @Transaction
    suspend fun linkVariation(parentExerciseId: Long, variationExerciseId: Long, focus: String): ExerciseSaveOutcome {
        if (getById(variationExerciseId) == null) return ExerciseSaveOutcome.ExerciseNotFound
        if (parentExerciseId == variationExerciseId) return ExerciseSaveOutcome.SelfLink
        val parent = getById(parentExerciseId) ?: return ExerciseSaveOutcome.ParentNotFound(parentExerciseId)
        if (countLinksAsVariation(parentExerciseId) > 0) {
            return ExerciseSaveOutcome.ParentIsAlreadyVariation(parent.name)
        }
        if (countVariationsForParent(variationExerciseId) > 0) {
            val self = getById(variationExerciseId)
            return ExerciseSaveOutcome.ExerciseHasOwnVariations(self?.name ?: "This exercise")
        }
        if (getVariationLink(variationExerciseId) != null) {
            return ExerciseSaveOutcome.AlreadyLinkedElsewhere
        }
        insertVariationLink(
            ExerciseVariationCrossRef(
                variationExerciseId = variationExerciseId,
                parentExerciseId = parentExerciseId,
                focus = focus.trim()
            )
        )
        return ExerciseSaveOutcome.Success(variationExerciseId)
    }

    /**
     * Checks whether [mutation] is valid without writing anything, so callers can validate
     * before any destructive mutation. Returns null when valid, or the specific
     * [ExerciseSaveOutcome] failure otherwise. [existingExerciseId] is null when saving a
     * brand-new exercise (which can never self-link or already have variations of its own).
     */
    private suspend fun validateFamilyMutation(
        existingExerciseId: Long?,
        mutation: ExerciseFamilyMutation
    ): ExerciseSaveOutcome? {
        if (mutation !is ExerciseFamilyMutation.LinkTo) return null
        if (existingExerciseId != null && mutation.parentExerciseId == existingExerciseId) {
            return ExerciseSaveOutcome.SelfLink
        }
        val parent = getById(mutation.parentExerciseId)
            ?: return ExerciseSaveOutcome.ParentNotFound(mutation.parentExerciseId)
        if (countLinksAsVariation(mutation.parentExerciseId) > 0) {
            return ExerciseSaveOutcome.ParentIsAlreadyVariation(parent.name)
        }
        if (existingExerciseId != null && countVariationsForParent(existingExerciseId) > 0) {
            val self = getById(existingExerciseId)
            return ExerciseSaveOutcome.ExerciseHasOwnVariations(self?.name ?: "This exercise")
        }
        return null
    }

    /** Applies an already-validated [mutation]; never call without [validateFamilyMutation] first. */
    private suspend fun applyFamilyMutation(exerciseId: Long, mutation: ExerciseFamilyMutation) {
        when (mutation) {
            is ExerciseFamilyMutation.NoChange -> Unit
            is ExerciseFamilyMutation.Detach -> deleteVariationLink(exerciseId)
            is ExerciseFamilyMutation.LinkTo -> {
                // Detach any existing link first so reparenting is a single atomic replace, not
                // a separate unlink-then-relink that could leave the exercise detached if the
                // second step failed.
                deleteVariationLink(exerciseId)
                insertVariationLink(
                    ExerciseVariationCrossRef(
                        variationExerciseId = exerciseId,
                        parentExerciseId = mutation.parentExerciseId,
                        focus = mutation.focus.trim()
                    )
                )
            }
        }
    }

    private suspend fun replaceRelations(
        exerciseId: Long,
        categories: List<WorkoutCategory>,
        equipmentIds: List<Long>,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup>
    ) {
        clearCategoriesForExercise(exerciseId)
        clearEquipmentForExercise(exerciseId)
        clearMusclesForExercise(exerciseId)

        if (categories.isNotEmpty()) {
            insertCategoryRefs(categories.map { ExerciseCategoryCrossRef(exerciseId, it) })
        }
        if (equipmentIds.isNotEmpty()) {
            insertEquipmentRefs(equipmentIds.distinct().map { ExerciseEquipmentCrossRef(exerciseId, it) })
        }
        val muscleRefs = primaryMuscles.distinct().map {
            ExerciseMuscleCrossRef(exerciseId, it, isPrimary = true)
        } + secondaryMuscles.distinct().filterNot { it in primaryMuscles }.map {
            ExerciseMuscleCrossRef(exerciseId, it, isPrimary = false)
        }
        if (muscleRefs.isNotEmpty()) {
            insertMuscleRefs(muscleRefs)
        }
    }
}
