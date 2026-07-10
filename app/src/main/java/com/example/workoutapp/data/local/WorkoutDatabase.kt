package com.example.workoutapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.workoutapp.data.local.dao.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        // Core entities
        Exercise::class,
        Equipment::class,
        EquipmentLocation::class,
        WorkoutSession::class,
        SessionExercise::class,
        SetLog::class,
        WorkoutPlanTemplate::class,
        WorkoutPlanTemplateExercise::class,

        // Physical therapy
        PhysicalTherapyRoutine::class,
        PTSessionLog::class,

        // User data
        UserGoal::class,
        CategoryStats::class,
        CustomCategory::class,

        // ML feedback
        MLFeedbackEvent::class,
        MLPreferenceScore::class,

        // Cross-reference tables
        ExerciseCategoryCrossRef::class,
        ExerciseEquipmentCrossRef::class,
        ExerciseMuscleCrossRef::class,
        ExerciseCustomCategoryCrossRef::class,
        LocationEquipmentCrossRef::class,
        PTRoutineExerciseCrossRef::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutPlanTemplateDao(): WorkoutPlanTemplateDao
    abstract fun physicalTherapyDao(): PhysicalTherapyDao
    abstract fun userGoalDao(): UserGoalDao
    abstract fun mlFeedbackDao(): MLFeedbackDao

    companion object {
        const val DATABASE_NAME = "workout_database"

        @Volatile
        private var INSTANCE: WorkoutDatabase? = null

        fun getInstance(context: Context): WorkoutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Callback to pre-populate database with default data
     */
    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase(database)
                }
            }
        }

        private suspend fun populateDatabase(database: WorkoutDatabase) {
            // Insert default equipment
            val equipmentDao = database.equipmentDao()
            equipmentDao.insertAll(DefaultEquipment.all)

            // Insert default locations
            val locationIds = equipmentDao.insertLocations(DefaultLocations.all)

            // Set Commercial Gym as default and give it all equipment
            if (locationIds.size >= 2) {
                val commercialGymId = locationIds[1] // Index 1 is Commercial Gym
                equipmentDao.setAsDefaultLocation(commercialGymId)

                // Commercial gym has everything
                val allEquipment = DefaultEquipment.all.mapIndexed { index, _ ->
                    LocationEquipmentCrossRef(commercialGymId, index + 1L)
                }
                equipmentDao.insertLocationEquipmentAll(allEquipment)
            }

            // Home gym gets a reasonable subset
            if (locationIds.isNotEmpty()) {
                val homeGymId = locationIds[0]
                val homeEquipment = listOf(
                    "Dumbbells", "Resistance Bands", "Pull-up Bar", "Yoga Mat",
                    "Foam Roller", "Kettlebell", "Adjustable Bench", "No Equipment"
                )
                val homeRefs = DefaultEquipment.all.mapIndexedNotNull { index, eq ->
                    if (eq.name in homeEquipment) LocationEquipmentCrossRef(homeGymId, index + 1L)
                    else null
                }
                equipmentDao.insertLocationEquipmentAll(homeRefs)
            }

            // Travel gets portable equipment only
            if (locationIds.size >= 3) {
                val travelId = locationIds[2]
                val travelRefs = DefaultEquipment.all.mapIndexedNotNull { index, eq ->
                    if (eq.isPortable) LocationEquipmentCrossRef(travelId, index + 1L)
                    else null
                }
                equipmentDao.insertLocationEquipmentAll(travelRefs)
            }

            // Outdoor gets bodyweight-friendly equipment
            if (locationIds.size >= 4) {
                val outdoorId = locationIds[3]
                val outdoorEquipment = listOf(
                    "Pull-up Bar", "Resistance Bands", "Jump Rope",
                    "Yoga Mat", "No Equipment"
                )
                val outdoorRefs = DefaultEquipment.all.mapIndexedNotNull { index, eq ->
                    if (eq.name in outdoorEquipment) LocationEquipmentCrossRef(outdoorId, index + 1L)
                    else null
                }
                equipmentDao.insertLocationEquipmentAll(outdoorRefs)
            }

            // Initialize user goals with defaults
            val userGoalDao = database.userGoalDao()
            userGoalDao.insert(UserGoal())

            // Initialize category stats
            val categoryStats = WorkoutCategory.rotationCategories().map { category ->
                CategoryStats(category = category)
            }
            userGoalDao.insertAllCategoryStats(categoryStats)

            // Insert sample exercises
            val exerciseDao = database.exerciseDao()

            for (sample in SampleExercises.all) {
                // Create exercise
                val exercise = Exercise(
                    name = sample.name,
                    description = sample.description,
                    instructions = sample.instructions,
                    tips = sample.tips,
                    difficulty = sample.difficulty,
                    isCompound = sample.isCompound,
                    isUnilateral = sample.isUnilateral,
                    defaultSets = sample.defaultSets,
                    defaultReps = sample.defaultReps,
                    defaultRestSeconds = sample.defaultRestSeconds
                )
                val exerciseId = exerciseDao.insert(exercise)

                // Add categories
                val categoryRefs = sample.categories.map {
                    ExerciseCategoryCrossRef(exerciseId, it)
                }
                exerciseDao.insertCategoryRefs(categoryRefs)

                // Add equipment
                val equipmentRefs = sample.equipmentNames.mapNotNull { name ->
                    val equipmentIndex = DefaultEquipment.all.indexOfFirst { it.name == name }
                    if (equipmentIndex >= 0) {
                        ExerciseEquipmentCrossRef(exerciseId, equipmentIndex + 1L)
                    } else null
                }
                if (equipmentRefs.isNotEmpty()) {
                    exerciseDao.insertEquipmentRefs(equipmentRefs)
                }

                // Add muscle groups
                val primaryRefs = sample.primaryMuscles.map {
                    ExerciseMuscleCrossRef(exerciseId, it, isPrimary = true)
                }
                val secondaryRefs = sample.secondaryMuscles.map {
                    ExerciseMuscleCrossRef(exerciseId, it, isPrimary = false)
                }
                exerciseDao.insertMuscleRefs(primaryRefs + secondaryRefs)
            }
        }
    }
}
