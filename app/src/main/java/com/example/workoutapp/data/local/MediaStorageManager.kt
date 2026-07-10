package com.example.workoutapp.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.workoutapp.data.model.Exercise
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages local media storage for exercises.
 * Tracks usage, computes sizes, and suggests cleanup.
 */
@Singleton
class MediaStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compute total storage used by local media across all exercises.
     * Returns size in bytes.
     */
    suspend fun computeTotalStorageBytes(exercises: List<Exercise>): Long = withContext(Dispatchers.IO) {
        var total = 0L
        for (exercise in exercises) {
            total += computeExerciseMediaBytes(exercise)
        }
        total
    }

    /**
     * Compute storage used by a single exercise's local media.
     */
    suspend fun computeExerciseMediaBytes(exercise: Exercise): Long = withContext(Dispatchers.IO) {
        val uris = parseUriList(exercise.localMediaUris)
        uris.sumOf { uri -> getUriSizeBytes(uri) }
    }

    /**
     * Get usage details per exercise for the storage management UI.
     */
    suspend fun getStorageBreakdown(exercises: List<Exercise>): List<ExerciseStorageInfo> =
        withContext(Dispatchers.IO) {
            exercises.mapNotNull { exercise ->
                val uris = parseUriList(exercise.localMediaUris)
                if (uris.isEmpty()) return@mapNotNull null
                val totalBytes = uris.sumOf { getUriSizeBytes(it) }
                ExerciseStorageInfo(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    localMediaCount = uris.size,
                    totalBytes = totalBytes,
                    hasExternalUrls = parseUrlList(exercise.externalMediaUrls).isNotEmpty()
                )
            }.sortedByDescending { it.totalBytes }
        }

    /**
     * Suggest exercises that could have their local media offloaded to external URLs.
     * Returns exercises with local media that could be replaced with external links.
     */
    suspend fun getOffloadCandidates(
        exercises: List<Exercise>,
        minBytes: Long = 1_000_000 // 1 MB minimum
    ): List<ExerciseStorageInfo> = withContext(Dispatchers.IO) {
        getStorageBreakdown(exercises).filter { info ->
            info.totalBytes >= minBytes && !info.hasExternalUrls
        }
    }

    /**
     * Get exercises with unused local media (never performed, could be cleaned up).
     */
    suspend fun getCleanupCandidates(
        exercises: List<Exercise>,
        minDaysUnused: Int = 90
    ): List<ExerciseStorageInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val threshold = now - minDaysUnused.toLong() * 24 * 3600_000L

        getStorageBreakdown(exercises).filter { info ->
            val exercise = exercises.find { it.id == info.exerciseId } ?: return@filter false
            val lastUsed = exercise.lastPerformedAt ?: exercise.createdAt
            lastUsed < threshold
        }
    }

    /**
     * Count total local media files across all exercises.
     */
    fun countLocalMediaFiles(exercises: List<Exercise>): Int {
        return exercises.sumOf { parseUriList(it.localMediaUris).size }
    }

    /**
     * Count external URLs across all exercises.
     */
    fun countExternalUrls(exercises: List<Exercise>): Int {
        return exercises.sumOf { parseUrlList(it.externalMediaUrls).size }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun parseUriList(jsonArray: String): List<Uri> {
        return try {
            val list = json.decodeFromString<List<String>>(jsonArray)
            list.mapNotNull { s ->
                try { Uri.parse(s) } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseUrlList(jsonArray: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(jsonArray)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getUriSizeBytes(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

/**
 * Storage info for a single exercise.
 */
data class ExerciseStorageInfo(
    val exerciseId: Long,
    val exerciseName: String,
    val localMediaCount: Int,
    val totalBytes: Long,
    val hasExternalUrls: Boolean
) {
    val formattedSize: String
        get() = formatBytes(totalBytes)

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
