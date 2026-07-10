package com.example.workoutapp.ui.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.repository.EquipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
        if (name.isBlank()) return
        viewModelScope.launch {
            val locationId = equipmentRepository.createLocation(name.trim(), description.trim())
            if (setDefault) equipmentRepository.setDefaultLocation(locationId)
        }
    }

    fun createEquipment(name: String, description: String, isPortable: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            equipmentRepository.createEquipment(name.trim(), description.trim(), isPortable)
        }
    }

    fun deleteCustomEquipment(equipment: Equipment) {
        if (!equipment.isCustom) return
        viewModelScope.launch {
            equipmentRepository.deleteEquipment(equipment)
        }
    }

    fun loadLocation(locationId: Long) {
        _locationDetailState.update { it.copy(locationId = locationId, isLoading = true, saveError = null) }
        viewModelScope.launch {
            val location = equipmentRepository.getLocationById(locationId) ?: return@launch
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
                val existing = equipmentRepository.getLocationById(locationId) ?: return@launch
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
            val existing = equipmentRepository.getLocationById(locationId) ?: return@launch
            equipmentRepository.deleteLocation(existing)
            _locationDetailState.update { LocationDetailUiState(isDeleted = true) }
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
    val customEquipment: List<Equipment> = emptyList(),
    val isLoading: Boolean = true
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

