package com.example.workoutapp.di

import android.content.Context
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WorkoutDatabase {
        return WorkoutDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideExerciseDao(database: WorkoutDatabase): ExerciseDao {
        return database.exerciseDao()
    }

    @Provides
    @Singleton
    fun provideEquipmentDao(database: WorkoutDatabase): EquipmentDao {
        return database.equipmentDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutSessionDao(database: WorkoutDatabase): WorkoutSessionDao {
        return database.workoutSessionDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutPlanTemplateDao(database: WorkoutDatabase): WorkoutPlanTemplateDao {
        return database.workoutPlanTemplateDao()
    }

    @Provides
    @Singleton
    fun providePhysicalTherapyDao(database: WorkoutDatabase): PhysicalTherapyDao {
        return database.physicalTherapyDao()
    }

    @Provides
    @Singleton
    fun provideUserGoalDao(database: WorkoutDatabase): UserGoalDao {
        return database.userGoalDao()
    }

    @Provides
    @Singleton
    fun provideMLFeedbackDao(database: WorkoutDatabase): MLFeedbackDao {
        return database.mlFeedbackDao()
    }
}

