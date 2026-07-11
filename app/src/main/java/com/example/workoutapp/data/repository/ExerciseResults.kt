package com.example.workoutapp.data.repository

import com.example.workoutapp.data.model.Exercise

/**
 * Typed outcome of atomically saving an exercise (create or update) together with its
 * categories/equipment/muscles and its exercise-family mutation, so a rejected family change
 * (self-link, missing/invalid parent, invalid nesting) never leaves a partially-written exercise
 * row or relation set - see [ExerciseRepository.saveExerciseWithFamily].
 */
sealed class ExerciseSaveResult {
    data class Success(val exerciseId: Long) : ExerciseSaveResult()
    data class Failure(val error: ExerciseSaveError) : ExerciseSaveResult()
}

/** Validation/persistence failures for [ExerciseRepository.saveExerciseWithFamily]. [message] is safe to show directly to users. */
sealed class ExerciseSaveError(val message: String) {
    data object ExerciseNotFound :
        ExerciseSaveError("Exercise no longer exists. Your changes were not saved.")

    data object SelfLink :
        ExerciseSaveError("An exercise cannot be a variation of itself.")

    data object ParentNotFound :
        ExerciseSaveError("The selected main exercise no longer exists.")

    data class ParentIsAlreadyVariation(val parentName: String) : ExerciseSaveError(
        "\"$parentName\" is already a variation of another exercise, so it can't also be a main exercise."
    )

    data class ExerciseHasOwnVariations(val exerciseName: String) : ExerciseSaveError(
        "\"$exerciseName\" already has its own variations, so it can't also be a variation."
    )

    data object PersistFailed :
        ExerciseSaveError("Could not save the exercise. Please try again.")
}

/** Typed outcome of linking an already-saved exercise as a variation of another. */
sealed class ExerciseFamilyLinkResult {
    data object Success : ExerciseFamilyLinkResult()
    data class Failure(val error: ExerciseFamilyLinkError) : ExerciseFamilyLinkResult()
}

/** Failures for [ExerciseRepository.linkVariationResult]. [message] is safe to show directly to users. */
sealed class ExerciseFamilyLinkError(val message: String) {
    data object VariationNotFound :
        ExerciseFamilyLinkError("The selected variation exercise no longer exists.")

    data object SelfLink :
        ExerciseFamilyLinkError("An exercise cannot be a variation of itself.")

    data object ParentNotFound :
        ExerciseFamilyLinkError("The selected main exercise no longer exists.")

    data class ParentIsAlreadyVariation(val parentName: String) : ExerciseFamilyLinkError(
        "\"$parentName\" is already a variation of another exercise, so it can't also be a main exercise."
    )

    data class ExerciseHasOwnVariations(val exerciseName: String) : ExerciseFamilyLinkError(
        "\"$exerciseName\" already has its own variations, so it can't also be a variation."
    )

    data object AlreadyLinkedElsewhere :
        ExerciseFamilyLinkError("This exercise is already a variation of another exercise. Detach it first.")

    data object PersistFailed :
        ExerciseFamilyLinkError("Could not save the exercise family link. Please try again.")
}

/**
 * A main exercise together with all of its linked variations. [variations] always includes
 * [root] itself when [root] happens to be a variation exercise being viewed from its own
 * perspective (see [ExerciseRepository.getFamily]), so callers should filter it out by id when
 * rendering "other variations" lists.
 */
data class ExerciseFamily(
    val root: Exercise,
    val variations: List<ExerciseVariationMember>
)

data class ExerciseVariationMember(
    val exercise: Exercise,
    val focus: String
)
