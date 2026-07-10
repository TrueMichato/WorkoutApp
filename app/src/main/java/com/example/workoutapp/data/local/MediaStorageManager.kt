package com.example.workoutapp.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.PersistedDataDecodeException
import com.example.workoutapp.data.model.PersistedJsonIssue
import com.example.workoutapp.data.model.PersistedJsonResult
import com.example.workoutapp.data.model.decodePersistedStringList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
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
    private val mediaDirectory: File
        get() = File(context.filesDir, MEDIA_DIRECTORY).apply { mkdirs() }

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
        val uris = decodeLocalMediaUrisStrict(exercise.localMediaUris)
        uris.sumOf { uri -> getUriSizeBytes(uri) }
    }

    /**
     * Get usage details per exercise for the storage management UI.
     */
    suspend fun getStorageBreakdown(exercises: List<Exercise>): List<ExerciseStorageInfo> =
        withContext(Dispatchers.IO) {
            exercises.mapNotNull { exercise ->
                val uris = decodeLocalMediaUrisStrict(exercise.localMediaUris)
                if (uris.isEmpty()) return@mapNotNull null
                val totalBytes = uris.sumOf { getUriSizeBytes(it) }
                ExerciseStorageInfo(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    localMediaCount = uris.size,
                    totalBytes = totalBytes,
                    hasExternalUrls = decodeExternalUrlsStrict(exercise.externalMediaUrls).isNotEmpty()
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
        return exercises.sumOf { decodeLocalMediaUrisStrict(it.localMediaUris).size }
    }

    /**
     * Count external URLs across all exercises.
     */
    fun countExternalUrls(exercises: List<Exercise>): Int {
        return exercises.sumOf { decodeExternalUrlsStrict(it.externalMediaUrls).size }
    }

    suspend fun copyIntoExerciseMedia(sourceUris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        sourceUris.map { uri ->
            if (isOwnedExerciseMedia(uri)) {
                uri
            } else {
                copyOne(uri)
            }
        }
    }

    suspend fun deleteUnreferencedMedia(
        candidateUris: Collection<Uri>,
        allExercises: List<Exercise>
    ): Int = withContext(Dispatchers.IO) {
        val referenced = allExercises
            .flatMap { decodeLocalMediaUris(it.localMediaUris).value }
            .map { it.toString() }
            .toSet()

        candidateUris
            .filter { isOwnedExerciseMedia(it) }
            .filterNot { it.toString() in referenced }
            .mapNotNull { it.path?.let(::File) }
            .distinctBy { it.canonicalPath }
            .count { file ->
                file.isFile && file.delete()
            }
    }

    suspend fun deleteOwnedMediaFiles(candidateUris: Collection<Uri>): Int = withContext(Dispatchers.IO) {
        candidateUris
            .filter { isOwnedExerciseMedia(it) }
            .mapNotNull { it.path?.let(::File) }
            .distinctBy { it.canonicalPath }
            .count { file -> file.isFile && file.delete() }
    }

    fun decodeLocalMediaUris(rawJson: String): PersistedJsonResult<List<Uri>> {
        val decoded = decodePersistedStringList("local media URIs", rawJson)
        val issues = decoded.issues.toMutableList()
        val uris = decoded.value.mapNotNull { raw ->
            val uri = raw.toUri()
            if (uri.scheme.isNullOrBlank()) {
                issues += PersistedJsonIssue(
                    fieldName = "local media URIs",
                    rawValue = raw,
                    message = "Saved local media URI is missing a scheme and cannot be opened: $raw"
                )
                null
            } else {
                if (!isOwnedExerciseMedia(uri)) {
                    issues += PersistedJsonIssue(
                        fieldName = "local media URIs",
                        rawValue = raw,
                        message = "Saved local media is outside WorkoutApp storage and may not survive restore. Edit and save the exercise to import it."
                    )
                }
                uri
            }
        }
        if (issues.any { it.rawValue == rawJson }) {
            throw PersistedDataDecodeException(
                fieldName = "local media URIs",
                rawValue = rawJson,
                cause = IllegalArgumentException(issues.joinToString("\n") { it.message })
            )
        }
        return PersistedJsonResult(uris, issues)
    }

    fun decodeExternalUrls(rawJson: String): PersistedJsonResult<List<String>> =
        decodePersistedStringList("external media URLs", rawJson)

    fun isOwnedExerciseMedia(uri: Uri): Boolean {
        val path = uri.path ?: return false
        val file = File(path)
        return try {
            uri.scheme == "file" && file.canonicalPath.startsWith(mediaDirectory.canonicalPath + File.separator)
        } catch (_: IOException) {
            false
        }
    }

    private fun copyOne(sourceUri: Uri): Uri {
        val extension = sourceUri.extensionForCopy()
        val destination = File(mediaDirectory, "${UUID.randomUUID()}$extension")
        try {
            openInputStream(sourceUri).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            destination.delete()
            throw MediaStorageException("Could not copy selected media into app storage.", e)
        } catch (e: SecurityException) {
            destination.delete()
            throw MediaStorageException("WorkoutApp does not have permission to read the selected media.", e)
        }
        return destination.toUri()
    }

    private fun openInputStream(sourceUri: Uri) =
        if (sourceUri.scheme == "file") {
            val path = sourceUri.path ?: throw MediaStorageException("Selected media file path is invalid.")
            FileInputStream(File(path))
        } else {
            context.contentResolver.openInputStream(sourceUri)
                ?: throw MediaStorageException("Selected media could not be opened.")
        }

    private fun Uri.extensionForCopy(): String {
        val name = if (scheme == "file") {
            path?.substringAfterLast(File.separatorChar)
        } else {
            queryDisplayName()
        }
        val displayNameExtension = name?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() && it.length <= 8 }
        val mimeExtension = if (scheme == "file") {
            null
        } else {
            context.contentResolver.getType(this)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        }
        return (displayNameExtension ?: mimeExtension)?.let { ".$it" } ?: ""
    }

    private fun Uri.queryDisplayName(): String? =
        context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }

    private fun getUriSizeBytes(uri: Uri): Long {
        if (isOwnedExerciseMedia(uri)) {
            val file = File(requireNotNull(uri.path))
            if (!file.isFile) {
                throw MediaStorageException("Saved local media is missing from app storage: $uri")
            }
            return file.length()
        }

        try {
            return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                } else 0L
            } ?: 0L
        } catch (e: SecurityException) {
            throw MediaStorageException("WorkoutApp no longer has permission to read saved media: $uri", e)
        } catch (e: IllegalArgumentException) {
            throw MediaStorageException("Saved local media cannot be opened: $uri", e)
        }
    }

    private fun decodeLocalMediaUrisStrict(rawJson: String): List<Uri> {
        val result = decodeLocalMediaUris(rawJson)
        if (result.hasIssues) {
            throw PersistedDataDecodeException(
                fieldName = "local media URIs",
                rawValue = rawJson,
                cause = IllegalArgumentException(result.issues.joinToString("\n") { it.message })
            )
        }
        return result.value
    }

    private fun decodeExternalUrlsStrict(rawJson: String): List<String> {
        val result = decodeExternalUrls(rawJson)
        if (result.hasIssues) {
            throw PersistedDataDecodeException(
                fieldName = "external media URLs",
                rawValue = rawJson,
                cause = IllegalArgumentException(result.issues.joinToString("\n") { it.message })
            )
        }
        return result.value
    }

    companion object {
        const val MEDIA_DIRECTORY = "exercise_media"
    }
}

class MediaStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

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
