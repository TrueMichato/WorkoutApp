package com.example.workoutapp.data.repository

import com.example.workoutapp.data.model.Equipment

/**
 * Typed outcome of attempting to create or update a custom [Equipment] row, so every caller
 * (dialogs, view models, CSV import) handles validation failures the same way instead of each
 * re-implementing ad-hoc blank/duplicate checks.
 */
sealed class EquipmentSaveResult {
    data class Success(val equipment: Equipment) : EquipmentSaveResult()
    data class Failure(val error: EquipmentValidationError) : EquipmentSaveResult()
}

/**
 * Validation failures for equipment creation/rename. [message] is safe to show directly to users.
 */
sealed class EquipmentValidationError(val message: String) {
    data object BlankName : EquipmentValidationError("Equipment name is required.")

    data class NameTooLong(val maxLength: Int) :
        EquipmentValidationError("Equipment name must be $maxLength characters or fewer.")

    data class DuplicateName(val existingName: String) :
        EquipmentValidationError("\"$existingName\" already exists. Choose a different name.")

    data object PersistFailed :
        EquipmentValidationError("Could not save the equipment. Please try again.")
}

/** Typed outcome of attempting to delete a custom [Equipment] row. */
sealed class EquipmentDeletionResult {
    data object Success : EquipmentDeletionResult()
    data class Failure(val error: EquipmentDeletionError) : EquipmentDeletionResult()
}

/**
 * Deletion failures. [message] is safe to show directly to users. Built-in equipment can never be
 * removed, and custom equipment still referenced by an exercise or a location is refused rather
 * than silently detached, to avoid unexpectedly changing a user's saved exercises/locations.
 */
sealed class EquipmentDeletionError(val message: String) {
    data object NotCustom : EquipmentDeletionError("Built-in equipment can't be removed.")

    data object NotFound : EquipmentDeletionError("This equipment no longer exists.")

    data class InUse(val exerciseCount: Int, val locationCount: Int) : EquipmentDeletionError(
        buildString {
            append("Can't remove this equipment: it's used by ")
            val parts = mutableListOf<String>()
            if (exerciseCount > 0) parts += "$exerciseCount exercise" + if (exerciseCount != 1) "s" else ""
            if (locationCount > 0) parts += "$locationCount location" + if (locationCount != 1) "s" else ""
            append(parts.joinToString(" and "))
            append(". Remove it from those first.")
        }
    )

    /** An unexpected persistence error (e.g. a DB exception) rather than a validation outcome. */
    data object UnexpectedError :
        EquipmentDeletionError("Something went wrong while removing this equipment. Please try again.")
}
