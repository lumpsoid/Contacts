package org.fossify.contacts.helpers

import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctByLinkedContactTest {
    companion object {
        private const val GOOGLE = "me@gmail.com"
    }

    @Test
    fun deviceRowsOfOneSystemContactAreOneRow() {
        val rows = listOf(
            Contact(id = 1, contactId = 7, source = GOOGLE),
            Contact(id = 2, contactId = 7, source = GOOGLE),
            Contact(id = 3, contactId = 8, source = GOOGLE)
        )
        // Positive control in the same list: contact 8 is a row of its own.
        assertEquals(listOf(1, 3), rows.distinctByLinkedContact().map { it.id })
    }

    @Test
    fun privateRowsAreKeptEvenWhenTheyShareAContactId() {
        val rows = listOf(
            Contact(id = 1, contactId = 7, source = SMT_PRIVATE),
            Contact(id = 2, contactId = 7, source = SMT_PRIVATE)
        )
        assertEquals(listOf(1, 2), rows.distinctByLinkedContact().map { it.id })
    }
}
