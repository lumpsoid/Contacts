package org.fossify.contacts.helpers

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
import android.database.Cursor
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.models.contacts.Contact

private const val RAW_ID_COLUMN = 0
private const val GROUP_ID_COLUMN = 1

/**
 * Group memberships of the raw contacts linked to a merge target. Commons updateContact deletes memberships
 * by aggregate contact id, so it also strips the raw contacts the merge keeps linked; this puts them back.
 */
class GroupMemberships(private val context: Context) {
    /** (raw id, group row id) of every raw contact in [target]'s aggregate but [target]; null when unreadable. */
    fun ofOthersLinkedTo(target: Contact): Set<Pair<Int, Long>>? {
        if (target.contactId == 0) return emptySet()
        val selection = "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} != ?"
        val selectionArgs = arrayOf(
            target.contactId.toString(),
            CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
            target.id.toString()
        )
        return queryPairs(selection, selectionArgs)
    }

    /** Group row ids [rawId] is a member of; null when unreadable. */
    fun groupIdsOf(rawId: Int): Set<Long>? {
        val selection = "${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} = ?"
        val selectionArgs = arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, rawId.toString())
        return queryPairs(selection, selectionArgs)?.map { it.second }?.toSet()
    }

    /**
     * Re-adds the memberships of [before] that are missing now; false when that failed. Reads by the raw contacts
     * of [before], not by aggregate, so an aggregate id that changed during the write does not matter.
     */
    fun restore(before: Set<Pair<Int, Long>>): Boolean {
        if (before.isEmpty()) return true
        val rawIds = before.map { it.first }.distinct()
        val placeholders = rawIds.joinToString(",") { "?" }
        val selection = "${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} IN ($placeholders)"
        val selectionArgs = (listOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE) + rawIds.map { it.toString() })
            .toTypedArray()
        val now = queryPairs(selection, selectionArgs) ?: return false
        val missing = before - now
        if (missing.isEmpty()) return true
        val operations = ArrayList<ContentProviderOperation>()
        missing.forEach { (rawId, groupId) ->
            operations.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI).apply {
                    withValue(Data.RAW_CONTACT_ID, rawId)
                    withValue(Data.MIMETYPE, CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                    withValue(CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                }.build()
            )
        }
        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
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
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
            false
        }
    }

    /** The (raw id, group row id) pairs of the group memberships that [selection] matches; null when unreadable. */
    private fun queryPairs(selection: String, selectionArgs: Array<String>): Set<Pair<Int, Long>>? {
        val projection = arrayOf(Data.RAW_CONTACT_ID, CommonDataKinds.GroupMembership.GROUP_ROW_ID)
        return try {
            context.contentResolver.query(Data.CONTENT_URI, projection, selection, selectionArgs, null)?.use {
                readPairs(it)
            }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
            null
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
            null
        }
    }

    private fun readPairs(cursor: Cursor): Set<Pair<Int, Long>> {
        val memberships = HashSet<Pair<Int, Long>>()
        while (cursor.moveToNext()) {
            memberships.add(cursor.getInt(RAW_ID_COLUMN) to cursor.getLong(GROUP_ID_COLUMN))
        }
        return memberships
    }
}
