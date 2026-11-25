package org.fossify.contacts.helpers

import android.graphics.Bitmap
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.models.MergeRecord

/** The contact storage a merge reads and writes: the system provider and this app's private database. */
interface MergeStore {
    /** Groups of [destination] titled [titles], created where missing; null when one could not be created. */
    fun groupsFor(destination: ContactSource, titles: List<String>): ArrayList<Group>?

    /** Saves [photo] to a file this app shares; its uri, or null when it could not be saved. */
    fun savePhoto(photo: Bitmap): String?

    /** Writes [merged] over the stored [target]; false when the write failed. */
    fun update(merged: Contact, target: MergeRecord): Boolean

    /** Inserts [merged] into [destination]; its new id, or null when the insert failed. */
    fun insert(merged: Contact, destination: ContactSource): Int?

    /** The stored record [id] of [destination], or null when it cannot be read. */
    fun readBack(id: Int, destination: ContactSource): Contact?

    /** Deletes [contacts]; false when the delete failed. */
    fun delete(contacts: List<Contact>): Boolean

    /** Links the device raw contacts [rawIds] into one contact; false when it failed. */
    fun link(rawIds: List<Int>): Boolean

    /** Whether any of the device raw contacts [rawIds] sends calls to voicemail. */
    fun sendsToVoicemail(rawIds: Set<Int>): Boolean

    fun setSendToVoicemail(rawId: Int)
}
