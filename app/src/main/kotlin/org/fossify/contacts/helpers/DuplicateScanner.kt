package org.fossify.contacts.helpers

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import org.fossify.commons.extensions.getPrivateContactSource
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.extensions.getContactsWithNameless
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.ExtraDetails
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.ScanResult

/** Loads the visible contact records with their account access and scans them for duplicates. */
class DuplicateScanner(private val context: Context) {
    /** [callback] runs on a background thread. */
    fun scan(callback: (ScanResult) -> Unit) {
        context.getContactsWithNameless(
            getAll = true,
            ignoredContactSources = HashSet(),
            showOnlyContactsWithNumbers = false
        ) { contacts ->
            ensureBackgroundThread {
                val records = toRecords(contacts)
                val keptSeparate = ContactLinkHelper(context).getKeptSeparatePairs()
                callback(scanDuplicates(records, keptSeparate, context.getVisibleContactSources()))
            }
        }
    }

    /**
     * Accounts this app can save into, the private phone storage included, with their access. Blocking.
     * An account whose name another account also carries is left out: commons resolves the account by name.
     */
    fun writableSources(): List<Pair<ContactSource, AccountAccess>> {
        val readOnly = readOnlyAdapterTypes()
        val deviceSources = ContactsHelper(context).getDeviceContactSources().toList()
        val usable = deviceSources.filter { accountTypeOf(it.name, deviceSources) != null }
        val sources = usable + context.getPrivateContactSource()
        return sources.map { it to AccountAccess.of(it.type, readOnly) }.filter { it.second.isWritable() }
    }

    /**
     * [contacts] with their account type, access and extra details; reads the accounts and the provider,
     * so call it off the main thread.
     */
    fun toRecords(contacts: List<Contact>): List<MergeRecord> {
        val sources = ContactsHelper(context).getDeviceContactSources().toList()
        val readOnly = readOnlyAdapterTypes()
        val extras = if (contacts.all { it.isPrivate() }) {
            emptyMap<Int, ExtraDetails>()
        } else {
            ExtraDetailsStore(context).readAll()
        }
        return mergeRecordsOf(contacts, sources, readOnly, extras)
    }

    private fun readOnlyAdapterTypes(): Set<String> = ContentResolver.getSyncAdapterTypes()
        .filter { it.authority == ContactsContract.AUTHORITY && !it.supportsUploading() }
        .map { it.accountType }
        .toSet()
}

/** The type of the account named [name] among [sources]; null when none or several types carry that name. */
fun accountTypeOf(name: String, sources: List<ContactSource>): String? =
    sources.filter { it.name == name }.map { it.type }.distinct().singleOrNull()

/**
 * [contacts] as records with their account type, access and extra details. Pure: the caller loads
 * [sources], [readOnlyTypes] and [extras]. [extras] null: they could not be read.
 */
fun mergeRecordsOf(
    contacts: List<Contact>,
    sources: List<ContactSource>,
    readOnlyTypes: Set<String>,
    extras: Map<Int, ExtraDetails>?
): List<MergeRecord> {
    return contacts.map { contact ->
        val type = if (contact.isPrivate()) SMT_PRIVATE else accountTypeOf(contact.source, sources)
        val details = when {
            contact.isPrivate() -> ExtraDetails()
            extras == null -> null
            else -> extras[contact.id] ?: ExtraDetails()
        }
        MergeRecord(contact, type ?: "", AccountAccess.of(type, readOnlyTypes), details)
    }
}
