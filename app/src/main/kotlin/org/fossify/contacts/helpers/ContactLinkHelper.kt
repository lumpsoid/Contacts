package org.fossify.contacts.helpers

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.models.contacts.Contact

/**
 * Links and unlinks device raw contacts in the system contacts provider, so other apps
 * see linked raw contacts as one contact too.
 */
class ContactLinkHelper(private val context: Context) {
    companion object {
        private const val BATCH_SIZE = 400
    }

    fun getLinkedRawContactIds(contactId: Int): List<Int> {
        val ids = ArrayList<Int>()
        val projection = arrayOf(RawContacts._ID)
        val selection = "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0"
        val selectionArgs = arrayOf(contactId.toString())
        try {
            context.contentResolver.query(RawContacts.CONTENT_URI, projection, selection, selectionArgs, null)?.use {
                while (it.moveToNext()) {
                    ids.add(it.getInt(0))
                }
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
        return ids
    }

    fun linkRawContacts(rawIds: List<Int>): Boolean {
        val ids = rawIds.distinct()
        val pairs = ids.drop(1).map { Pair(ids.first(), it) }
        return applyAggregationType(pairs, AggregationExceptions.TYPE_KEEP_TOGETHER)
    }

    fun unlinkRawContacts(rawIds: List<Int>): Boolean {
        val ids = rawIds.distinct()
        val pairs = ids.flatMapIndexed { index, first -> ids.drop(index + 1).map { Pair(first, it) } }
        return applyAggregationType(pairs, AggregationExceptions.TYPE_KEEP_SEPARATE)
    }

    private fun applyAggregationType(pairs: List<Pair<Int, Int>>, type: Int): Boolean {
        if (pairs.isEmpty()) {
            return false
        }

        val operations = pairs.map { (first, second) ->
            ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                .withValue(AggregationExceptions.TYPE, type)
                .withValue(AggregationExceptions.RAW_CONTACT_ID1, first.toLong())
                .withValue(AggregationExceptions.RAW_CONTACT_ID2, second.toLong())
                .build()
        }

        return try {
            operations.chunked(BATCH_SIZE).forEach {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(it))
            }
            true
        } catch (e: Exception) {
            context.showErrorToast(e)
            false
        }
    }
}

/** True when both are device contacts aggregated into the same system contact. */
fun Contact.isLinkedTo(other: Contact): Boolean {
    return !isPrivate() && !other.isPrivate() && contactId != 0 && contactId == other.contactId
}

/** Keeps one row per system contact; private contacts and contacts without a contactId stay as they are. */
fun List<Contact>.distinctByLinkedContact(): List<Contact> {
    return distinctBy {
        if (it.isPrivate() || it.contactId == 0) "raw:${it.source}:${it.id}" else "contact:${it.contactId}"
    }
}
