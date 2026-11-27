package org.fossify.contacts.contentproviders

import org.fossify.commons.models.SimpleContact
import org.fossify.contacts.models.PrivateContactsQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateContactRowsTest {
    @Test
    fun noArgsMeansAllContactsWithNumbersOnly() {
        assertEquals(PrivateContactsQuery(false, true), privateContactsQueryOf(null))
    }

    @Test
    fun argsSelectFavoritesAndAllContacts() {
        assertEquals(PrivateContactsQuery(true, false), privateContactsQueryOf(arrayOf("1", "0")))
    }

    @Test
    fun aRowCarriesTheContactInColumnOrder() {
        val contact = SimpleContact(3, 4, "Ann", "", arrayListOf(), arrayListOf("1990-01-02"), arrayListOf())
        assertEquals(PRIVATE_CONTACT_COLUMNS.size, privateContactRow(contact).size)
        assertEquals(listOf(3, 4, "Ann", "", "[]", "[\"1990-01-02\"]", "[]"), privateContactRow(contact))
    }
}
