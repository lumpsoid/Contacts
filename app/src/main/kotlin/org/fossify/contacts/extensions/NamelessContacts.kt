package org.fossify.contacts.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.helpers.ContactLinkHelper
import org.fossify.contacts.helpers.RawContactLoader
import org.fossify.contacts.helpers.addNamelessContacts
import org.fossify.contacts.helpers.mergeDuplicatesByName
import org.fossify.contacts.helpers.namelessDuplicatesOf
import org.fossify.contacts.helpers.namelessRules

/** Commons getContactWithId misses device raw contacts without a name or organization row; load those too. */
fun Context.getContactWithIdOrRaw(id: Int, isPrivate: Boolean): Contact? {
    val contact = ContactsHelper(this).getContactWithId(id, isPrivate)
    return if (contact != null || isPrivate) contact else RawContactLoader(this).getContact(id)
}

/**
 * Commons getContacts, plus the device raw contacts it skips for having no name or organization row.
 * Backups and exports pass showOnlyContactsWithNumbers = false so the list setting never drops contacts from them.
 * With merging on, contacts with the same name are merged here rather than in commons, so ones the system keeps
 * apart stay apart.
 */
fun Context.getContactsWithNameless(
    getAll: Boolean = false,
    ignoredContactSources: HashSet<String> = HashSet(),
    showOnlyContactsWithNumbers: Boolean = config.showOnlyContactsWithNumbers,
    callback: (ArrayList<Contact>) -> Unit
) {
    // commons merges same-name contacts only in this case; merge here instead,
    // so contacts the system keeps apart stay apart
    val mergeByName = config.mergeDuplicateContacts && ignoredContactSources.isEmpty() && !getAll
    val loadAll = getAll || mergeByName
    ContactsHelper(this).getContacts(loadAll, false, ignoredContactSources, showOnlyContactsWithNumbers) { contacts ->
        ensureBackgroundThread {
            val listed = if (mergeByName) {
                val visibleSources = getVisibleContactSources()
                mergeDuplicatesByName(
                    contacts,
                    ContactLinkHelper(this).getKeptSeparatePairs(),
                    visibleSources
                )
            } else {
                contacts
            }
            val rules = namelessRules(
                getAll = getAll,
                ignoredContactSources = ignoredContactSources,
                mergeDuplicates = config.mergeDuplicateContacts,
                showOnlyWithNumbers = showOnlyContactsWithNumbers
            )
            val namelessIgnored = if (getAll) ignoredContactSources else baseConfig.ignoredContactSources
            val result = addNamelessContacts(
                listed = listed,
                nameless = RawContactLoader(this).getNamelessContacts(namelessIgnored),
                mergeLinked = rules.mergeLinked,
                onlyWithNumbers = rules.onlyWithNumbers
            )
            result.sort()
            Handler(Looper.getMainLooper()).post { callback(result) }
        }
    }
}

/** Commons getDuplicatesOfContact, plus the nameless raw contacts it cannot load. */
fun Context.getDuplicatesOfContactWithNameless(contact: Contact, callback: (ArrayList<Contact>) -> Unit) {
    ContactsHelper(this).getDuplicatesOfContact(contact, false) { duplicates ->
        ensureBackgroundThread {
            val nameless = RawContactLoader(this).getNamelessContacts(HashSet())
            val result = ArrayList(duplicates + namelessDuplicatesOf(contact, nameless))
            Handler(Looper.getMainLooper()).post { callback(result) }
        }
    }
}
