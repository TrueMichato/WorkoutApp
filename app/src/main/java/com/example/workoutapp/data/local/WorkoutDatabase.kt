package com.example.workoutapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.workoutapp.data.local.dao.*
import com.example.workoutapp.data.model.*

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
        PTRoutineExerciseCrossRef::class,
        ExerciseVariationCrossRef::class
    ],
    version = WorkoutDatabaseMigrations.CURRENT_VERSION,
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
                    .addMigrations(*WorkoutDatabaseMigrations.ALL)
                    .addCallback(WorkoutDatabaseSeedCallback)
                    // Missing migrations must fail visibly instead of erasing user data.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
