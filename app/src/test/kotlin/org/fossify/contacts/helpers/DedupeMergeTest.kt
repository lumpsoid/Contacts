package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Event
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.ConflictField
import org.fossify.contacts.models.MergeChoices
import org.fossify.contacts.models.MergeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupeMergeTest {
    private fun record(
        id: Int,
        name: String,
        access: AccountAccess = AccountAccess.WRITABLE,
        numbers: List<String> = emptyList(),
        notes: String = "",
        photoUri: String = "",
        events: List<Event> = emptyList()
    ) = MergeRecord(
        Contact(
            id = id,
            contactId = id,
            firstName = name,
            notes = notes,
            photoUri = photoUri,
            source = "acc",
            phoneNumbers = ArrayList(numbers.map { PhoneNumber(it, 0, "", it) }),
            events = ArrayList(events)
        ),
        "com.google",
        access
    )

    @Test
    fun readOnlyNameIsNotTakenByDefault() {
        val writable = record(1, "Ann")
        val readOnly = record(2, "Anna W", access = AccountAccess.READ_ONLY, numbers = listOf("333"))
        val merged = buildMergedContact(writable.contact, listOf(writable, readOnly), MergeChoices())
        assertEquals("Ann", merged.firstName)
        assertTrue(merged.phoneNumbers.any { it.value == "333" })
    }

    @Test
    fun allReadOnlyNamesGiveABlankName() {
        val first = record(1, "Anna", access = AccountAccess.READ_ONLY)
        val second = record(2, "Anna W", access = AccountAccess.READ_ONLY)
        val merged = buildMergedContact(first.contact, listOf(first, second), MergeChoices())
        assertEquals("", merged.firstName)
    }

    @Test
    fun aPickWinsOverTheDefaultName() {
        val writable = record(1, "Ann")
        val readOnly = record(2, "Anna W", access = AccountAccess.READ_ONLY)
        val choices = MergeChoices().pick(ConflictField.NAME, readOnly.key)
        val merged = buildMergedContact(writable.contact, listOf(writable, readOnly), choices)
        assertEquals("Anna W", merged.firstName)
    }

    @Test
    fun readOnlyPhotoIsNotTakenByDefault() {
        val readOnly = record(1, "Ann", access = AccountAccess.READ_ONLY, photoUri = "r.jpg")
        val writable = record(2, "Ann")
        val merged = buildMergedContact(writable.contact, listOf(readOnly, writable), MergeChoices())
        assertEquals("", merged.photoUri)
    }

    @Test
    fun writablePhotoIsTaken() {
        val writable = record(1, "Ann", photoUri = "w.jpg")
        val readOnly = record(2, "Ann", access = AccountAccess.READ_ONLY, photoUri = "r.jpg")
        val merged = buildMergedContact(writable.contact, listOf(writable, readOnly), MergeChoices())
        assertEquals("w.jpg", merged.photoUri)
    }

    @Test
    fun numbersAreDeduplicated() {
        val first = record(1, "Ann", numbers = listOf("111"))
        val second = record(2, "Ann", numbers = listOf("111", "222"))
        val merged = buildMergedContact(first.contact, listOf(first, second), MergeChoices())
        assertEquals(listOf("111", "222"), merged.phoneNumbers.map { it.value })
    }

    @Test
    fun aLaterPrimaryCopyMakesTheNumberPrimary() {
        val first = record(1, "Ann", numbers = listOf("111"))
        val second = MergeRecord(
            Contact(
                id = 2,
                contactId = 2,
                firstName = "Ann",
                source = "acc",
                phoneNumbers = arrayListOf(PhoneNumber("111", 0, "", "111", isPrimary = true))
            ),
            "com.google",
            AccountAccess.WRITABLE
        )
        val merged = buildMergedContact(first.contact, listOf(first, second), MergeChoices())
        assertEquals(listOf("111"), merged.phoneNumbers.map { it.value })
        assertEquals(listOf(true), merged.phoneNumbers.map { it.isPrimary })

        // Positive control: with two different primary numbers, only the first one stays primary.
        val other = MergeRecord(
            Contact(
                id = 3,
                contactId = 3,
                firstName = "Ann",
                source = "acc",
                phoneNumbers = arrayListOf(PhoneNumber("222", 0, "", "222", isPrimary = true))
            ),
            "com.google",
            AccountAccess.WRITABLE
        )
        val twoPrimaries = buildMergedContact(first.contact, listOf(second, other), MergeChoices())
        assertEquals(listOf(true, false), twoPrimaries.phoneNumbers.map { it.isPrimary })
    }

    @Test
    fun keepBothNotesJoinsEveryText() {
        val first = record(1, "Ann", notes = "a")
        val second = record(2, "Ann", notes = "b")
        val records = listOf(first, second)
        assertEquals("a", buildMergedContact(first.contact, records, MergeChoices()).notes)
        val both = MergeChoices().withKeepBothNotes(true)
        assertEquals("a\n\nb", buildMergedContact(first.contact, records, both).notes)
    }

    @Test
    fun findConflictsReportsOnlyDifferingNames() {
        val conflicts = findConflicts(listOf(record(1, "Ann"), record(2, "Bob")))
        assertEquals(2, conflicts.getValue(ConflictField.NAME).size)
        val same = findConflicts(listOf(record(1, "Ann"), record(2, "Ann")))
        assertFalse(same.containsKey(ConflictField.NAME))
    }

    @Test
    fun aPickedBirthdayReplacesTheOthers() {
        val first = record(1, "Ann", events = listOf(Event("1990-01-01", TYPE_BIRTHDAY)))
        val second = record(2, "Ann", events = listOf(Event("1991-02-02", TYPE_BIRTHDAY)))
        val choices = MergeChoices().pick(ConflictField.BIRTHDAY, second.key)
        val merged = buildMergedContact(first.contact, listOf(first, second), choices)
        assertEquals(listOf(Event("1991-02-02", TYPE_BIRTHDAY)), merged.events)
    }

    @Test
    fun onlyABitmapPhotoBoundForADeviceAccountNeedsAFile() {
        assertTrue(needsPhotoFile("", hasBitmap = true, toPrivate = false))
        assertFalse(needsPhotoFile("content://photo", hasBitmap = true, toPrivate = false))
        assertFalse(needsPhotoFile("", hasBitmap = true, toPrivate = true))
        assertFalse(needsPhotoFile("", hasBitmap = false, toPrivate = false))
    }
}
