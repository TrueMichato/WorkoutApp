package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow

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
