package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Address
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressFieldsTest {
    @Test
    fun aFormattedOnlyAddressGoesToTheStreet() {
        val address = Address("Main St 1\n12345 Town", 1, "")
        assertEquals("Main St 1\n12345 Town", address.streetForStructuredEdit())
    }

    @Test
    fun aStructuredAddressKeepsItsStreet() {
        val address = Address("ignored", 1, "", city = "Town", street = "Main St 1")
        assertEquals("Main St 1", address.streetForStructuredEdit())
    }

    @Test
    fun componentsWithoutStreetKeepAnEmptyStreet() {
        val address = Address("12345 Town", 1, "", city = "Town", postcode = "12345")
        assertEquals("", address.streetForStructuredEdit())
    }

    @Test
    fun anEmptyAddressStaysEmpty() {
        val address = Address("", 1, "")
        assertEquals("", address.streetForStructuredEdit())
    }
}
