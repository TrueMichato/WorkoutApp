package com.example.workoutapp.data.csv

import com.example.workoutapp.data.model.TrainingPhase

/**
 * Canonical description of every CSV column [ExerciseCsvImporter] understands.
 *
 * This is the single source of truth for accepted headers: [ExerciseCsvImporter] validates
 * against it, the Exercise Library help text renders it, and [ExerciseCsvTemplate] generates
 * the downloadable example file from it. Any change to supported columns should happen here so
 * validation, documentation, and the example file cannot silently drift apart.
 */
object ExerciseCsvSchema {

    /** A single documented column: its normalized header key and a short human description. */
    data class Column(val header: String, val description: String, val required: Boolean = false)

    /** Delimiters accepted between values in multi-value columns (equipment, muscles, categories). */
    val multiValueDelimiters: List<Char> = listOf('|', ';', ',')

    /** Accepted spellings for boolean columns, in addition to the canonical true/false. */
    const val booleanSyntaxHelp = "true/false, yes/no, y/n, or 1/0"

    /** Core columns, in the order they appear in the generated template. */
    val coreColumns: List<Column> = listOf(
        Column("name", "Exercise name. Must be unique in the library.", required = true),
        Column(
            "categories",
            "One or more of: ${categoryNames()}. Separate multiple values with | or ;.",
            required = true
        ),
        Column("description", "Short summary shown in the library list."),
        Column("instructions", "Step-by-step execution cues."),
        Column("tips", "Form tips or common mistakes to avoid."),
        Column("difficulty", "One of: ${difficultyNames()}. Defaults to Intermediate."),
        Column("is_compound", "Multi-joint movement? $booleanSyntaxHelp. Defaults to true."),
        Column("is_unilateral", "Single-limb movement? $booleanSyntaxHelp. Defaults to false."),
        Column("default_sets", "Whole number of sets. Defaults to 3."),
        Column("default_reps", "Free text, e.g. \"8-12\", \"30s\", \"AMRAP\". Defaults to 8-12."),
        Column("default_rest_seconds", "Whole number of seconds. Defaults to 90."),
        Column("estimated_duration_seconds", "Whole-exercise time estimate for planning. Defaults to 180."),
        Column("equipment", "Free-text equipment names, separated by | or ; or ,. Unknown names are created automatically."),
        Column(
            "primary_muscles",
            "One or more of: ${muscleNames()}. Separate multiple values with | or ;."
        ),
        Column("secondary_muscles", "Same syntax as primary_muscles."),
        Column("personal_notes", "Freeform notes only you will see.")
    )

    /** Suffixes appended to a training-phase prefix, e.g. "strength_focus_sets". */
    val phaseColumnSuffixes: List<String> = listOf("sets", "reps", "rest", "notes")

    /** Header key used for a given training phase + suffix, matching [ExerciseCsvImporter]. */
    fun phaseHeader(phase: TrainingPhase, suffix: String): String = "${phase.name.lowercase()}_$suffix"

    private fun phaseColumns(): List<Column> =
        TrainingPhase.entries.flatMap { phase ->
            val prefix = phase.name.lowercase()
            listOf(
                Column("${prefix}_sets", "Sets override for the ${phase.displayName} phase."),
                Column("${prefix}_reps", "Reps override for the ${phase.displayName} phase."),
                Column("${prefix}_rest", "Rest-seconds override for the ${phase.displayName} phase."),
                Column("${prefix}_notes", "Notes override for the ${phase.displayName} phase.")
            )
        }

    /** Every column the importer recognizes, in template column order. */
    val allColumns: List<Column> = coreColumns + phaseColumns()

    /** Normalized header keys in template column order. */
    val headers: List<String> = allColumns.map { it.header }

    /** Header keys that must be present in the CSV for it to be importable at all. */
    val requiredHeaders: List<String> = allColumns.filter { it.required }.map { it.header }

    private val knownHeaders: Set<String> = headers.toSet()

    /** Headers present in [csvHeaders] that are missing from [requiredHeaders]. */
    fun missingRequiredHeaders(csvHeaders: Collection<String>): List<String> {
        val present = csvHeaders.toSet()
        return requiredHeaders.filterNot { it in present }
    }

    /** Headers present in [csvHeaders] that this schema does not recognize. */
    fun unknownHeaders(csvHeaders: Collection<String>): List<String> =
        csvHeaders.filterNot { it in knownHeaders }.distinct()

    /** Short prose summary shown inline in the Exercise Library UI. */
    fun helpSummary(): String =
        "CSV columns supported: ${coreColumns.joinToString(", ") { it.header }}, plus optional phase " +
            "columns like ${phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "sets")} / " +
            "${phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "reps")} / " +
            "${phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "rest")} / " +
            "${phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "notes")} for each training phase " +
            "(${TrainingPhase.entries.joinToString(", ") { it.name.lowercase() }}). Multi-value columns " +
            "accept | ; or , as separators. Booleans accept $booleanSyntaxHelp."

    private fun categoryNames(): String =
        com.example.workoutapp.data.model.WorkoutCategory.rotationCategories().joinToString(", ") { it.displayName }

    private fun difficultyNames(): String =
        com.example.workoutapp.data.model.Difficulty.entries.joinToString(", ") { it.displayName }

    private fun muscleNames(): String =
        com.example.workoutapp.data.model.MuscleGroup.entries.joinToString(", ") { it.displayName }
}
