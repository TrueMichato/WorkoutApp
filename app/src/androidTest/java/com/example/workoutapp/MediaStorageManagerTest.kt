package com.example.workoutapp

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workoutapp.data.local.MediaStorageManager
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.persistedJson
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStorageManagerTest {

    @Test
    fun copyIntoExerciseMedia_createsOwnedRestorableFileUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = MediaStorageManager(context)
        val source = File(context.cacheDir, "selected-media.txt").apply {
            writeText("demo media")
        }

        val copied = manager.copyIntoExerciseMedia(listOf(source.toUri())).single()
        val copiedFile = File(requireNotNull(copied.path))

        assertTrue(manager.isOwnedExerciseMedia(copied))
        assertTrue(copiedFile.isFile)
        assertEquals("demo media", copiedFile.readText())

        manager.deleteOwnedMediaFiles(listOf(copied))
        source.delete()
    }

    @Test
    fun deleteUnreferencedMedia_keepsFilesStillReferencedByAnotherExercise() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = MediaStorageManager(context)
        val source = File(context.cacheDir, "shared-media.txt").apply {
            writeText("shared media")
        }
        val copied = manager.copyIntoExerciseMedia(listOf(source.toUri())).single()
        val referencedExercise = Exercise(
            id = 2,
            name = "Referenced",
            localMediaUris = persistedJson.encodeToString(listOf(copied.toString()))
        )

        val deleted = manager.deleteUnreferencedMedia(listOf(copied), listOf(referencedExercise))

        assertEquals(0, deleted)
        assertTrue(File(requireNotNull(copied.path)).exists())

        manager.deleteUnreferencedMedia(listOf(copied), emptyList())
        assertFalse(File(requireNotNull(copied.path)).exists())
        source.delete()
    }
}
