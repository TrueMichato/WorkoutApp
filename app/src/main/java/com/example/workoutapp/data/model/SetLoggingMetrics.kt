package com.example.workoutapp.data.model

/**
 * Which set-entry metrics are meaningful for a given exercise prescription.
 *
 * Deliberately derived only from data that already exists on [SessionExercise] /
 * [RichPrescriptionData] (planned reps text and prescribed work duration) — no new
 * modality/exercise-type schema is introduced. This keeps the mapping conservative and
 * explainable: a prescription that reads as a duration (e.g. "30s") or that carries an
 * explicit [RichPrescriptionData.durationSeconds] is treated as time-based and shows the
 * duration field instead of reps; everything else is treated as a rep-based lift and shows
 * reps instead of duration.
 *
 * Weight is shown for BOTH timed and rep-based entries. The data model has no
 * bodyweight/load-capability signal per exercise (e.g. nothing distinguishes a bodyweight
 * plank hold from a weighted farmer's walk, both of which are prescribed by duration), so
 * hiding weight for every timed exercise would silently drop load data for real, shipped
 * exercises. Showing weight unconditionally is the lossless, conservative choice: it never
 * hides a metric a user might legitimately need, and an empty weight field costs nothing for
 * exercises where load truly doesn't apply.
 */
data class SetMetricVisibility(
    val showReps: Boolean,
    val showWeight: Boolean = true,
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
        showWeight = true,
        showDuration = isTimed
    )
}
