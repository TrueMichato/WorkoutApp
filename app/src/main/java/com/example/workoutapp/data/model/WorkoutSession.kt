package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A planned workout session with exercises and parameters
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "",
    val notes: String = "",

    // Planning parameters used to generate this session
    val locationId: Long? = null,
    val targetDurationMinutes: Int = 60,
    val targetCategories: String = "[]", // JSON array of WorkoutCategory

    // Status
    val status: SessionStatus = SessionStatus.PLANNED,

    // Timing
    val plannedDate: Long = System.currentTimeMillis(),
    val scheduledTimeSlot: TimeSlot = TimeSlot.ANYTIME,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val actualDurationMinutes: Int? = null,

    // Rating & feedback
    val perceivedDifficulty: Int? = null, // 1-10 scale
    val energyLevel: Int? = null, // 1-10 scale
    val satisfactionRating: Int? = null, // 1-5 stars
    val postSessionNotes: String = "",

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SessionStatus {
    PLANNED,     // Generated but not started
    IN_PROGRESS, // Currently being performed
    COMPLETED,   // Finished successfully
    SKIPPED,     // User skipped this session
    PARTIAL      // Started but not fully completed
}

/**
 * Time slots for scheduling multiple daily workouts
 */
enum class TimeSlot(val displayName: String, val sortOrder: Int) {
    EARLY_MORNING("Early Morning (5-7am)", 1),
    MORNING("Morning (7-10am)", 2),
    LATE_MORNING("Late Morning (10am-12pm)", 3),
    LUNCH("Lunch (12-2pm)", 4),
    AFTERNOON("Afternoon (2-5pm)", 5),
    EVENING("Evening (5-8pm)", 6),
    NIGHT("Night (8pm+)", 7),
    ANYTIME("Anytime", 0)
}

/**
 * Individual exercise within a workout session
 */
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("exerciseId")
    ]
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,
    val exerciseId: Long,
    val orderIndex: Int, // Position in the workout
    val section: PlanExerciseSection = PlanExerciseSection.MAIN,

    // Planned parameters
    val plannedSets: Int = 3,
    val plannedReps: String = "8-12",
    val plannedRestSeconds: Int = 90,
    val prescriptionJson: String = "",

    // Actual performance (updated during workout)
    val isCompleted: Boolean = false,
    val isSkipped: Boolean = false,
    val notes: String = ""
)

/**
 * Individual set performance log
 */
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionExercise::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionExerciseId")]
)
data class SetLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionExerciseId: Long,
    val setNumber: Int,

    // Performance metrics (nullable for flexibility)
    val reps: Int? = null,
    val weight: Float? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val durationSeconds: Int? = null, // For timed exercises
    val distance: Float? = null, // For cardio/distance exercises
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,

    // RPE (Rate of Perceived Exertion) 1-10
    val rpe: Int? = null,

    // Rest taken after this set
    val restTakenSeconds: Int? = null,

    val notes: String = "",
    val completedAt: Long = System.currentTimeMillis()
)

enum class WeightUnit(val displayName: String, val abbreviation: String) {
    KG("Kilograms", "kg"),
    LBS("Pounds", "lbs")
}

enum class DistanceUnit(val displayName: String, val abbreviation: String) {
    METERS("Meters", "m"),
    KILOMETERS("Kilometers", "km"),
    FEET("Feet", "ft"),
    MILES("Miles", "mi")
}

