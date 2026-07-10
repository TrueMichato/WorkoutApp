package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Physical Therapy routine - separate from regular workouts
 * with priority scheduling and must-do capabilities
 */
@Entity(tableName = "pt_routines")
data class PhysicalTherapyRoutine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val description: String = "",
    val therapistName: String = "", // Prescribing PT/doctor
    val condition: String = "", // Condition being treated

    // Scheduling
    val frequency: PTFrequency = PTFrequency.DAILY,
    val timesPerDay: Int = 1,
    val preferredTimeSlots: String = "[]", // JSON array of TimeSlot
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null, // Null = ongoing

    // Priority
    val isMustDo: Boolean = true, // Always appears in daily planning
    val priority: Int = 1, // 1 = highest priority

    // Status
    val isActive: Boolean = true,
    val isArchived: Boolean = false,

    // Notes
    val notes: String = "",
    val precautions: String = "", // Warnings or movement restrictions

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class PTFrequency(val displayName: String, val timesPerWeek: Int) {
    MULTIPLE_DAILY("Multiple times daily", 14),
    DAILY("Daily", 7),
    EVERY_OTHER_DAY("Every other day", 4),
    THREE_TIMES_WEEK("3x per week", 3),
    TWICE_WEEK("2x per week", 2),
    WEEKLY("Weekly", 1),
    AS_NEEDED("As needed", 0)
}

/**
 * Exercise within a PT routine
 */
@Entity(
    tableName = "pt_routine_exercises",
    primaryKeys = ["routineId", "exerciseId"]
)
data class PTRoutineExerciseCrossRef(
    val routineId: Long,
    val exerciseId: Long,
    val orderIndex: Int = 0,

    // PT-specific parameters
    val prescribedSets: Int = 3,
    val prescribedReps: String = "10-15",
    val prescribedHoldSeconds: Int? = null, // For stretches/isometrics
    val prescribedRestSeconds: Int = 30,

    val specialInstructions: String = "",
    val progressionNotes: String = "" // How to progress this exercise
)

/**
 * Log of completed PT sessions
 */
@Entity(tableName = "pt_session_logs")
data class PTSessionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val routineId: Long,

    // Timing
    val startedAt: Long,
    val completedAt: Long? = null,
    val timeSlot: TimeSlot = TimeSlot.ANYTIME,

    // Performance
    val exercisesCompleted: Int = 0,
    val exercisesTotal: Int = 0,

    // Feedback
    val painLevelBefore: Int? = null, // 0-10 scale
    val painLevelAfter: Int? = null,
    val notes: String = "",
    val symptomChanges: String = "" // Better/worse/same
)

