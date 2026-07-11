package com.example.workoutapp.data.csv

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the [ExerciseCsvTemplate] example CSV to a destination the user picked via the
 * Storage Access Framework (`ActivityResultContracts.CreateDocument`). This never requests
 * broad storage permissions: the system picker grants access only to the single chosen file,
 * and that file is only created/overwritten because the user explicitly selected it.
 */
@Singleton
class ExerciseCsvTemplateExporter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun saveTemplate(uri: Uri): Result<Unit> = runCatching {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Unable to open the selected destination for writing.")
        outputStream.use { stream ->
            stream.write(ExerciseCsvTemplate.render().toByteArray(Charsets.UTF_8))
        }
    }
}
