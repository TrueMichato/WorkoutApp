package com.example.workoutapp.ui.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.repository.EquipmentDeletionResult
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.EquipmentSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EquipmentViewModel @Inject constructor(
    private val equipmentRepository: EquipmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EquipmentManagementUiState())
    val uiState: StateFlow<EquipmentManagementUiState> = _uiState.asStateFlow()

    private val _locationDetailState = MutableStateFlow(LocationDetailUiState())
    val locationDetailState: StateFlow<LocationDetailUiState> = _locationDetailState.asStateFlow()

    private val _addEquipmentState = MutableStateFlow(AddEquipmentUiState())
    val addEquipmentState: StateFlow<AddEquipmentUiState> = _addEquipmentState.asStateFlow()

    private val _createLocationState = MutableStateFlow(CreateLocationUiState())
    val createLocationState: StateFlow<CreateLocationUiState> = _createLocationState.asStateFlow()

    init {
        observeEquipment()
        observeLocations()
    }

    private fun observeEquipment() {
        viewModelScope.launch {
            equipmentRepository.getAllEquipment().collect { equipment ->
                _uiState.update {
                    it.copy(
                        allEquipment = equipment,
                        builtInEquipment = equipment.filter { item -> !item.isCustom },
                        customEquipment = equipment.filter { item -> item.isCustom },
                        isLoading = false
                    )
                }
                _locationDetailState.update { state -> state.copy(allEquipment = equipment) }
            }
        }
    }

    private fun observeLocations() {
        viewModelScope.launch {
            equipmentRepository.getAllLocations().collect { locations ->
                val summaries = locations.map { location ->
                    LocationSummary(
                        location = location,
                        equipmentCount = equipmentRepository.getEquipmentCountForLocation(location.id)
                    )
                }
                _uiState.update { it.copy(locations = summaries, isLoading = false) }
            }
        }
    }

    fun createLocation(name: String, description: String, setDefault: Boolean) {
        if (_createLocationState.value.isSaving) return // debounce double-taps while a save is in flight
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _createLocationState.update { it.copy(error = "Location name is required") }
            return
        }
        viewModelScope.launch {
            _createLocationState.update { it.copy(isSaving = true, error = null) }
            try {
                val locationId = equipmentRepository.createLocation(trimmedName, description.trim())
                if (setDefault) equipmentRepository.setDefaultLocation(locationId)
                _createLocationState.update {
                    it.copy(isSaving = false, error = null, createdLocationId = locationId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface the failure instead of silently discarding it - the dialog must stay
                // open with a visible error rather than closing as if the location was saved.
                _createLocationState.update {
                    it.copy(isSaving = false, error = "Could not save the location. Please try again.")
                }
            }
        }
    }

    /** Clears the one-shot "just created" signal after the caller has reacted to it. */
    fun consumeCreatedLocationSignal() {
        _createLocationState.update { it.copy(createdLocationId = null) }
    }

    fun resetCreateLocationState() {
        _createLocationState.value = CreateLocationUiState()
    }

    fun createEquipment(name: String, description: String, isPortable: Boolean) {
        if (_addEquipmentState.value.isSaving) return // debounce double-taps while a save is in flight
        viewModelScope.launch {
            _addEquipmentState.update { it.copy(isSaving = true, error = null) }
            try {
                when (val result = equipmentRepository.createEquipment(name, description, isPortable)) {
                    is EquipmentSaveResult.Success -> _addEquipmentState.update {
                        it.copy(isSaving = false, error = null, createdEquipmentId = result.equipment.id)
                    }
                    is EquipmentSaveResult.Failure -> _addEquipmentState.update {
                        it.copy(isSaving = false, error = result.error.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The repository already converts persistence failures into a typed result; this
                // is a last-resort guard so an unexpected exception never crashes the app or
                // leaves the dialog stuck mid-save.
                _addEquipmentState.update {
                    it.copy(isSaving = false, error = "Could not save the equipment. Please try again.")
                }
            }
        }
    }

    /** Clears the one-shot "just created" signal after the caller has reacted to it. */
    fun consumeCreatedEquipmentSignal() {
        _addEquipmentState.update { it.copy(createdEquipmentId = null) }
    }

    fun resetAddEquipmentState() {
        _addEquipmentState.value = AddEquipmentUiState()
    }

    fun deleteCustomEquipment(equipment: Equipment) {
        viewModelScope.launch {
            try {
                val result = equipmentRepository.deleteEquipment(equipment)
                if (result is EquipmentDeletionResult.Failure) {
                    _uiState.update { it.copy(equipmentActionError = result.error.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(equipmentActionError = "Something went wrong while removing this equipment. Please try again.")
                }
            }
        }
    }

    fun clearEquipmentActionError() {
        _uiState.update { it.copy(equipmentActionError = null) }
    }

    fun loadLocation(locationId: Long) {
        _locationDetailState.update { it.copy(locationId = locationId, isLoading = true, saveError = null) }
        viewModelScope.launch {
            try {
                val location = equipmentRepository.getLocationById(locationId)
                if (location == null) {
                    _locationDetailState.update {
                        it.copy(isLoading = false, saveError = "This location no longer exists.")
                    }
                    return@launch
                }
                val selectedEquipmentIds = equipmentRepository.getEquipmentIdsForLocation(locationId).toSet()
                _locationDetailState.update {
                    it.copy(
                        locationId = locationId,
                        name = location.name,
                        description = location.description,
                        isDefault = location.isDefault,
                        selectedEquipmentIds = selectedEquipmentIds,
                        isLoading = false,
                        saveError = null,
                        saveSuccess = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _locationDetailState.update {
                    it.copy(isLoading = false, saveError = "Could not load this location. Please try again.")
                }
            }
        }
    }

    fun updateLocationName(value: String) {
        _locationDetailState.update { it.copy(name = value, saveError = null) }
    }

    fun updateLocationDescription(value: String) {
        _locationDetailState.update { it.copy(description = value, saveError = null) }
    }

    fun updateLocationDefault(value: Boolean) {
        _locationDetailState.update { it.copy(isDefault = value, saveError = null) }
    }

    fun toggleLocationEquipment(equipmentId: Long) {
        _locationDetailState.update { state ->
            val updated = if (equipmentId in state.selectedEquipmentIds) {
                state.selectedEquipmentIds - equipmentId
            } else {
                state.selectedEquipmentIds + equipmentId
            }
            state.copy(selectedEquipmentIds = updated, saveError = null)
        }
    }

    fun saveLocation() {
        val state = _locationDetailState.value
        val locationId = state.locationId ?: return
        if (state.name.isBlank()) {
            _locationDetailState.update { it.copy(saveError = "Location name is required") }
            return
        }
        viewModelScope.launch {
            _locationDetailState.update { it.copy(isSaving = true, saveError = null) }
            try {
                val existing = equipmentRepository.getLocationById(locationId)
                if (existing == null) {
                    _locationDetailState.update {
                        it.copy(isSaving = false, saveError = "This location no longer exists.")
                    }
                    return@launch
                }
                equipmentRepository.updateLocation(
                    existing.copy(
                        name = state.name.trim(),
                        description = state.description.trim(),
                        isDefault = state.isDefault
                    )
                )
                equipmentRepository.setLocationEquipment(locationId, state.selectedEquipmentIds.toList())
                if (state.isDefault) equipmentRepository.setDefaultLocation(locationId)
                _locationDetailState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _locationDetailState.update {
                    it.copy(isSaving = false, saveError = e.message ?: "Failed to save location")
                }
            }
        }
    }

    fun deleteLocation() {
        val state = _locationDetailState.value
        val locationId = state.locationId ?: return
        viewModelScope.launch {
            _locationDetailState.update { it.copy(isSaving = true, saveError = null) }
            try {
                val existing = equipmentRepository.getLocationById(locationId)
                if (existing == null) {
                    _locationDetailState.update {
                        it.copy(isSaving = false, saveError = "This location no longer exists.")
                    }
                    return@launch
                }
                equipmentRepository.deleteLocation(existing)
                _locationDetailState.update { LocationDetailUiState(isDeleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _locationDetailState.update {
                    it.copy(isSaving = false, saveError = e.message ?: "Failed to delete location")
                }
            }
        }
    }

    fun clearLocationSavedState() {
        _locationDetailState.update { it.copy(saveSuccess = false, isDeleted = false) }
    }
}

data class LocationSummary(
    val location: EquipmentLocation,
    val equipmentCount: Int
)

data class EquipmentManagementUiState(
    val locations: List<LocationSummary> = emptyList(),
    val allEquipment: List<Equipment> = emptyList(),
    val builtInEquipment: List<Equipment> = emptyList(),
    val customEquipment: List<Equipment> = emptyList(),
    val isLoading: Boolean = true,
    val equipmentActionError: String? = null
)

data class AddEquipmentUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdEquipmentId: Long? = null
)

data class CreateLocationUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdLocationId: Long? = null
)

data class LocationDetailUiState(
    val locationId: Long? = null,
    val name: String = "",
    val description: String = "",
    val isDefault: Boolean = false,
    val allEquipment: List<Equipment> = emptyList(),
    val selectedEquipmentIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val isDeleted: Boolean = false,
    val saveError: String? = null
)

