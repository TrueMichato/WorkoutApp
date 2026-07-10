package com.example.workoutapp.data.model

/**
 * Exercise difficulty levels for filtering and progression tracking
 */
enum class Difficulty(
    val displayName: String,
    val level: Int,
    val description: String
) {
    BEGINNER(
        displayName = "Beginner",
        level = 1,
        description = "Suitable for those new to exercise or this movement pattern"
    ),
    NOVICE(
        displayName = "Novice",
        level = 2,
        description = "Some experience required, basic movement competency needed"
    ),
    INTERMEDIATE(
        displayName = "Intermediate",
        level = 3,
        description = "Solid foundation required, moderate complexity"
    ),
    ADVANCED(
        displayName = "Advanced",
        level = 4,
        description = "High skill and strength requirements"
    ),
    ELITE(
        displayName = "Elite",
        level = 5,
        description = "Expert level, requires years of dedicated training"
    );

    companion object {
        fun fromLevel(level: Int): Difficulty = entries.find { it.level == level } ?: INTERMEDIATE
    }
}

