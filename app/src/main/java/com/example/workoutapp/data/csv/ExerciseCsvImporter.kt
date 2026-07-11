package com.example.workoutapp.data.csv

import android.content.Context
import android.net.Uri
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.ExerciseProgrammingPreset
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.EquipmentSaveResult
import com.example.workoutapp.data.repository.EquipmentValidationError
import com.example.workoutapp.data.repository.ExerciseFamilyLinkResult
import com.example.workoutapp.data.repository.ExerciseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseCsvImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exerciseRepository: ExerciseRepository,
    private val equipmentRepository: EquipmentRepository
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun importFromUri(uri: Uri): ExerciseCsvImportResult {
        val csvText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use(BufferedReader::readText)
            ?: return ExerciseCsvImportResult(
                importedCount = 0,
                skippedCount = 1,
                errors = listOf("Unable to read the selected CSV file.")
            )

        val headers = ExerciseCsvParser.parseHeaders(csvText)
        if (headers.isEmpty()) {
            return ExerciseCsvImportResult(
                importedCount = 0,
                skippedCount = 1,
                errors = listOf("The CSV file did not contain any importable rows.")
            )
        }

        val missingHeaders = ExerciseCsvSchema.missingRequiredHeaders(headers)
        if (missingHeaders.isNotEmpty()) {
            return ExerciseCsvImportResult(
                importedCount = 0,
                skippedCount = 0,
                errors = listOf(
                    "Missing required column(s): ${missingHeaders.joinToString(", ")}. " +
                        "No exercises or equipment were created."
                )
            )
        }

        val records = ExerciseCsvParser.parse(csvText)
        if (records.isEmpty()) {
            return ExerciseCsvImportResult(
                importedCount = 0,
                skippedCount = 1,
                errors = listOf("The CSV file did not contain any importable rows.")
            )
        }

        val existingExercises = exerciseRepository.getAllExercises().first()
        val existingNames = existingExercises
            .map { it.name.trim().lowercase() }
            .toMutableSet()
        // Every exercise's id by normalized name, seeded from the existing library and grown as
        // rows are imported below, so a variation row's main_exercise can resolve to either a
        // pre-existing exercise or one created earlier/later in this same file - family linking
        // is resolved in a second pass after every row has been imported, so row order in the
        // CSV never matters.
        val idsByLowerName = existingExercises.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
        val equipmentCache = equipmentRepository.getAllEquipment().first()
            .associateBy { it.name.trim().lowercase() }
            .toMutableMap()

        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        // (rowNumber, thisExerciseId, mainExerciseName, focus) for every successfully-imported
        // row that requested a family link, resolved in the second pass below.
        val pendingFamilyLinks = mutableListOf<PendingFamilyLink>()

        val unknownHeaders = ExerciseCsvSchema.unknownHeaders(headers)
        if (unknownHeaders.isNotEmpty()) {
            errors += "Unknown column(s) ignored: ${unknownHeaders.joinToString(", ")}."
        }

        records.forEachIndexed { index, record ->
            val rowNumber = index + 2
            try {
                val name = record.required("name")
                val normalizedName = name.lowercase()
                if (normalizedName in existingNames) {
                    skipped += 1
                    errors += "Row $rowNumber: exercise \"$name\" already exists and was skipped."
                    return@forEachIndexed
                }

                val categories = parseCategories(record["categories"])
                if (categories.isEmpty()) {
                    skipped += 1
                    errors += "Row $rowNumber: at least one valid category is required."
                    return@forEachIndexed
                }

                val difficulty = parseDifficulty(record["difficulty"])
                val defaultSets = record["default_sets"]?.toIntOrNull()?.coerceIn(1, 20) ?: 3
                val defaultReps = record["default_reps"].orEmpty().ifBlank { "8-12" }
                val defaultRest = record["default_rest_seconds"]?.toIntOrNull()?.coerceIn(0, 600) ?: 90
                val estimatedDurationSeconds = record["estimated_duration_seconds"]?.toIntOrNull()?.coerceIn(30, 3600) ?: 180
                val presets = buildPhasePresets(record, defaultSets, defaultReps, defaultRest)

                val equipmentIds = resolveEquipmentIds(record["equipment"], equipmentCache, rowNumber, errors)
                val exercise = Exercise(
                    name = name,
                    description = record["description"].orEmpty(),
                    instructions = record["instructions"].orEmpty(),
                    tips = record["tips"].orEmpty(),
                    difficulty = difficulty,
                    isCompound = record.boolean("is_compound", true),
                    isUnilateral = record.boolean("is_unilateral", false),
                    defaultSets = defaultSets,
                    defaultReps = defaultReps,
                    defaultRestSeconds = defaultRest,
                    estimatedDurationSeconds = estimatedDurationSeconds,
                    trainingPhasePresets = json.encodeToString(presets.mapKeys { it.key.name }),
                    personalNotes = record["personal_notes"].orEmpty()
                )
                val newExerciseId = exerciseRepository.createExerciseWithRelations(
                    exercise = exercise,
                    categories = categories,
                    equipmentIds = equipmentIds,
                    primaryMuscles = parseMuscles(record["primary_muscles"]),
                    secondaryMuscles = parseMuscles(record["secondary_muscles"])
                )
                existingNames += normalizedName
                idsByLowerName[normalizedName] = newExerciseId
                imported += 1

                val mainExerciseName = record["main_exercise"]?.trim().orEmpty()
                if (mainExerciseName.isNotEmpty()) {
                    pendingFamilyLinks += PendingFamilyLink(
                        rowNumber = rowNumber,
                        exerciseId = newExerciseId,
                        exerciseName = name,
                        mainExerciseName = mainExerciseName,
                        focus = record["variation_focus"].orEmpty()
                    )
                }
            } catch (e: IllegalArgumentException) {
                skipped += 1
                errors += "Row $rowNumber: ${e.message}"
            } catch (e: Exception) {
                skipped += 1
                errors += "Row $rowNumber: ${e.message ?: "Unexpected import error."}"
            }
        }

        // Second pass: resolve and apply every requested family link now that every row's
        // exercise (regardless of its position in the file) has already been created, so a
        // variation row can appear before or after its main exercise's row.
        pendingFamilyLinks.forEach { pending ->
            val parentId = idsByLowerName[pending.mainExerciseName.trim().lowercase()]
            if (parentId == null) {
                errors += "Row ${pending.rowNumber}: main exercise \"${pending.mainExerciseName}\" for " +
                    "\"${pending.exerciseName}\" was not found; the exercise was imported standalone."
                return@forEach
            }
            when (val result = exerciseRepository.linkVariationResult(parentId, pending.exerciseId, pending.focus)) {
                is ExerciseFamilyLinkResult.Success -> Unit
                is ExerciseFamilyLinkResult.Failure -> {
                    errors += "Row ${pending.rowNumber}: could not link \"${pending.exerciseName}\" as a variation " +
                        "of \"${pending.mainExerciseName}\" (${result.error.message}); the exercise was imported standalone."
                }
            }
        }

        return ExerciseCsvImportResult(
            importedCount = imported,
            skippedCount = skipped,
            errors = errors
        )
    }

    private data class PendingFamilyLink(
        val rowNumber: Int,
        val exerciseId: Long,
        val exerciseName: String,
        val mainExerciseName: String,
        val focus: String
    )

    private suspend fun resolveEquipmentIds(
        rawEquipment: String?,
        equipmentCache: MutableMap<String, Equipment>,
        rowNumber: Int,
        errors: MutableList<String>
    ): List<Long> {
        val ids = mutableListOf<Long>()
        tokenize(rawEquipment).forEach { equipmentName ->
            val key = equipmentName.trim().lowercase()
            val equipment = equipmentCache[key] ?: when (
                val result = equipmentRepository.createEquipment(equipmentName, isPortable = false)
            ) {
                is EquipmentSaveResult.Success -> result.equipment.also { equipmentCache[key] = it }
                is EquipmentSaveResult.Failure -> when (val error = result.error) {
                    is EquipmentValidationError.DuplicateName -> {
                        // Cache miss but the row already exists in the DB (e.g. two rows use the
                        // same equipment name with different casing/whitespace within one import) -
                        // reuse the existing row instead of dropping the reference.
                        equipmentRepository.getEquipmentByNameIgnoreCase(equipmentName)?.also {
                            equipmentCache[key] = it
                        }
                    }
                    else -> {
                        errors += "Row $rowNumber: equipment \"$equipmentName\" was skipped (${error.message})"
                        null
                    }
                }
            }
            if (equipment != null) ids += equipment.id
        }
        return ids.distinct()
    }

    private fun buildPhasePresets(
        record: CsvRecord,
        defaultSets: Int,
        defaultReps: String,
        defaultRest: Int
    ): Map<TrainingPhase, ExerciseProgrammingPreset> {
        val presets = mutableMapOf<TrainingPhase, ExerciseProgrammingPreset>()
        TrainingPhase.entries.forEach { phase ->
            val sets = record[ExerciseCsvSchema.phaseHeader(phase, "sets")]?.takeIf { it.isNotBlank() }
            val reps = record[ExerciseCsvSchema.phaseHeader(phase, "reps")]?.takeIf { it.isNotBlank() }
            val rest = record[ExerciseCsvSchema.phaseHeader(phase, "rest")]?.toIntOrNull()
            val notes = record[ExerciseCsvSchema.phaseHeader(phase, "notes")].orEmpty()
            if (sets != null || reps != null || rest != null || notes.isNotBlank()) {
                presets[phase] = ExerciseProgrammingPreset(
                    setsText = sets ?: if (phase == TrainingPhase.BALANCED) defaultSets.toString() else defaultPresetForPhase(phase).setsText,
                    repsText = reps ?: if (phase == TrainingPhase.BALANCED) defaultReps else defaultPresetForPhase(phase).repsText,
                    restSeconds = (rest ?: if (phase == TrainingPhase.BALANCED) defaultRest else defaultPresetForPhase(phase).restSeconds).coerceIn(0, 600),
                    notes = notes.ifBlank { if (phase == TrainingPhase.BALANCED) "" else defaultPresetForPhase(phase).notes }
                )
            }
        }
        return presets
    }

    private fun defaultPresetForPhase(phase: TrainingPhase): ExerciseProgrammingPreset = when (phase) {
        TrainingPhase.BALANCED -> ExerciseProgrammingPreset("3", "8-12", 90)
        TrainingPhase.STRENGTH_FOCUS -> ExerciseProgrammingPreset("4-6", "3-6", 150, notes = "Lower reps with longer recovery.")
        TrainingPhase.HYPERTROPHY_FOCUS -> ExerciseProgrammingPreset("3-5", "6-12", 75, notes = "Moderate reps and manageable rest for muscle gain.")
        TrainingPhase.ENDURANCE_FOCUS -> ExerciseProgrammingPreset("2-4", "15-30", 45, notes = "Higher reps and shorter rest for stamina.")
        TrainingPhase.SKILL_ACQUISITION -> ExerciseProgrammingPreset("3-6", "2-5", 90, notes = "Crisp quality reps with space to reset technique.")
        TrainingPhase.RECOVERY -> ExerciseProgrammingPreset("2-3", "5-8", 45, notes = "Easy volume that supports recovery.")
        TrainingPhase.MARTIAL_ARTS_FOCUS -> ExerciseProgrammingPreset("3-5", "60-180s rounds", 60, notes = "Conditioning-style rounds for fight prep.")
        TrainingPhase.MOBILITY_REHAB -> ExerciseProgrammingPreset("2-4", "5-10 reps / 30-60s", 30, notes = "Controlled mobility and corrective work.")
    }
}

