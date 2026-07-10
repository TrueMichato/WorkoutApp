package com.example.workoutapp.di

import android.content.Context
import androidx.room.Room
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.local.dao.EquipmentDao
import com.example.workoutapp.data.local.dao.ExerciseDao
import com.example.workoutapp.data.local.dao.MLFeedbackDao
import com.example.workoutapp.data.local.dao.PhysicalTherapyDao
import com.example.workoutapp.data.local.dao.UserGoalDao
import com.example.workoutapp.data.local.dao.WorkoutPlanTemplateDao
import com.example.workoutapp.data.local.dao.WorkoutSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WorkoutDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            WorkoutDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    @Singleton
    fun provideExerciseDao(database: WorkoutDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    @Singleton
    fun provideEquipmentDao(database: WorkoutDatabase): EquipmentDao = database.equipmentDao()

    @Provides
    @Singleton
    fun provideWorkoutSessionDao(database: WorkoutDatabase): WorkoutSessionDao = database.workoutSessionDao()

    @Provides
    @Singleton
    fun provideWorkoutPlanTemplateDao(database: WorkoutDatabase): WorkoutPlanTemplateDao =
        database.workoutPlanTemplateDao()

    @Provides
    @Singleton
    fun providePhysicalTherapyDao(database: WorkoutDatabase): PhysicalTherapyDao =
        database.physicalTherapyDao()

    @Provides
    @Singleton
    fun provideUserGoalDao(database: WorkoutDatabase): UserGoalDao = database.userGoalDao()

    @Provides
    @Singleton
    fun provideMLFeedbackDao(database: WorkoutDatabase): MLFeedbackDao = database.mlFeedbackDao()
}


