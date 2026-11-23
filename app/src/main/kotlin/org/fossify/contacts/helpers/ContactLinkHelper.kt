package org.fossify.contacts.helpers

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.os.RemoteException
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
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
        }
        return ids
    }

    fun getLinkedRawContactIdsOf(contacts: List<Contact>): List<Int> {
        return contacts
            .filter { !it.isPrivate() && it.contactId != 0 }
            .flatMap { getLinkedRawContactIds(it.contactId) }
            .distinct()
    }

    fun deleteRawContacts(rawIds: List<Int>): Boolean {
        if (rawIds.isEmpty()) {
            return true
        }

        val selection = "${RawContacts._ID} = ?"
        val operations = rawIds.distinct().map {
            ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                .withSelection(selection, arrayOf(it.toString()))
                .build()
        }

        return applyInBatches(operations)
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

        return applyInBatches(operations)
    }

    private fun applyInBatches(operations: List<ContentProviderOperation>): Boolean {
        return try {
            operations.chunked(BATCH_SIZE).forEach {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(it))
            }
            true
        } catch (e: OperationApplicationException) {
            context.showErrorToast(e)
            false
        } catch (e: RemoteException) {
            context.showErrorToast(e)
            false
        } catch (e: SecurityException) {
            context.showErrorToast(e)
            false
        }
    }
}

/** Keeps one row per system contact; private contacts and contacts without a contactId stay as they are. */
fun List<Contact>.distinctByLinkedContact(): List<Contact> {
    return distinctBy {
        if (it.isPrivate() || it.contactId == 0) "raw:${it.source}:${it.id}" else "contact:${it.contactId}"
    }
}