data class ExerciseCsvImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<String>
) {
    val summary: String
        get() = buildString {
            append("Imported $importedCount exercise")
            if (importedCount != 1) append('s')
            if (skippedCount > 0) {
                append(" • skipped $skippedCount")
            }
        }
}

data class CsvRecord(private val values: Map<String, String>) {
    operator fun get(key: String): String? = values[normalizeHeader(key)]

    fun required(key: String): String {
        return get(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required column \"$key\".")
    }

    fun boolean(key: String, default: Boolean): Boolean {
        val raw = get(key)?.trim()?.lowercase().orEmpty()
        return when (raw) {
            "true", "yes", "y", "1" -> true
            "false", "no", "n", "0" -> false
            "" -> default
            else -> default
        }
    }
}

object ExerciseCsvParser {
    fun parse(csvText: String): List<CsvRecord> {
        val rows = csvText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .filter { it.isNotBlank() }
        if (rows.isEmpty()) return emptyList()

        val headers = parseLine(rows.first()).map(::normalizeHeader)
        return rows.drop(1).mapNotNull { row ->
            val cells = parseLine(row)
            if (cells.all { it.isBlank() }) return@mapNotNull null
            val values = headers.mapIndexed { index, header ->
                header to cells.getOrElse(index) { "" }.trim()
            }.toMap()
            CsvRecord(values)
        }
    }

