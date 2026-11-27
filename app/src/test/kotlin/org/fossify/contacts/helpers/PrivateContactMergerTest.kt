package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrivateContactMergerTest {
    @Test
    fun aMergeTakesTheNameOfTheFirstContactThatHasOne() {
        val target = Contact(id = 1, contactId = 1, source = "A")
        val other = Contact(id = 2, contactId = 2, source = "A", firstName = "Bo")
        assertEquals("Bo", mergeContactData(target, listOf(other)).firstName)
    }

    @Test
    fun aMergeDoesNotAddANumberTheTargetHasAndAddsOthersAsNotPrimary() {
        val target = Contact(
            id = 1,
            contactId = 1,
            source = "A",
            phoneNumbers = arrayListOf(PhoneNumber("111", 0, "", "111"))
        )
        val other = Contact(
            id = 2,
            contactId = 2,
            source = "A",
            phoneNumbers = arrayListOf(
                PhoneNumber("111", 0, "", "111"),
                PhoneNumber("222", 0, "", "222", isPrimary = true)
            )
        )
        val merged = mergeContactData(target, listOf(other))
        assertEquals(listOf("111", "222"), merged.phoneNumbers.map { it.value })
        assertFalse(merged.phoneNumbers.single { it.value == "222" }.isPrimary)
    }

    @Test
    fun aMergeIsStarredWhenAnyContactIsStarred() {
        val target = Contact(id = 1, contactId = 1, source = "A", starred = 0)
        val other = Contact(id = 2, contactId = 2, source = "A", starred = 1)
        assertEquals(1, mergeContactData(target, listOf(other)).starred)
    }

    @Test
    fun aMergeKeepsOneGroupForAGroupIdTwoContactsShare() {
        val target = Contact(
            id = 1,
            contactId = 1,
            source = "A",
            groups = arrayListOf(Group(7L, "Family"))
        )
        val other = Contact(
            id = 2,
            contactId = 2,
            source = "A",
            groups = arrayListOf(Group(7L, "Family"))
        )
        assertEquals(listOf(7L), mergeContactData(target, listOf(other)).groups.map { it.id })
    }
}
