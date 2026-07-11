package com.example.workoutapp.ui.exercises

import com.example.workoutapp.data.local.MediaStorageManager
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.EquipmentSaveResult
import com.example.workoutapp.data.repository.EquipmentValidationError
import com.example.workoutapp.data.repository.ExerciseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for the inline "Add Equipment" flow inside Add/Edit Exercise
 * ([AddEditExerciseViewModel.createCustomEquipment]), one of the three add-equipment surfaces
 * reported as crashing/not robust. This must behave consistently with the top-level Equipment
 * Management dialog: stay open on failure, debounce while saving, and only add/select the new
 * equipment after the repository confirms it was actually persisted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditExerciseViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var equipmentRepository: EquipmentRepository
    private lateinit var mediaStorageManager: MediaStorageManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        exerciseRepository = mockk(relaxed = true)
        equipmentRepository = mockk(relaxed = true)
        mediaStorageManager = mockk(relaxed = true)
        every { equipmentRepository.getAllEquipment() } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AddEditExerciseViewModel =
        AddEditExerciseViewModel(exerciseRepository, equipmentRepository, mediaStorageManager)

    @Test
    fun createCustomEquipment_success_addsToSelectedEquipment_andSignalsCreation() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val created = Equipment(id = 42, name = "Sandbag", isCustom = true)
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns EquipmentSaveResult.Success(created)

        viewModel.createCustomEquipment("Sandbag", "", false)

        val state = viewModel.uiState.value
        assertFalse(state.isCreatingEquipment)
        assertNull(state.equipmentCreationError)
        assertEquals(42L, state.createdCustomEquipmentId)
        assertTrue(state.selectedEquipment.any { it.id == 42L })
    }

    @Test
    fun createCustomEquipment_duplicateName_setsErrorAndDoesNotSelectAnything() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns
            EquipmentSaveResult.Failure(EquipmentValidationError.DuplicateName("Sandbag"))

        viewModel.createCustomEquipment("sandbag", "", false)

        val state = viewModel.uiState.value
        assertFalse(state.isCreatingEquipment)
        assertNull(state.createdCustomEquipmentId)
        assertTrue(state.selectedEquipment.isEmpty())
        assertTrue(state.equipmentCreationError!!.contains("Sandbag"))
    }

    @Test
    fun createCustomEquipment_blankName_setsBlankNameError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns
            EquipmentSaveResult.Failure(EquipmentValidationError.BlankName)

        viewModel.createCustomEquipment("   ", "", false)

        assertEquals(EquipmentValidationError.BlankName.message, viewModel.uiState.value.equipmentCreationError)
    }

    @Test
    fun createCustomEquipment_alreadySelectedEquipment_doesNotDuplicateSelection() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val created = Equipment(id = 42, name = "Sandbag", isCustom = true)
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns EquipmentSaveResult.Success(created)
        viewModel.toggleEquipment(created)
        assertEquals(1, viewModel.uiState.value.selectedEquipment.size)

        viewModel.createCustomEquipment("Sandbag", "", false)

        assertEquals(1, viewModel.uiState.value.selectedEquipment.size)
    }

    @Test
    fun createCustomEquipment_whileCreationInFlight_debouncesSecondCall() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val gate = Channel<Unit>()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } coAnswers {
            gate.receive()
            EquipmentSaveResult.Success(Equipment(id = 1, name = "Sandbag", isCustom = true))
        }

        viewModel.createCustomEquipment("Sandbag", "", false)
        assertTrue(viewModel.uiState.value.isCreatingEquipment)

        viewModel.createCustomEquipment("Sandbag", "", false) // must be ignored while in flight

        gate.send(Unit)
        coVerify(exactly = 1) { equipmentRepository.createEquipment(any(), any(), any()) }
    }

    @Test
    fun consumeCreatedCustomEquipmentSignal_clearsOneShotId() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns
            EquipmentSaveResult.Success(Equipment(id = 9, name = "Sandbag", isCustom = true))
        viewModel.createCustomEquipment("Sandbag", "", false)
        assertEquals(9L, viewModel.uiState.value.createdCustomEquipmentId)

        viewModel.consumeCreatedCustomEquipmentSignal()

        assertNull(viewModel.uiState.value.createdCustomEquipmentId)
    }

    @Test
    fun clearEquipmentCreationError_resetsErrorToNull() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns
            EquipmentSaveResult.Failure(EquipmentValidationError.BlankName)
        viewModel.createCustomEquipment("", "", false)
        assertTrue(viewModel.uiState.value.equipmentCreationError != null)

        viewModel.clearEquipmentCreationError()

        assertNull(viewModel.uiState.value.equipmentCreationError)
    }
}
