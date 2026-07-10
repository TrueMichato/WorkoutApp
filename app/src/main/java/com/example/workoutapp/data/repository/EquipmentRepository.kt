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
    // Equipment
    fun getAllEquipment(): Flow<List<Equipment>> = equipmentDao.getAll()

    fun getPortableEquipment(): Flow<List<Equipment>> = equipmentDao.getPortable()

    fun getCustomEquipment(): Flow<List<Equipment>> = equipmentDao.getCustom()

    fun searchEquipment(query: String): Flow<List<Equipment>> = equipmentDao.search(query)

    suspend fun getEquipmentById(id: Long): Equipment? = equipmentDao.getById(id)

    suspend fun createEquipment(name: String, description: String = "", isPortable: Boolean = false): Long {
        return equipmentDao.insert(
            Equipment(
                name = name,
                description = description,
                isPortable = isPortable,
                isCustom = true
            )
        )
    }

    suspend fun updateEquipment(equipment: Equipment) = equipmentDao.update(equipment)

    suspend fun deleteEquipment(equipment: Equipment) = equipmentDao.delete(equipment)

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

