package com.example.workoutapp.data.model

/**
 * Muscle groups for exercise targeting and balance tracking
 */
enum class MuscleGroup(
    val displayName: String,
    val bodyRegion: BodyRegion
) {
    // Upper body - Push
    CHEST("Chest", BodyRegion.UPPER_PUSH),
    FRONT_DELTS("Front Delts", BodyRegion.UPPER_PUSH),
    SIDE_DELTS("Side Delts", BodyRegion.UPPER_PUSH),
    TRICEPS("Triceps", BodyRegion.UPPER_PUSH),

    // Upper body - Pull
    UPPER_BACK("Upper Back", BodyRegion.UPPER_PULL),
    LATS("Lats", BodyRegion.UPPER_PULL),
    REAR_DELTS("Rear Delts", BodyRegion.UPPER_PULL),
    BICEPS("Biceps", BodyRegion.UPPER_PULL),
    FOREARMS("Forearms", BodyRegion.UPPER_PULL),

    // Core
    ABS("Abs", BodyRegion.CORE),
    OBLIQUES("Obliques", BodyRegion.CORE),
    LOWER_BACK("Lower Back", BodyRegion.CORE),

    // Lower body
    QUADS("Quads", BodyRegion.LOWER),
    HAMSTRINGS("Hamstrings", BodyRegion.LOWER),
    GLUTES("Glutes", BodyRegion.LOWER),
    CALVES("Calves", BodyRegion.LOWER),
    HIP_FLEXORS("Hip Flexors", BodyRegion.LOWER),
    ADDUCTORS("Adductors", BodyRegion.LOWER),
    ABDUCTORS("Abductors", BodyRegion.LOWER),

    // Full body / Other
    FULL_BODY("Full Body", BodyRegion.FULL_BODY),
    NECK("Neck", BodyRegion.OTHER),
    GRIP("Grip", BodyRegion.OTHER);

    companion object {
        fun byRegion(region: BodyRegion): List<MuscleGroup> =
            entries.filter { it.bodyRegion == region }
    }
}

enum class BodyRegion(val displayName: String) {
    UPPER_PUSH("Upper Body - Push"),
    UPPER_PULL("Upper Body - Pull"),
    CORE("Core"),
    LOWER("Lower Body"),
    FULL_BODY("Full Body"),
    OTHER("Other")
}

