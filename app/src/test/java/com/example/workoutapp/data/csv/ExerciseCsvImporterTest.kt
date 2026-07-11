package com.example.workoutapp.data.csv

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.decodeStoredProgrammingPresets
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.EquipmentSaveResult
import com.example.workoutapp.data.repository.ExerciseFamilyLinkResult
import com.example.workoutapp.data.repository.ExerciseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ExerciseCsvImporter] coverage proving the canonical [ExerciseCsvSchema] and the generated
 * [ExerciseCsvTemplate] cannot silently drift apart, and that preflight header validation is
 * both actionable and side-effect free.
 */
class ExerciseCsvImporterTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var equipmentRepository: EquipmentRepository
    private lateinit var importer: ExerciseCsvImporter
    private val uri: Uri = mockk(relaxed = true)
    private var nextExerciseId = 1L

    @Before
    fun setUp() {
        context = mockk()
        contentResolver = mockk()
        every { context.contentResolver } returns contentResolver

        exerciseRepository = mockk(relaxed = true)
        equipmentRepository = mockk(relaxed = true)
        every { exerciseRepository.getAllExercises() } returns flowOf(emptyList())
        every { equipmentRepository.getAllEquipment() } returns flowOf(emptyList())
        coEvery { exerciseRepository.createExerciseWithRelations(any(), any(), any(), any(), any()) } answers {
            nextExerciseId++
        }
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns
            EquipmentSaveResult.Success(Equipment(id = 100L, name = "Kettlebell"))
        coEvery { equipmentRepository.getEquipmentById(100L) } returns Equipment(id = 100L, name = "Kettlebell")
        coEvery { exerciseRepository.linkVariationResult(any(), any(), any()) } returns ExerciseFamilyLinkResult.Success

        importer = ExerciseCsvImporter(context, exerciseRepository, equipmentRepository)
    }

    private fun stubCsvContent(csvText: String) {
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(csvText.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun importFromUri_missingRequiredHeaders_reportsActionableErrorWithoutSideEffects() = runTest {
        stubCsvContent(
            """
            description,difficulty
            "A description only row","Beginner"
            """.trimIndent()
        )

        val result = importer.importFromUri(uri)

        assertEquals(0, result.importedCount)
        assertTrue(result.errors.single().contains("name"))
        assertTrue(result.errors.single().contains("categories"))
        coVerify(exactly = 0) { exerciseRepository.createExerciseWithRelations(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { equipmentRepository.createEquipment(any(), any(), any()) }
    }

    @Test
    fun importFromUri_unknownColumn_isReportedButDoesNotBlockImport() = runTest {
        stubCsvContent(
            """
            name,categories,made_up_column
            Squat,Strength,ignored value
            """.trimIndent()
        )

        val result = importer.importFromUri(uri)

        assertEquals(1, result.importedCount)
        assertTrue(result.errors.any { it.contains("made_up_column") && it.contains("Unknown column") })
    }

    @Test
    fun importFromUri_generatedTemplate_importsAllSampleRowsUnderTheSameSchema() = runTest {
        stubCsvContent(ExerciseCsvTemplate.render())

        val result = importer.importFromUri(uri)

        assertEquals(emptyList<String>(), result.errors)
        assertEquals(3, result.importedCount)
        assertEquals(0, result.skippedCount)
        coVerify(exactly = 3) { exerciseRepository.createExerciseWithRelations(any(), any(), any(), any(), any()) }
    }

    @Test
    fun importFromUri_generatedTemplate_secondRowDemonstratesQuotingDelimitersAndMuscles() = runTest {
        stubCsvContent(ExerciseCsvTemplate.render())
        val categoriesSlots = mutableListOf<List<WorkoutCategory>>()
        val primarySlots = mutableListOf<List<MuscleGroup>>()
        val secondarySlots = mutableListOf<List<MuscleGroup>>()
        coEvery {
            exerciseRepository.createExerciseWithRelations(
                any(),
                capture(categoriesSlots),
                any(),
                capture(primarySlots),
                capture(secondarySlots)
            )
        } answers { nextExerciseId++ }

        importer.importFromUri(uri)

        // The "Farmer's Walk" row uses "Functional,Strength" (quoted comma-delimited categories),
        // "Forearms;Grip" (semicolon-delimited primary muscles), and "Abs|Glutes" (pipe-delimited
        // secondary muscles) - proving quoting and all three multi-value delimiters round-trip.
        assertEquals(3, categoriesSlots.size)
        assertTrue(categoriesSlots.any { WorkoutCategory.FUNCTIONAL in it && WorkoutCategory.STRENGTH in it })
        assertTrue(primarySlots.any { MuscleGroup.FOREARMS in it && MuscleGroup.GRIP in it })
        assertTrue(secondarySlots.any { MuscleGroup.ABS in it && MuscleGroup.GLUTES in it })
    }

    @Test
    fun importFromUri_generatedTemplate_populatesPhasePresetsFromSchemaHeaders() = runTest {
        stubCsvContent(ExerciseCsvTemplate.render())
        val exerciseSlot = mutableListOf<Exercise>()
        coEvery {
            exerciseRepository.createExerciseWithRelations(capture(exerciseSlot), any(), any(), any(), any())
        } answers { nextExerciseId++ }

        importer.importFromUri(uri)

        val farmersWalk = exerciseSlot.single { it.name == "Farmer's Walk" }
        val presets = farmersWalk.decodeStoredProgrammingPresets().value
        assertEquals("4", presets[TrainingPhase.STRENGTH_FOCUS]?.setsText)
        assertEquals("20m carry", presets[TrainingPhase.STRENGTH_FOCUS]?.repsText)
        assertEquals(120, presets[TrainingPhase.STRENGTH_FOCUS]?.restSeconds)
        assertEquals("3", presets[TrainingPhase.HYPERTROPHY_FOCUS]?.setsText)
    }

    @Test
    fun importFromUri_emptyFile_reportsNoImportableRows() = runTest {
        stubCsvContent("")

        val result = importer.importFromUri(uri)

        assertEquals(0, result.importedCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun importFromUri_variationRowBeforeItsMainExerciseRow_resolvesRegardlessOfOrder() = runTest {
        // "Diamond Push-Up" (a variation) appears BEFORE "Push-Up" (its main exercise) in the
        // file - the two-pass import must still resolve the link correctly.
        stubCsvContent(
            """
            name,categories,main_exercise,variation_focus
            Diamond Push-Up,Strength,Push-Up,Triceps emphasis
            Push-Up,Strength,,
            """.trimIndent()
        )
        val linkedParentIds = mutableListOf<Long>()
        val linkedVariationIds = mutableListOf<Long>()
        val linkedFocuses = mutableListOf<String>()
        coEvery {
            exerciseRepository.linkVariationResult(capture(linkedParentIds), capture(linkedVariationIds), capture(linkedFocuses))
        } returns ExerciseFamilyLinkResult.Success

        val result = importer.importFromUri(uri)

        assertEquals(2, result.importedCount)
        assertEquals(emptyList<String>(), result.errors)
        // Diamond Push-Up was created first (id 1), Push-Up second (id 2) - the link must
        // still resolve to Push-Up's id as the parent, not fail just because it came later.
        assertEquals(listOf(2L), linkedParentIds)
        assertEquals(listOf(1L), linkedVariationIds)
        assertEquals(listOf("Triceps emphasis"), linkedFocuses)
    }

    @Test
    fun importFromUri_mainExerciseAlreadyExists_linksAgainstThePreExistingRow() = runTest {
        every { exerciseRepository.getAllExercises() } returns
            flowOf(listOf(Exercise(id = 500L, name = "Push-Up")))
        stubCsvContent(
            """
            name,categories,main_exercise,variation_focus
            Diamond Push-Up,Strength,Push-Up,Triceps emphasis
            """.trimIndent()
        )
        coEvery { exerciseRepository.linkVariationResult(500L, any(), "Triceps emphasis") } returns
            ExerciseFamilyLinkResult.Success

        val result = importer.importFromUri(uri)

        assertEquals(1, result.importedCount)
        assertEquals(emptyList<String>(), result.errors)
        coVerify(exactly = 1) { exerciseRepository.linkVariationResult(500L, any(), "Triceps emphasis") }
    }

    @Test
    fun importFromUri_missingMainExercise_reportsRowErrorButKeepsTheExerciseImported() = runTest {
        stubCsvContent(
            """
            name,categories,main_exercise,variation_focus
            Orphaned Variation,Strength,Nonexistent Main Exercise,Some focus
            """.trimIndent()
        )

        val result = importer.importFromUri(uri)

        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertTrue(result.errors.single().contains("Nonexistent Main Exercise"))
        assertTrue(result.errors.single().contains("was not found"))
        coVerify(exactly = 0) { exerciseRepository.linkVariationResult(any(), any(), any()) }
    }

    @Test
    fun importFromUri_invalidFamilyLink_reportsRowErrorAndImportsExerciseStandalone() = runTest {
        every { exerciseRepository.getAllExercises() } returns
            flowOf(listOf(Exercise(id = 999L, name = "Some Main Exercise")))
        stubCsvContent(
            """
            name,categories,main_exercise,variation_focus
            Nested Variation,Strength,Some Main Exercise,Some focus
            """.trimIndent()
        )
        coEvery { exerciseRepository.linkVariationResult(any(), any(), any()) } returns
            ExerciseFamilyLinkResult.Failure(
                com.example.workoutapp.data.repository.ExerciseFamilyLinkError.ParentIsAlreadyVariation("Some Main Exercise")
            )

        val result = importer.importFromUri(uri)

        // The exercise itself is still imported - only the family link is reported as failed.
        assertEquals(1, result.importedCount)
        assertTrue(result.errors.single().contains("Nested Variation"))
        assertTrue(result.errors.single().contains("could not link"))
        assertTrue(result.errors.single().contains("imported standalone"))
    }

    @Test
    fun importFromUri_templateRoundTrip_reimportingTheExactSameFileSkipsEveryRowAsDuplicate() = runTest {
        val templateCsv = ExerciseCsvTemplate.render()
        every { exerciseRepository.getAllExercises() } returns
            flowOf(listOf(Exercise(id = 1L, name = "Push-Up"), Exercise(id = 2L, name = "Farmer's Walk"), Exercise(id = 3L, name = "Diamond Push-Up")))
        stubCsvContent(templateCsv)

        val result = importer.importFromUri(uri)

        assertEquals(0, result.importedCount)
        assertEquals(3, result.skippedCount)
        coVerify(exactly = 0) { exerciseRepository.createExerciseWithRelations(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { exerciseRepository.linkVariationResult(any(), any(), any()) }
    }
}
