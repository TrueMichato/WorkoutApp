package com.example.workoutapp.data.csv

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExerciseCsvTemplateExporterTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var exporter: ExerciseCsvTemplateExporter
    private val uri: Uri = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = mockk()
        contentResolver = mockk()
        every { context.contentResolver } returns contentResolver
        exporter = ExerciseCsvTemplateExporter(context)
    }

    @Test
    fun saveTemplate_writesExactTemplateBytesToTheChosenDestination() = runTest {
        val outputStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri) } returns outputStream

        val result = exporter.saveTemplate(uri)

        assertTrue(result.isSuccess)
        assertEquals(ExerciseCsvTemplate.render(), outputStream.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun saveTemplate_failsGracefullyWhenDestinationCannotBeOpened() = runTest {
        every { contentResolver.openOutputStream(uri) } returns null

        val result = exporter.saveTemplate(uri)

        assertTrue(result.isFailure)
    }
}
