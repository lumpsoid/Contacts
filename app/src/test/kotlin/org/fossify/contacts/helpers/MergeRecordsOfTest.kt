package org.fossify.contacts.helpers

import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.contacts.models.AccountAccess
import org.fossify.contacts.models.ExtraDetails
import org.fossify.contacts.models.LabeledValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeRecordsOfTest {
    companion object {
        private const val GOOGLE = "me@gmail.com"
        private const val GOOGLE_TYPE = "com.google"
    }

    private val sources = listOf(ContactSource(GOOGLE, GOOGLE_TYPE, GOOGLE))
    private val noReadOnly = emptySet<String>()

    private fun contact(id: Int, source: String = GOOGLE) = Contact(id = id, contactId = id, source = source)

    @Test
    fun aPrivateContactIsAPrivateRecordWithNoExtras() {
        val record = mergeRecordsOf(listOf(contact(1, SMT_PRIVATE)), sources, noReadOnly, null).single()
        assertEquals(SMT_PRIVATE, record.accountType)
        assertEquals(ExtraDetails(), record.extras)
    }

    @Test
    fun unreadableExtrasStayUnknownForDeviceContacts() {
        val record = mergeRecordsOf(listOf(contact(1)), sources, noReadOnly, null).single()
        assertEquals(GOOGLE_TYPE, record.accountType)
        assertNull(record.extras)
    }

    @Test
    fun aDeviceContactGetsItsExtrasById() {
        val sip = ExtraDetails(sipAddresses = listOf(LabeledValue("s", 1, "")))
        val records = mergeRecordsOf(listOf(contact(4), contact(5)), sources, noReadOnly, mapOf(4 to sip))
        assertEquals(sip, records[0].extras)
        assertEquals(ExtraDetails(), records[1].extras)
    }

    @Test
    fun anUnknownSourceHasABlankType() {
        val record = mergeRecordsOf(listOf(contact(1, "Z")), sources, noReadOnly, emptyMap()).single()
        assertEquals("", record.accountType)
        assertEquals(AccountAccess.of(null, noReadOnly), record.access)
    }
}
