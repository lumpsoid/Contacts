package org.fossify.contacts.helpers

import android.content.Context
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.models.contacts.Contact

/**
 * Merges contacts stored only in this app (phone storage, not visible by other apps) into the
 * first of them and deletes the rest. Not reversible.
 */
class PrivateContactMerger(private val context: Context) {
    fun merge(contacts: List<Contact>): Boolean {
        val helper = LocalContactsHelper(context)
        val loaded = contacts.filter { it.isPrivate() }.mapNotNull { helper.getContactWithId(it.id) }
        if (loaded.size < 2) {
            return false
        }

        val others = loaded.drop(1)
        val merged = mergeContactData(loaded.first(), others)
        if (!helper.insertOrUpdateContact(merged)) {
            return false
        }

        helper.deleteContactIds(others.map { it.id.toLong() }.toMutableList())
        return true
    }
}

fun mergeContactData(target: Contact, others: List<Contact>): Contact {
    val all = listOf(target) + others
    fun pick(field: (Contact) -> String) = all.map(field).firstOrNull { it.isNotBlank() } ?: field(target)

    val photoHolder = all.firstOrNull { it.photo != null || it.photoUri.isNotEmpty() } ?: target
    val organizationHolder = all.firstOrNull {
        it.organization.company.isNotBlank() || it.organization.jobPosition.isNotBlank()
    } ?: target
    val phoneNumbers = ArrayList(target.phoneNumbers)
    others.flatMap { it.phoneNumbers }.forEach { number ->
        val key = number.normalizedNumber.ifEmpty { number.value }
        if (phoneNumbers.none { it.normalizedNumber.ifEmpty { it.value } == key }) {
            phoneNumbers.add(number.copy(isPrimary = false))
        }
    }

    return target.copy(
        prefix = pick { it.prefix },
        firstName = pick { it.firstName },
        middleName = pick { it.middleName },
        surname = pick { it.surname },
        suffix = pick { it.suffix },
        nickname = pick { it.nickname },
        notes = pick { it.notes },
        photo = photoHolder.photo,
        photoUri = photoHolder.photoUri,
        phoneNumbers = phoneNumbers,
        emails = ArrayList(all.flatMap { it.emails }.distinctBy { it.value.trim().lowercase() }),
        addresses = ArrayList(all.flatMap { it.addresses }.distinctBy { it.value.trim().lowercase() }),
        events = ArrayList(all.flatMap { it.events }.distinctBy { "${it.type}:${it.value}" }),
        IMs = ArrayList(all.flatMap { it.IMs }.distinctBy { it.value.trim().lowercase() }),
        websites = ArrayList(all.flatMap { it.websites }.distinct()),
        groups = ArrayList(all.flatMap { it.groups }.distinctBy { it.id }),
        organization = organizationHolder.organization,
        starred = all.maxOf { it.starred },
        ringtone = all.map { it.ringtone }.firstOrNull { !it.isNullOrBlank() } ?: target.ringtone
    )
}
