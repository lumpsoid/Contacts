package org.fossify.contacts.helpers

import android.graphics.Bitmap
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Group
import org.fossify.contacts.models.MergeRecord

/** A [MergeStore] that records each call in order and answers with what the test set up. */
class FakeMergeStore : MergeStore {
    val calls = ArrayList<String>()
    var groups: ArrayList<Group>? = ArrayList()
    var updateWorks = true
    var newIds: List<Int>? = listOf(NEW_ID)
    var deleteWorks = true
    var linkWorks = true
    var failUpdateWith: RuntimeException? = null
    val storedById = HashMap<Int, Contact>()
    private var written: Contact? = null

    override fun groupsFor(destination: ContactSource, titles: List<String>): ArrayList<Group>? {
        calls.add("groups")
        return groups
    }

    override fun savePhoto(photo: Bitmap): String? = null

    override fun update(merged: Contact, target: MergeRecord): Boolean {
        failUpdateWith?.let { throw it }
        calls.add("update:${target.contact.id}")
        written = merged
        return updateWorks
    }

    override fun insert(merged: Contact, destination: ContactSource): List<Int>? {
        calls.add("insert")
        written = merged
        return newIds
    }

    override fun readBack(id: Int, destination: ContactSource): Contact? {
        calls.add("readBack:$id")
        return storedById[id] ?: written?.copy(id = id)
    }

    override fun delete(contacts: List<Contact>): Boolean {
        calls.add("delete:" + contacts.joinToString(",") { it.id.toString() })
        return deleteWorks
    }

    override fun link(rawIds: List<Int>): Boolean {
        calls.add("link:" + rawIds.joinToString(","))
        return linkWorks
    }

    override fun sendsToVoicemail(rawIds: Set<Int>): Boolean = false

    override fun setSendToVoicemail(rawId: Int) {
        calls.add("voicemail:$rawId")
    }

    fun deletes() = calls.filter { it.startsWith("delete") }

    fun links() = calls.filter { it.startsWith("link") }

    companion object {
        const val NEW_ID = 100
    }
}
