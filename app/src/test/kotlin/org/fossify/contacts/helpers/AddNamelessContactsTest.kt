package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.junit.Assert.assertEquals
import org.junit.Test

class AddNamelessContactsTest {
    companion object {
        private const val ID_A = 10
        private const val ID_B = 11
        private const val ID_C = 12
        private const val CONTACT_A = 20
        private const val CONTACT_B = 30
        private const val CONTACT_C = 31
    }

    private fun named(id: Int, contactId: Int) =
        Contact(id = id, contactId = contactId, firstName = "Named $id", source = "acc")

    private fun phoneOnly(id: Int, contactId: Int) = Contact(
        id = id,
        contactId = contactId,
        source = "acc",
        phoneNumbers = arrayListOf(PhoneNumber("555$id", 2, "", "555$id"))
    )

    private fun emailOnly(id: Int, contactId: Int) = Contact(
        id = id,
        contactId = contactId,
        source = "acc",
        emails = arrayListOf(Email("a$id@x.org", 1, ""))
    )

    private fun noteOnly(id: Int, contactId: Int) =
        Contact(id = id, contactId = contactId, source = "acc", notes = "note")

    private fun ids(contacts: List<Contact>) = contacts.map { it.id }

    @Test
    fun standaloneNamelessContactIsAdded() {
        val result = addNamelessContacts(
            listed = listOf(named(ID_A, CONTACT_A)),
            nameless = listOf(phoneOnly(ID_B, CONTACT_B)),
            mergeLinked = true,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_A, ID_B), ids(result))
    }

    @Test
    fun namelessPartOfListedContactIsMergedAway() {
        val result = addNamelessContacts(
            listed = listOf(named(ID_A, CONTACT_A)),
            nameless = listOf(phoneOnly(ID_B, CONTACT_A)),
            mergeLinked = true,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_A), ids(result))
    }

    @Test
    fun namelessPartIsOwnRowWhenNotMerging() {
        val result = addNamelessContacts(
            listed = listOf(named(ID_A, CONTACT_A)),
            nameless = listOf(phoneOnly(ID_B, CONTACT_A)),
            mergeLinked = false,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_A, ID_B), ids(result))
    }

    @Test
    fun namelessPartsOfOneUnlistedContactShowOnce() {
        val merged = addNamelessContacts(
            listed = emptyList(),
            nameless = listOf(phoneOnly(ID_B, CONTACT_B), emailOnly(ID_C, CONTACT_B)),
            mergeLinked = true,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_B), ids(merged))

        val separate = addNamelessContacts(
            listed = emptyList(),
            nameless = listOf(phoneOnly(ID_B, CONTACT_B), emailOnly(ID_C, CONTACT_B)),
            mergeLinked = false,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_B, ID_C), ids(separate))
    }

    @Test
    fun blankNamelessContactIsLeftOut() {
        val onlyBlank = addNamelessContacts(
            listed = emptyList(),
            nameless = listOf(noteOnly(ID_B, CONTACT_B)),
            mergeLinked = false,
            onlyWithNumbers = false
        )
        assertEquals(emptyList<Int>(), ids(onlyBlank))

        val withPositiveControl = addNamelessContacts(
            listed = emptyList(),
            nameless = listOf(noteOnly(ID_B, CONTACT_B), phoneOnly(ID_C, CONTACT_C)),
            mergeLinked = false,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_C), ids(withPositiveControl))
    }

    @Test
    fun onlyWithNumbersDropsNamelessWithoutPhone() {
        val withNumbers = addNamelessContacts(
            listed = emptyList(),
            nameless = listOf(emailOnly(ID_B, CONTACT_B), phoneOnly(ID_C, CONTACT_C)),
            mergeLinked = false,
            onlyWithNumbers = true
        )
        assertEquals(listOf(ID_C), ids(withNumbers))

        val withoutFilter = addNamelessContacts(
            listed = emptyList(),
            nameless = listOf(emailOnly(ID_B, CONTACT_B), phoneOnly(ID_C, CONTACT_C)),
            mergeLinked = false,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_B, ID_C), ids(withoutFilter))
    }

    @Test
    fun privateListedContactDoesNotHideNameless() {
        val privateListed = Contact(
            id = ID_A,
            contactId = CONTACT_B,
            firstName = "P",
            source = "smt_private"
        )
        val result = addNamelessContacts(
            listed = listOf(privateListed),
            nameless = listOf(phoneOnly(ID_B, CONTACT_B)),
            mergeLinked = true,
            onlyWithNumbers = false
        )
        assertEquals(listOf(ID_A, ID_B), ids(result))
    }
}
