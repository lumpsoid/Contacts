package org.fossify.contacts.helpers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.extensions.getCachePhotoUri
import org.fossify.contacts.models.MergeRecord
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
        FileOutputStream(file).use { photo.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
        context.getCachePhotoUri(file).toString()
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
        return linked.isEmpty() || memberships.restore(target.contact, linked)
    }

    override fun insert(merged: Contact, destination: ContactSource): Int? {
        val before = newestRecordId(destination)
        if (!ContactsHelper(context).insertContact(merged)) return null
        return newestRecordId(destination).takeIf { it > before }
    }

    override fun readBack(id: Int, destination: ContactSource): Contact? {
        if (destination.type == SMT_PRIVATE) {
            return LocalContactsHelper(context).getContactWithId(id)
        }
        val loader = RawContactLoader(context)
        return loader.getContact(id)?.also { loader.fillName(it) }
    }

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
                newest = firstOwnId(it, destination)
            }
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        } catch (e: IllegalArgumentException) {
            context.showErrorToast(e)
        }
        return newest
    }
}

/** The id of the first row of [cursor] in the [destination] account, or 0 when there is none. */
private fun firstOwnId(cursor: Cursor, destination: ContactSource): Int {
    while (cursor.moveToNext()) {
        val accountName = cursor.getString(ACCOUNT_NAME_COLUMN) ?: ""
        val accountType = cursor.getString(ACCOUNT_TYPE_COLUMN) ?: ""
        if (accountName == destination.name && accountType == destination.type) {
            return cursor.getInt(ID_COLUMN)
        }
    }
    return 0
}
