package org.fossify.contacts.helpers

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactSearchTest {
    private fun digitsOnly(text: String) = text.filter { it.isDigit() || it == '+' }
    private fun query(raw: String) = searchQuery(raw, ::digitsOnly)
    private fun named(id: Int, first: String) = Contact(id = id, contactId = id, firstName = first)
    private fun ids(list: List<Contact>) = list.map { it.id }

    private fun numbered() = Contact(
        id = 1,
        contactId = 1,
        firstName = "Ann",
        phoneNumbers = arrayListOf(PhoneNumber("+1 555-0100", 0, "", "+15550100"))
    )

    @Test
    fun queryIsTrimmedAndCollapsed() {
        assertEquals(SearchQuery("a b", ""), searchQuery("  a \t  b ", ::digitsOnly))
        assertEquals("55501", searchQuery("555 01", ::digitsOnly).digits)
    }

    @Test
    fun nameMatchesIgnoringCase() {
        val contacts = listOf(named(1, "Anna"), named(2, "Bob"))
        assertEquals(listOf(1), ids(contacts.matching(query("ann"))))
    }

    @Test
    fun queryWithoutDiacriticsFindsNamesWithThem() {
        val contacts = listOf(named(1, "Čech"), named(2, "Cech"))
        assertEquals(listOf(1, 2), ids(contacts.matching(query("cech"))))
    }

    @Test
    fun queryWithDiacriticsMatchesAsTyped() {
        val contacts = listOf(named(1, "Čech"), named(2, "Cech"))
        assertEquals(listOf(1), ids(contacts.matching(query("Čech"))))
    }

    @Test
    fun numberMatchesTheNormalizedNumber() {
        val contacts = listOf(numbered())
        assertEquals(listOf(1), ids(contacts.matching(query("5550100"))))
        assertEquals(emptyList<Int>(), ids(contacts.matching(query("555 0100"))))
    }

    @Test
    fun numberIsNotMatchedWithoutDigits() {
        val contacts = listOf(numbered())
        assertEquals(emptyList<Int>(), ids(contacts.matching(SearchQuery("5550100", ""))))
    }

    @Test
    fun namelessContactIsFoundByItsNumber() {
        val contact = Contact(
            id = 1,
            contactId = 1,
            phoneNumbers = arrayListOf(PhoneNumber("555-0100", 0, "", "5550100"))
        )
        assertEquals(listOf(1), ids(listOf(contact).matching(query("0100"))))
    }

    @Test
    fun otherFieldsMatch() {
        val contacts = listOf(
            Contact(id = 1, contactId = 1, firstName = "X1", nickname = "Bobby"),
            Contact(id = 2, contactId = 2, firstName = "X2", emails = arrayListOf(Email("bobby@mail.org", 0, ""))),
            Contact(id = 3, contactId = 3, firstName = "X3", addresses = arrayListOf(Address("Bobby street 1", 0, ""))),
            Contact(id = 4, contactId = 4, firstName = "X4", IMs = arrayListOf(IM("bobby_im", 0, ""))),
            Contact(id = 5, contactId = 5, firstName = "X5", notes = "met bobby"),
            Contact(id = 6, contactId = 6, firstName = "X6", organization = Organization("Bobby Inc", "")),
            Contact(id = 7, contactId = 7, firstName = "X7", organization = Organization("", "bobby keeper")),
            Contact(id = 8, contactId = 8, firstName = "X8", websites = arrayListOf("bobby.org")),
            Contact(id = 9, contactId = 9, firstName = "X9")
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), ids(contacts.matching(query("bobby"))))
    }

    @Test
    fun nameMatchesComeFirstOtherwiseOrderIsKept() {
        val contacts = listOf(
            Contact(id = 1, contactId = 1, firstName = "Zed", notes = "ann"),
            Contact(id = 2, contactId = 2, firstName = "Anna"),
            Contact(id = 3, contactId = 3, firstName = "Yan", notes = "ann"),
            Contact(id = 4, contactId = 4, firstName = "Joanne")
        )
        assertEquals(listOf(2, 4, 1, 3), ids(contacts.matching(query("ann"))))
    }
}
