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

    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getById(id)

    fun getExerciseByIdFlow(id: Long): Flow<Exercise?> = exerciseDao.getByIdFlow(id)

    fun getAllExercises(): Flow<List<Exercise>> = exerciseDao.getAllActive()

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

    suspend fun archiveExercise(exerciseId: Long) = exerciseDao.setArchived(exerciseId, true)

    suspend fun unarchiveExercise(exerciseId: Long) = exerciseDao.setArchived(exerciseId, false)

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
}
