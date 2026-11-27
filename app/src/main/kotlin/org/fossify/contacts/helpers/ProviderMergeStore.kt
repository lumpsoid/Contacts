package org.fossify.contacts.helpers

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.databases.ContactsDatabase
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.extensions.getCachePhotoUri
import org.fossify.contacts.models.ExtraDetails
import org.fossify.contacts.models.MergeRecord
import org.fossify.contacts.models.RawAccountRow
import java.io.FileOutputStream
import java.io.IOException

private const val ID_COLUMN = 0
private const val ACCOUNT_NAME_COLUMN = 1
private const val ACCOUNT_TYPE_COLUMN = 2
private const val PHOTO_QUALITY = 100

/**
 * [MergeStore] over the system contacts provider and the private contacts database.
 * Blocking; call off the main thread.
 */
class ProviderMergeStore(private val context: Context) : MergeStore {
    override fun groupsFor(destination: ContactSource, titles: List<String>): ArrayList<Group>? {
        return DestinationGroups(context).groupsFor(destination, titles)
    }

    override fun savePhoto(photo: Bitmap): String? = try {
        val file = context.getCachePhoto()
        val saved = FileOutputStream(file).use { photo.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
        if (saved) context.getCachePhotoUri(file).toString() else null
    } catch (e: IOException) {
        context.showErrorToast(e)
        null
    } catch (e: IllegalArgumentException) {
        context.showErrorToast(e)
        null
    }

    /** Commons drops the group memberships of every raw contact linked to [target]; they are put back after. */
    override fun update(merged: Contact, target: MergeRecord): Boolean {
        val memberships = GroupMemberships(context)
        val linked = if (merged.isPrivate()) {
            emptySet()
        } else {
            memberships.ofOthersLinkedTo(target.contact) ?: return false
        }
        RawContactLoader(context).ensureNameRow(merged)
        val photo = photoUpdateStatus(target.contact.photoUri, merged.photoUri)
        if (!ContactsHelper(context).updateContact(merged, photo)) return false
        return linked.isEmpty() || memberships.restore(linked)
    }

    override fun insert(merged: Contact, destination: ContactSource): List<Int>? {
        val before = context.newestRecordId(destination)
        if (!ContactsHelper(context).insertContact(merged)) {
            // Commons can fail after its batch already created the raw contact; remove it when it is the only new one.
            val created = context.recordIdsAfter(before, destination)
            if (created.size == 1) {
                ContactsHelper(context).deleteContacts(arrayListOf(merged.copy(id = created.first())))
            }
            return null
        }
        return context.recordIdsAfter(before, destination)
    }

    override fun readBack(id: Int, destination: ContactSource): Contact? {
        if (destination.type == SMT_PRIVATE) {
            return LocalContactsHelper(context).getContactWithId(id)
        }
        val loader = RawContactLoader(context)
        return loader.getContact(id)?.also { loader.fillName(it) }
    }

    override fun addExtras(rawId: Int, extras: ExtraDetails): Boolean = ExtraDetailsStore(context).add(rawId, extras)

    override fun delete(contacts: List<Contact>): Boolean {
        return ContactsHelper(context).deleteContacts(ArrayList(contacts))
    }

    override fun link(rawIds: List<Int>): Boolean {
        return ContactLinkHelper(context).linkRawContacts(rawIds)
    }

    override fun sendsToVoicemail(rawIds: Set<Int>): Boolean {
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

    override fun setSendToVoicemail(rawId: Int) {
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

/** Highest raw id in [destination]'s store; every record inserted later is above it. */
private fun Context.newestRecordId(destination: ContactSource): Int {
    if (destination.type == SMT_PRIVATE) {
        val db = ContactsDatabase.getInstance(applicationContext).openHelper.readableDatabase
        return db.query("SELECT MAX(id) FROM contacts").use {
            if (it.moveToFirst()) it.getInt(ID_COLUMN) else 0
        }
    }
    val projection = arrayOf(RawContacts._ID)
    val sortOrder = "${RawContacts._ID} DESC"
    return try {
        contentResolver.query(RawContacts.CONTENT_URI, projection, null, null, sortOrder)?.use {
            if (it.moveToFirst()) it.getInt(ID_COLUMN) else 0
        } ?: 0
    } catch (e: SecurityException) {
        showErrorToast(e)
        0
    } catch (e: IllegalArgumentException) {
        showErrorToast(e)
        0
    }
}

/** Raw ids above [after] in [destination], ascending. */
private fun Context.recordIdsAfter(after: Int, destination: ContactSource): List<Int> {
    if (destination.type == SMT_PRIVATE) {
        val db = ContactsDatabase.getInstance(applicationContext).openHelper.readableDatabase
        val ids = ArrayList<Int>()
        db.query("SELECT id FROM contacts WHERE id > ? ORDER BY id", arrayOf(after)).use {
            while (it.moveToNext()) {
                ids.add(it.getInt(ID_COLUMN))
            }
        }
        return ids
    }
    var ids: List<Int> = emptyList()
    val projection = arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE)
    val selection = "${RawContacts.DELETED} = 0 AND ${RawContacts._ID} > ?"
    val sortOrder = "${RawContacts._ID} ASC"
    try {
        contentResolver.query(
            RawContacts.CONTENT_URI, projection, selection, arrayOf(after.toString()), sortOrder
        )?.use {
            val rows = ArrayList<RawAccountRow>()
            while (it.moveToNext()) {
                rows.add(
                    RawAccountRow(
                        it.getInt(ID_COLUMN),
                        it.getString(ACCOUNT_NAME_COLUMN) ?: "",
                        it.getString(ACCOUNT_TYPE_COLUMN) ?: ""
                    )
                )
            }
            ids = ownIds(rows, destination)
        }
    } catch (e: SecurityException) {
        showErrorToast(e)
    } catch (e: IllegalArgumentException) {
        showErrorToast(e)
    }
    return ids
}

/** The ids of the rows in the [destination] account, in row order. */
internal fun ownIds(rows: List<RawAccountRow>, destination: ContactSource): List<Int> {
    return rows
        .filter { it.accountName == destination.name && it.accountType == destination.type }
        .map { it.id }
}
