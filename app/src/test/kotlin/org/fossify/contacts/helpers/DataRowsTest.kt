package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import org.fossify.contacts.models.DataRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRowsTest {
    private fun row(
        rawId: Int = 1,
        mimetype: String?,
        contactId: Int = 0,
        isPrimary: Int = 0,
        data1: String? = null,
        data2Int: Int = 0,
        data3: String? = null,
        data4: String? = null,
        data5: String? = null,
        data5Int: Int = 0,
        data6: String? = null,
        data7: String? = null,
        data8: String? = null,
        data9: String? = null,
        data10: String? = null,
        accountName: String? = null,
        starred: Int = 0
    ) = DataRow(
        rawId = rawId,
        contactId = contactId,
        mimetype = mimetype,
        isPrimary = isPrimary,
        data1 = data1,
        data2Int = data2Int,
        data3 = data3,
        data4 = data4,
        data5 = data5,
        data5Int = data5Int,
        data6 = data6,
        data7 = data7,
        data8 = data8,
        data9 = data9,
        data10 = data10,
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
    fun contactsKeepTheOrderOfTheirFirstRow() {
        val email = CommonDataKinds.Email.CONTENT_ITEM_TYPE
        val rows = listOf(
            row(rawId = 5, mimetype = email, data1 = "a@x.org"),
            row(rawId = 3, mimetype = email, data1 = "b@x.org"),
            row(rawId = 5, mimetype = email, data1 = "c@x.org")
        )

        val contacts = contactsOf(rows) { it }

        assertEquals(listOf(5, 3), contacts.keys.toList())
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
    fun aPhoneKeepsItsTypeLabelAndPrimaryFlag() {
        val rows = listOf(
            row(
                mimetype = CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                isPrimary = 1,
                data1 = "+1 555",
                data2Int = 2,
                data3 = "work"
            )
        )

        val numbers = contactsOf(rows) { "N:$it" }.getValue(1).phoneNumbers

        assertEquals(listOf(PhoneNumber("+1 555", 2, "work", "N:+1 555", true)), numbers)
    }

    @Test
    fun aPostalAddressMapsEachColumnToItsField() {
        val rows = listOf(
            row(
                mimetype = CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                data1 = "formatted",
                data2Int = 3,
                data3 = "label",
                data4 = "street",
                data5 = "pobox",
                data6 = "neighborhood",
                data7 = "city",
                data8 = "region",
                data9 = "postcode",
                data10 = "country"
            )
        )

        val addresses = contactsOf(rows) { it }.getValue(1).addresses

        assertEquals(
            listOf(
                Address(
                    value = "formatted",
                    type = 3,
                    label = "label",
                    country = "country",
                    region = "region",
                    city = "city",
                    postcode = "postcode",
                    pobox = "pobox",
                    street = "street",
                    neighborhood = "neighborhood"
                )
            ),
            addresses
        )
    }

    @Test
    fun anImKeepsItsValueProtocolAndCustomProtocol() {
        val rows = listOf(
            row(
                mimetype = CommonDataKinds.Im.CONTENT_ITEM_TYPE,
                data1 = "ann@im",
                data5 = "4",
                data5Int = 4,
                data6 = "label"
            )
        )

        val ims = contactsOf(rows) { it }.getValue(1).IMs

        assertEquals(listOf(IM("ann@im", 4, "label")), ims)
    }

    @Test
    fun anEventKeepsItsStartDateAndType() {
        val rows = listOf(
            row(mimetype = CommonDataKinds.Event.CONTENT_ITEM_TYPE, data1 = "2001-02-03", data2Int = 3)
        )

        val events = contactsOf(rows) { it }.getValue(1).events

        assertEquals(listOf(Event("2001-02-03", 3)), events)
    }

    @Test
    fun aNicknameIsKept() {
        val rows = listOf(row(mimetype = CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, data1 = "Nick"))

        assertEquals("Nick", contactsOf(rows) { it }.getValue(1).nickname)
    }

    @Test
    fun anOrganizationKeepsItsCompanyAndTitle() {
        val rows = listOf(
            row(mimetype = CommonDataKinds.Organization.CONTENT_ITEM_TYPE, data1 = "Acme", data4 = "Engineer")
        )

        assertEquals(Organization("Acme", "Engineer"), contactsOf(rows) { it }.getValue(1).organization)
    }

    @Test
    fun aWebsiteIsKept() {
        val rows = listOf(row(mimetype = CommonDataKinds.Website.CONTENT_ITEM_TYPE, data1 = "https://x.org"))

        assertEquals(listOf("https://x.org"), contactsOf(rows) { it }.getValue(1).websites)
    }

    @Test
    fun unknownMimetypesAddNothing() {
        val contact = contactsOf(listOf(row(mimetype = "x/unknown", data1 = "v"))) { it }.getValue(1)

        assertEquals(1, contact.id)
        assertTrue(contact.phoneNumbers.isEmpty())
        assertTrue(contact.emails.isEmpty())
    }
}
