package com.example.workoutapp.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Codec for persisting the workout generator's category selection into a SavedStateHandle-friendly
 * String, so an in-progress setup (selected focus categories) survives configuration changes and
 * process death without needing a Parcelable/custom Bundle entry per category.
 */
private val generatorCategorySelectionJson = Json { ignoreUnknownKeys = true }

fun encodeGeneratorCategorySelection(categories: Set<WorkoutCategory>): String =
    generatorCategorySelectionJson.encodeToString(categories.map { it.name })

fun decodeGeneratorCategorySelection(encoded: String?): Set<WorkoutCategory> {
    if (encoded.isNullOrBlank()) return emptySet()
    return runCatching {
        generatorCategorySelectionJson.decodeFromString<List<String>>(encoded)
            .mapNotNull { name -> WorkoutCategory.entries.firstOrNull { it.name == name } }
            .toSet()
    }.getOrDefault(emptySet())
}
