package org.fossify.contacts.helpers

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.MergeFailure
import org.fossify.contacts.models.MergeOutcome
import org.fossify.contacts.models.MergePlan
import org.fossify.contacts.models.MergeRecord

private const val ID_COLUMN = 0
private const val ACCOUNT_NAME_COLUMN = 1
private const val ACCOUNT_TYPE_COLUMN = 2

/**
 * Merges one plan into the destination: write, verify, then delete the old copies and link the rest.
 * Blocking; call off the main thread.
 */
class DedupeWriter(private val context: Context) {
    /** Writes [plan] into [destination]; a failure leaves the group as far as it got. */
    fun merge(plan: MergePlan, destination: ContactSource, access: AccountAccess): MergeOutcome {
        val target = pickTarget(plan.records, destination)
        val base = target?.contact ?: Contact(id = 0, contactId = 0, source = destination.name)
        val merged = buildMergedContact(base, plan.records, plan.choices)
        val isPrivate = destination.type == SMT_PRIVATE
        val sendToVoicemail = !isPrivate && isSendToVoicemail(plan)
        val failure = if (!access.fits(merged)) {
            MergeFailure.SIM_LIMIT
        } else {
            merged.groups = DestinationGroups(context).groupsFor(destination, merged.groups.map { it.title })
            val resultId = writeVerified(merged, target, destination)
            when {
                resultId == null -> MergeFailure.WRITE_FAILED
                !ContactsHelper(context).deleteContacts(ArrayList(recordsToDelete(plan, target).map { it.contact })) ->
                    MergeFailure.DELETE_FAILED
                !link(resultId, isPrivate, rawIdsToLink(plan, target)) -> MergeFailure.LINK_FAILED
                else -> {
                    if (sendToVoicemail) setSendToVoicemail(resultId)
                    null
                }
            }
        }
        return if (failure == null) MergeOutcome.Merged(plan.key) else MergeOutcome.Failed(plan.key, failure)
    }

    /** Updates [target] or inserts [merged] new, and returns its id only when reading it back verifies. */
    private fun writeVerified(merged: Contact, target: MergeRecord?, destination: ContactSource): Int? {
        val id = if (target != null) update(merged, target) else insert(merged, destination)
        if (id == null) return null
        val written = readBack(id, destination)
        if (written != null && verifyMerged(merged, written)) return id
        if (target == null) ContactsHelper(context).deleteContacts(arrayListOf(merged.copy(id = id)))
        return null
    }

    private fun update(merged: Contact, target: MergeRecord): Int? {
        RawContactLoader(context).ensureNameRow(merged)
        val photo = photoUpdateStatus(target.contact.photoUri, merged.photoUri)
        return if (ContactsHelper(context).updateContact(merged, photo)) target.contact.id else null
    }

    private fun insert(merged: Contact, destination: ContactSource): Int? {
        val before = newestRecordId(destination)
        if (!ContactsHelper(context).insertContact(merged)) return null
        return newestRecordId(destination).takeIf { it > before }
    }

    /** The largest raw id in the [destination] account, or 0 when there is none. */
    private fun newestRecordId(destination: ContactSource): Int {
        if (destination.type == SMT_PRIVATE) {
            return LocalContactsHelper(context).getAllContacts().maxOfOrNull { it.id } ?: 0
        }
        var newest = 0
        val projection = arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE)
        val selection = "${RawContacts.DELETED} = 0"
        val sortOrder = "${RawContacts._ID} DESC"
        try {
            context.contentResolver.query(RawContacts.CONTENT_URI, projection, selection, null, sortOrder)?.use {
                while (it.moveToNext()) {
                    val accountName = it.getString(ACCOUNT_NAME_COLUMN) ?: ""
                    val accountType = it.getString(ACCOUNT_TYPE_COLUMN) ?: ""
                    if (accountName == destination.name && accountType == destination.type) {
                        newest = it.getInt(ID_COLUMN)
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
        }
        return newest
    }

    private fun readBack(id: Int, destination: ContactSource): Contact? {
        if (destination.type == SMT_PRIVATE) {
            return LocalContactsHelper(context).getContactWithId(id)
        }
        return RawContactLoader(context).getContact(id)
    }

    /** Private contacts are not in the system provider, so they cannot be linked. */
    private fun link(resultId: Int, isPrivate: Boolean, rawIds: List<Int>): Boolean {
        return isPrivate || rawIds.isEmpty() || ContactLinkHelper(context).linkRawContacts(listOf(resultId) + rawIds)
    }

    /** Whether any device raw contact of [plan] already sends calls to voicemail. */
    private fun isSendToVoicemail(plan: MergePlan): Boolean {
        val rawIds = plan.records.filter { !it.contact.isPrivate() }.map { it.contact.id }.toSet()
        var found = false
        val projection = arrayOf(RawContacts._ID)
        val selection = "${RawContacts.SEND_TO_VOICEMAIL} = 1"
        try {
            context.contentResolver.query(RawContacts.CONTENT_URI, projection, selection, null, null)?.use {
                while (!found && it.moveToNext()) {
                    found = it.getInt(ID_COLUMN) in rawIds
                }
            }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
        }
        return found
    }

    private fun setSendToVoicemail(rawId: Int) {
        val values = ContentValues().apply { put(RawContacts.SEND_TO_VOICEMAIL, 1) }
        try {
            context.contentResolver.update(
                RawContacts.CONTENT_URI,
                values,
                "${RawContacts._ID} = ?",
                arrayOf(rawId.toString())
            )
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
        }
    }
}
