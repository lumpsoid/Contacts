package org.fossify.contacts.models

import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactSource
import org.fossify.commons.models.contacts.Email
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeRecordTest {
    companion object {
        private const val GOOGLE = "me@gmail.com"
        private const val GOOGLE_TYPE = "com.google"
    }

    private fun contact(numbers: List<PhoneNumber> = emptyList(), emails: List<Email> = emptyList()) = Contact(
        id = 1,
        contactId = 1,
        source = GOOGLE,
        phoneNumbers = ArrayList(numbers),
        emails = ArrayList(emails)
    )

    @Test
    fun aNumberWithoutANormalizedFormIsKeyedByItsValue() {
        val keys = MergeRecord.detailKeysOf(contact(numbers = listOf(PhoneNumber("555 0100", 0, "", ""))))
        assertEquals(setOf("tel:555 0100"), keys)
    }

    @Test
    fun aNumberIsKeyedByItsNormalizedForm() {
        val keys = MergeRecord.detailKeysOf(contact(numbers = listOf(PhoneNumber("(555) 0100", 0, "", "5550100"))))
        assertEquals(setOf("tel:5550100"), keys)
    }

    @Test
    fun anEmailIsKeyedTrimmedAndLowerCase() {
        val keys = MergeRecord.detailKeysOf(contact(emails = listOf(Email(" A@B.com ", 0, ""))))
        assertEquals(setOf("mail:a@b.com"), keys)
    }

    @Test
    fun anAccountIsMatchedByNameAndTypeTogether() {
        val record = MergeRecord(contact(), GOOGLE_TYPE, AccountAccess.WRITABLE)
        assertFalse(record.isIn(ContactSource(GOOGLE, "org.other", "Other")))
        assertTrue(record.isIn(ContactSource(GOOGLE, GOOGLE_TYPE, "Google")))
    }
}
