package org.fossify.contacts.models

import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource

/** One stored contact record (a device raw contact or a private contact) with its account type and access. */
data class MergeRecord(val contact: Contact, val accountType: String, val access: AccountAccess) {
    /** Unique over device and private records. */
    val key: String get() = "${contact.source}:${contact.id}"

    fun isWritable() = access.isWritable()

    fun isIn(account: ContactSource) = contact.source == account.name && accountType == account.type

    fun detailKeys(): Set<String> = detailKeysOf(contact)

    companion object {
        /** Normalized phone numbers as "tel:..." and trimmed lower-case emails as "mail:...". */
        fun detailKeysOf(contact: Contact): Set<String> =
            contact.phoneNumbers.map { "tel:" + it.normalizedNumber.ifEmpty { it.value } }.toSet() +
                contact.emails.map { "mail:" + it.value.trim().lowercase() }
    }
}
