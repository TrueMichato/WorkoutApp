package com.example.workoutapp.data.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class PersistedDataDecodeException(
    val fieldName: String,
    val rawValue: String,
    cause: Throwable
) : IllegalStateException("Saved $fieldName is malformed. The raw value was preserved.", cause)

data class PersistedJsonIssue(
    val fieldName: String,
    val rawValue: String,
    val message: String,
    val kind: PersistedJsonIssueKind = PersistedJsonIssueKind.UNKNOWN_VALUE
)

enum class PersistedJsonIssueKind {
    MALFORMED_JSON,
    UNKNOWN_VALUE
}

data class PersistedJsonResult<T>(
    val value: T,
    val issues: List<PersistedJsonIssue> = emptyList()
) {
    val hasIssues: Boolean = issues.isNotEmpty()
}

val persistedJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

inline fun <reified T> decodePersistedJsonStrict(fieldName: String, rawValue: String): T =
    try {
        persistedJson.decodeFromString(rawValue)
    } catch (e: SerializationException) {
        throw PersistedDataDecodeException(fieldName, rawValue, e)
    } catch (e: IllegalArgumentException) {
        throw PersistedDataDecodeException(fieldName, rawValue, e)
    }

inline fun <reified T> decodePersistedJsonCompatible(
    fieldName: String,
    rawValue: String,
    fallback: T
): PersistedJsonResult<T> =
    try {
        PersistedJsonResult(persistedJson.decodeFromString(rawValue))
    } catch (e: SerializationException) {
        malformedPersistedJsonResult(fieldName, rawValue, fallback)
    } catch (e: IllegalArgumentException) {
        malformedPersistedJsonResult(fieldName, rawValue, fallback)
    }

fun <T> malformedPersistedJsonResult(
    fieldName: String,
    rawValue: String,
    fallback: T
): PersistedJsonResult<T> =
        PersistedJsonResult(
            value = fallback,
            issues = listOf(
                PersistedJsonIssue(
                    fieldName = fieldName,
                    rawValue = rawValue,
                    message = "Saved $fieldName is malformed and could not be decoded. The raw value will be kept until you replace it.",
                    kind = PersistedJsonIssueKind.MALFORMED_JSON
                )
            )
        )

fun decodePersistedStringList(fieldName: String, rawValue: String): PersistedJsonResult<List<String>> =
    decodePersistedJsonCompatible(fieldName, rawValue, emptyList())

inline fun <reified E : Enum<E>> decodePersistedEnumNameList(
    fieldName: String,
    rawValue: String
): PersistedJsonResult<List<E>> {
    val decoded = decodePersistedStringList(fieldName, rawValue)
    if (decoded.hasIssues) return PersistedJsonResult(emptyList(), decoded.issues)

    val known = mutableListOf<E>()
    val issues = mutableListOf<PersistedJsonIssue>()
    decoded.value.forEach { rawName ->
        val match = enumValues<E>().firstOrNull { it.name == rawName }
            ?: enumValues<E>().firstOrNull { it.name.equals(rawName, ignoreCase = true) }
        if (match != null) {
            known += match
        } else {
            issues += PersistedJsonIssue(
                fieldName = fieldName,
                rawValue = rawName,
                message = "Saved $fieldName contains an unknown ${E::class.simpleName} value: $rawName"
            )
        }
    }
    return PersistedJsonResult(known, decoded.issues + issues)
}

fun decodeTrainingPhasePresets(
    fieldName: String,
    rawValue: String
): PersistedJsonResult<Map<TrainingPhase, ExerciseProgrammingPreset>> {
    val decoded = decodePersistedJsonCompatible<Map<String, ExerciseProgrammingPreset>>(fieldName, rawValue, emptyMap())
    if (decoded.hasIssues) return PersistedJsonResult(emptyMap(), decoded.issues)

    val known = mutableMapOf<TrainingPhase, ExerciseProgrammingPreset>()
    val issues = mutableListOf<PersistedJsonIssue>()
    decoded.value.forEach { (rawPhase, preset) ->
        val phase = TrainingPhase.entries.firstOrNull { it.name == rawPhase }
            ?: TrainingPhase.entries.firstOrNull { it.name.equals(rawPhase, ignoreCase = true) }
        if (phase != null) {
            known[phase] = preset
        } else {
            issues += PersistedJsonIssue(
                fieldName = fieldName,
                rawValue = rawPhase,
                message = "Saved $fieldName contains an unknown training phase: $rawPhase"
            )
        }
    }
    return PersistedJsonResult(known, decoded.issues + issues)
}

fun decodeCategoryWeights(rawValue: String): PersistedJsonResult<Map<WorkoutCategory, Float>> {
    val decoded = decodePersistedJsonCompatible<Map<String, Float>>("category weights", rawValue, emptyMap())
    if (decoded.hasIssues) return PersistedJsonResult(emptyMap(), decoded.issues)

    val known = mutableMapOf<WorkoutCategory, Float>()
    val issues = mutableListOf<PersistedJsonIssue>()
    decoded.value.forEach { (rawCategory, weight) ->
        val category = WorkoutCategory.entries.firstOrNull { it.name == rawCategory }
            ?: WorkoutCategory.entries.firstOrNull { it.name.equals(rawCategory, ignoreCase = true) }
        if (category != null) {
            known[category] = weight
        } else {
            issues += PersistedJsonIssue(
                fieldName = "category weights",
                rawValue = rawCategory,
                message = "Saved category weights contain an unknown category: $rawCategory"
            )
        }
    }
    return PersistedJsonResult(known, decoded.issues + issues)
}

fun List<PersistedJsonIssue>.toUserMessage(): String =
    joinToString(separator = "\n") { it.message }

fun List<PersistedJsonIssue>.hasMalformedJson(): Boolean =
    any { it.kind == PersistedJsonIssueKind.MALFORMED_JSON }
