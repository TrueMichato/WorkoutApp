package com.example.workoutapp.data.model

/**
 * Which set-entry metrics are meaningful for a given exercise prescription.
 *
 * Deliberately derived only from data that already exists on [SessionExercise] /
 * [RichPrescriptionData] (planned reps text and prescribed work duration) — no new
 * modality/exercise-type schema is introduced. This keeps the mapping conservative and
 * explainable: a prescription that reads as a duration (e.g. "30s") or that carries an
 * explicit [RichPrescriptionData.durationSeconds] is treated as time-based and shows a
 * duration field instead of reps/weight; everything else is treated as a rep-based lift and
 * shows reps + weight instead of duration.
 */
data class SetMetricVisibility(
    val showReps: Boolean,
    val showWeight: Boolean,
    val showDuration: Boolean,
    val showRpe: Boolean = true,
    val showNotes: Boolean = true
)

private val timedPrescriptionPattern =
    Regex("^(\\d+)\\s*(s|sec|secs|second|seconds)\\.?$", RegexOption.IGNORE_CASE)

/**
 * Parses a planned-reps string such as "30s", "45 sec" or "60 seconds" into a duration in
 * seconds. Returns null when the text doesn't describe a fixed duration (e.g. "8-12", "AMRAP").
 */
fun parseTimedPrescriptionSeconds(plannedReps: String): Int? =
    timedPrescriptionPattern.find(plannedReps.trim())?.groupValues?.get(1)?.toIntOrNull()

/**
 * Resolves which set-entry metrics apply to an exercise given its planned reps text and (if
 * present) rich prescription data. A prescription is considered time-based when the planned
 * reps text itself reads as a duration, or when the prescription carries an explicit work
 * duration.
 */
fun resolveSetMetricVisibility(
    plannedReps: String,
    richPrescription: RichPrescriptionData?
): SetMetricVisibility {
    val isTimed = parseTimedPrescriptionSeconds(plannedReps) != null ||
        richPrescription?.durationSeconds != null
    return SetMetricVisibility(
        showReps = !isTimed,
        showWeight = !isTimed,
        showDuration = isTimed
    )
}
