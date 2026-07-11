package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.EquipmentDao
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EquipmentRepository @Inject constructor(
    private val equipmentDao: EquipmentDao
) {
    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 200
    }

    // Equipment
    fun getAllEquipment(): Flow<List<Equipment>> = equipmentDao.getAll()

    fun getPortableEquipment(): Flow<List<Equipment>> = equipmentDao.getPortable()

    fun getCustomEquipment(): Flow<List<Equipment>> = equipmentDao.getCustom()

    fun searchEquipment(query: String): Flow<List<Equipment>> = equipmentDao.search(query)

    suspend fun getEquipmentById(id: Long): Equipment? = equipmentDao.getById(id)

    suspend fun getEquipmentByNameIgnoreCase(name: String): Equipment? =
        equipmentDao.findByNameIgnoreCase(name)

    /**
     * Validates and creates a custom equipment row. Rejects blank/too-long names and
     * case/whitespace-insensitive duplicates of any existing equipment (built-in or custom) so
     * users can't shadow a built-in item or create duplicate custom rows.
     */
    suspend fun createEquipment(
        name: String,
        description: String = "",
        isPortable: Boolean = false
    ): EquipmentSaveResult {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return EquipmentSaveResult.Failure(EquipmentValidationError.BlankName)
        }
        if (trimmedName.length > MAX_NAME_LENGTH) {
            return EquipmentSaveResult.Failure(EquipmentValidationError.NameTooLong(MAX_NAME_LENGTH))
        }
        val duplicate = equipmentDao.findByNameIgnoreCase(trimmedName)
        if (duplicate != null) {
            return EquipmentSaveResult.Failure(EquipmentValidationError.DuplicateName(duplicate.name))
        }
        val trimmedDescription = description.trim().take(MAX_DESCRIPTION_LENGTH)
        val id = equipmentDao.insert(
            Equipment(
                name = trimmedName,
                description = trimmedDescription,
                isPortable = isPortable,
                isCustom = true
            )
        )
        val created = equipmentDao.getById(id)
            ?: return EquipmentSaveResult.Failure(EquipmentValidationError.PersistFailed)
        return EquipmentSaveResult.Success(created)
    }

    suspend fun updateEquipment(equipment: Equipment) = equipmentDao.update(equipment)

    /**
     * Deletes custom equipment only after confirming it still exists and is not referenced by any
     * exercise or location, so removal never silently strips equipment out of a user's saved data.
     */
    suspend fun deleteEquipment(equipment: Equipment): EquipmentDeletionResult {
        if (!equipment.isCustom) {
            return EquipmentDeletionResult.Failure(EquipmentDeletionError.NotCustom)
        }
        val current = equipmentDao.getById(equipment.id)
            ?: return EquipmentDeletionResult.Failure(EquipmentDeletionError.NotFound)
        val exerciseRefs = equipmentDao.countExerciseReferences(current.id)
        val locationRefs = equipmentDao.countLocationReferences(current.id)
        if (exerciseRefs > 0 || locationRefs > 0) {
            return EquipmentDeletionResult.Failure(
                EquipmentDeletionError.InUse(exerciseRefs, locationRefs)
            )
        }
        equipmentDao.delete(current)
        return EquipmentDeletionResult.Success
    }

    // Locations
    fun getAllLocations(): Flow<List<EquipmentLocation>> = equipmentDao.getAllLocations()

    suspend fun getLocationById(id: Long): EquipmentLocation? = equipmentDao.getLocationById(id)

    suspend fun getDefaultLocation(): EquipmentLocation? = equipmentDao.getDefaultLocation()

    fun getDefaultLocationFlow(): Flow<EquipmentLocation?> = equipmentDao.getDefaultLocationFlow()

    suspend fun createLocation(name: String, description: String = "", iconName: String = "location_on"): Long {
        return equipmentDao.insertLocation(
            EquipmentLocation(
                name = name,
                description = description,
                iconName = iconName
            )
        )
    }

    suspend fun updateLocation(location: EquipmentLocation) = equipmentDao.updateLocation(location)

    suspend fun deleteLocation(location: EquipmentLocation) = equipmentDao.deleteLocation(location)

    suspend fun setDefaultLocation(locationId: Long) = equipmentDao.setAsDefaultLocation(locationId)

    // Location-Equipment mapping
    fun getEquipmentForLocation(locationId: Long): Flow<List<Equipment>> =
        equipmentDao.getEquipmentForLocation(locationId)

    suspend fun getEquipmentIdsForLocation(locationId: Long): List<Long> =
        equipmentDao.getEquipmentIdsForLocation(locationId)

    suspend fun addEquipmentToLocation(locationId: Long, equipmentId: Long) {
        equipmentDao.insertLocationEquipment(LocationEquipmentCrossRef(locationId, equipmentId))
    }

    suspend fun removeEquipmentFromLocation(locationId: Long, equipmentId: Long) {
        equipmentDao.removeEquipmentFromLocation(locationId, equipmentId)
    }

    suspend fun setLocationEquipment(locationId: Long, equipmentIds: List<Long>) {
        equipmentDao.clearEquipmentForLocation(locationId)
        val refs = equipmentIds.map { LocationEquipmentCrossRef(locationId, it) }
        equipmentDao.insertLocationEquipmentAll(refs)
    }

    suspend fun isEquipmentAtLocation(locationId: Long, equipmentId: Long): Boolean =
        equipmentDao.isEquipmentAtLocation(locationId, equipmentId)

    suspend fun getEquipmentCountForLocation(locationId: Long): Int =
        equipmentDao.getEquipmentCountForLocation(locationId)
}

