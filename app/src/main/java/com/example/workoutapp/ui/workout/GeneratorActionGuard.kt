package com.example.workoutapp.ui.workout

/**
 * Single source of truth for whether the generator's Preview/Generate/Save-as-plan actions may be
 * dispatched right now. Used both to disable the buttons in [WorkoutGeneratorScreen] and as a
 * defensive guard inside [WorkoutViewModel] so a double-tap that lands before recomposition
 * disables the button can't fire the same network/DB action twice.
 */
fun canDispatchGeneratorAction(isGenerating: Boolean, isPreviewing: Boolean, isSavingPlan: Boolean): Boolean =
    !isGenerating && !isPreviewing && !isSavingPlan
