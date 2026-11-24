package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact
import java.util.Locale

/** A keep-separate pair of device raw contact ids, smaller id first. */
fun keptSeparatePair(first: Int, second: Int): Pair<Int, Int> =
    if (first <= second) Pair(first, second) else Pair(second, first)

/** Ids of the device raw contacts in [group] that the system keeps apart from another one of [group]. */
fun keptSeparateIds(group: List<Contact>, keptSeparate: Set<Pair<Int, Int>>): Set<Int> {
    val ids = group.filter { !it.isPrivate() }.map { it.id }.distinct()
    return ids.filter { id ->
        ids.any { other -> other != id && keptSeparate.contains(keptSeparatePair(id, other)) }
    }.toSet()
}

/**
 * Commons' merge of contacts with the same name, except that device raw contacts the system
 * keeps apart (see [keptSeparateIds]) each keep their own row. The result is not sorted.
 */
fun mergeDuplicatesByName(contacts: List<Contact>, keptSeparate: Set<Pair<Int, Int>>): ArrayList<Contact> {
    val result = ArrayList<Contact>()
    contacts.groupBy { it.getNameToDisplay().lowercase(Locale.getDefault()) }.values.forEach { group ->
        val separate = keptSeparateIds(group, keptSeparate)
        val (apart, together) = group.partition { !it.isPrivate() && separate.contains(it.id) }
        result.addAll(apart)
        if (together.isNotEmpty()) {
            result.add(together.sortedByDescending { it.getStringToCompare().length }.first())
        }
    }

    val groupsByContactId = contacts
        .filter { !it.isPrivate() && it.contactId != 0 && it.groups.isNotEmpty() }
        .groupBy { it.contactId }
        .mapValues { it.value.first().groups }
    val deviceRows = result.filter { !it.isPrivate() && it.contactId != 0 }
    groupsByContactId.forEach { (contactId, groups) ->
        val rows = deviceRows.filter { it.contactId == contactId }
        if (rows.none { it.groups.isNotEmpty() }) {
            rows.firstOrNull()?.let { it.groups = groups }
        }
    }
    return result
}

/**
 * The name duplicates of [contact] that merge with it, by the same rule as [mergeDuplicatesByName] over the
 * contacts of [visibleSources]. A hidden duplicate kept apart from [contact] itself is left out too.
 */
fun withoutKeptSeparate(
    contact: Contact,
    duplicates: List<Contact>,
    keptSeparate: Set<Pair<Int, Int>>,
    visibleSources: Collection<String>
): List<Contact> {
    val separate = keptSeparateIds((duplicates + contact).filter { visibleSources.contains(it.source) }, keptSeparate)
    if (!contact.isPrivate() && separate.contains(contact.id)) {
        return emptyList()
    }
    return duplicates.filter {
        it.isPrivate() || (!separate.contains(it.id) && !isKeptApart(contact, it, keptSeparate))
    }
}

private fun isKeptApart(first: Contact, second: Contact, keptSeparate: Set<Pair<Int, Int>>) =
    !first.isPrivate() && !second.isPrivate() && keptSeparate.contains(keptSeparatePair(first.id, second.id))
