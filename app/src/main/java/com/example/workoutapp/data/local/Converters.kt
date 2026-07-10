package com.example.workoutapp.data.local

import androidx.room.TypeConverter
import com.example.workoutapp.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room type converters for complex types
 */
class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // WorkoutCategory
    @TypeConverter
    fun fromWorkoutCategory(category: WorkoutCategory): String = category.name

    @TypeConverter
    fun toWorkoutCategory(value: String): WorkoutCategory =
        WorkoutCategory.valueOf(value)

    // Difficulty
    @TypeConverter
    fun fromDifficulty(difficulty: Difficulty): String = difficulty.name

    @TypeConverter
    fun toDifficulty(value: String): Difficulty =
        Difficulty.valueOf(value)

    // MuscleGroup
    @TypeConverter
    fun fromMuscleGroup(muscleGroup: MuscleGroup): String = muscleGroup.name

    @TypeConverter
    fun toMuscleGroup(value: String): MuscleGroup =
        MuscleGroup.valueOf(value)

    // SessionStatus
    @TypeConverter
    fun fromSessionStatus(status: SessionStatus): String = status.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus =
        SessionStatus.valueOf(value)

    // TimeSlot
    @TypeConverter
    fun fromTimeSlot(timeSlot: TimeSlot): String = timeSlot.name

    @TypeConverter
    fun toTimeSlot(value: String): TimeSlot =
        TimeSlot.valueOf(value)

    // WeightUnit
    @TypeConverter
    fun fromWeightUnit(unit: WeightUnit): String = unit.name

    @TypeConverter
    fun toWeightUnit(value: String): WeightUnit =
        WeightUnit.valueOf(value)

    // DistanceUnit
    @TypeConverter
    fun fromDistanceUnit(unit: DistanceUnit): String = unit.name

    @TypeConverter
    fun toDistanceUnit(value: String): DistanceUnit =
        DistanceUnit.valueOf(value)

    // PTFrequency
    @TypeConverter
    fun fromPTFrequency(frequency: PTFrequency): String = frequency.name

    @TypeConverter
    fun toPTFrequency(value: String): PTFrequency =
        PTFrequency.valueOf(value)

    // TrainingPhase
    @TypeConverter
    fun fromTrainingPhase(phase: TrainingPhase): String = phase.name

    @TypeConverter
    fun toTrainingPhase(value: String): TrainingPhase =
        TrainingPhase.valueOf(value)

    // FeedbackAction
    @TypeConverter
    fun fromFeedbackAction(action: FeedbackAction): String = action.name

    @TypeConverter
    fun toFeedbackAction(value: String): FeedbackAction =
        FeedbackAction.valueOf(value)

    // List<String> for JSON arrays
    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        try { json.decodeFromString(value) } catch (e: Exception) { emptyList() }

    // List<WorkoutCategory>
    @TypeConverter
    fun fromCategoryList(list: List<WorkoutCategory>): String =
        json.encodeToString(list.map { it.name })

    @TypeConverter
    fun toCategoryList(value: String): List<WorkoutCategory> =
        try {
            json.decodeFromString<List<String>>(value).map { WorkoutCategory.valueOf(it) }
        } catch (e: Exception) {
            emptyList()
        }

    // List<TimeSlot>
    @TypeConverter
    fun fromTimeSlotList(list: List<TimeSlot>): String =
        json.encodeToString(list.map { it.name })

    @TypeConverter
    fun toTimeSlotList(value: String): List<TimeSlot> =
        try {
            json.decodeFromString<List<String>>(value).map { TimeSlot.valueOf(it) }
        } catch (e: Exception) {
            emptyList()
        }

    // List<MediaItem>
    @TypeConverter
    fun fromMediaItemList(list: List<MediaItem>): String = json.encodeToString(list)

    @TypeConverter
    fun toMediaItemList(value: String): List<MediaItem> =
        try { json.decodeFromString(value) } catch (e: Exception) { emptyList() }

    // Map<String, Float> for category weights
    @TypeConverter
    fun fromFloatMap(map: Map<String, Float>): String = json.encodeToString(map)

    @TypeConverter
    fun toFloatMap(value: String): Map<String, Float> =
        try { json.decodeFromString(value) } catch (e: Exception) { emptyMap() }

    // List<Int> for rest day preferences
    @TypeConverter
    fun fromIntList(list: List<Int>): String = json.encodeToString(list)

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        try { json.decodeFromString(value) } catch (e: Exception) { emptyList() }
}
