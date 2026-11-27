package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.contacts.models.DataRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRowsTest {
    private fun row(
        rawId: Int = 1,
        mimetype: String?,
        contactId: Int = 0,
        data1: String? = null,
        data4: String? = null,
        accountName: String? = null,
        starred: Int = 0
    ) = DataRow(
        rawId = rawId,
        contactId = contactId,
        mimetype = mimetype,
        isPrimary = 0,
        data1 = data1,
        data2Int = 0,
        data3 = null,
        data4 = data4,
        data5 = null,
        data5Int = 0,
        data6 = null,
        data7 = null,
        data8 = null,
        data9 = null,
        data10 = null,
        accountName = accountName,
        starred = starred,
        photoUri = null,
        photoThumbnailUri = null,
        customRingtone = null
    )

    @Test
    fun rowsOfOneRawContactBuildOneContact() {
        val email = CommonDataKinds.Email.CONTENT_ITEM_TYPE
        val rows = listOf(
            row(rawId = 1, mimetype = email, data1 = "a@x.org"),
            row(rawId = 2, mimetype = email, data1 = "c@x.org"),
            row(rawId = 1, mimetype = email, data1 = "b@x.org")
        )

        val contacts = contactsOf(rows) { it }

        assertEquals(listOf(1, 2), contacts.keys.toList())
        assertEquals(listOf("a@x.org", "b@x.org"), contacts.getValue(1).emails.map { it.value })
        assertEquals(listOf("c@x.org"), contacts.getValue(2).emails.map { it.value })
    }

    @Test
    fun theFirstRowSetsTheContactFields() {
        val rows = listOf(
            row(mimetype = CommonDataKinds.Note.CONTENT_ITEM_TYPE, accountName = "A", starred = 1, contactId = 7)
        )

        val contact = contactsOf(rows) { it }.getValue(1)

        assertEquals("A", contact.source)
        assertEquals(1, contact.starred)
        assertEquals(7, contact.contactId)
    }

    @Test
    fun aPhoneWithoutNormalizedNumberIsNormalizedByTheGivenFunction() {
        val phone = CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        val rows = listOf(
            row(mimetype = phone, data1 = "+1 555"),
            row(mimetype = phone, data1 = "+2 666", data4 = "+2666")
        )

        val numbers = contactsOf(rows) { "N:$it" }.getValue(1).phoneNumbers

        assertEquals("N:+1 555", numbers[0].normalizedNumber)
        assertEquals("+2666", numbers[1].normalizedNumber)
    }

    @Test
    fun unknownMimetypesAddNothing() {
        val contact = contactsOf(listOf(row(mimetype = "x/unknown", data1 = "v"))) { it }.getValue(1)

        assertEquals(1, contact.id)
        assertTrue(contact.phoneNumbers.isEmpty())
        assertTrue(contact.emails.isEmpty())
    }
}
