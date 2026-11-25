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

private val TEXT_FIELDS: List<(Contact) -> String> = listOf(
    { it.prefix }, { it.firstName }, { it.middleName }, { it.surname }, { it.suffix },
    { it.nickname }, { it.notes }, { it.organization.company }, { it.organization.jobPosition }
)

private val LIST_FIELDS: List<(Contact) -> List<String>> = listOf(
    { contact -> contact.phoneNumbers.map { n -> n.value.filter { it.isDigit() || it == '+' } } },
    { contact -> contact.emails.map { it.value } },
    { contact -> contact.addresses.map { it.value } },
    { contact -> contact.IMs.map { it.value } },
    { contact -> contact.events.map { "${it.type}:${it.value}" } },
    { contact -> contact.websites },
    { contact -> contact.groups.map { it.title } }
)

private fun String.comparable() = trim().lowercase()

/**
 * Whether [actual] holds everything the merge wrote in [expected]: each non-blank name part, nickname, notes and
 * organization field, and every number (digits and "+"), email, address, IM, event, website and group title.
 * Compared trimmed and ignoring case. Sources are deleted only when this holds.
 */
fun verifyMerged(expected: Contact, actual: Contact): Boolean {
    val textsHeld = TEXT_FIELDS.all { field ->
        field(expected).isBlank() || field(expected).comparable() == field(actual).comparable()
    }
    val listsHeld = LIST_FIELDS.all { field ->
        val held = field(actual).map { it.comparable() }.toSet()
        field(expected).map { it.comparable() }.filter { it.isNotEmpty() }.all { it in held }
    }
    return textsHeld && listsHeld
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
