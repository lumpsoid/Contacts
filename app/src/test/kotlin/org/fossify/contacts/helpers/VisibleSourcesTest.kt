package org.fossify.contacts.helpers

import org.fossify.commons.models.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleSourcesTest {
    companion object {
        private const val ID_A = 10
        private const val ID_B = 11
        private const val ID_C = 12
        private const val ID_PRIVATE = 13
    }

    private fun contact(id: Int, source: String) = Contact(id = id, contactId = id, firstName = "Ann", source = source)

    @Test
    fun keepsOnlyContactsFromTheGivenSources() {
        val contacts = listOf(contact(ID_A, "a"), contact(ID_B, "b"), contact(ID_C, "c"))
        val result = contacts.inSources(listOf("a", "c"))
        assertEquals(listOf(ID_A, ID_C), result.map { it.id })
    }

    @Test
    fun noSourcesKeepsNothing() {
        assertTrue(listOf(contact(ID_A, "a")).inSources(emptyList()).isEmpty())
    }

    @Test
    fun aPrivateContactNeedsThePrivateSource() {
        val privateContact = contact(ID_PRIVATE, "smt_private")
        assertTrue(listOf(privateContact).inSources(listOf("acc")).isEmpty())
        assertEquals(listOf(ID_PRIVATE), listOf(privateContact).inSources(listOf("acc", "smt_private")).map { it.id })
    }
}
