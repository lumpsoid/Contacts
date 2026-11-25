package org.fossify.contacts.helpers

import org.fossify.contacts.models.ConflictField
import org.fossify.contacts.models.MatchConfidence
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeKind
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.ScanResult
import org.fossify.contacts.models.ScannedPerson

/** What the user picked on the review screen: which groups and people merge, and each group's choices. */
class DedupeSelection(val result: ScanResult) {
    private val selected = HashSet<String>()
    private val excluded = HashSet<String>()
    private val choices = HashMap<String, MergeChoices>()

    init {
        selected.addAll(result.duplicates.filter { it.confidence == MatchConfidence.HIGH }.map { it.key })
        selected.addAll(result.splitDetails.map { it.key })
    }

    fun isSelected(key: String) = key in selected

    fun setSelected(key: String, value: Boolean) {
        if (value) selected.add(key) else selected.remove(key)
    }

    fun isIncluded(key: String, personKey: String) = "$key$SEP$personKey" !in excluded

    fun setIncluded(key: String, personKey: String, value: Boolean) {
        val id = "$key$SEP$personKey"
        if (value) excluded.remove(id) else excluded.add(id)
    }

    fun choicesOf(key: String) = choices[key] ?: MergeChoices()

    fun setChoices(key: String, value: MergeChoices) {
        choices[key] = value
    }

    fun recordsOf(key: String, people: List<ScannedPerson>): List<MergeRecord> =
        people.filter { isIncluded(key, it.key) }.flatMap { it.records }

    /** Plans for every selected group with 2+ included people and every selected split detail. */
    fun plans(): List<MergePlan> {
        val duplicates = result.duplicates
            .filter { isSelected(it.key) && it.people.count { person -> isIncluded(it.key, person.key) } >= 2 }
            .map { MergePlan(it.key, MergeKind.DUPLICATES, recordsOf(it.key, it.people), choicesOf(it.key)) }
        val splits = result.splitDetails
            .filter { isSelected(it.key) }
            .map { MergePlan(it.key, MergeKind.SPLIT_DETAILS, it.person.records, choicesOf(it.key)) }
        return duplicates + splits
    }

    /** Display name of each plan's group, by plan key. */
    fun planNames(): Map<String, String> =
        result.duplicates.associate { it.key to it.people.first().displayName() } +
            result.splitDetails.associate { it.key to it.person.displayName() }

    /** Lines: "S<sep>key", "X<sep>key<sep>personKey", "C<sep>key<sep>FIELD<sep>recordKey", "B<sep>key". */
    fun saveState(): ArrayList<String> {
        val state = ArrayList<String>()
        for (key in selected) state.add("S$SEP$key")
        for (entry in excluded) state.add("X$SEP$entry")
        for ((key, value) in choices) {
            for ((field, recordKey) in value.picks) state.add("C$SEP$key$SEP${field.name}$SEP$recordKey")
            if (value.keepBothNotes) state.add("B$SEP$key")
        }
        return state
    }

    /** Replaces the selection with [state] from [saveState]; unknown lines are ignored. */
    fun restoreState(state: List<String>) {
        selected.clear()
        excluded.clear()
        choices.clear()
        for (parts in state.map { it.split(SEP) }.filter { it.size >= 2 }) {
            val key = parts[1]
            val args = parts.drop(2)
            when (parts[0]) {
                "S" -> selected.add(key)
                "X" -> args.firstOrNull()?.let { excluded.add("$key$SEP$it") }
                "C" -> {
                    val field = ConflictField.entries.firstOrNull { it.name == args.getOrNull(0) }
                    val recordKey = args.getOrNull(1)
                    if (field != null && recordKey != null) choices[key] = choicesOf(key).pick(field, recordKey)
                }
                "B" -> choices[key] = choicesOf(key).withKeepBothNotes(true)
            }
        }
    }

    companion object {
        private const val SEP = '\u001F'
    }
}
