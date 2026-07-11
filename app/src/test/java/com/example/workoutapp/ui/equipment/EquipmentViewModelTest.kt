package com.example.workoutapp.ui.equipment

import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.repository.EquipmentDeletionError
import com.example.workoutapp.data.repository.EquipmentDeletionResult
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.EquipmentSaveResult
import com.example.workoutapp.data.repository.EquipmentValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
 * Real [EquipmentViewModel] coverage (constructed with a mocked [EquipmentRepository], never a
 * pure-helper-only test) for the equipment-management robustness fixes:
 *
 * 1. The regression that crashed the app: [EquipmentManagementUiState.builtInEquipment] and
 *    [EquipmentManagementUiState.customEquipment] must be a true partition of
 *    [EquipmentManagementUiState.allEquipment] - i.e. every equipment id appears in exactly one of
 *    the two groups the Equipment tab renders, never both (that duplication is what crashed
 *    Compose's LazyColumn with a duplicate-key error the first time a custom item was added).
 * 2. Add-equipment must debounce, and the dialog state must reflect success/failure distinctly
 *    (never silently closing on failure, never leaving isSaving stuck).
 * 3. Delete-equipment failures (not-custom, in-use) must surface as a visible error, not a silent
 *    no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var equipmentRepository: EquipmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        equipmentRepository = mockk(relaxed = true)
        every { equipmentRepository.getAllLocations() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(equipment: List<Equipment> = emptyList()): EquipmentViewModel {
        every { equipmentRepository.getAllEquipment() } returns MutableStateFlow(equipment)
        return EquipmentViewModel(equipmentRepository)
    }

    // ===================== Duplicate-key regression =====================

    @Test
    fun uiState_partitionsEquipmentIntoBuiltInAndCustom_asDisjointGroupsCoveringAllEquipment() = runTest(testDispatcher) {
        val builtIn = Equipment(id = 1, name = "Barbell", isCustom = false)
        val custom = Equipment(id = 2, name = "Weird Bar", isCustom = true)
        val viewModel = createViewModel(listOf(builtIn, custom))

        val state = viewModel.uiState.value

        assertEquals(listOf(builtIn), state.builtInEquipment)
        assertEquals(listOf(custom), state.customEquipment)
        // The two groups rendered as separate LazyColumn item blocks must never share an id -
        // that overlap was the exact cause of the duplicate-key crash.
        val builtInIds = state.builtInEquipment.map { it.id }.toSet()
        val customIds = state.customEquipment.map { it.id }.toSet()
        assertTrue(builtInIds.intersect(customIds).isEmpty())
        assertEquals(state.allEquipment.map { it.id }.toSet(), builtInIds + customIds)
    }

    @Test
    fun uiState_singleCustomEquipmentItem_appearsInCustomGroupOnly() = runTest(testDispatcher) {
        val custom = Equipment(id = 5, name = "My Bar", isCustom = true)
        val viewModel = createViewModel(listOf(custom))

        val state = viewModel.uiState.value

        assertEquals(1, state.customEquipment.size)
        assertTrue(state.builtInEquipment.isEmpty())
    }

    // ===================== Add equipment =====================

    @Test
    fun createEquipment_success_setsCreatedIdAndClearsSavingAndError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val created = Equipment(id = 10, name = "Slam Ball", isCustom = true)
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns EquipmentSaveResult.Success(created)

        viewModel.createEquipment("Slam Ball", "", false)

        val state = viewModel.addEquipmentState.value
        assertFalse(state.isSaving)
        assertNull(state.error)
        assertEquals(10L, state.createdEquipmentId)
    }

    @Test
    fun createEquipment_duplicateName_setsErrorAndDoesNotSignalCreation() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns
            EquipmentSaveResult.Failure(EquipmentValidationError.DuplicateName("Barbell"))

        viewModel.createEquipment("barbell", "", false)

        val state = viewModel.addEquipmentState.value
        assertFalse(state.isSaving)
        assertNull(state.createdEquipmentId)
        assertTrue(state.error!!.contains("Barbell"))
    }

    @Test
    fun createEquipment_whileSaveInFlight_debouncesSecondCall() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val gate = Channel<Unit>()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } coAnswers {
            gate.receive()
            EquipmentSaveResult.Success(Equipment(id = 1, name = "Slam Ball", isCustom = true))
        }

        viewModel.createEquipment("Slam Ball", "", false) // suspends on the gate, isSaving = true
        assertTrue(viewModel.addEquipmentState.value.isSaving)

        viewModel.createEquipment("Slam Ball", "", false) // must be ignored while saving

        gate.send(Unit) // let the first call complete
        coVerify(exactly = 1) { equipmentRepository.createEquipment(any(), any(), any()) }
    }

    @Test
    fun createEquipment_repositoryThrows_resetsIsSavingAndSetsVisibleErrorInsteadOfCrashing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } throws RuntimeException("simulated failure")

        viewModel.createEquipment("Slam Ball", "", false)

        val state = viewModel.addEquipmentState.value
        assertFalse(state.isSaving)
        assertNull(state.createdEquipmentId)
        assertTrue(state.error != null)
    }

    @Test
    fun consumeCreatedEquipmentSignal_clearsOneShotId() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createEquipment(any(), any(), any()) } returns
            EquipmentSaveResult.Success(Equipment(id = 3, name = "Foam Roller", isCustom = true))
        viewModel.createEquipment("Foam Roller", "", false)
        assertEquals(3L, viewModel.addEquipmentState.value.createdEquipmentId)

        viewModel.consumeCreatedEquipmentSignal()

        assertNull(viewModel.addEquipmentState.value.createdEquipmentId)
    }

    // ===================== Guarded deletion =====================

    @Test
    fun deleteCustomEquipment_inUseFailure_setsVisibleActionError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val equipment = Equipment(id = 4, name = "Kettlebell", isCustom = true)
        coEvery { equipmentRepository.deleteEquipment(equipment) } returns
            EquipmentDeletionResult.Failure(EquipmentDeletionError.InUse(exerciseCount = 2, locationCount = 0))

        viewModel.deleteCustomEquipment(equipment)

        val error = viewModel.uiState.value.equipmentActionError
        assertTrue(error != null && error.contains("2 exercises"))
    }

    @Test
    fun deleteCustomEquipment_success_leavesActionErrorNull() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val equipment = Equipment(id = 6, name = "Kettlebell", isCustom = true)
        coEvery { equipmentRepository.deleteEquipment(equipment) } returns EquipmentDeletionResult.Success

        viewModel.deleteCustomEquipment(equipment)

        assertNull(viewModel.uiState.value.equipmentActionError)
    }

    @Test
    fun clearEquipmentActionError_resetsErrorToNull() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val equipment = Equipment(id = 7, name = "Kettlebell", isCustom = true)
        coEvery { equipmentRepository.deleteEquipment(equipment) } returns
            EquipmentDeletionResult.Failure(EquipmentDeletionError.NotFound)
        viewModel.deleteCustomEquipment(equipment)
        assertTrue(viewModel.uiState.value.equipmentActionError != null)

        viewModel.clearEquipmentActionError()

        assertNull(viewModel.uiState.value.equipmentActionError)
    }

    @Test
    fun deleteCustomEquipment_repositoryThrows_setsVisibleActionErrorInsteadOfCrashing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val equipment = Equipment(id = 8, name = "Kettlebell", isCustom = true)
        coEvery { equipmentRepository.deleteEquipment(equipment) } throws RuntimeException("simulated failure")

        viewModel.deleteCustomEquipment(equipment)

        assertTrue(viewModel.uiState.value.equipmentActionError != null)
    }

    // ===================== Create location (hardened, mirrors add-equipment) =====================

    @Test
    fun createLocation_success_setsCreatedIdAndClearsSavingAndError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createLocation(any(), any()) } returns 42L

        viewModel.createLocation("Home Gym", "My garage", setDefault = false)

        val state = viewModel.createLocationState.value
        assertFalse(state.isSaving)
        assertNull(state.error)
        assertEquals(42L, state.createdLocationId)
    }

    @Test
    fun createLocation_blankName_isRejectedWithoutCallingRepository() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.createLocation("   ", "", setDefault = false)

        val state = viewModel.createLocationState.value
        assertTrue(state.error != null)
        assertNull(state.createdLocationId)
        coVerify(exactly = 0) { equipmentRepository.createLocation(any(), any()) }
    }

    @Test
    fun createLocation_repositoryThrows_setsVisibleErrorInsteadOfSilentlyClosing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createLocation(any(), any()) } throws RuntimeException("simulated DB failure")

        viewModel.createLocation("Home Gym", "", setDefault = false)

        val state = viewModel.createLocationState.value
        assertFalse(state.isSaving)
        assertNull(state.createdLocationId)
        assertTrue(state.error != null)
    }

    @Test
    fun createLocation_whileSaveInFlight_debouncesSecondCall() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val gate = Channel<Unit>()
        coEvery { equipmentRepository.createLocation(any(), any()) } coAnswers {
            gate.receive()
            99L
        }

        viewModel.createLocation("Home Gym", "", setDefault = false) // suspends on the gate
        assertTrue(viewModel.createLocationState.value.isSaving)

        viewModel.createLocation("Home Gym", "", setDefault = false) // must be ignored while saving

        gate.send(Unit)
        coVerify(exactly = 1) { equipmentRepository.createLocation(any(), any()) }
    }

    @Test
    fun consumeCreatedLocationSignal_clearsOneShotId() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.createLocation(any(), any()) } returns 5L
        viewModel.createLocation("Home Gym", "", setDefault = false)
        assertEquals(5L, viewModel.createLocationState.value.createdLocationId)

        viewModel.consumeCreatedLocationSignal()

        assertNull(viewModel.createLocationState.value.createdLocationId)
    }

    // ===================== loadLocation resilience =====================

    @Test
    fun loadLocation_missingRecord_clearsLoadingAndSetsVisibleErrorInsteadOfStayingStuckLoading() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.getLocationById(123L) } returns null

        viewModel.loadLocation(123L)

        val state = viewModel.locationDetailState.value
        assertFalse(state.isLoading)
        assertTrue(state.saveError != null)
    }

    @Test
    fun loadLocation_repositoryThrows_clearsLoadingAndSetsVisibleErrorInsteadOfStayingStuckLoading() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { equipmentRepository.getLocationById(123L) } throws RuntimeException("simulated DB failure")

        viewModel.loadLocation(123L)

        val state = viewModel.locationDetailState.value
        assertFalse(state.isLoading)
        assertTrue(state.saveError != null)
    }

    @Test
    fun loadLocation_success_populatesStateAndClearsLoading() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val location = EquipmentLocation(id = 9, name = "Home Gym", description = "desc", isDefault = true)
        coEvery { equipmentRepository.getLocationById(9L) } returns location
        coEvery { equipmentRepository.getEquipmentIdsForLocation(9L) } returns listOf(1L, 2L)

        viewModel.loadLocation(9L)

        val state = viewModel.locationDetailState.value
        assertFalse(state.isLoading)
        assertNull(state.saveError)
        assertEquals("Home Gym", state.name)
        assertEquals(setOf(1L, 2L), state.selectedEquipmentIds)
    }
}
