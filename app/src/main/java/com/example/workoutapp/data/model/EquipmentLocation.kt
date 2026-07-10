package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Location profile with associated equipment.
 * Users can define different equipment setups for different locations.
 */
@Entity(tableName = "equipment_locations")
data class EquipmentLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val iconName: String = "location_on",
    val isDefault: Boolean = false, // The default selected location
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Junction table linking locations to their available equipment
 */
@Entity(
    tableName = "location_equipment",
    primaryKeys = ["locationId", "equipmentId"]
)
data class LocationEquipmentCrossRef(
    val locationId: Long,
    val equipmentId: Long
)

/**
 * Pre-defined location templates
 */
object DefaultLocations {
    val HOME_GYM = EquipmentLocation(
        name = "Home Gym",
        description = "Your home workout space",
        iconName = "home"
    )

    val COMMERCIAL_GYM = EquipmentLocation(
        name = "Commercial Gym",
        description = "Full-service fitness center",
        iconName = "fitness_center"
    )

    val TRAVEL = EquipmentLocation(
        name = "Travel",
        description = "Hotel room or minimal equipment",
        iconName = "luggage"
    )

    val OUTDOOR = EquipmentLocation(
        name = "Outdoor",
        description = "Park or outdoor training area",
        iconName = "park"
    )

    val all = listOf(HOME_GYM, COMMERCIAL_GYM, TRAVEL, OUTDOOR)
}

