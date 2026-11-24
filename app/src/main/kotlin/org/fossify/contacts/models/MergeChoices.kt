package org.fossify.contacts.models

/** The user's picks for one merge: field -> key of the record to take it from; notes may keep every text. */
data class MergeChoices(val picks: Map<ConflictField, String> = emptyMap(), val keepBothNotes: Boolean = false) {
    fun pick(field: ConflictField, recordKey: String) = copy(picks = picks + (field to recordKey))

    fun withKeepBothNotes(keep: Boolean) =
        copy(keepBothNotes = keep, picks = if (keep) picks - ConflictField.NOTES else picks)
}
