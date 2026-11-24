package org.fossify.contacts.models

/** People with the same normalized name that the scan suggests merging. [key] identifies the group. */
data class DuplicateGroup(val key: String, val confidence: MatchConfidence, val people: List<ScannedPerson>) {
    fun records(): List<MergeRecord> = people.flatMap { it.records }
}
