package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Organization
import org.fossify.contacts.models.ConflictField
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeRecord

/** The value of [field] in [record], blank when the record has none. */
fun conflictValue(record: MergeRecord, field: ConflictField): String {
    val contact = record.contact
    return when (field) {
        ConflictField.NAME -> listOf(
            contact.prefix, contact.firstName, contact.middleName, contact.surname, contact.suffix
        ).filter { it.isNotBlank() }.joinToString(" ")

        ConflictField.PHOTO -> when {
            contact.photoUri.isNotEmpty() -> contact.photoUri
            contact.photo != null -> "bitmap:${record.key}"
            else -> ""
        }

        ConflictField.BIRTHDAY -> contact.events.firstOrNull { it.type == TYPE_BIRTHDAY }?.value ?: ""
        ConflictField.ORGANIZATION -> contact.getFullCompany()
        ConflictField.NOTES -> contact.notes.trim()
    }
}

/** The fields on which [records] disagree, each with the records holding a distinct value. */
fun findConflicts(records: List<MergeRecord>): Map<ConflictField, List<MergeRecord>> =
    ConflictField.entries.associateWith { field ->
        records.filter { conflictValue(it, field).isNotBlank() }.distinctBy { conflictValue(it, field) }
    }.filterValues { it.size >= 2 }

/** The record a field comes from when the user picked none; null when no record can give it. */
fun defaultHolder(field: ConflictField, records: List<MergeRecord>): MergeRecord? {
    val holders = records.filter { conflictValue(it, field).isNotBlank() }
    val writable = holders.firstOrNull { it.isWritable() }
    return when (field) {
        ConflictField.NAME, ConflictField.PHOTO -> writable
        else -> writable ?: holders.firstOrNull()
    }
}

/** The record the user picked for [field] if it is one of [records], else the default holder. */
fun holderOf(field: ConflictField, records: List<MergeRecord>, choices: MergeChoices): MergeRecord? {
    val picked = choices.picks[field]
    return records.firstOrNull { it.key == picked } ?: defaultHolder(field, records)
}

/**
 * One contact holding the data of [records]. Identity (id, contactId, source, mimetype) comes from
 * [base]; name, photo, birthday, organization and notes from the chosen or default holder; numbers,
 * emails, addresses, IMs, websites, other events and group titles from every record, each once.
 */
fun buildMergedContact(base: Contact, records: List<MergeRecord>, choices: MergeChoices): Contact {
    val contacts = records.map { it.contact }
    val nameFrom = holderOf(ConflictField.NAME, records, choices)?.contact
    val photoFrom = holderOf(ConflictField.PHOTO, records, choices)?.contact
    val birthday = holderOf(ConflictField.BIRTHDAY, records, choices)?.contact?.events
        ?.firstOrNull { it.type == TYPE_BIRTHDAY }
    val organizationFrom = holderOf(ConflictField.ORGANIZATION, records, choices)?.contact
    val notesFrom = holderOf(ConflictField.NOTES, records, choices)?.contact
    val keptNotes = contacts.map { it.notes.trim() }.filter { it.isNotEmpty() }.distinct()
    val otherEvents = contacts.flatMap { it.events }.filter { it.type != TYPE_BIRTHDAY } + listOfNotNull(birthday)
    return base.copy(
        prefix = nameFrom?.prefix ?: "",
        firstName = nameFrom?.firstName ?: "",
        middleName = nameFrom?.middleName ?: "",
        surname = nameFrom?.surname ?: "",
        suffix = nameFrom?.suffix ?: "",
        nickname = contacts.map { it.nickname }.firstOrNull { it.isNotBlank() } ?: "",
        notes = if (choices.keepBothNotes) keptNotes.joinToString("\n\n") else (notesFrom?.notes ?: ""),
        photoUri = photoFrom?.photoUri ?: "",
        thumbnailUri = photoFrom?.thumbnailUri ?: "",
        photo = photoFrom?.photo,
        phoneNumbers = mergedPhoneNumbers(contacts),
        emails = ArrayList(contacts.flatMap { it.emails }.distinctBy { it.value.trim().lowercase() }),
        addresses = ArrayList(contacts.flatMap { it.addresses }.distinctBy { it.value.trim().lowercase() }),
        IMs = ArrayList(contacts.flatMap { it.IMs }.distinctBy { it.value.trim().lowercase() }),
        websites = ArrayList(contacts.flatMap { it.websites }.distinct()),
        events = ArrayList(otherEvents.distinctBy { "${it.type}:${it.value}" }),
        groups = ArrayList(contacts.flatMap { it.groups }.distinctBy { it.title }),
        organization = organizationFrom?.organization?.copy() ?: Organization("", ""),
        starred = contacts.maxOfOrNull { it.starred } ?: 0,
        ringtone = contacts.map { it.ringtone }.firstOrNull { !it.isNullOrBlank() } ?: base.ringtone
    )
}

/** Numbers of [contacts] in order, each normalized number once; the first primary copy of any number stays primary. */
private fun mergedPhoneNumbers(contacts: List<Contact>): ArrayList<PhoneNumber> {
    val keys = ArrayList<String>()
    val numbers = ArrayList<PhoneNumber>()
    var primaryKept = false
    contacts.flatMap { it.phoneNumbers }.forEach { number ->
        val key = number.normalizedNumber.ifEmpty { number.value }
        val index = keys.indexOf(key)
        val primary = number.isPrimary && !primaryKept
        primaryKept = primaryKept || primary
        if (index < 0) {
            keys.add(key)
            numbers.add(number.copy(isPrimary = primary))
        } else if (primary) {
            numbers[index] = numbers[index].copy(isPrimary = true)
        }
    }
    return numbers
}

/** Whether a merged photo held only as a bitmap must be saved to a file first: device accounts read only the uri. */
fun needsPhotoFile(photoUri: String, hasBitmap: Boolean, toPrivate: Boolean) =
    hasBitmap && photoUri.isEmpty() && !toPrivate
