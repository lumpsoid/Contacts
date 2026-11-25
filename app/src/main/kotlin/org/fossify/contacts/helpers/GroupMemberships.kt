package org.fossify.contacts.helpers

import android.content.ContentProviderOperation
import android.content.Context
import android.content.OperationApplicationException
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
        val projection = arrayOf(Data.RAW_CONTACT_ID, CommonDataKinds.GroupMembership.GROUP_ROW_ID)
        val selection = "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} != ?"
        val selectionArgs = arrayOf(
            target.contactId.toString(),
            CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
            target.id.toString()
        )
        return try {
            context.contentResolver.query(Data.CONTENT_URI, projection, selection, selectionArgs, null)?.use {
                val memberships = HashSet<Pair<Int, Long>>()
                while (it.moveToNext()) {
                    memberships.add(it.getInt(RAW_ID_COLUMN) to it.getLong(GROUP_ID_COLUMN))
                }
                memberships
            }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
            null
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
            null
        }
    }

    /** Re-adds the memberships of [before] that are missing now; false when that failed. */
    fun restore(target: Contact, before: Set<Pair<Int, Long>>): Boolean {
        val now = ofOthersLinkedTo(target) ?: return false
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
}
