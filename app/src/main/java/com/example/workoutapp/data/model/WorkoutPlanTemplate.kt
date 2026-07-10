package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Reusable workout template that can be played into a fresh workout session.
 */
@Entity(tableName = "workout_plan_templates")
data class WorkoutPlanTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val notes: String = "",
    val locationId: Long? = null,
    val targetDurationMinutes: Int = 60,
    val targetCategories: String = "[]",
    val scheduledTimeSlot: TimeSlot = TimeSlot.ANYTIME,
    val sourcePhase: TrainingPhase = TrainingPhase.BALANCED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class PlanExerciseSection(val displayName: String) {
    WARMUP("Warm-up"),
    MAIN("Main"),
    COOLDOWN("Cooldown")
}

@Serializable
data class RichPrescriptionData(
    val rounds: Int = 1,
    val durationSeconds: Int? = null,
    val tempo: String = "",
    val effortTarget: String = ""
)

@Entity(
    tableName = "workout_plan_template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class WorkoutPlanTemplateExercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val section: PlanExerciseSection = PlanExerciseSection.MAIN,
    val plannedSets: Int = 3,
    val plannedReps: String = "8-12",
    val plannedRestSeconds: Int = 90,
    val prescriptionJson: String = "",
    val coachingNotes: String = ""
)

data class WorkoutPlanTemplateSummary(
    val id: Long,
    val name: String,
    val description: String,
    val targetDurationMinutes: Int,
    val targetCategories: String,
    val scheduledTimeSlot: TimeSlot,
    val sourcePhase: TrainingPhase,
    val exerciseCount: Int,
    val updatedAt: Long
)

fun RichPrescriptionData.toJson(): String = persistedJson.encodeToString(this)

fun String.toRichPrescriptionDataOrNull(): RichPrescriptionData? =
    decodeRichPrescriptionData().value

fun String.decodeRichPrescriptionData(): PersistedJsonResult<RichPrescriptionData?> =
    if (isBlank()) {
        PersistedJsonResult(null)
    } else {
        decodePersistedJsonCompatible("rich prescription data", this, null)
    }

fun RichPrescriptionData.displaySummary(): String = buildList {
    if (rounds > 1) add("$rounds rounds")
    durationSeconds?.let { add("${it}s work") }
    if (tempo.isNotBlank()) add("Tempo $tempo")
    if (effortTarget.isNotBlank()) add(effortTarget)
}.joinToString(" • ")
