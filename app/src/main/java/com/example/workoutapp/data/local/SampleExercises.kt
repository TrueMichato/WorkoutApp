package com.example.workoutapp.data.local

import com.example.workoutapp.data.model.*

/**
 * Sample exercises to pre-populate the database
 * These cover various categories and equipment to give users a starting point
 */
object SampleExercises {

    data class SampleExercise(
        val name: String,
        val description: String,
        val instructions: String = "",
        val tips: String = "",
        val difficulty: Difficulty = Difficulty.INTERMEDIATE,
        val categories: List<WorkoutCategory>,
        val equipmentNames: List<String>,
        val primaryMuscles: List<MuscleGroup>,
        val secondaryMuscles: List<MuscleGroup> = emptyList(),
        val isCompound: Boolean = true,
        val isUnilateral: Boolean = false,
        val defaultSets: Int = 3,
        val defaultReps: String = "8-12",
        val defaultRestSeconds: Int = 90
    )

    val all = listOf(
        // STRENGTH exercises
        SampleExercise(
            name = "Barbell Back Squat",
            description = "The king of lower body exercises. A compound movement that targets the entire lower body.",
            instructions = "1. Set up the bar on a squat rack at shoulder height\n2. Step under the bar and position it across your upper traps\n3. Unrack and step back with feet shoulder-width apart\n4. Brace your core and descend by pushing hips back and bending knees\n5. Go as deep as mobility allows while maintaining neutral spine\n6. Drive through your feet to stand back up",
            tips = "Keep your chest up and don't let knees cave inward. Think about spreading the floor with your feet.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("Barbell", "Power Rack"),
            primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            secondaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.LOWER_BACK, MuscleGroup.ABS),
            defaultReps = "5-8"
        ),
        SampleExercise(
            name = "Deadlift",
            description = "Fundamental hip hinge pattern. Builds total body strength and posterior chain power.",
            instructions = "1. Stand with feet hip-width apart, bar over mid-foot\n2. Hinge at hips and grip the bar just outside your legs\n3. Pull slack out of the bar, brace core, chest up\n4. Drive through the floor and extend hips and knees together\n5. Lock out at the top, then reverse the movement",
            tips = "Keep the bar close to your body. Don't round your lower back.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.FUNCTIONAL),
            equipmentNames = listOf("Barbell"),
            primaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK),
            secondaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.LATS, MuscleGroup.FOREARMS),
            defaultReps = "5"
        ),
        SampleExercise(
            name = "Bench Press",
            description = "Classic upper body pushing movement for chest, shoulders, and triceps.",
            instructions = "1. Lie on bench with eyes under the bar\n2. Grip bar slightly wider than shoulder width\n3. Unrack and position bar over chest\n4. Lower bar to mid-chest with controlled speed\n5. Press back up to starting position",
            tips = "Keep shoulder blades retracted and maintain an arch in your lower back. Drive feet into the floor.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("Barbell", "Flat Bench", "Power Rack"),
            primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS),
            secondaryMuscles = listOf(MuscleGroup.LATS),
            defaultReps = "5-8"
        ),

        // HYPERTROPHY exercises
        SampleExercise(
            name = "Dumbbell Romanian Deadlift",
            description = "Excellent hamstring and glute builder with great stretch at the bottom.",
            instructions = "1. Hold dumbbells in front of thighs\n2. Push hips back while maintaining soft knee bend\n3. Lower dumbbells along legs until you feel a hamstring stretch\n4. Drive hips forward to return to standing",
            tips = "Keep back flat throughout. Think about pushing your butt to the wall behind you.",
            difficulty = Difficulty.NOVICE,
            categories = listOf(WorkoutCategory.HYPERTROPHY, WorkoutCategory.FUNCTIONAL),
            equipmentNames = listOf("Dumbbells"),
            primaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
            secondaryMuscles = listOf(MuscleGroup.LOWER_BACK),
            defaultReps = "10-12"
        ),
        SampleExercise(
            name = "Lat Pulldown",
            description = "Machine variation of the pull-up for building back width.",
            instructions = "1. Sit at lat pulldown machine with thighs under pad\n2. Grip bar wider than shoulder width\n3. Pull bar down to upper chest while squeezing shoulder blades\n4. Control the weight back up with arms extended",
            tips = "Lean back slightly and focus on pulling with your elbows, not your hands.",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("Lat Pulldown Machine"),
            primaryMuscles = listOf(MuscleGroup.LATS),
            secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.REAR_DELTS),
            defaultReps = "10-12"
        ),

        // ENDURANCE exercises
        SampleExercise(
            name = "Rowing Machine Intervals",
            description = "High-intensity cardio using the rowing ergometer.",
            instructions = "1. Set damper to 4-6\n2. Warm up for 2 minutes at easy pace\n3. Perform intervals: 30s hard, 30s easy\n4. Focus on leg drive, then body swing, then arm pull",
            tips = "Power comes from legs first! Don't just pull with arms.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.ENDURANCE),
            equipmentNames = listOf("Rowing Machine"),
            primaryMuscles = listOf(MuscleGroup.FULL_BODY),
            isCompound = true,
            defaultSets = 1,
            defaultReps = "10 rounds",
            defaultRestSeconds = 0
        ),
        SampleExercise(
            name = "Burpees",
            description = "Full-body conditioning exercise that builds endurance and explosiveness.",
            instructions = "1. Start standing\n2. Drop into squat position, hands on floor\n3. Jump feet back to plank position\n4. Perform a push-up (optional)\n5. Jump feet forward to squat\n6. Jump up with arms overhead",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.ENDURANCE, WorkoutCategory.FUNCTIONAL),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.FULL_BODY),
            defaultReps = "10-15",
            defaultRestSeconds = 60
        ),

        // MOBILITY exercises
        SampleExercise(
            name = "90/90 Hip Stretch",
            description = "Excellent hip mobility drill targeting internal and external rotation.",
            instructions = "1. Sit with front leg at 90° in front, back leg at 90° to the side\n2. Keep both sit bones grounded\n3. Lean forward over front shin\n4. Hold, then transition to other side",
            tips = "Keep spine tall. If you can't sit flat, elevate your hips on a cushion.",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(WorkoutCategory.MOBILITY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.HIP_FLEXORS, MuscleGroup.GLUTES),
            isCompound = false,
            defaultSets = 2,
            defaultReps = "60s each side",
            defaultRestSeconds = 0
        ),
        SampleExercise(
            name = "Cat-Cow Stretch",
            description = "Spinal mobility exercise that improves flexibility and relieves back tension.",
            instructions = "1. Start on hands and knees\n2. Cow: Drop belly, lift chest and tailbone\n3. Cat: Round spine up, tuck chin and pelvis\n4. Flow between positions with breath",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(WorkoutCategory.MOBILITY, WorkoutCategory.CORRECTIVES),
            equipmentNames = listOf("Yoga Mat"),
            primaryMuscles = listOf(MuscleGroup.LOWER_BACK, MuscleGroup.ABS),
            isCompound = false,
            defaultSets = 2,
            defaultReps = "10 cycles"
        ),

        // FLEXIBILITY exercises
        SampleExercise(
            name = "Standing Hamstring Stretch",
            description = "Simple but effective stretch for the back of the legs.",
            instructions = "1. Stand with one foot forward, heel down, toes up\n2. Hinge at hips with flat back\n3. Reach toward toes until stretch is felt\n4. Hold and breathe deeply",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(WorkoutCategory.FLEXIBILITY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.HAMSTRINGS),
            isCompound = false,
            isUnilateral = true,
            defaultSets = 2,
            defaultReps = "30s each side"
        ),

        // FUNCTIONAL exercises
        SampleExercise(
            name = "Turkish Get-Up",
            description = "Complex movement that builds total body coordination, stability, and strength.",
            instructions = "1. Lie on back with kettlebell pressed up in one hand\n2. Follow the sequence: to elbow, to hand, sweep leg, half kneel, stand\n3. Reverse the sequence to return to ground\n4. Keep eyes on kettlebell throughout",
            tips = "Go slow and master each position. This is not a speed exercise.",
            difficulty = Difficulty.ADVANCED,
            categories = listOf(WorkoutCategory.FUNCTIONAL, WorkoutCategory.SKILLS),
            equipmentNames = listOf("Kettlebell"),
            primaryMuscles = listOf(MuscleGroup.FULL_BODY),
            secondaryMuscles = listOf(MuscleGroup.ABS, MuscleGroup.GLUTES, MuscleGroup.FRONT_DELTS),
            isUnilateral = true,
            defaultSets = 3,
            defaultReps = "3 each side",
            defaultRestSeconds = 60
        ),
        SampleExercise(
            name = "Farmer's Walk",
            description = "Loaded carry that builds grip, core stability, and overall work capacity.",
            instructions = "1. Pick up heavy weights in each hand\n2. Stand tall with shoulders back\n3. Walk with short, quick steps\n4. Maintain upright posture throughout",
            tips = "Don't let the weights pull you forward or to the sides. Brace your core hard.",
            difficulty = Difficulty.NOVICE,
            categories = listOf(WorkoutCategory.FUNCTIONAL, WorkoutCategory.STRENGTH),
            equipmentNames = listOf("Dumbbells"),
            primaryMuscles = listOf(MuscleGroup.GRIP, MuscleGroup.ABS),
            secondaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            defaultSets = 3,
            defaultReps = "40m"
        ),

        // CORRECTIVES
        SampleExercise(
            name = "Face Pull",
            description = "Essential for shoulder health and posture. Strengthens often-neglected rear delts and rotator cuff.",
            instructions = "1. Set cable at face height with rope attachment\n2. Pull rope toward face, separating hands\n3. External rotate shoulders at end position\n4. Squeeze shoulder blades and hold briefly",
            tips = "Focus on the squeeze, not the weight. Keep elbows high.",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(WorkoutCategory.CORRECTIVES, WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("Cable Machine"),
            primaryMuscles = listOf(MuscleGroup.REAR_DELTS),
            secondaryMuscles = listOf(MuscleGroup.UPPER_BACK),
            isCompound = false,
            defaultReps = "15-20",
            defaultRestSeconds = 60
        ),
        SampleExercise(
            name = "Dead Bug",
            description = "Core stability exercise that trains anti-extension and coordination.",
            instructions = "1. Lie on back with arms pointing to ceiling\n2. Lift legs with knees at 90°\n3. Lower opposite arm and leg toward floor\n4. Keep lower back pressed into floor\n5. Return and repeat other side",
            tips = "If your back arches off the floor, you've gone too far. Quality over range.",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(WorkoutCategory.CORRECTIVES, WorkoutCategory.FUNCTIONAL),
            equipmentNames = listOf("Yoga Mat"),
            primaryMuscles = listOf(MuscleGroup.ABS),
            isCompound = false,
            defaultReps = "10 each side"
        ),

        // SKILLS
        SampleExercise(
            name = "Pull-Up",
            description = "Bodyweight pulling movement. A benchmark of upper body strength.",
            instructions = "1. Hang from bar with overhand grip, slightly wider than shoulders\n2. Pull yourself up until chin clears bar\n3. Lower with control to full hang\n4. Repeat",
            tips = "Initiate by depressing and retracting scapulae. Avoid kipping unless intentional.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.SKILLS, WorkoutCategory.STRENGTH),
            equipmentNames = listOf("Pull-up Bar"),
            primaryMuscles = listOf(MuscleGroup.LATS),
            secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.REAR_DELTS, MuscleGroup.ABS),
            defaultReps = "AMRAP",
            defaultRestSeconds = 120
        ),
        SampleExercise(
            name = "Handstand Hold",
            description = "Inverted bodyweight skill that builds shoulder strength and body awareness.",
            instructions = "1. Kick up to handstand against wall (belly facing wall is easier)\n2. Stack shoulders over wrists\n3. Squeeze glutes and point toes\n4. Hold for time",
            tips = "Start with wall support. Focus on creating a straight line from wrists to toes.",
            difficulty = Difficulty.ADVANCED,
            categories = listOf(WorkoutCategory.SKILLS, WorkoutCategory.DEXTERITY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.FRONT_DELTS),
            secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.ABS),
            isCompound = false,
            defaultSets = 5,
            defaultReps = "30s"
        ),

        // MARTIAL ARTS
        SampleExercise(
            name = "Heavy Bag Combinations",
            description = "Practice striking combinations on the heavy bag for technique and conditioning.",
            instructions = "1. Start in fighting stance\n2. Throw combination: jab-cross-hook-cross\n3. Move around bag between combinations\n4. Focus on technique first, then power and speed",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.MARTIAL_ARTS, WorkoutCategory.ENDURANCE),
            equipmentNames = listOf("Heavy Bag"),
            primaryMuscles = listOf(MuscleGroup.FULL_BODY),
            defaultSets = 5,
            defaultReps = "3 min rounds",
            defaultRestSeconds = 60
        ),
        SampleExercise(
            name = "Shadow Boxing",
            description = "Practice footwork and combinations without equipment.",
            instructions = "1. Start in fighting stance\n2. Move around, throwing combinations\n3. Practice slips, ducks, and pivots\n4. Visualize an opponent",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(WorkoutCategory.MARTIAL_ARTS, WorkoutCategory.DEXTERITY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.FULL_BODY),
            defaultSets = 3,
            defaultReps = "3 min rounds"
        ),

        // DEXTERITY
        SampleExercise(
            name = "Agility Ladder Drills",
            description = "Quick feet patterns for coordination and agility.",
            instructions = "1. Set up ladder on ground\n2. Perform various patterns: in-in-out-out, ickey shuffle, etc.\n3. Focus on quick, light feet\n4. Maintain athletic posture",
            difficulty = Difficulty.NOVICE,
            categories = listOf(WorkoutCategory.DEXTERITY, WorkoutCategory.ENDURANCE),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.CALVES, MuscleGroup.QUADS),
            defaultSets = 4,
            defaultReps = "2 lengths each pattern"
        ),

        // ODDBALLS
        SampleExercise(
            name = "Steel Mace 360",
            description = "Rotational movement with the mace for shoulder mobility and grip strength.",
            instructions = "1. Hold mace at end of handle\n2. Swing mace behind head in circular motion\n3. Control the weight throughout the rotation\n4. Perform equal reps each direction",
            tips = "Start very light. The leverage makes even light maces challenging.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.ODDBALLS, WorkoutCategory.MOBILITY),
            equipmentNames = listOf("Mace"),
            primaryMuscles = listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.GRIP),
            secondaryMuscles = listOf(MuscleGroup.ABS, MuscleGroup.LATS),
            defaultReps = "10 each direction"
        ),
        SampleExercise(
            name = "Juggling",
            description = "Hand-eye coordination practice that also builds shoulder endurance.",
            instructions = "1. Start with two balls, master the pattern\n2. Add third ball\n3. Focus on consistent throws and catches\n4. Practice for time",
            difficulty = Difficulty.NOVICE,
            categories = listOf(WorkoutCategory.ODDBALLS, WorkoutCategory.DEXTERITY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.FRONT_DELTS),
            isCompound = false,
            defaultSets = 1,
            defaultReps = "10 min practice"
        ),

        // PUSH-UP FAMILY (main exercise + variations, linked via exercise_variations by
        // WorkoutDatabaseSeeder.seedExerciseVariations - see PUSH_UP_FAMILY below). Appended at
        // the end of this list, not inserted earlier, so existing installs' already-seeded
        // exercise ids never shift.
        SampleExercise(
            name = "Push-up",
            description = "The classic bodyweight chest press. A compound movement that builds upper body pressing strength anywhere.",
            instructions = "1. Start in a plank with hands slightly wider than shoulders\n2. Keep body in a straight line from head to heels\n3. Lower chest toward the floor, elbows at about 45 degrees\n4. Press back up to full arm extension",
            tips = "Keep your core tight and don't let your hips sag or pike.",
            difficulty = Difficulty.NOVICE,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            secondaryMuscles = listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.ABS),
            defaultReps = "10-20"
        ),
        SampleExercise(
            name = "Tiger Push-up",
            description = "A push-up variation with a deep bent-arm start position that shifts emphasis onto the triceps.",
            instructions = "1. Start kneeling with hands planted and elbows bent close to the ribs\n2. Lower chin toward the floor by extending the arms forward\n3. Push back through the arms to return to the start position",
            tips = "Move slowly - the extended lever on the elbows makes this harder than it looks.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.CHEST),
            secondaryMuscles = listOf(MuscleGroup.FRONT_DELTS),
            defaultReps = "6-12"
        ),
        SampleExercise(
            name = "Pike Push-up",
            description = "A push-up variation performed with hips raised so the pressing angle shifts emphasis onto the shoulders.",
            instructions = "1. Start in a downward-dog position with hips high and hands shoulder-width apart\n2. Bend the elbows to lower the top of your head toward the floor\n3. Press back up through the shoulders to the start position",
            tips = "Keep hips high throughout - the higher the hips, the more vertical (shoulder-dominant) the press becomes.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            secondaryMuscles = listOf(MuscleGroup.TRICEPS),
            defaultReps = "6-12"
        ),
        SampleExercise(
            name = "Plyometric Push-up",
            description = "An explosive push-up variation that develops upper body power via a clapping or hand-lift push-off.",
            instructions = "1. Start in a standard push-up position\n2. Lower under control to the bottom position\n3. Push up explosively so the hands leave the floor\n4. Land softly with bent elbows and reset before the next rep",
            tips = "Prioritize a soft landing over height - control on the way down matters more than how far you leave the floor.",
            difficulty = Difficulty.ADVANCED,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.ENDURANCE),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            secondaryMuscles = listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.ABS),
            defaultSets = 3,
            defaultReps = "5-10"
        ),
        SampleExercise(
            name = "Slow Push-up",
            description = "A tempo-controlled push-up variation that emphasizes time under tension and isometric control over speed or load.",
            instructions = "1. Start in a standard push-up position\n2. Lower over a slow count (e.g. 4-5 seconds)\n3. Pause briefly just above the floor\n4. Press back up over a slow, controlled count",
            tips = "Resist the urge to speed up near the top or bottom - constant tempo is the point of this variation.",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(WorkoutCategory.STRENGTH, WorkoutCategory.HYPERTROPHY),
            equipmentNames = listOf("No Equipment"),
            primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            secondaryMuscles = listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.ABS),
            defaultSets = 3,
            defaultReps = "6-10"
        )
    )

    /**
     * Family links seeded alongside [all] on brand-new installs (see
     * WorkoutDatabaseSeeder.seedExerciseVariations): each pair is (variation name, focus),
     * all linked under the "Push-up" main exercise. Names are resolved to ids at seed time from
     * the exercise names already inserted from [all], so this list must stay in sync with the
     * Push-up family entries above.
     */
    const val PUSH_UP_MAIN_NAME = "Push-up"
    val PUSH_UP_VARIATIONS: List<Pair<String, String>> = listOf(
        "Tiger Push-up" to "Triceps emphasis",
        "Pike Push-up" to "Shoulder emphasis",
        "Plyometric Push-up" to "Explosiveness",
        "Slow Push-up" to "Tempo / isometric control"
    )
}

