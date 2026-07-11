package com.example.workoutapp.ui.test

import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.ui.exercises.ExerciseLibraryFilter

object TestTags {
    object BottomNav {
        const val Dashboard = "bottom_nav_dashboard"
        const val Exercises = "bottom_nav_exercises"
        const val Workout = "bottom_nav_workout"
        const val PhysicalTherapy = "bottom_nav_physical_therapy"
        const val Settings = "bottom_nav_settings"

        fun item(route: String): String = "bottom_nav_$route"
    }

    object Dashboard {
        const val Screen = "dashboard_screen"
        const val ContentList = "dashboard_content"
        const val BalanceBaselineCard = "dashboard_balance_baseline_card"
        const val BalanceScoreCard = "dashboard_balance_score_card"
        const val GenerateWorkoutButton = "dashboard_generate_workout_button"
    }

    object Exercises {
        const val Screen = "exercises_screen"
        const val SearchField = "exercises_search_field"
        const val AddFab = "exercises_add_fab"
        const val ImportButton = "exercises_import_button"
        const val SaveTemplateButton = "exercises_save_template_button"
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
        const val FamilyParentPickerButton = "add_edit_exercise_family_parent_picker"
        const val FamilyFocusField = "add_edit_exercise_family_focus"
        const val FamilyDetachButton = "add_edit_exercise_family_detach"
        const val FamilyClearParentButton = "add_edit_exercise_family_clear_parent"

        fun categoryChip(category: WorkoutCategory): String =
            "add_edit_exercise_category_${category.name.lowercase()}"

        fun presetPhaseChip(phase: TrainingPhase): String =
            "add_edit_exercise_preset_phase_${phase.name.lowercase()}"

        fun familyParentOption(exerciseId: Long): String =
            "add_edit_exercise_family_parent_option_$exerciseId"
    }

    object Equipment {
        const val Screen = "equipment_management_screen"
        const val AddButton = "equipment_add_button"
        const val NameField = "equipment_name_field"
        const val CreateButton = "equipment_create_button"
        const val ErrorText = "equipment_error_text"

        fun row(equipmentId: Long): String = "equipment_row_$equipmentId"
    }

    object Workout {
        const val Screen = "workout_screen"
        const val NewPlanButton = "workout_new_plan_button"
        const val NewWorkoutFab = "workout_new_workout_fab"
    }

    object WorkoutGenerator {
        const val Screen = "workout_generator_screen"
        const val ContentList = "workout_generator_content"
        const val SummaryCard = "workout_generator_summary_card"
        const val PrimaryActionButton = "workout_generator_primary_action_button"
        const val AdvancedToggle = "workout_generator_advanced_toggle"
        const val PreviewButton = "workout_generator_preview_button"
        const val ResetDefaultsButton = "workout_generator_reset_defaults_button"
        const val ErrorRetryButton = "workout_generator_error_retry_button"
        const val ErrorDismissButton = "workout_generator_error_dismiss_button"
        const val EditSetupButton = "workout_generator_edit_setup_button"
        const val PreviewCard = "workout_generator_preview_card"
        const val FamilyDedupToggle = "workout_generator_family_dedup_toggle"
    }

    object WorkoutPlanEditor {
        const val Screen = "workout_plan_editor_screen"
        const val ContentList = "workout_plan_editor_content"
        const val NameField = "workout_plan_editor_name"
        const val DescriptionField = "workout_plan_editor_description"
        const val NotesField = "workout_plan_editor_notes"
        const val AddExerciseButton = "workout_plan_editor_add_exercise"
        const val SaveButton = "workout_plan_editor_save"
        const val SaveAndPlayButton = "workout_plan_editor_save_and_play"
        const val ExerciseSearchField = "workout_plan_editor_exercise_search"

        fun exerciseOption(exerciseId: Long): String =
            "workout_plan_editor_exercise_$exerciseId"

        fun sectionChip(section: PlanExerciseSection): String =
            "workout_plan_editor_section_${section.name.lowercase()}"
    }

    object ActiveWorkout {
        const val Screen = "active_workout_screen"
        const val ContentList = "active_workout_content"
        const val CurrentExerciseCard = "active_workout_current_exercise_card"
        const val DoneButton = "active_workout_done_button"
        const val SkipButton = "active_workout_skip_button"
        const val RepsField = "active_workout_reps_field"
        const val WeightField = "active_workout_weight_field"
        const val DurationField = "active_workout_duration_field"
        const val RpeField = "active_workout_rpe_field"
        const val NotesField = "active_workout_notes_field"
        const val SaveSetButton = "active_workout_save_set_button"
        const val RepeatLastSetButton = "active_workout_repeat_last_set_button"
        const val CompleteWorkoutButton = "active_workout_complete_button"

        fun upcomingRow(sessionExerciseId: Long): String =
            "active_workout_upcoming_row_$sessionExerciseId"
    }
}