    /** Extracts and normalizes just the header row, without parsing any data rows. */
    fun parseHeaders(csvText: String): List<String> {
        val firstLine = csvText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .firstOrNull { it.isNotBlank() }
            ?: return emptyList()
        return parseLine(firstLine).map(::normalizeHeader)
    }

    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        result += current.toString()
        return result
    }

    /** Serializes a row of cells into a single CSV line, quoting/escaping as needed. */
    fun writeLine(cells: List<String>): String = cells.joinToString(",") { escapeCell(it) }

    private fun escapeCell(cell: String): String {
        val needsQuoting = cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"${cell.replace("\"", "\"\"")}\""
        } else {
            cell
        }
    }
}

private fun normalizeHeader(header: String): String =
    header.trim().lowercase().replace(' ', '_')

private fun parseDifficulty(raw: String?): Difficulty {
    val value = raw?.trim().orEmpty()
    return Difficulty.entries.firstOrNull {
        it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true)
    } ?: Difficulty.INTERMEDIATE
}

private fun parseCategories(raw: String?): List<WorkoutCategory> =
    tokenize(raw).mapNotNull { token ->
        WorkoutCategory.entries.firstOrNull {
            it.name.equals(token.replace(' ', '_'), ignoreCase = true) ||
                it.displayName.equals(token, ignoreCase = true)
        }
    }.filter { it != WorkoutCategory.CUSTOM }.distinct()

private fun parseMuscles(raw: String?): List<MuscleGroup> =
    tokenize(raw).mapNotNull { token ->
        MuscleGroup.entries.firstOrNull {
            it.name.equals(token.replace(' ', '_'), ignoreCase = true) ||
                it.displayName.equals(token, ignoreCase = true)
        }
    }.distinct()

private fun tokenize(raw: String?): List<String> =
    raw.orEmpty()
        .split('|', ';', ',')
        .map { it.trim() }
        .filter { it.isNotBlank() }

