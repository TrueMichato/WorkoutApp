package com.example.workoutapp.data.model

import kotlinx.serialization.Serializable

/**
 * In-progress, unsaved input for the next set of an exercise. Kept as raw strings so the UI
 * can preserve exactly what the user typed (including transiently invalid text) across
 * recompositions; parsing/validation happens in [validateSetEntryDraft]. Serializable so it can
 * be persisted to a SavedStateHandle and survive process death without being written to history.
 */
@Serializable
data class SetEntryDraft(
    val reps: String = "",
    val weight: String = "",
    val durationSeconds: String = "",
    val rpe: String = "",
    val notes: String = ""
)

/** Per-field inline validation errors for a [SetEntryDraft]. Null means the field is valid. */
data class SetEntryFieldErrors(
    val reps: String? = null,
    val weight: String? = null,
    val durationSeconds: String? = null,
    val rpe: String? = null
) {
    val hasErrors: Boolean get() = reps != null || weight != null || durationSeconds != null || rpe != null
}

/**
 * Result of validating a [SetEntryDraft] against the metrics applicable to an exercise.
 * [errors] is empty when the draft is safe to save. Fields hidden by [SetMetricVisibility] are
 * always parsed as null and never produce an error, since the user had no way to edit them.
 */
data class ValidatedSetEntry(
    val reps: Int?,
    val weight: Float?,
    val durationSeconds: Int?,
    val rpe: Int?,
    val notes: String,
    val errors: SetEntryFieldErrors
) {
    val isValid: Boolean get() = !errors.hasErrors
}

private const val MAX_REPS = 200
private const val MAX_WEIGHT_KG = 1000f
private const val MAX_DURATION_SECONDS = 7200
private const val MIN_RPE = 1
private const val MAX_RPE = 10

/**
 * Validates and parses a [SetEntryDraft], surfacing actionable inline errors for
 * negative/nonsensical input rather than silently dropping or accepting it. Blank input for a
 * visible field is valid and parses to null (meaning "use the planned default"); it is distinct
 * from an explicit invalid value such as "-5".
 */
fun validateSetEntryDraft(draft: SetEntryDraft, metrics: SetMetricVisibility): ValidatedSetEntry {
    val reps = if (metrics.showReps) parseNonNegativeInt(draft.reps, MAX_REPS, "reps") else Field(null, null)
    val weight = if (metrics.showWeight) parseNonNegativeFloat(draft.weight, MAX_WEIGHT_KG, "weight") else Field(null, null)
    val duration = if (metrics.showDuration) parsePositiveInt(draft.durationSeconds, MAX_DURATION_SECONDS, "duration") else Field(null, null)
    val rpe = if (metrics.showRpe) parseRangedInt(draft.rpe, MIN_RPE, MAX_RPE, "RPE") else Field(null, null)

    return ValidatedSetEntry(
        reps = reps.value,
        weight = weight.value,
        durationSeconds = duration.value,
        rpe = rpe.value,
        notes = draft.notes,
        errors = SetEntryFieldErrors(
            reps = reps.error,
            weight = weight.error,
            durationSeconds = duration.error,
            rpe = rpe.error
        )
    )
}

private data class Field<T>(val value: T?, val error: String?)

private fun parseNonNegativeInt(raw: String, max: Int, label: String): Field<Int> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return Field(null, null)
    val parsed = trimmed.toIntOrNull()
        ?: return Field(null, "Enter a whole number for $label")
    return when {
        parsed < 0 -> Field(null, "${label.capitalizeFirst()} can't be negative")
        parsed > max -> Field(null, "${label.capitalizeFirst()} looks too high (max $max)")
        else -> Field(parsed, null)
    }
}

private fun parsePositiveInt(raw: String, max: Int, label: String): Field<Int> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return Field(null, null)
    val parsed = trimmed.toIntOrNull()
        ?: return Field(null, "Enter a whole number of seconds")
    return when {
        parsed <= 0 -> Field(null, "${label.capitalizeFirst()} must be at least 1 second")
        parsed > max -> Field(null, "${label.capitalizeFirst()} looks too high (max ${max}s)")
        else -> Field(parsed, null)
    }
}

private fun parseNonNegativeFloat(raw: String, max: Float, label: String): Field<Float> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return Field(null, null)
    val parsed = trimmed.toFloatOrNull()
        ?: return Field(null, "Enter a valid $label")
    return when {
        !parsed.isFinite() -> Field(null, "Enter a valid $label")
        parsed < 0f -> Field(null, "${label.capitalizeFirst()} can't be negative")
        parsed > max -> Field(null, "${label.capitalizeFirst()} looks too high (max $max)")
        else -> Field(parsed, null)
    }
}

private fun parseRangedInt(raw: String, min: Int, max: Int, label: String): Field<Int> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return Field(null, null)
    val parsed = trimmed.toIntOrNull()
        ?: return Field(null, "Enter a whole number for $label")
    return if (parsed < min || parsed > max) {
        Field(null, "$label must be between $min and $max")
    } else {
        Field(parsed, null)
    }
}

private fun String.capitalizeFirst(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
