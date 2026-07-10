package com.example.workoutapp.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Codec for persisting in-progress [SetEntryDraft]s (keyed by session-exercise id) into a
 * SavedStateHandle-friendly String, so drafts survive configuration changes and process death
 * without ever being written to Room as completed sets or history.
 */
private val draftCodecJson = Json { ignoreUnknownKeys = true }

fun encodeSetEntryDrafts(drafts: Map<Long, SetEntryDraft>): String =
    draftCodecJson.encodeToString(drafts.mapKeys { it.key.toString() })

fun decodeSetEntryDrafts(encoded: String?): Map<Long, SetEntryDraft> {
    if (encoded.isNullOrBlank()) return emptyMap()
    return runCatching {
        draftCodecJson.decodeFromString<Map<String, SetEntryDraft>>(encoded)
            .mapNotNull { (key, draft) -> key.toLongOrNull()?.let { it to draft } }
            .toMap()
    }.getOrDefault(emptyMap())
}
