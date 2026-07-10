package com.example.workoutapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Equipment that can be used for exercises.
 * Pre-populated with common equipment, users can add custom entries.
 */
@Entity(tableName = "equipment")
data class Equipment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val iconName: String = "fitness_center", // Material icon name
    val isCustom: Boolean = false, // User-created equipment
    val isPortable: Boolean = false // Can be easily transported (for travel profiles)
)

/**
 * Pre-defined equipment for initial database population
 */
object DefaultEquipment {
    val all = listOf(
        // Bars
        Equipment(name = "Barbell", description = "Standard Olympic barbell", iconName = "fitness_center"),
        Equipment(name = "EZ Curl Bar", description = "Curved barbell for arm exercises", iconName = "fitness_center"),
        Equipment(name = "Trap Bar", description = "Hexagonal barbell for deadlifts", iconName = "fitness_center"),
        Equipment(name = "Pull-up Bar", description = "Fixed or doorway pull-up bar", iconName = "fitness_center", isPortable = true),

        // Free weights
        Equipment(name = "Dumbbells", description = "Adjustable or fixed dumbbells", iconName = "fitness_center"),
        Equipment(name = "Kettlebell", description = "Cast iron weight with handle", iconName = "fitness_center", isPortable = true),
        Equipment(name = "Weight Plates", description = "Olympic or standard plates", iconName = "fitness_center"),
        Equipment(name = "Medicine Ball", description = "Weighted ball for throws and core work", iconName = "sports_baseball", isPortable = true),
        Equipment(name = "Slam Ball", description = "Heavy ball designed for throwing", iconName = "sports_baseball"),

        // Machines
        Equipment(name = "Cable Machine", description = "Adjustable cable pulley system", iconName = "settings"),
        Equipment(name = "Lat Pulldown Machine", description = "Machine for lat pulldowns", iconName = "settings"),
        Equipment(name = "Leg Press Machine", description = "Machine for leg pressing", iconName = "settings"),
        Equipment(name = "Smith Machine", description = "Guided barbell on rails", iconName = "settings"),
        Equipment(name = "Hack Squat Machine", description = "Machine for hack squats", iconName = "settings"),
        Equipment(name = "Leg Curl Machine", description = "Machine for hamstring curls", iconName = "settings"),
        Equipment(name = "Leg Extension Machine", description = "Machine for quad extensions", iconName = "settings"),
        Equipment(name = "Chest Press Machine", description = "Machine for chest pressing", iconName = "settings"),
        Equipment(name = "Shoulder Press Machine", description = "Machine for shoulder pressing", iconName = "settings"),
        Equipment(name = "Row Machine", description = "Machine for rowing movements", iconName = "settings"),
        Equipment(name = "Pec Deck", description = "Machine for chest flyes", iconName = "settings"),

        // Cardio
        Equipment(name = "Treadmill", description = "Running/walking machine", iconName = "directions_run"),
        Equipment(name = "Stationary Bike", description = "Cycling machine", iconName = "pedal_bike"),
        Equipment(name = "Rowing Machine", description = "Ergometer for rowing", iconName = "rowing"),
        Equipment(name = "Elliptical", description = "Low-impact cardio machine", iconName = "directions_walk"),
        Equipment(name = "Stair Climber", description = "Stair stepping machine", iconName = "stairs"),
        Equipment(name = "Assault Bike", description = "Air resistance bike", iconName = "pedal_bike"),
        Equipment(name = "Ski Erg", description = "Skiing motion cardio machine", iconName = "downhill_skiing"),

        // Benches & Racks
        Equipment(name = "Flat Bench", description = "Flat weight bench", iconName = "weekend"),
        Equipment(name = "Adjustable Bench", description = "Incline/decline bench", iconName = "weekend"),
        Equipment(name = "Power Rack", description = "Squat rack with safety bars", iconName = "grid_on"),
        Equipment(name = "Squat Rack", description = "Basic squat stands", iconName = "grid_on"),
        Equipment(name = "Dip Station", description = "Parallel bars for dips", iconName = "fitness_center"),
        Equipment(name = "Preacher Curl Bench", description = "Angled bench for curls", iconName = "weekend"),
        Equipment(name = "Roman Chair", description = "Back extension station", iconName = "weekend"),
        Equipment(name = "GHD", description = "Glute ham developer", iconName = "weekend"),

        // Bands & Cables
        Equipment(name = "Resistance Bands", description = "Elastic bands of various resistances", iconName = "all_inclusive", isPortable = true),
        Equipment(name = "Mini Bands", description = "Small loop bands for activation", iconName = "all_inclusive", isPortable = true),
        Equipment(name = "Suspension Trainer", description = "TRX or similar suspension system", iconName = "all_inclusive", isPortable = true),
        Equipment(name = "Cable Attachments", description = "Various handles and bars for cables", iconName = "settings"),

        // Bodyweight & Gymnastics
        Equipment(name = "Gymnastics Rings", description = "Suspended rings for bodyweight training", iconName = "radio_button_unchecked", isPortable = true),
        Equipment(name = "Parallettes", description = "Low parallel bars", iconName = "fitness_center", isPortable = true),
        Equipment(name = "Ab Wheel", description = "Rollout wheel for core", iconName = "radio_button_unchecked", isPortable = true),
        Equipment(name = "Jump Rope", description = "Skipping rope", iconName = "all_inclusive", isPortable = true),

        // Mobility & Recovery
        Equipment(name = "Foam Roller", description = "Cylindrical foam for self-massage", iconName = "radio_button_unchecked", isPortable = true),
        Equipment(name = "Lacrosse Ball", description = "Small ball for trigger points", iconName = "sports_baseball", isPortable = true),
        Equipment(name = "Yoga Mat", description = "Padded mat for floor exercises", iconName = "rectangle", isPortable = true),
        Equipment(name = "Yoga Blocks", description = "Support blocks for stretching", iconName = "crop_square", isPortable = true),
        Equipment(name = "Stretching Strap", description = "Strap with loops for assisted stretching", iconName = "all_inclusive", isPortable = true),

        // Specialty
        Equipment(name = "Battle Ropes", description = "Heavy ropes for conditioning", iconName = "all_inclusive"),
        Equipment(name = "Sandbag", description = "Weighted bag for functional training", iconName = "shopping_bag"),
        Equipment(name = "Plyo Box", description = "Box for jumping exercises", iconName = "crop_square"),
        Equipment(name = "Landmine", description = "Barbell pivot attachment", iconName = "fitness_center"),
        Equipment(name = "Sled", description = "Push/pull weighted sled", iconName = "sledding"),
        Equipment(name = "Farmer's Walk Handles", description = "Handles for loaded carries", iconName = "fitness_center"),
        Equipment(name = "Mace", description = "Steel mace for rotational training", iconName = "fitness_center", isPortable = true),
        Equipment(name = "Indian Clubs", description = "Weighted clubs for shoulder mobility", iconName = "fitness_center", isPortable = true),

        // Martial Arts
        Equipment(name = "Heavy Bag", description = "Punching bag", iconName = "sports_mma"),
        Equipment(name = "Speed Bag", description = "Small rhythm bag", iconName = "sports_mma"),
        Equipment(name = "Focus Mitts", description = "Pad targets for striking", iconName = "sports_mma", isPortable = true),
        Equipment(name = "Thai Pads", description = "Large kick pads", iconName = "sports_mma", isPortable = true),
        Equipment(name = "Grappling Dummy", description = "Training dummy for wrestling", iconName = "sports_mma"),

        // None (bodyweight only)
        Equipment(name = "No Equipment", description = "Bodyweight exercises only", iconName = "accessibility_new", isPortable = true)
    )
}

