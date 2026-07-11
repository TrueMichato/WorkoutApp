package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Core Exercise entity representing a single exercise in the library.
 * Supports multiple categories, equipment requirements, rich media,
 * and goal-specific programming presets.
 */
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Basic info
    val name: String,
    val description: String = "",
    val instructions: String = "", // Step-by-step instructions
    val tips: String = "", // Form tips and common mistakes

    // Classification
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val isUnilateral: Boolean = false, // Single limb exercise
    val isCompound: Boolean = true, // Multiple joints/muscle groups

    // Timing defaults
    val defaultSets: Int = 3,
    val defaultReps: String = "8-12", // Can be "8-12", "30s", "AMRAP", etc.
    val defaultRestSeconds: Int = 90,
    val estimatedDurationSeconds: Int = 180, // For workout time planning
    val trainingPhasePresets: String = "{}", // JSON map of TrainingPhase -> ExerciseProgrammingPreset

    // Media - stored as JSON arrays
    val localMediaUris: String = "[]", // JSON array of local file URIs
    val externalMediaUrls: String = "[]", // JSON array of external URLs (YouTube, etc.)

    // Tracking metadata
    val timesPerformed: Int = 0,
    val lastPerformedAt: Long? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false, // Soft delete

    // User notes
    val personalNotes: String = "",

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ExerciseProgrammingPreset(
    val setsText: String = "3",
    val repsText: String = "8-12",
    val restSeconds: Int = 90,
    val rounds: Int = 1,
    val durationSeconds: Int? = null,
    val tempo: String = "",
    val effortTarget: String = "",
    val notes: String = ""
)

fun Exercise.decodeStoredProgrammingPresets(): PersistedJsonResult<Map<TrainingPhase, ExerciseProgrammingPreset>> =
    decodeTrainingPhasePresets("exercise programming presets", trainingPhasePresets)

fun Exercise.resolveStoredProgrammingPreset(phase: TrainingPhase): ExerciseProgrammingPreset? {
    val presets = decodeStoredProgrammingPresets()
    return presets.value[phase] ?: presets.value[TrainingPhase.BALANCED]
}

fun Exercise.resolveBalancedProgrammingPreset(): ExerciseProgrammingPreset =
    resolveStoredProgrammingPreset(TrainingPhase.BALANCED) ?: ExerciseProgrammingPreset(
        setsText = defaultSets.toString(),
        repsText = defaultReps,
        restSeconds = defaultRestSeconds
    )

fun ExerciseProgrammingPreset.toRichPrescriptionData(): RichPrescriptionData = RichPrescriptionData(
    rounds = rounds,
    durationSeconds = durationSeconds,
    tempo = tempo.trim(),
    effortTarget = effortTarget.trim()
)

/**
 * Junction table linking exercises to their categories
 */
@Entity(
    tableName = "exercise_categories",
    primaryKeys = ["exerciseId", "category"]
)
data class ExerciseCategoryCrossRef(
    val exerciseId: Long,
    val category: WorkoutCategory
)

/**
 * Junction table linking exercises to required equipment
 */
@Entity(
    tableName = "exercise_equipment",
    primaryKeys = ["exerciseId", "equipmentId"]
)
data class ExerciseEquipmentCrossRef(
    val exerciseId: Long,
    val equipmentId: Long,
    val isRequired: Boolean = true, // vs optional/alternative
    val isAlternative: Boolean = false // Part of an alternative equipment group
)

/**
 * Junction table linking exercises to target muscle groups
 */
@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exerciseId", "muscleGroup"]
)
data class ExerciseMuscleCrossRef(
    val exerciseId: Long,
    val muscleGroup: MuscleGroup,
    val isPrimary: Boolean = true // Primary vs secondary/stabilizer
)

/**
 * Links a variation exercise (e.g. "Tiger Push-up") to its main/root exercise
 * (e.g. "Push-up"). Both sides are full, independent [Exercise] rows with their own
 * categories, muscles, equipment, media, favorites, and history - this table only
 * records the family relationship and a short explanation of what makes the
 * variation different.
 *
 * [variationExerciseId] is the primary key so a variation can belong to at most one
 * parent. Repository-level validation (see `ExerciseRepository.linkVariation`) additionally
 * rejects self-links, cycles, and multi-level nesting (a parent can't also be a variation,
 * and a variation can't also be a parent) - the schema alone does not express those rules.
 */
@Entity(
    tableName = "exercise_variations",
    primaryKeys = ["variationExerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["variationExerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["parentExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentExerciseId")]
)
data class ExerciseVariationCrossRef(
    val variationExerciseId: Long,
    val parentExerciseId: Long,
    val focus: String = ""
)

/**
 * Custom category created by user
 */
@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val iconName: String = "label",
    val defaultWeight: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Junction table linking exercises to custom categories
 */
@Entity(
    tableName = "exercise_custom_categories",
    primaryKeys = ["exerciseId", "customCategoryId"]
)
data class ExerciseCustomCategoryCrossRef(
    val exerciseId: Long,
    val customCategoryId: Long
)

/**
 * Media item for serialization
 */
@Serializable
data class MediaItem(
    val uri: String,
    val type: MediaType,
    val thumbnailUri: String? = null,
    val sizeBytes: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class MediaType {
    IMAGE,
    VIDEO,
    EXTERNAL_VIDEO // YouTube, Vimeo, etc.
}
