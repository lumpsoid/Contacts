package org.fossify.contacts.extensions

import android.content.Context
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.helpers.ContactLinkHelper
import org.fossify.contacts.helpers.withoutKeptSeparate

/**
 * Commons getDuplicatesOfContactWithNameless, without the raw contacts the system keeps apart
 * (see withoutKeptSeparate). Calls back on a background thread.
 */
fun Context.getDuplicatesKeptTogether(contact: Contact, addOriginal: Boolean, callback: (ArrayList<Contact>) -> Unit) {
    getDuplicatesOfContactWithNameless(contact) { duplicates ->
        ensureBackgroundThread {
            val result = ArrayList<Contact>()
            if (addOriginal) {
                result.add(contact)
            }
            result.addAll(withoutKeptSeparate(contact, duplicates, ContactLinkHelper(this).getKeptSeparatePairs()))
            callback(result)
        }
    }
}
