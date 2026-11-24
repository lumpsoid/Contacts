package org.fossify.contacts.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.helpers.RawContactLoader
import org.fossify.contacts.helpers.addNamelessContacts
import org.fossify.contacts.helpers.namelessDuplicatesOf
import org.fossify.contacts.helpers.namelessRules

/** Commons getContactWithId misses device raw contacts without a name or organization row; load those too. */
fun Context.getContactWithIdOrRaw(id: Int, isPrivate: Boolean): Contact? {
    val contact = ContactsHelper(this).getContactWithId(id, isPrivate)
    return if (contact != null || isPrivate) contact else RawContactLoader(this).getContact(id)
}

/** Commons getContacts, plus the device raw contacts it skips for having no name or organization row. */
fun Context.getContactsWithNameless(
    getAll: Boolean = false,
    ignoredContactSources: HashSet<String> = HashSet(),
    callback: (ArrayList<Contact>) -> Unit
) {
    ContactsHelper(this).getContacts(getAll, false, ignoredContactSources) { contacts ->
        ensureBackgroundThread {
            val rules = namelessRules(
                getAll = getAll,
                ignoredContactSources = ignoredContactSources,
                mergeDuplicates = config.mergeDuplicateContacts,
                showOnlyWithNumbers = config.showOnlyContactsWithNumbers
            )
            val namelessIgnored = if (getAll) ignoredContactSources else baseConfig.ignoredContactSources
            val result = addNamelessContacts(
                listed = contacts,
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
