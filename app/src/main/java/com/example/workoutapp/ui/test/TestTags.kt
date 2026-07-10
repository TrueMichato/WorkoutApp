package com.example.workoutapp.ui.test

import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.ui.exercises.ExerciseLibraryFilter

object TestTags {
    object Exercises {
        const val Screen = "exercises_screen"
        const val SearchField = "exercises_search_field"
        const val AddFab = "exercises_add_fab"
        const val ImportButton = "exercises_import_button"
        const val FilterButton = "exercises_filter_button"

        fun libraryFilter(filter: ExerciseLibraryFilter): String =
            "exercises_filter_${filter.name.lowercase()}"

        fun exerciseCard(exerciseId: Long): String =
            "exercises_card_$exerciseId"
    }

    object AddEditExercise {
        const val Screen = "add_edit_exercise_screen"
        const val NameField = "add_edit_exercise_name"
        const val DescriptionField = "add_edit_exercise_description"
        const val InstructionsField = "add_edit_exercise_instructions"
        const val TipsField = "add_edit_exercise_tips"
        const val SaveButton = "add_edit_exercise_save"
        const val PersonalNotesField = "add_edit_exercise_personal_notes"

        fun categoryChip(category: WorkoutCategory): String =
            "add_edit_exercise_category_${category.name.lowercase()}"

        fun presetPhaseChip(phase: TrainingPhase): String =
            "add_edit_exercise_preset_phase_${phase.name.lowercase()}"
    }

    object Workout {
        const val Screen = "workout_screen"
        const val NewPlanButton = "workout_new_plan_button"
    }

    object WorkoutPlanEditor {
        const val Screen = "workout_plan_editor_screen"
        const val NameField = "workout_plan_editor_name"
        const val DescriptionField = "workout_plan_editor_description"
        const val NotesField = "workout_plan_editor_notes"
        const val AddExerciseButton = "workout_plan_editor_add_exercise"
        const val SaveButton = "workout_plan_editor_save"
        const val SaveAndPlayButton = "workout_plan_editor_save_and_play"
        const val ExerciseSearchField = "workout_plan_editor_exercise_search"

        fun sectionChip(section: PlanExerciseSection): String =
            "workout_plan_editor_section_${section.name.lowercase()}"
    }
}
