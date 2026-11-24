package org.fossify.contacts.helpers

import org.fossify.commons.helpers.PHOTO_ADDED
import org.fossify.commons.helpers.PHOTO_CHANGED
import org.fossify.commons.helpers.PHOTO_UNCHANGED
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.MergeKind
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.MergeSummary

/** The writable record in [destination] with most numbers and emails, then lowest id; null when there is none. */
fun pickTarget(records: List<MergeRecord>, destination: ContactSource): MergeRecord? =
    records.filter { it.isWritable() && it.isIn(destination) }
        .sortedWith(
            compareByDescending<MergeRecord> { it.contact.phoneNumbers.size + it.contact.emails.size }
                .thenBy { it.contact.id }
        )
        .firstOrNull()

/** Writable records to delete: every one but the [target] for DUPLICATES, none for SPLIT_DETAILS. */
fun recordsToDelete(plan: MergePlan, target: MergeRecord?): List<MergeRecord> =
    if (plan.kind == MergeKind.SPLIT_DETAILS) {
        emptyList()
    } else {
        plan.records.filter { it.isWritable() && it.key != target?.key }
    }

/** Raw ids of the device records that stay, except the [target], to link to the result. */
fun rawIdsToLink(plan: MergePlan, target: MergeRecord?): List<Int> {
    val deleted = recordsToDelete(plan, target).map { it.key }.toSet()
    return plan.records
        .filter { !it.contact.isPrivate() && it.key != target?.key && it.key !in deleted }
        .map { it.contact.id }
}

/** The run's summary, with the target of each plan picked in [destination] the way the engine picks it. */
fun summarize(plans: List<MergePlan>, destination: ContactSource) = MergeSummary(
    groups = plans.size,
    records = plans.sumOf { it.records.size },
    deletedPerAccount = plans
        .flatMap { recordsToDelete(it, pickTarget(it.records, destination)) }
        .groupingBy { it.contact.source }
        .eachCount()
)

/** Whether [actual] holds every number (digits and "+") and every email of [expected]. */
fun verifyMerged(expected: Contact, actual: Contact): Boolean {
    fun numbers(contact: Contact) =
        contact.phoneNumbers.map { n -> n.value.filter { it.isDigit() || it == '+' } }.toSet()
    fun emails(contact: Contact) = contact.emails.map { it.value.trim().lowercase() }.toSet()
    return numbers(actual).containsAll(numbers(expected)) && emails(actual).containsAll(emails(expected))
}

/** The commons photo status of an update: a photo is never removed, an equal or empty new uri is unchanged. */
fun photoUpdateStatus(oldUri: String, newUri: String) = when {
    newUri.isEmpty() || newUri == oldUri -> PHOTO_UNCHANGED
    oldUri.isEmpty() -> PHOTO_ADDED
    else -> PHOTO_CHANGED
}

/** The writable source holding every writable record of [plans], else the one named [lastUsed], else the first. */
fun preferredDestination(plans: List<MergePlan>, writable: List<ContactSource>, lastUsed: String): ContactSource? {
    val records = plans.flatMap { it.records }.filter { it.isWritable() }
    return writable.firstOrNull { source -> records.isNotEmpty() && records.all { it.isIn(source) } }
        ?: writable.firstOrNull { it.name == lastUsed }
        ?: writable.firstOrNull()
}
