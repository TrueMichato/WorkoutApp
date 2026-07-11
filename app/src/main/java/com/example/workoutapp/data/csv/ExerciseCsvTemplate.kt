package com.example.workoutapp.data.csv

import com.example.workoutapp.data.model.TrainingPhase

/**
 * Builds the downloadable example/template CSV for exercise import.
 *
 * Headers always come from [ExerciseCsvSchema.headers] so the file can never drift from the
 * columns [ExerciseCsvImporter] actually understands. Only the sample row values live here.
 */
object ExerciseCsvTemplate {

    /** File name suggested when the user saves the template via Storage Access Framework. */
    const val suggestedFileName = "exercise_import_template.csv"

    /** Renders the full example CSV, including header row, as UTF-8 text. */
    fun render(): String {
        val rows = listOf(ExerciseCsvSchema.headers) + sampleRows().map { row ->
            ExerciseCsvSchema.headers.map { header -> row[header].orEmpty() }
        }
        return rows.joinToString("\r\n") { ExerciseCsvParser.writeLine(it) } + "\r\n"
    }

    /**
     * Three illustrative rows keyed by normalized header:
     * - a simple bodyweight strength exercise using plain values and the "|" delimiter
     * - a richer, timed carry exercise demonstrating quoted commas, alternate boolean spellings,
     *   ";" and "," delimiters, escaped quotes, and multiple training-phase overrides
     * - a variation row linking back to the first row's exercise by name via main_exercise,
     *   demonstrating the exercise-family columns
     */
    private fun sampleRows(): List<Map<String, String>> = listOf(
        mapOf(
            "name" to "Push-Up",
            "categories" to "Strength|Hypertrophy",
            "description" to "Classic bodyweight press for chest, shoulders, and triceps.",
            "instructions" to "Hands under shoulders, keep a straight line from head to heel, lower your chest to the floor, then press back up.",
            "tips" to "Squeeze the glutes and brace the core so the hips don't sag.",
            "difficulty" to "Beginner",
            "is_compound" to "true",
            "is_unilateral" to "false",
            "default_sets" to "3",
            "default_reps" to "8-12",
            "default_rest_seconds" to "60",
            "estimated_duration_seconds" to "180",
            "equipment" to "",
            "primary_muscles" to "Chest|Triceps",
            "secondary_muscles" to "Front Delts",
            "personal_notes" to "",
            "main_exercise" to "",
            "variation_focus" to "",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "reps") to "10-15"
        ),
        mapOf(
            "name" to "Farmer's Walk",
            "categories" to "Functional,Strength",
            "description" to "Loaded carry that builds grip, trunk stability, and total-body conditioning.",
            "instructions" to "Grip the handles, brace your core, stand tall, and walk with ${'"'}proud${'"'} posture for the target distance.",
            "tips" to "Keep shoulders back; don't let the load pull you into a lean.",
            "difficulty" to "Intermediate",
            "is_compound" to "yes",
            "is_unilateral" to "no",
            "default_sets" to "",
            "default_reps" to "30-45s carry",
            "default_rest_seconds" to "",
            "estimated_duration_seconds" to "240",
            "equipment" to "Kettlebell, Sandbag",
            "primary_muscles" to "Forearms;Grip",
            "secondary_muscles" to "Abs|Glutes",
            "personal_notes" to "",
            "main_exercise" to "",
            "variation_focus" to "",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.STRENGTH_FOCUS, "sets") to "4",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.STRENGTH_FOCUS, "reps") to "20m carry",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.STRENGTH_FOCUS, "rest") to "120",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.STRENGTH_FOCUS, "notes") to "Heavy load, controlled pace.",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "sets") to "3",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "reps") to "30-40m carry",
            ExerciseCsvSchema.phaseHeader(TrainingPhase.HYPERTROPHY_FOCUS, "rest") to "90"
        ),
        mapOf(
            "name" to "Diamond Push-Up",
            "categories" to "Strength|Hypertrophy",
            "description" to "Push-Up variation with hands close together to shift emphasis onto the triceps.",
            "instructions" to "Same setup as a standard push-up, but form a diamond/triangle with your thumbs and index fingers under your chest.",
            "tips" to "Keep elbows tucked close to the body throughout the movement.",
            "difficulty" to "Intermediate",
            "is_compound" to "true",
            "is_unilateral" to "false",
            "default_sets" to "3",
            "default_reps" to "6-10",
            "default_rest_seconds" to "60",
            "estimated_duration_seconds" to "180",
            "equipment" to "",
            "primary_muscles" to "Triceps|Chest",
            "secondary_muscles" to "Front Delts",
            "personal_notes" to "",
            "main_exercise" to "Push-Up",
            "variation_focus" to "Triceps emphasis"
        )
    )
}
