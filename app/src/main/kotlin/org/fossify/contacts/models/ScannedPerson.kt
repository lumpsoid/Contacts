package org.fossify.contacts.models

import org.fossify.commons.models.contacts.Contact

/** The visible records the system shows as one contact; a private record is a person of its own. */
data class ScannedPerson(val key: String, val records: List<MergeRecord>) {
    /** The first non-blank name, writable records first. */
    fun displayName(): String = (records.filter { it.isWritable() } + records)
        .map { it.contact.getNameToDisplay() }
        .firstOrNull { it.isNotBlank() } ?: ""

    fun deviceRawIds(): List<Int> = records.filter { !it.contact.isPrivate() }.map { it.contact.id }

    fun detailKeys(): Set<String> = records.flatMap { it.detailKeys() }.toSet()

    companion object {
        fun keyOf(contact: Contact) = if (contact.isPrivate() || contact.contactId == 0) {
            "raw:${contact.source}:${contact.id}"
        } else {
            "contact:${contact.contactId}"
        }
    }
}
