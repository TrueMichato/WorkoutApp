package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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

private val exercisePresetJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Exercise.resolveStoredProgrammingPreset(phase: TrainingPhase): ExerciseProgrammingPreset? {
    val presets = try {
        exercisePresetJson.decodeFromString<Map<String, ExerciseProgrammingPreset>>(trainingPhasePresets)
    } catch (_: Exception) {
        emptyMap()
    }
    return presets[phase.name] ?: presets[TrainingPhase.BALANCED.name]
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
