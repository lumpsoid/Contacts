package org.fossify.contacts.models

data class ScanResult(val duplicates: List<DuplicateGroup>, val splitDetails: List<SplitDetail>) {
    fun isEmpty() = duplicates.isEmpty() && splitDetails.isEmpty()

    /** Display name of each plan's group, by plan key. */
    fun planNames(): Map<String, String> =
        duplicates.associate { it.key to it.people.first().displayName() } +
            splitDetails.associate { it.key to it.person.displayName() }
}
